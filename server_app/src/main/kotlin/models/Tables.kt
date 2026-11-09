package models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/*
 * Schema notes
 * ------------
 * Tables are created/extended on start-up with SchemaUtils.createMissingTablesAndColumns (see plugins/Database.kt),
 * so every column added after the first release is nullable or has a DEFAULT - existing rows stay valid.
 */

/** Registered accounts. `username` is the immutable login id; `nickname` is the editable display name. */
object Users : Table("users") {
    val id = varchar("id", 36)
    val username = varchar("username", 50).uniqueIndex()
    val nickname = varchar("nickname", 50).nullable()
    val email = varchar("email", 255).nullable()
    val passwordHash = varchar("password_hash", 60)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at").default(0)
    override val primaryKey = PrimaryKey(id)
}

/** Opaque refresh tokens (rotated on every refresh, revoked on logout / password change). */
object RefreshTokens : Table("refresh_tokens") {
    val token = varchar("token", 36)
    val userId = varchar("user_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(token)
}

/** A group of people that can be granted access to boards as a unit. */
object Teams : Table("teams") {
    val id = varchar("id", 36)
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val ownerId = varchar("owner_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Membership of a user in a team (`role` = TeamRole). */
object TeamMembers : Table("team_members") {
    val teamId = varchar("team_id", 36).references(Teams.id, onDelete = ReferenceOption.CASCADE)
    val userId = varchar("user_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 16).default("MEMBER")
    override val primaryKey = PrimaryKey(teamId, userId)
}

/** A board (Jira: project + board). `userId` is the creator/owner. */
object Boards : Table("boards") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val key = varchar("board_key", 10).default("BRD")
    val description = text("description").nullable()
    val taskSeq = integer("task_seq").default(0)
    val createdAt = long("created_at").default(0)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** Direct access of a user to a board (`role` = BoardRole). */
object BoardMembers : Table("board_members") {
    val boardId = varchar("board_id", 36).references(Boards.id, onDelete = ReferenceOption.CASCADE)
    val userId = varchar("user_id", 36).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 16).default("MEMBER")
    override val primaryKey = PrimaryKey(boardId, userId)
}

/** Access of a whole team to a board (`role` = BoardRole applied to every team member). */
object BoardTeams : Table("board_teams") {
    val boardId = varchar("board_id", 36).references(Boards.id, onDelete = ReferenceOption.CASCADE)
    val teamId = varchar("team_id", 36).references(Teams.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 16).default("MEMBER")
    override val primaryKey = PrimaryKey(boardId, teamId)
}

/** Board columns ("lists"). `category` is the Jira-style status category; `wipLimit` is the optional column constraint. */
object Lists : Table("lists") {
    val id = varchar("id", 36)
    val boardId = varchar("board_id", 36).references(Boards.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val position = integer("position")
    val category = varchar("category", 20).default("TODO")
    val wipLimit = integer("wip_limit").nullable()
    val createdAt = long("created_at").default(0)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** Issues. Soft-deleted via `isDeleted`. `number` is the per-board sequence behind the key (WEB-12). */
object Tasks : Table("tasks") {
    val id = varchar("id", 36)
    val boardId = varchar("board_id", 36).references(Boards.id, onDelete = ReferenceOption.CASCADE)
    val listId = varchar("list_id", 36).references(Lists.id, onDelete = ReferenceOption.CASCADE)
    val number = integer("issue_number").default(0)
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val position = integer("position")
    val status = varchar("status", 50)
    val priority = varchar("priority", 16).default("MEDIUM")
    val type = varchar("issue_type", 16).default("TASK")
    val assigneeId = varchar("assignee_id", 36).nullable()
    val reporterId = varchar("reporter_id", 36).nullable()
    val startDate = long("start_date").nullable()
    val dueDate = long("due_date").nullable()
    val labels = text("labels").default("")
    val createdAt = long("created_at").default(0)
    val updatedAt = long("updated_at")
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

/** Comments on an issue. */
object Comments : Table("comments") {
    val id = varchar("id", 36)
    val taskId = varchar("task_id", 36).references(Tasks.id, onDelete = ReferenceOption.CASCADE)
    val authorId = varchar("author_id", 36)
    val body = text("body")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Append-only audit trail of everything that happens on a board. `details` is a JSON object of strings. */
object ActivityLog : Table("activity_log") {
    val id = varchar("id", 36)
    val boardId = varchar("board_id", 36).references(Boards.id, onDelete = ReferenceOption.CASCADE)
    val taskId = varchar("task_id", 36).nullable()
    val actorId = varchar("actor_id", 36)
    val actorName = varchar("actor_name", 100)
    val type = varchar("type", 40)
    val details = text("details").default("{}")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, boardId, createdAt) }
}

/** Crash / error reports uploaded by the app (and uncaught server errors) for developers. */
object ErrorReports : Table("error_reports") {
    val id = varchar("id", 36)
    val source = varchar("source", 16)
    val level = varchar("level", 16)
    val message = text("message")
    val stackTrace = text("stack_trace").nullable()
    val screen = varchar("screen", 100).nullable()
    val appVersion = varchar("app_version", 50).nullable()
    val osVersion = varchar("os_version", 50).nullable()
    val device = varchar("device", 100).nullable()
    val userId = varchar("user_id", 36).nullable()
    val thread = varchar("thread", 100).nullable()
    val breadcrumbs = text("breadcrumbs").nullable()
    val occurredAt = long("occurred_at")
    val receivedAt = long("received_at")
    val requestId = varchar("request_id", 64).nullable()
    override val primaryKey = PrimaryKey(id)
}
