package com.google.mytaskmanager.data.repository

import com.google.gson.Gson
import com.google.mytaskmanager.core.AppException
import com.google.mytaskmanager.core.ErrorKind
import com.google.mytaskmanager.core.appCall
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.local.dao.*
import com.google.mytaskmanager.data.local.entity.PendingChangeEntity
import com.google.mytaskmanager.data.local.entity.TaskEntity
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.dto.*
import com.google.mytaskmanager.data.sync.PendingChangeProcessor
import com.google.mytaskmanager.data.sync.PendingOp
import com.google.mytaskmanager.data.util.*
import com.google.mytaskmanager.domain.model.*
import com.google.mytaskmanager.domain.repository.TaskRepository
import com.google.mytaskmanager.domain.rules.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first implementation of [TaskRepository].
 *
 * **Reads** are Flows over Room, so the UI always shows the last known state instantly.
 * **Task writes** (create/update/move/delete) are *optimistic*: applied locally first, then sent to the server.
 *  - If the request fails for a transient reason the change is queued (see [PendingChangeProcessor]) and replayed later;
 *  - if the server rejects it (403/404/409/400) the local change is rolled back and an `AppException` is thrown,
 *    so e.g. a viewer's drag & drop snaps back and the user sees why.
 * Changes are queued *behind* earlier queued changes so the server always sees them in the order they were made.
 * **Board/column writes** require a connection (they are rare and must be validated by the server).
 */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val boardDao: BoardDao,
    private val listDao: ListDao,
    private val taskDao: TaskDao,
    private val pendingDao: PendingChangeDao,
    private val processor: PendingChangeProcessor,
    private val authPrefs: AuthPreferences
) : TaskRepository {
    private val gson = Gson()

    // ------------------------------------------------------------------ observe

    override fun observeBoards(): Flow<List<Board>> = boardDao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observeBoard(boardId: String): Flow<Board?> = boardDao.observe(boardId).map { it?.toDomain() }
    override fun observeLists(boardId: String): Flow<List<BoardList>> = listDao.observeForBoard(boardId).map { l -> l.map { it.toDomain() } }
    override fun observeTasks(boardId: String): Flow<List<Task>> = taskDao.observeForBoard(boardId).map { l -> l.map { it.toDomain() } }
    override fun observeTask(taskId: String): Flow<Task?> = taskDao.observe(taskId).map { it?.toDomain() }
    override fun observePendingCount(): Flow<Int> = pendingDao.observeCount()

    // ------------------------------------------------------------------ refresh

    override suspend fun refreshBoards() {
        val boards = appCall { api.getBoards() }
        boardDao.replaceAll(boards.map { it.toEntity() })
    }

    override suspend fun refreshBoard(boardId: String) {
        flushPending()
        try {
            val lists = appCall { api.getLists(boardId) }
            val tasks = appCall { api.getTasks(boardId) }
            listDao.replaceForBoard(boardId, lists.map { it.toEntity() })
            // Never overwrite local edits that have not reached the server yet.
            if (pendingDao.countForBoard(boardId) == 0) taskDao.replaceForBoard(boardId, tasks.map { it.toEntity() })
        } catch (e: AppException) {
            if (e.kind == ErrorKind.NOT_FOUND) { // board deleted or access revoked
                taskDao.deleteForBoard(boardId); listDao.deleteForBoard(boardId); boardDao.delete(boardId)
            }
            throw e
        }
    }

    override suspend fun flushPending(): Boolean = processor.flush()

    // ------------------------------------------------------------------ boards (online)

    override suspend fun createBoard(name: String, key: String?, description: String?, template: BoardTemplate): Board {
        val dto = appCall { api.createBoard(CreateBoardRequest(UUID.randomUUID().toString(), name.trim(), key?.trim()?.ifEmpty { null }, description?.trim()?.ifEmpty { null }, template.name)) }
        boardDao.upsert(dto.toEntity())
        refreshBoard(dto.id)
        return dto.toEntity().toDomain()
    }

    override suspend fun updateBoard(boardId: String, name: String, description: String?) {
        boardDao.upsert(appCall { api.updateBoard(boardId, UpdateBoardRequest(name.trim(), description?.trim()?.ifEmpty { null })) }.toEntity())
    }

    override suspend fun deleteBoard(boardId: String) {
        appCall { api.deleteBoard(boardId) }
        taskDao.deleteForBoard(boardId); listDao.deleteForBoard(boardId); boardDao.delete(boardId)
    }

    override suspend fun emptyBoard(boardId: String): Int {
        val removed = appCall { api.emptyBoard(boardId) }.removed
        taskDao.deleteForBoard(boardId)
        refreshBoards()
        return removed
    }

    // ------------------------------------------------------------------ columns (online)

    override suspend fun createList(boardId: String, title: String, category: ColumnCategory, wipLimit: Int?) {
        val position = listDao.observeForBoard(boardId).first().size
        appCall { api.createList(ListDto(id = UUID.randomUUID().toString(), boardId = boardId, title = title.trim(), position = position, category = category.name, wipLimit = wipLimit)) }
        refreshBoard(boardId)
    }

    override suspend fun updateList(list: BoardList) {
        appCall { api.updateList(list.id, ListDto(list.id, list.boardId, list.title.trim(), list.position, list.category.name, list.wipLimit)) }
        refreshBoard(list.boardId)
    }

    override suspend fun deleteList(listId: String, moveTo: String?, deleteTasks: Boolean) {
        val boardId = listDao.get(listId)?.boardId
        appCall { api.deleteList(listId, moveTo, deleteTasks) }
        if (boardId != null) { refreshBoard(boardId); refreshBoards() }
    }

    override suspend fun reorderLists(boardId: String, orderedIds: List<String>) {
        val before = listDao.observeForBoard(boardId).first()
        // optimistic: show the new order immediately
        listDao.upsertAll(before.map { it.copy(position = orderedIds.indexOf(it.id).coerceAtLeast(0)) })
        try { listDao.replaceForBoard(boardId, appCall { api.reorderLists(boardId, ReorderRequest(orderedIds)) }.map { it.toEntity() }) }
        catch (e: AppException) { listDao.upsertAll(before); throw e }
    }

    // ------------------------------------------------------------------ tasks (optimistic + queue)

    override suspend fun createTask(
        boardId: String, listId: String, title: String, description: String?,
        priority: TaskPriority, type: TaskType, assigneeId: String?, dueDate: Long?
    ): Task {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val keyPrefix = boardDao.get(boardId)?.key ?: "TSK"
        val position = taskDao.forBoard(boardId).count { it.listId == listId }
        val me = authPrefs.userIdFlow.first()
        val local = TaskEntity(
            id, boardId, listId, "$keyPrefix-…", 0, title.trim(), description?.trim()?.ifEmpty { null }, position,
            priority.name, type.name, assigneeId, me, null, dueDate, "", now, now
        )
        taskDao.upsert(local)
        pushOrQueue(id, boardId, PendingOp.TASK_CREATE, gson.toJson(local.toDto()), undo = { taskDao.delete(id) }) {
            taskDao.upsert(api.createTask(local.toDto()).toEntity())
        }
        return (taskDao.get(id) ?: local).toDomain()
    }

    override suspend fun updateTask(task: Task) {
        val old = taskDao.get(task.id) ?: return
        val local = task.toEntity().copy(updatedAt = System.currentTimeMillis())
        taskDao.upsert(local)
        pushOrQueue(task.id, task.boardId, PendingOp.TASK_UPDATE, gson.toJson(local.toDto()), undo = { taskDao.upsert(old) }) {
            taskDao.upsert(api.updateTask(task.id, local.toDto()).toEntity())
        }
    }

    override suspend fun moveTask(taskId: String, toListId: String, toIndex: Int) {
        val moved = taskDao.get(taskId) ?: return
        val snapshot = taskDao.forBoard(moved.boardId)
        val now = System.currentTimeMillis()

        // Compute the new order of the target (and, when it changes, the source) column - same rules as the server.
        val targetIds = Ordering.moveTo(snapshot.filter { it.listId == toListId && it.id != taskId }.sortedBy { it.position }.map { it.id }, taskId, toIndex)
        val sourceIds = if (moved.listId != toListId) snapshot.filter { it.listId == moved.listId && it.id != taskId }.sortedBy { it.position }.map { it.id } else emptyList()
        val byId = snapshot.associateBy { it.id }
        val updates = targetIds.mapIndexed { i, id -> byId.getValue(id).copy(listId = toListId, position = i, updatedAt = if (id == taskId) now else byId.getValue(id).updatedAt) } +
            sourceIds.mapIndexed { i, id -> byId.getValue(id).copy(position = i) }
        taskDao.upsertAll(updates)

        pushOrQueue(taskId, moved.boardId, PendingOp.TASK_MOVE, gson.toJson(MoveTaskRequest(toListId, toIndex)), undo = { taskDao.upsertAll(snapshot) }) {
            taskDao.upsert(api.moveTask(taskId, MoveTaskRequest(toListId, toIndex)).toEntity())
        }
    }

    override suspend fun deleteTask(taskId: String) {
        val old = taskDao.get(taskId) ?: return
        taskDao.delete(taskId)
        pendingDao.deleteForEntity(taskId) // queued create/update for a deleted task are pointless
        // If the task never reached the server there is nothing to delete remotely.
        val neverSynced = old.number == 0 && old.key.endsWith("…")
        if (neverSynced) return
        pushOrQueue(taskId, old.boardId, PendingOp.TASK_DELETE, "", undo = { taskDao.upsert(old) }) { api.deleteTask(taskId) }
    }

    /**
     * Sends [call] now, or queues it. See the class comment for the exact rules.
     *
     * @param undo restores the local state if the server rejects the change permanently.
     */
    private suspend fun pushOrQueue(
        entityId: String, boardId: String, op: String, payload: String,
        undo: suspend () -> Unit, call: suspend () -> Unit
    ) {
        suspend fun queue() {
            pendingDao.insert(PendingChangeEntity(entityId = entityId, boardId = boardId, operation = op, payload = payload, createdAt = System.currentTimeMillis()))
        }
        // Earlier changes are still waiting -> keep order: queue behind them and try to flush.
        if (pendingDao.countForBoard(boardId) > 0) { queue(); runCatching { processor.flush() }; return }
        try {
            appCall { call() }
        } catch (e: AppException) {
            if (e.kind.retryable) queue() else { undo(); throw e }
        }
    }
}
