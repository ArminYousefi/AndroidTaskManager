package services

import domain.*
import kotlinx.serialization.json.Json
import models.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import plugins.ApiException
import java.util.UUID

/**
 * Issues (tasks): create, edit, move between columns, delete, comment.
 *
 * The server computes *what changed* by diffing the stored row against the request, so the activity log is
 * accurate no matter which client (or offline replay) made the change: a title change is logged as
 * `TASK_RENAMED`, due/start date changes as `TASK_REPLANNED`, a different column as `TASK_MOVED`, and so on.
 */
object TaskService {
    private val json = Json { encodeDefaults = true }

    // ------------------------------------------------------------------ reads

    /** @return the board's non-deleted tasks (every column). */
    fun list(userId: String, boardId: String): List<TaskDto> {
        AccessService.require(boardId, userId, Permission.VIEW_BOARD)
        return transaction {
            val key = boardKey(boardId)
            Tasks.select { (Tasks.boardId eq boardId) and (Tasks.isDeleted eq false) }
                .orderBy(Tasks.position to SortOrder.ASC).map { toDto(it, key) }
        }
    }

    /** @return one task. @throws ApiException.NotFound when missing/deleted or the caller has no access. */
    fun get(userId: String, taskId: String): TaskDto {
        val row = rowOrThrow(taskId)
        AccessService.require(row[Tasks.boardId], userId, Permission.VIEW_BOARD)
        return transaction { toDto(row, boardKey(row[Tasks.boardId])) }
    }

    // ------------------------------------------------------------------ writes

    /**
     * Creates a task at the requested position (default: bottom of the column). Idempotent for a client-supplied id,
     * so an offline create that is retried never produces duplicates.
     *
     * @throws ApiException.Validation for a bad title, a column that is not on this board, or an assignee who has no access.
     */
    fun create(userId: String, req: TaskDto): TaskDto {
        AccessService.require(req.boardId, userId, Permission.CREATE_TASK)
        val title = validTitle(req.title)
        val id = req.id.ifBlank { UUID.randomUUID().toString() }
        transaction { Tasks.select { Tasks.id eq id }.firstOrNull() }?.let {
            if (it[Tasks.boardId] != req.boardId) throw ApiException.Conflict("Task id already in use")
            return transaction { toDto(it, boardKey(req.boardId)) }
        }
        val list = transaction { Lists.select { Lists.id eq req.listId }.firstOrNull() }
        if (list == null || list[Lists.boardId] != req.boardId) throw ApiException.Validation("Column does not belong to this board")
        checkAssignee(req.boardId, req.assigneeId)
        checkDates(req.startDate, req.dueDate)
        val now = System.currentTimeMillis()
        val dto = transaction {
            Boards.update({ Boards.id eq req.boardId }) { it[Boards.taskSeq] = Boards.taskSeq + 1; it[Boards.updatedAt] = now }
            val number = Boards.select { Boards.id eq req.boardId }.first()[Boards.taskSeq]
            Tasks.insert {
                it[Tasks.id] = id; it[Tasks.boardId] = req.boardId; it[Tasks.listId] = req.listId; it[Tasks.number] = number
                it[Tasks.title] = title; it[Tasks.description] = req.description?.take(20_000); it[Tasks.position] = 0
                it[Tasks.status] = list[Lists.category]
                it[Tasks.priority] = TaskPriority.parse(req.priority).name; it[Tasks.type] = TaskType.parse(req.type).name
                it[Tasks.assigneeId] = req.assigneeId?.takeIf { a -> a.isNotBlank() }; it[Tasks.reporterId] = userId
                it[Tasks.startDate] = req.startDate; it[Tasks.dueDate] = req.dueDate
                it[Tasks.labels] = joinLabels(req.labels); it[Tasks.createdAt] = now; it[Tasks.updatedAt] = now
            }
            placeInColumn(id, req.listId, if (req.position >= 0) req.position else Int.MAX_VALUE)
            toDto(Tasks.select { Tasks.id eq id }.first(), boardKey(req.boardId))
        }
        ActivityService.record(req.boardId, id, userId, ActivityType.TASK_CREATED, mapOf("key" to dto.key, "title" to title, "column" to list[Lists.title]))
        WebSocketHub.toBoard(req.boardId, "task_created", "task", json.encodeToJsonElement(TaskDto.serializer(), dto))
        return dto
    }

    /**
     * Applies edits. Only changed fields are written and logged. A different `listId` is treated as a move.
     * `key`, `number`, `reporterId`, `createdAt` are read-only.
     */
    fun update(userId: String, taskId: String, req: TaskDto): TaskDto {
        val old = rowOrThrow(taskId)
        val boardId = old[Tasks.boardId]
        AccessService.require(boardId, userId, Permission.EDIT_TASK)
        val key = transaction { boardKey(boardId) + "-" + old[Tasks.number] }
        val title = validTitle(req.title)
        val priority = TaskPriority.parse(req.priority).name
        val type = TaskType.parse(req.type).name
        val assignee = req.assigneeId?.takeIf { it.isNotBlank() }
        if (assignee != old[Tasks.assigneeId]) checkAssignee(boardId, assignee)
        checkDates(req.startDate, req.dueDate)
        val labels = joinLabels(req.labels)
        val desc = req.description?.take(20_000)

        transaction {
            Tasks.update({ Tasks.id eq taskId }) {
                it[Tasks.title] = title; it[Tasks.description] = desc; it[Tasks.priority] = priority; it[Tasks.type] = type
                it[Tasks.assigneeId] = assignee; it[Tasks.startDate] = req.startDate; it[Tasks.dueDate] = req.dueDate
                it[Tasks.labels] = labels; it[Tasks.updatedAt] = System.currentTimeMillis()
            }
            Boards.update({ Boards.id eq boardId }) { it[Boards.updatedAt] = System.currentTimeMillis() }
        }
        fun log(t: ActivityType, d: Map<String, String>) = ActivityService.record(boardId, taskId, userId, t, d + ("key" to key) + ("title" to title))
        if (old[Tasks.title] != title) log(ActivityType.TASK_RENAMED, mapOf("from" to old[Tasks.title], "to" to title))
        if ((old[Tasks.description] ?: "") != (desc ?: "")) log(ActivityType.TASK_DESCRIPTION_CHANGED, emptyMap())
        if (old[Tasks.priority] != priority) log(ActivityType.TASK_PRIORITY_CHANGED, mapOf("from" to old[Tasks.priority], "to" to priority))
        if (old[Tasks.type] != type) log(ActivityType.TASK_TYPE_CHANGED, mapOf("from" to old[Tasks.type], "to" to type))
        if (old[Tasks.assigneeId] != assignee) log(ActivityType.TASK_ASSIGNED, mapOf(
            "from" to (old[Tasks.assigneeId]?.let { AccessService.displayName(it) } ?: ""), "to" to (assignee?.let { AccessService.displayName(it) } ?: "")))
        if (old[Tasks.dueDate] != req.dueDate || old[Tasks.startDate] != req.startDate) log(ActivityType.TASK_REPLANNED, mapOf(
            "fromDue" to (old[Tasks.dueDate]?.toString() ?: ""), "toDue" to (req.dueDate?.toString() ?: ""),
            "fromStart" to (old[Tasks.startDate]?.toString() ?: ""), "toStart" to (req.startDate?.toString() ?: "")))
        if (old[Tasks.labels] != labels) log(ActivityType.TASK_LABELS_CHANGED, mapOf("to" to labels.replace(",", ", ")))

        if (req.listId.isNotBlank() && req.listId != old[Tasks.listId]) return move(userId, taskId, MoveTaskRequest(req.listId, if (req.position >= 0) req.position else Int.MAX_VALUE))
        val dto = transaction { toDto(Tasks.select { Tasks.id eq taskId }.first(), boardKey(boardId)) }
        WebSocketHub.toBoard(boardId, "task_updated", "task", json.encodeToJsonElement(TaskDto.serializer(), dto))
        return dto
    }

    /**
     * Moves a task to [req].`listId` at index [req].`position`, re-numbering both columns. This is what a drag & drop
     * ends in. Moving into a column that is over its WIP limit is *allowed* (the UI warns), like Jira.
     */
    fun move(userId: String, taskId: String, req: MoveTaskRequest): TaskDto {
        val row = rowOrThrow(taskId)
        val boardId = row[Tasks.boardId]
        AccessService.require(boardId, userId, Permission.MOVE_TASK)
        val target = transaction { Lists.select { Lists.id eq req.listId }.firstOrNull() }
        if (target == null || target[Lists.boardId] != boardId) throw ApiException.Validation("Column does not belong to this board")
        val fromId = row[Tasks.listId]
        val fromTitle = transaction { Lists.select { Lists.id eq fromId }.firstOrNull()?.get(Lists.title) ?: "" }
        val dto = transaction {
            val now = System.currentTimeMillis()
            Tasks.update({ Tasks.id eq taskId }) { it[Tasks.listId] = target[Lists.id]; it[Tasks.status] = target[Lists.category]; it[Tasks.updatedAt] = now }
            if (fromId != target[Lists.id]) compact(fromId)
            placeInColumn(taskId, target[Lists.id], req.position)
            Boards.update({ Boards.id eq boardId }) { it[Boards.updatedAt] = now }
            toDto(Tasks.select { Tasks.id eq taskId }.first(), boardKey(boardId))
        }
        if (fromId != target[Lists.id]) ActivityService.record(boardId, taskId, userId, ActivityType.TASK_MOVED, mapOf(
            "key" to dto.key, "title" to dto.title, "from" to fromTitle, "to" to target[Lists.title], "toCategory" to target[Lists.category]))
        WebSocketHub.toBoard(boardId, "task_moved", "task", json.encodeToJsonElement(TaskDto.serializer(), dto))
        return dto
    }

    /** Soft-deletes a task (idempotent). */
    fun delete(userId: String, taskId: String) {
        val row = transaction { Tasks.select { Tasks.id eq taskId }.firstOrNull() } ?: return
        val boardId = row[Tasks.boardId]
        AccessService.require(boardId, userId, Permission.DELETE_TASK)
        if (row[Tasks.isDeleted]) return
        val colTitle = transaction { Lists.select { Lists.id eq row[Tasks.listId] }.firstOrNull()?.get(Lists.title) ?: "" }
        val key = transaction { boardKey(boardId) + "-" + row[Tasks.number] }
        transaction {
            Tasks.update({ Tasks.id eq taskId }) { it[Tasks.isDeleted] = true; it[Tasks.updatedAt] = System.currentTimeMillis() }
            compact(row[Tasks.listId])
        }
        ActivityService.record(boardId, taskId, userId, ActivityType.TASK_DELETED, mapOf("key" to key, "title" to row[Tasks.title], "column" to colTitle))
        WebSocketHub.toBoard(boardId, "task_deleted", "task", kotlinx.serialization.json.JsonPrimitive(taskId))
    }

    // ------------------------------------------------------------------ comments

    /** @return comments oldest-first. */
    fun comments(userId: String, taskId: String): List<CommentDto> {
        val boardId = rowOrThrow(taskId)[Tasks.boardId]
        AccessService.require(boardId, userId, Permission.VIEW_BOARD)
        return transaction {
            val rows = Comments.select { Comments.taskId eq taskId }.orderBy(Comments.createdAt to SortOrder.ASC).toList()
            val names = Users.select { Users.id inList rows.map { it[Comments.authorId] }.distinct() }
                .associate { it[Users.id] to (it[Users.nickname]?.takeIf { n -> n.isNotBlank() } ?: it[Users.username]) }
            rows.map { CommentDto(it[Comments.id], taskId, it[Comments.authorId], names[it[Comments.authorId]] ?: "Unknown", it[Comments.body], it[Comments.createdAt]) }
        }
    }

    /** Adds a comment. MEMBER+ (viewers cannot comment). */
    fun addComment(userId: String, taskId: String, body: String): CommentDto {
        val row = rowOrThrow(taskId)
        val boardId = row[Tasks.boardId]
        AccessService.require(boardId, userId, Permission.COMMENT)
        val text = body.trim()
        if (text.isEmpty() || text.length > 4000) throw ApiException.Validation("Comment must be 1-4000 characters")
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        transaction { Comments.insert { it[Comments.id] = id; it[Comments.taskId] = taskId; it[Comments.authorId] = userId; it[Comments.body] = text; it[Comments.createdAt] = now } }
        val key = transaction { boardKey(boardId) + "-" + row[Tasks.number] }
        ActivityService.record(boardId, taskId, userId, ActivityType.COMMENT_ADDED, mapOf("key" to key, "title" to row[Tasks.title], "preview" to text.take(80)))
        WebSocketHub.toBoard(boardId, "task_updated", "comment", kotlinx.serialization.json.JsonPrimitive(taskId))
        return CommentDto(id, taskId, userId, AccessService.displayName(userId), text, now)
    }

    /** Deletes a comment: its author, or a board ADMIN. */
    fun deleteComment(userId: String, taskId: String, commentId: String) {
        val row = rowOrThrow(taskId)
        val boardId = row[Tasks.boardId]
        val role = AccessService.require(boardId, userId, Permission.VIEW_BOARD)
        val c = transaction { Comments.select { (Comments.id eq commentId) and (Comments.taskId eq taskId) }.firstOrNull() } ?: throw ApiException.NotFound("Comment not found")
        if (c[Comments.authorId] != userId && role != BoardRole.ADMIN) throw ApiException.Forbidden("Only the author or a board admin can delete a comment")
        transaction { Comments.deleteWhere { Comments.id eq commentId } }
        ActivityService.record(boardId, taskId, userId, ActivityType.COMMENT_DELETED, mapOf("title" to row[Tasks.title]))
        WebSocketHub.toBoard(boardId, "task_updated", "comment", kotlinx.serialization.json.JsonPrimitive(taskId))
    }

    /** @return the activity (history) of one task, newest first. */
    fun history(userId: String, taskId: String, limit: Int, before: Long?): List<ActivityDto> {
        val boardId = AccessService.boardIdOfTask(taskId) ?: throw ApiException.NotFound("Task not found")
        AccessService.require(boardId, userId, Permission.VIEW_BOARD)
        return ActivityService.query(boardId, taskId, limit, before)
    }

    // ------------------------------------------------------------------ legacy bulk sync

    /**
     * Compatibility endpoint for older clients: replays [changes] with last-write-wins. Newer clients call the
     * normal create/update/move/delete endpoints instead.
     */
    fun sync(userId: String, changes: List<TaskDto>): SyncResponse {
        val serverChanges = mutableListOf<TaskDto>()
        val problems = mutableListOf<String>()
        changes.forEach { t ->
            try {
                val existing = transaction { Tasks.select { Tasks.id eq t.id }.firstOrNull() }
                when {
                    existing == null -> if (!t.isDeleted) create(userId, t)
                    t.isDeleted -> delete(userId, t.id)
                    existing[Tasks.isDeleted] -> Unit
                    existing[Tasks.updatedAt] > t.updatedAt -> serverChanges += get(userId, t.id)
                    else -> update(userId, t.id, t)
                }
            } catch (e: ApiException) { problems += "${t.id}: ${e.message}" }
        }
        return SyncResponse(problems.isEmpty(), serverChanges, problems.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }

    // ------------------------------------------------------------------ helpers

    private fun rowOrThrow(taskId: String): ResultRow =
        transaction { Tasks.select { (Tasks.id eq taskId) and (Tasks.isDeleted eq false) }.firstOrNull() } ?: throw ApiException.NotFound("Task not found")

    private fun boardKey(boardId: String): String = Boards.select { Boards.id eq boardId }.firstOrNull()?.get(Boards.key) ?: "BRD"

    private fun validTitle(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 255) throw ApiException.Validation("Title must be 1-255 characters")
        return t
    }

    private fun checkAssignee(boardId: String, assigneeId: String?) {
        if (!assigneeId.isNullOrBlank() && assigneeId !in AccessService.memberIds(boardId))
            throw ApiException.Validation("Assignee does not have access to this board")
    }

    private fun checkDates(start: Long?, due: Long?) {
        if (start != null && due != null && due < start) throw ApiException.Validation("Due date cannot be before the start date")
    }

    private fun joinLabels(labels: List<String>): String =
        labels.map { it.replace(",", " ").trim().take(30) }.filter { it.isNotEmpty() }.distinct().take(10).joinToString(",")

    /** Inserts/moves [taskId] to [index] inside [listId] and rewrites that column's positions 0..n-1. Call inside a transaction. */
    private fun placeInColumn(taskId: String, listId: String, index: Int) {
        val ordered = Tasks.select { (Tasks.listId eq listId) and (Tasks.isDeleted eq false) }
            .orderBy(Tasks.position to SortOrder.ASC, Tasks.createdAt to SortOrder.ASC).map { it[Tasks.id] }
        Ordering.positions(Ordering.moveTo(ordered, taskId, index)).forEach { (id, pos) -> Tasks.update({ Tasks.id eq id }) { it[Tasks.position] = pos } }
    }

    /** Re-numbers a column's remaining tasks 0..n-1 after one left it. Call inside a transaction. */
    private fun compact(listId: String) {
        val ordered = Tasks.select { (Tasks.listId eq listId) and (Tasks.isDeleted eq false) }.orderBy(Tasks.position to SortOrder.ASC).map { it[Tasks.id] }
        ordered.forEachIndexed { i, id -> Tasks.update({ Tasks.id eq id }) { it[Tasks.position] = i } }
    }

    private fun toDto(r: ResultRow, boardKey: String) = TaskDto(
        id = r[Tasks.id], boardId = r[Tasks.boardId], listId = r[Tasks.listId], key = "$boardKey-${r[Tasks.number]}", number = r[Tasks.number],
        title = r[Tasks.title], description = r[Tasks.description], position = r[Tasks.position], status = r[Tasks.status],
        priority = r[Tasks.priority], type = r[Tasks.type], assigneeId = r[Tasks.assigneeId], reporterId = r[Tasks.reporterId],
        startDate = r[Tasks.startDate], dueDate = r[Tasks.dueDate], labels = r[Tasks.labels].split(',').filter { it.isNotBlank() },
        createdAt = r[Tasks.createdAt], updatedAt = r[Tasks.updatedAt], isDeleted = r[Tasks.isDeleted]
    )
}
