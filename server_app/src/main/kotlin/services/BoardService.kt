package services

import domain.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import models.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import plugins.ApiException
import java.util.UUID

/**
 * Boards, their columns, and who has access to them (members and teams).
 * Every public function authorises through [AccessService] before touching data and records the
 * change in the activity log.
 */
object BoardService {
    private val json = Json { encodeDefaults = true }

    // ------------------------------------------------------------------ boards

    /** @return every board [userId] can see (direct or via a team) with role and progress counts, newest first. */
    fun listFor(userId: String): List<BoardDto> = transaction {
        val roles = HashMap<String, BoardRole>()
        BoardMembers.select { BoardMembers.userId eq userId }.forEach { r ->
            BoardRole.parseOrNull(r[BoardMembers.role])?.let { roles.merge(r[BoardMembers.boardId], it) { a, b -> if (a.rank >= b.rank) a else b } }
        }
        val teamIds = AccessService.teamIdsOf(userId)
        if (teamIds.isNotEmpty()) BoardTeams.select { BoardTeams.teamId inList teamIds }.forEach { r ->
            BoardRole.parseOrNull(r[BoardTeams.role])?.let { roles.merge(r[BoardTeams.boardId], it) { a, b -> if (a.rank >= b.rank) a else b } }
        }
        if (roles.isEmpty()) return@transaction emptyList()
        val ids = roles.keys.toList()
        val listCounts = Lists.select { Lists.boardId inList ids }.groupingBy { it[Lists.boardId] }.eachCount()
        val doneListIds = Lists.select { (Lists.boardId inList ids) and (Lists.category eq ColumnCategory.DONE.name) }.map { it[Lists.id] }
        val cnt = Tasks.id.count()
        val taskCounts = Tasks.slice(Tasks.boardId, cnt).select { (Tasks.boardId inList ids) and (Tasks.isDeleted eq false) }
            .groupBy(Tasks.boardId).associate { it[Tasks.boardId] to it[cnt].toInt() }
        val doneCounts = if (doneListIds.isEmpty()) emptyMap() else
            Tasks.slice(Tasks.boardId, cnt).select { (Tasks.listId inList doneListIds) and (Tasks.isDeleted eq false) }
                .groupBy(Tasks.boardId).associate { it[Tasks.boardId] to it[cnt].toInt() }
        Boards.select { Boards.id inList ids }.orderBy(Boards.updatedAt to SortOrder.DESC).map {
            val id = it[Boards.id]
            BoardDto(id, it[Boards.title], it[Boards.key], it[Boards.description], it[Boards.userId], roles[id]!!.name,
                it[Boards.createdAt], it[Boards.updatedAt], listCounts[id] ?: 0, taskCounts[id] ?: 0, doneCounts[id] ?: 0)
        }
    }

    /** @return one board visible to [userId]. @throws ApiException.NotFound when no access. */
    fun get(userId: String, boardId: String): BoardDto {
        AccessService.require(boardId, userId, Permission.VIEW_BOARD)
        return listFor(userId).firstOrNull { it.id == boardId } ?: throw ApiException.NotFound("Board not found")
    }

    /**
     * Creates a board from a [BoardTemplate] (default: To Do / In Progress / In Review / Done),
     * makes the creator its ADMIN, and logs it. A client-supplied id makes offline creation idempotent.
     */
    fun create(userId: String, req: CreateBoardRequest): BoardDto {
        val name = req.name.trim()
        if (name.length !in 1..100) throw ApiException.Validation("Board name must be 1-100 characters")
        val id = req.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val existing = transaction { Boards.select { Boards.id eq id }.firstOrNull() }
        if (existing != null) {
            if (existing[Boards.userId] != userId) throw ApiException.Conflict("Board id already in use")
            return get(userId, id)
        }
        val now = System.currentTimeMillis()
        val template = BoardTemplate.parse(req.template)
        transaction {
            Boards.insert {
                it[Boards.id] = id; it[Boards.userId] = userId; it[Boards.title] = name
                it[Boards.key] = BoardKeys.normalizeOrGenerate(req.key, name)
                it[Boards.description] = req.description?.take(2000)
                it[Boards.createdAt] = now; it[Boards.updatedAt] = now
            }
            BoardMembers.insert { it[BoardMembers.boardId] = id; it[BoardMembers.userId] = userId; it[BoardMembers.role] = BoardRole.ADMIN.name }
            template.columns.forEachIndexed { i, c ->
                Lists.insert {
                    it[Lists.id] = UUID.randomUUID().toString(); it[Lists.boardId] = id; it[Lists.title] = c.title
                    it[Lists.position] = i; it[Lists.category] = c.category.name; it[Lists.wipLimit] = c.wipLimit
                    it[Lists.createdAt] = now; it[Lists.updatedAt] = now
                }
            }
        }
        ActivityService.record(id, null, userId, ActivityType.BOARD_CREATED, mapOf("name" to name))
        return get(userId, id)
    }

    /** Renames / re-describes a board. ADMIN only. */
    fun update(userId: String, boardId: String, req: UpdateBoardRequest): BoardDto {
        AccessService.require(boardId, userId, Permission.EDIT_BOARD)
        val name = req.name.trim()
        if (name.length !in 1..100) throw ApiException.Validation("Board name must be 1-100 characters")
        val old = transaction { Boards.select { Boards.id eq boardId }.first() }
        transaction { Boards.update({ Boards.id eq boardId }) { it[Boards.title] = name; it[Boards.description] = req.description?.take(2000); it[Boards.updatedAt] = System.currentTimeMillis() } }
        if (old[Boards.title] != name) ActivityService.record(boardId, null, userId, ActivityType.BOARD_RENAMED, mapOf("from" to old[Boards.title], "to" to name))
        if ((old[Boards.description] ?: "") != (req.description ?: "")) ActivityService.record(boardId, null, userId, ActivityType.BOARD_UPDATED, mapOf("field" to "description"))
        WebSocketHub.toBoard(boardId, "board_updated", "board", JsonPrimitive(boardId))
        return get(userId, boardId)
    }

    /** Deletes a board with everything on it. ADMIN only; members are told to drop it from their lists. */
    fun delete(userId: String, boardId: String) {
        AccessService.require(boardId, userId, Permission.DELETE_BOARD)
        val audience = AccessService.memberIds(boardId)
        transaction { Boards.deleteWhere { Boards.id eq boardId } }
        WebSocketHub.toUsers(audience, "board_deleted", "board", boardId, JsonPrimitive(boardId))
    }

    /** Soft-deletes every task on the board (columns stay). ADMIN only. @return number of tasks removed. */
    fun empty(userId: String, boardId: String): Int {
        AccessService.require(boardId, userId, Permission.EMPTY_BOARD)
        val n = transaction { Tasks.update({ (Tasks.boardId eq boardId) and (Tasks.isDeleted eq false) }) { it[Tasks.isDeleted] = true; it[Tasks.updatedAt] = System.currentTimeMillis() } }
        ActivityService.record(boardId, null, userId, ActivityType.BOARD_EMPTIED, mapOf("count" to n.toString()))
        WebSocketHub.toBoard(boardId, "board_emptied", "board", JsonPrimitive(boardId))
        return n
    }

    // ------------------------------------------------------------------ columns

    /** @return the board's columns ordered left to right. */
    fun lists(userId: String, boardId: String): List<ListDto> {
        AccessService.require(boardId, userId, Permission.VIEW_BOARD)
        return transaction { Lists.select { Lists.boardId eq boardId }.orderBy(Lists.position to SortOrder.ASC).map(::toListDto) }
    }

    /** Adds a column (appended at the end unless a valid position is given). ADMIN only. */
    fun createList(userId: String, req: ListDto): ListDto {
        AccessService.require(req.boardId, userId, Permission.MANAGE_COLUMNS)
        val title = validColumnTitle(req.title)
        val wip = validWip(req.wipLimit)
        val id = req.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val result = transaction {
            Lists.select { Lists.id eq id }.firstOrNull()?.let { return@transaction toListDto(it) }
            val ordered = Lists.select { Lists.boardId eq req.boardId }.orderBy(Lists.position to SortOrder.ASC).map { it[Lists.id] }
            val newOrder = Ordering.moveTo(ordered, id, if (req.position in 0..ordered.size) req.position else ordered.size)
            Lists.insert {
                it[Lists.id] = id; it[Lists.boardId] = req.boardId; it[Lists.title] = title; it[Lists.position] = 0
                it[Lists.category] = ColumnCategory.parse(req.category).name; it[Lists.wipLimit] = wip
                it[Lists.createdAt] = now; it[Lists.updatedAt] = now
            }
            Ordering.positions(newOrder).forEach { (lid, pos) -> Lists.update({ Lists.id eq lid }) { it[Lists.position] = pos } }
            toListDto(Lists.select { Lists.id eq id }.first())
        }
        ActivityService.record(req.boardId, null, userId, ActivityType.COLUMN_CREATED, mapOf("column" to title))
        WebSocketHub.toBoard(req.boardId, "list_created", "list", JsonPrimitive(id))
        return result
    }

    /** Renames a column / changes its category or WIP limit. ADMIN only. Order is changed via [reorderLists]. */
    fun updateList(userId: String, listId: String, req: ListDto): ListDto {
        val boardId = AccessService.boardIdOfList(listId) ?: throw ApiException.NotFound("Column not found")
        AccessService.require(boardId, userId, Permission.MANAGE_COLUMNS)
        val title = validColumnTitle(req.title)
        val wip = validWip(req.wipLimit)
        val cat = ColumnCategory.parse(req.category)
        val old = transaction { Lists.select { Lists.id eq listId }.first() }
        transaction {
            Lists.update({ Lists.id eq listId }) { it[Lists.title] = title; it[Lists.category] = cat.name; it[Lists.wipLimit] = wip; it[Lists.updatedAt] = System.currentTimeMillis() }
            // keep the denormalised status of the column's tasks in step with its category
            if (old[Lists.category] != cat.name) Tasks.update({ Tasks.listId eq listId }) { it[Tasks.status] = cat.name }
        }
        if (old[Lists.title] != title) ActivityService.record(boardId, null, userId, ActivityType.COLUMN_RENAMED, mapOf("from" to old[Lists.title], "to" to title))
        if (old[Lists.wipLimit] != wip) ActivityService.record(boardId, null, userId, ActivityType.COLUMN_WIP_CHANGED, mapOf("column" to title, "from" to (old[Lists.wipLimit]?.toString() ?: "none"), "to" to (wip?.toString() ?: "none")))
        if (old[Lists.category] != cat.name) ActivityService.record(boardId, null, userId, ActivityType.COLUMN_CATEGORY_CHANGED, mapOf("column" to title, "from" to old[Lists.category], "to" to cat.name))
        WebSocketHub.toBoard(boardId, "list_updated", "list", JsonPrimitive(listId))
        return transaction { toListDto(Lists.select { Lists.id eq listId }.first()) }
    }

    /**
     * Deletes a column. If it still holds tasks the caller must say what happens to them.
     *
     * @param moveTo id of another column of the same board that receives the tasks, or null.
     * @param deleteTasks when true and [moveTo] is null, the tasks are deleted with the column.
     * @throws ApiException.Conflict `COLUMN_NOT_EMPTY` when tasks exist and neither option was given;
     * `LAST_COLUMN` when it is the only column.
     */
    fun deleteList(userId: String, listId: String, moveTo: String?, deleteTasks: Boolean) {
        val boardId = AccessService.boardIdOfList(listId) ?: throw ApiException.NotFound("Column not found")
        AccessService.require(boardId, userId, Permission.MANAGE_COLUMNS)
        val col = transaction { Lists.select { Lists.id eq listId }.first() }
        var moved = 0
        transaction {
            val all = Lists.select { Lists.boardId eq boardId }.orderBy(Lists.position to SortOrder.ASC).toList()
            if (all.size <= 1) throw ApiException.Conflict("A board needs at least one column", "LAST_COLUMN")
            val taskIds = Tasks.select { (Tasks.listId eq listId) and (Tasks.isDeleted eq false) }.orderBy(Tasks.position to SortOrder.ASC).map { it[Tasks.id] }
            if (taskIds.isNotEmpty()) {
                when {
                    moveTo != null -> {
                        val target = all.firstOrNull { it[Lists.id] == moveTo && it[Lists.id] != listId } ?: throw ApiException.Validation("Target column is invalid")
                        val existing = Tasks.select { (Tasks.listId eq moveTo) and (Tasks.isDeleted eq false) }.count().toInt()
                        taskIds.forEachIndexed { i, tid ->
                            Tasks.update({ Tasks.id eq tid }) { it[Tasks.listId] = moveTo; it[Tasks.position] = existing + i; it[Tasks.status] = target[Lists.category]; it[Tasks.updatedAt] = System.currentTimeMillis() }
                        }
                        moved = taskIds.size
                    }
                    deleteTasks -> Unit // removed by ON DELETE CASCADE below
                    else -> throw ApiException.Conflict("Column still has ${taskIds.size} task(s). Move or delete them first.", "COLUMN_NOT_EMPTY")
                }
            }
            Lists.deleteWhere { Lists.id eq listId }
            all.map { it[Lists.id] }.filter { it != listId }.forEachIndexed { i, lid -> Lists.update({ Lists.id eq lid }) { it[Lists.position] = i } }
        }
        ActivityService.record(boardId, null, userId, ActivityType.COLUMN_DELETED, mapOf("column" to col[Lists.title], "moved" to moved.toString()))
        WebSocketHub.toBoard(boardId, "list_deleted", "list", JsonPrimitive(listId))
    }

    /** Sets the left-to-right order. [ids] must contain every column of the board exactly once. ADMIN only. */
    fun reorderLists(userId: String, boardId: String, ids: List<String>): List<ListDto> {
        AccessService.require(boardId, userId, Permission.MANAGE_COLUMNS)
        transaction {
            val current = Lists.select { Lists.boardId eq boardId }.map { it[Lists.id] }.toSet()
            if (ids.toSet() != current || ids.size != current.size) throw ApiException.Validation("Order must list every column exactly once")
            Ordering.positions(ids).forEach { (lid, pos) -> Lists.update({ Lists.id eq lid }) { it[Lists.position] = pos; it[Lists.updatedAt] = System.currentTimeMillis() } }
        }
        ActivityService.record(boardId, null, userId, ActivityType.COLUMN_REORDERED)
        WebSocketHub.toBoard(boardId, "list_reordered", "list", JsonPrimitive(boardId))
        return lists(userId, boardId)
    }

    // ------------------------------------------------------------------ members & teams

    /** @return everyone with access: direct members (`source=direct`) and members of attached teams (`source=team:<name>`). */
    fun members(userId: String, boardId: String): List<MemberDto> {
        AccessService.require(boardId, userId, Permission.VIEW_BOARD)
        return transaction {
            val out = mutableListOf<MemberDto>()
            val direct = BoardMembers.select { BoardMembers.boardId eq boardId }.toList()
            val teamGrants = BoardTeams.select { BoardTeams.boardId eq boardId }.toList()
            val teamNames = Teams.select { Teams.id inList teamGrants.map { it[BoardTeams.teamId] } }.associate { it[Teams.id] to it[Teams.name] }
            val teamMembers = TeamMembers.select { TeamMembers.teamId inList teamGrants.map { it[BoardTeams.teamId] } }.toList()
            val userIds = (direct.map { it[BoardMembers.userId] } + teamMembers.map { it[TeamMembers.userId] }).distinct()
            val users = Users.select { Users.id inList userIds }.associateBy { it[Users.id] }
            direct.forEach { r -> users[r[BoardMembers.userId]]?.let { u -> out += MemberDto(u[Users.id], u[Users.username], u[Users.nickname], r[BoardMembers.role]) } }
            teamGrants.forEach { g ->
                teamMembers.filter { it[TeamMembers.teamId] == g[BoardTeams.teamId] }.forEach { tm ->
                    users[tm[TeamMembers.userId]]?.let { u -> out += MemberDto(u[Users.id], u[Users.username], u[Users.nickname], g[BoardTeams.role], "team:${teamNames[g[BoardTeams.teamId]]}") }
                }
            }
            out.sortedWith(compareBy({ if (it.source == "direct") 0 else 1 }, { -(BoardRole.parseOrNull(it.role)?.rank ?: 0) }, { (it.nickname ?: it.username).lowercase() }))
        }
    }

    /** Grants a user (by username) direct access. ADMIN only. */
    fun addMember(userId: String, boardId: String, req: AddMemberRequest): MemberDto {
        AccessService.require(boardId, userId, Permission.MANAGE_MEMBERS)
        val role = BoardRole.parseOrNull(req.role) ?: BoardRole.MEMBER
        val user = transaction { Users.select { Users.username eq req.username.trim() }.firstOrNull() } ?: throw ApiException.NotFound("No user named '${req.username.trim()}'")
        val uid = user[Users.id]
        transaction {
            if (BoardMembers.select { (BoardMembers.boardId eq boardId) and (BoardMembers.userId eq uid) }.any()) throw ApiException.Conflict("Already a member of this board", "ALREADY_MEMBER")
            BoardMembers.insert { it[BoardMembers.boardId] = boardId; it[BoardMembers.userId] = uid; it[BoardMembers.role] = role.name }
        }
        ActivityService.record(boardId, null, userId, ActivityType.MEMBER_ADDED, mapOf("user" to (user[Users.nickname] ?: user[Users.username]), "role" to role.name))
        WebSocketHub.toUsers(listOf(uid), "board_membership_changed", "member", boardId, JsonPrimitive(boardId))
        WebSocketHub.toBoard(boardId, "member_changed", "member", JsonPrimitive(boardId))
        return MemberDto(uid, user[Users.username], user[Users.nickname], role.name)
    }

    /** Changes a direct member's role. ADMIN only; the owner always stays ADMIN. */
    fun updateMemberRole(userId: String, boardId: String, targetId: String, roleName: String) {
        AccessService.require(boardId, userId, Permission.MANAGE_MEMBERS)
        val role = BoardRole.parseOrNull(roleName) ?: throw ApiException.Validation("Unknown role")
        val owner = transaction { Boards.select { Boards.id eq boardId }.first()[Boards.userId] }
        if (targetId == owner && role != BoardRole.ADMIN) throw ApiException.Forbidden("The board owner is always an admin")
        val n = transaction { BoardMembers.update({ (BoardMembers.boardId eq boardId) and (BoardMembers.userId eq targetId) }) { it[BoardMembers.role] = role.name } }
        if (n == 0) throw ApiException.NotFound("Member not found")
        ActivityService.record(boardId, null, userId, ActivityType.MEMBER_ROLE_CHANGED, mapOf("user" to AccessService.displayName(targetId), "role" to role.name))
        WebSocketHub.toUsers(listOf(targetId), "board_membership_changed", "member", boardId, JsonPrimitive(boardId))
        WebSocketHub.toBoard(boardId, "member_changed", "member", JsonPrimitive(boardId))
    }

    /** Removes a direct member (admins) or lets a member leave. The owner cannot be removed. */
    fun removeMember(userId: String, boardId: String, targetId: String) {
        if (userId == targetId) AccessService.require(boardId, userId, Permission.VIEW_BOARD) else AccessService.require(boardId, userId, Permission.MANAGE_MEMBERS)
        val owner = transaction { Boards.select { Boards.id eq boardId }.first()[Boards.userId] }
        if (targetId == owner) throw ApiException.Forbidden("The board owner cannot be removed")
        val name = AccessService.displayName(targetId)
        val n = transaction { BoardMembers.deleteWhere { (BoardMembers.boardId eq boardId) and (BoardMembers.userId eq targetId) } }
        if (n == 0) throw ApiException.NotFound("Member not found")
        ActivityService.record(boardId, null, userId, ActivityType.MEMBER_REMOVED, mapOf("user" to name))
        WebSocketHub.toUsers(listOf(targetId), "board_membership_changed", "member", boardId, JsonPrimitive(boardId))
        WebSocketHub.toBoard(boardId, "member_changed", "member", JsonPrimitive(boardId))
    }

    /** @return teams that have been granted access to the board. */
    fun boardTeams(userId: String, boardId: String): List<BoardTeamDto> {
        AccessService.require(boardId, userId, Permission.VIEW_BOARD)
        return transaction {
            val grants = BoardTeams.select { BoardTeams.boardId eq boardId }.toList()
            val names = Teams.select { Teams.id inList grants.map { it[BoardTeams.teamId] } }.associate { it[Teams.id] to it[Teams.name] }
            val counts = TeamMembers.select { TeamMembers.teamId inList grants.map { it[BoardTeams.teamId] } }.groupingBy { it[TeamMembers.teamId] }.eachCount()
            grants.map { BoardTeamDto(it[BoardTeams.teamId], names[it[BoardTeams.teamId]] ?: "Team", it[BoardTeams.role], counts[it[BoardTeams.teamId]] ?: 0) }
        }
    }

    /** Gives a team access to the board. ADMIN only, and the caller must belong to that team. */
    fun addBoardTeam(userId: String, boardId: String, req: AddBoardTeamRequest) {
        AccessService.require(boardId, userId, Permission.MANAGE_MEMBERS)
        AccessService.requireTeam(req.teamId, userId, adminOnly = false)
        val role = BoardRole.parseOrNull(req.role) ?: BoardRole.MEMBER
        val teamName = transaction { Teams.select { Teams.id eq req.teamId }.first()[Teams.name] }
        val audience = transaction { TeamMembers.select { TeamMembers.teamId eq req.teamId }.map { it[TeamMembers.userId] } }
        transaction {
            if (BoardTeams.select { (BoardTeams.boardId eq boardId) and (BoardTeams.teamId eq req.teamId) }.any()) throw ApiException.Conflict("Team already has access", "ALREADY_MEMBER")
            BoardTeams.insert { it[BoardTeams.boardId] = boardId; it[BoardTeams.teamId] = req.teamId; it[BoardTeams.role] = role.name }
        }
        ActivityService.record(boardId, null, userId, ActivityType.TEAM_ADDED, mapOf("team" to teamName, "role" to role.name))
        WebSocketHub.toUsers(audience, "board_membership_changed", "member", boardId, JsonPrimitive(boardId))
        WebSocketHub.toBoard(boardId, "member_changed", "member", JsonPrimitive(boardId))
    }

    /** Changes the role a team has on the board. ADMIN only. */
    fun updateBoardTeamRole(userId: String, boardId: String, teamId: String, roleName: String) {
        AccessService.require(boardId, userId, Permission.MANAGE_MEMBERS)
        val role = BoardRole.parseOrNull(roleName) ?: throw ApiException.Validation("Unknown role")
        val n = transaction { BoardTeams.update({ (BoardTeams.boardId eq boardId) and (BoardTeams.teamId eq teamId) }) { it[BoardTeams.role] = role.name } }
        if (n == 0) throw ApiException.NotFound("Team has no access to this board")
        val teamName = transaction { Teams.select { Teams.id eq teamId }.firstOrNull()?.get(Teams.name) ?: "Team" }
        ActivityService.record(boardId, null, userId, ActivityType.TEAM_ROLE_CHANGED, mapOf("team" to teamName, "role" to role.name))
        WebSocketHub.toBoard(boardId, "member_changed", "member", JsonPrimitive(boardId))
    }

    /** Revokes a team's access. ADMIN only. */
    fun removeBoardTeam(userId: String, boardId: String, teamId: String) {
        AccessService.require(boardId, userId, Permission.MANAGE_MEMBERS)
        val teamName = transaction { Teams.select { Teams.id eq teamId }.firstOrNull()?.get(Teams.name) ?: "Team" }
        val audience = transaction { TeamMembers.select { TeamMembers.teamId eq teamId }.map { it[TeamMembers.userId] } }
        val n = transaction { BoardTeams.deleteWhere { (BoardTeams.boardId eq boardId) and (BoardTeams.teamId eq teamId) } }
        if (n == 0) throw ApiException.NotFound("Team has no access to this board")
        ActivityService.record(boardId, null, userId, ActivityType.TEAM_REMOVED, mapOf("team" to teamName))
        WebSocketHub.toUsers(audience, "board_membership_changed", "member", boardId, JsonPrimitive(boardId))
        WebSocketHub.toBoard(boardId, "member_changed", "member", JsonPrimitive(boardId))
    }

    // ------------------------------------------------------------------ helpers

    private fun validColumnTitle(raw: String): String {
        val t = raw.trim()
        if (t.length !in 1..50) throw ApiException.Validation("Column name must be 1-50 characters")
        return t
    }

    private fun validWip(limit: Int?): Int? {
        if (limit != null && limit !in 1..999) throw ApiException.Validation("WIP limit must be between 1 and 999")
        return limit
    }

    internal fun toListDto(r: org.jetbrains.exposed.sql.ResultRow) = ListDto(
        r[Lists.id], r[Lists.boardId], r[Lists.title], r[Lists.position], r[Lists.category], r[Lists.wipLimit], r[Lists.createdAt], r[Lists.updatedAt]
    )
}
