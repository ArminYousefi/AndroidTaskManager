package services

import domain.TeamRole
import kotlinx.serialization.json.JsonPrimitive
import models.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import plugins.ApiException
import java.util.UUID

/** Teams: named groups of users that can be granted access to boards as a unit. */
object TeamService {
    private fun validName(raw: String): String {
        val n = raw.trim()
        if (n.length !in 2..100) throw ApiException.Validation("Team name must be 2-100 characters")
        return n
    }

    /** @return teams [userId] belongs to, with their role and member count. */
    fun list(userId: String): List<TeamDto> = transaction {
        val mine = TeamMembers.select { TeamMembers.userId eq userId }.associate { it[TeamMembers.teamId] to it[TeamMembers.role] }
        if (mine.isEmpty()) return@transaction emptyList()
        val counts = TeamMembers.select { TeamMembers.teamId inList mine.keys }.groupingBy { it[TeamMembers.teamId] }.eachCount()
        Teams.select { Teams.id inList mine.keys }.map {
            TeamDto(it[Teams.id], it[Teams.name], it[Teams.description], it[Teams.ownerId], mine[it[Teams.id]] ?: "MEMBER", counts[it[Teams.id]] ?: 0)
        }.sortedBy { it.name.lowercase() }
    }

    /** Creates a team; the creator becomes owner and team admin. */
    fun create(userId: String, req: TeamRequest): TeamDto {
        val name = validName(req.name)
        val id = UUID.randomUUID().toString()
        transaction {
            Teams.insert { it[Teams.id] = id; it[Teams.name] = name; it[Teams.description] = req.description?.take(500); it[Teams.ownerId] = userId; it[Teams.createdAt] = System.currentTimeMillis() }
            TeamMembers.insert { it[TeamMembers.teamId] = id; it[TeamMembers.userId] = userId; it[TeamMembers.role] = TeamRole.ADMIN.name }
        }
        return TeamDto(id, name, req.description?.take(500), userId, "ADMIN", 1)
    }

    /** Renames/re-describes a team. Team admins only. */
    fun update(userId: String, teamId: String, req: TeamRequest): TeamDto {
        AccessService.requireTeam(teamId, userId, adminOnly = true)
        val name = validName(req.name)
        transaction { Teams.update({ Teams.id eq teamId }) { it[Teams.name] = name; it[Teams.description] = req.description?.take(500) } }
        return list(userId).first { it.id == teamId }
    }

    /** Deletes a team (and every board grant it had). Owner only. */
    fun delete(userId: String, teamId: String) {
        val team = transaction { Teams.select { Teams.id eq teamId }.firstOrNull() } ?: throw ApiException.NotFound("Team not found")
        AccessService.requireTeam(teamId, userId, adminOnly = false)
        if (team[Teams.ownerId] != userId) throw ApiException.Forbidden("Only the team owner can delete the team")
        val affectedBoards = transaction { BoardTeams.select { BoardTeams.teamId eq teamId }.map { it[BoardTeams.boardId] } }
        val affectedUsers = transaction { TeamMembers.select { TeamMembers.teamId eq teamId }.map { it[TeamMembers.userId] } }
        transaction { Teams.deleteWhere { Teams.id eq teamId } }
        notifyMembership(affectedUsers)
        affectedBoards.forEach { b -> ActivityService.record(b, null, userId, domain.ActivityType.TEAM_REMOVED, mapOf("team" to team[Teams.name])) }
    }

    /** @return members of the team. Any member may view. */
    fun members(userId: String, teamId: String): List<MemberDto> {
        AccessService.requireTeam(teamId, userId, adminOnly = false)
        return transaction {
            val rows = TeamMembers.select { TeamMembers.teamId eq teamId }.toList()
            val users = Users.select { Users.id inList rows.map { it[TeamMembers.userId] } }.associateBy { it[Users.id] }
            rows.mapNotNull { r -> users[r[TeamMembers.userId]]?.let { u -> MemberDto(u[Users.id], u[Users.username], u[Users.nickname], r[TeamMembers.role]) } }
                .sortedBy { (it.nickname ?: it.username).lowercase() }
        }
    }

    /** Adds a user by username. Team admins only. @throws ApiException.Conflict `ALREADY_MEMBER`. */
    fun addMember(userId: String, teamId: String, req: AddMemberRequest): MemberDto {
        AccessService.requireTeam(teamId, userId, adminOnly = true)
        val role = TeamRole.parseOrNull(req.role) ?: TeamRole.MEMBER
        val user = transaction { Users.select { Users.username eq req.username.trim() }.firstOrNull() } ?: throw ApiException.NotFound("No user named '${req.username.trim()}'")
        val uid = user[Users.id]
        if (AccessService.teamRole(teamId, uid) != null) throw ApiException.Conflict("Already a member of this team", "ALREADY_MEMBER")
        transaction { TeamMembers.insert { it[TeamMembers.teamId] = teamId; it[TeamMembers.userId] = uid; it[TeamMembers.role] = role.name } }
        notifyMembership(listOf(uid))
        return MemberDto(uid, user[Users.username], user[Users.nickname], role.name)
    }

    /** Changes a member's team role. Team admins only; the owner stays admin. */
    fun updateRole(userId: String, teamId: String, targetId: String, role: String) {
        AccessService.requireTeam(teamId, userId, adminOnly = true)
        val r = TeamRole.parseOrNull(role) ?: throw ApiException.Validation("Unknown role")
        val owner = transaction { Teams.select { Teams.id eq teamId }.first()[Teams.ownerId] }
        if (targetId == owner && r != TeamRole.ADMIN) throw ApiException.Forbidden("The team owner is always an admin")
        val n = transaction { TeamMembers.update({ (TeamMembers.teamId eq teamId) and (TeamMembers.userId eq targetId) }) { it[TeamMembers.role] = r.name } }
        if (n == 0) throw ApiException.NotFound("Member not found")
        notifyMembership(listOf(targetId))
    }

    /** Removes a member (admins) or lets a member leave. The owner cannot be removed. */
    fun removeMember(userId: String, teamId: String, targetId: String) {
        AccessService.requireTeam(teamId, userId, adminOnly = userId != targetId)
        val owner = transaction { Teams.select { Teams.id eq teamId }.first()[Teams.ownerId] }
        if (targetId == owner) throw ApiException.Forbidden("The team owner cannot be removed")
        transaction { TeamMembers.deleteWhere { (TeamMembers.teamId eq teamId) and (TeamMembers.userId eq targetId) } }
        notifyMembership(listOf(targetId))
    }

    private fun notifyMembership(userIds: Collection<String>) {
        WebSocketHub.toUsers(userIds, "board_membership_changed", "member", null, JsonPrimitive("team"))
    }
}
