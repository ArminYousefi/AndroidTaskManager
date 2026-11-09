package services

import domain.BoardRole
import domain.Permission
import domain.RolePolicy
import domain.TeamRole
import models.BoardMembers
import models.BoardTeams
import models.Lists
import models.TeamMembers
import models.Tasks
import models.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import plugins.ApiException

/**
 * Resolves *who may do what*. A user's effective role on a board is the strongest of their direct
 * membership and the roles granted to any team they belong to (which has been given access to the board).
 * All authorisation in the server goes through here.
 */
object AccessService {

    /**
     * @param boardId board to check.
     * @param userId the caller.
     * @return the caller's effective [BoardRole], or null when they have no access.
     */
    fun boardRole(boardId: String, userId: String): BoardRole? = transaction {
        val direct = BoardMembers.select { (BoardMembers.boardId eq boardId) and (BoardMembers.userId eq userId) }
            .firstOrNull()?.let { BoardRole.parseOrNull(it[BoardMembers.role]) }
        val teamIds = teamIdsOf(userId)
        val viaTeam = if (teamIds.isEmpty()) null else
            BoardTeams.select { (BoardTeams.boardId eq boardId) and (BoardTeams.teamId inList teamIds) }
                .mapNotNull { BoardRole.parseOrNull(it[BoardTeams.role]) }.maxByOrNull { it.rank }
        BoardRole.strongest(direct, viaTeam)
    }

    /**
     * Ensures the caller may perform [permission] on [boardId].
     *
     * @throws ApiException.NotFound when the caller has no access at all (existence is not revealed).
     * @throws ApiException.Forbidden when the caller's role is too weak.
     * @return the caller's role.
     */
    fun require(boardId: String, userId: String, permission: Permission): BoardRole {
        val role = boardRole(boardId, userId) ?: throw ApiException.NotFound("Board not found")
        if (!RolePolicy.can(role, permission)) throw ApiException.Forbidden()
        return role
    }

    /** @return ids of every user who can see [boardId] (direct members plus members of attached teams). */
    fun memberIds(boardId: String): Set<String> = transaction {
        val direct = BoardMembers.select { BoardMembers.boardId eq boardId }.map { it[BoardMembers.userId] }
        val teamIds = BoardTeams.select { BoardTeams.boardId eq boardId }.map { it[BoardTeams.teamId] }
        val viaTeams = if (teamIds.isEmpty()) emptyList() else
            TeamMembers.select { TeamMembers.teamId inList teamIds }.map { it[TeamMembers.userId] }
        (direct + viaTeams).toSet()
    }

    /** @return ids of all teams [userId] belongs to. */
    fun teamIdsOf(userId: String): List<String> = transaction {
        TeamMembers.select { TeamMembers.userId eq userId }.map { it[TeamMembers.teamId] }
    }

    /** @return the caller's [TeamRole] in [teamId], or null when not a member. */
    fun teamRole(teamId: String, userId: String): TeamRole? = transaction {
        TeamMembers.select { (TeamMembers.teamId eq teamId) and (TeamMembers.userId eq userId) }
            .firstOrNull()?.let { TeamRole.parseOrNull(it[TeamMembers.role]) }
    }

    /**
     * Ensures the caller belongs to [teamId] (and is a team admin when [adminOnly]).
     *
     * @throws ApiException.NotFound for non-members; [ApiException.Forbidden] for non-admins when [adminOnly].
     */
    fun requireTeam(teamId: String, userId: String, adminOnly: Boolean): TeamRole {
        val role = teamRole(teamId, userId) ?: throw ApiException.NotFound("Team not found")
        if (adminOnly && role != TeamRole.ADMIN) throw ApiException.Forbidden("Only team admins can do this")
        return role
    }

    /** @return the board id owning column [listId], or null. */
    fun boardIdOfList(listId: String): String? = transaction {
        Lists.select { Lists.id eq listId }.firstOrNull()?.get(Lists.boardId)
    }

    /** @return the board id owning task [taskId], or null. */
    fun boardIdOfTask(taskId: String): String? = transaction {
        Tasks.select { Tasks.id eq taskId }.firstOrNull()?.get(Tasks.boardId)
    }

    /** @return display name (nickname, falling back to username) of [userId]; "Unknown" if missing. */
    fun displayName(userId: String?): String = if (userId == null) "Unknown" else transaction {
        Users.select { Users.id eq userId }.firstOrNull()?.let { it[Users.nickname]?.takeIf { n -> n.isNotBlank() } ?: it[Users.username] } ?: "Unknown"
    }
}
