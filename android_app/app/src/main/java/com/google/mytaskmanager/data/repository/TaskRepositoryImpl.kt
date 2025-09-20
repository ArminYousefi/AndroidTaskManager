package com.google.mytaskmanager.data.repository

import com.google.mytaskmanager.data.local.model.PendingChangeEntity
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.dto.TaskDto
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.Board
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.domain.repository.TaskRepository
import com.google.gson.Gson
import com.google.mytaskmanager.util.LogUtil
import kotlinx.coroutines.delay
import kotlin.math.pow
import com.google.mytaskmanager.data.local.dao.BoardDao
import com.google.mytaskmanager.data.local.dao.ListDao
import com.google.mytaskmanager.data.local.dao.PendingChangeDao
import com.google.mytaskmanager.data.local.dao.TaskDao
import com.google.mytaskmanager.data.remote.dto.BoardDto
import com.google.mytaskmanager.data.remote.dto.ListDto
import com.google.mytaskmanager.data.util.toDomain
import com.google.mytaskmanager.data.util.toDto
import com.google.mytaskmanager.data.util.toEntity
import com.google.mytaskmanager.domain.model.BoardList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.google.mytaskmanager.data.conflict.ConflictManager

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val boardDao: BoardDao,
    private val listDao: ListDao,
    private val taskDao: TaskDao,
    private val pendingChangeDao: PendingChangeDao,
    private val wsManager: WebSocketManager
) : TaskRepository {

    private val gson = Gson()

    init {
        try {
            wsManager.connect()
        } catch (t: Throwable) {
            LogUtil.w("TaskRepository", "WebSocket connect failed: ${t.message}")
        }
    }

    override fun getBoards(): Flow<List<Board>> = flow {
        try {
            val remote = api.getBoards()
            boardDao.replaceAll(remote.map { it.toEntity() })
        } catch (e: Exception) {
            LogUtil.e("TaskRepository", "getBoards remote failed", e)
        }
        emitAll(boardDao.getBoardsFlow().map { boards -> boards.map { it.toDomain() } })
    }

    override fun getTasksForBoard(boardId: String): Flow<List<Task>> = flow {
        try {
            val remote = api.getTasks(boardId)
            taskDao.replaceAll(remote.map { it.toEntity() })
        } catch (e: Exception) {
            LogUtil.e("TaskRepository", "getTasks remote failed", e)
        }
        emitAll(taskDao.getTasksForBoard(boardId).map { tasks -> tasks.map { it.toDomain() } })
    }

    override suspend fun createTask(task: Task): Unit {
        val created = api.createTask(task.toDto())
        taskDao.upsert(created.toEntity())
        try {
            wsManager.send(gson.toJson(mapOf("event" to "task_created", "task" to created)))
        } catch (t: Throwable) {
            LogUtil.w("TaskRepository", "ws send create failed: ${t.message}")
        }
    }


    override suspend fun updateTask(task: Task) {
        val entity = task.toEntity()
        taskDao.upsert(entity)
        try {
            api.updateTask(task.id, task.toDto())
        } catch (e: Exception) {
            val json = gson.toJson(task.toDto())
            pendingChangeDao.insert(
                PendingChangeEntity(
                    entityId = task.id,
                    entityType = "task",
                    operation = "UPDATE",
                    payloadJson = json
                )
            )
        }
    }

    override suspend fun deleteTask(taskId: String) {
        val now = System.currentTimeMillis()
        taskDao.softDelete(taskId, now)
        val dto = TaskDto(taskId, "", "", "", null, 0, "", now, true)
        try {
            api.updateTask(taskId, dto)
        } catch (e: Exception) {
            val json = gson.toJson(dto)
            pendingChangeDao.insert(
                PendingChangeEntity(
                    entityId = taskId,
                    entityType = "task",
                    operation = "DELETE",
                    payloadJson = json
                )
            )
        }
    }


    override suspend fun createBoard(board: Board) = withContext(Dispatchers.IO) {
        val entity = board.toEntity()
        boardDao.insert(entity)
        try {
            val created = api.createBoard(board.toDto())
            boardDao.insert(created.toEntity())
        } catch (e: Exception) {
            val json = gson.toJson(board.toDto())
            pendingChangeDao.insert(PendingChangeEntity(entityId = board.id, entityType = "board", operation = "CREATE", payloadJson = json))
        }
    }

    
    override suspend fun updateBoard(board: Board) {
        boardDao.update(board.toEntity())
        try {
            api.updateBoard(board.id, board.toDto())
        } catch (e: Exception) {
            val json = gson.toJson(board.toDto())
            pendingChangeDao.insert(
                PendingChangeEntity(
                    entityId = board.id,
                    entityType = "board",
                    operation = "UPDATE",
                    payloadJson = json
                )
            )
        }
    }

override fun getListsForBoard(boardId: String): Flow<List<BoardList>> = flow {
        try {
            val remote = api.getLists(boardId)
            listDao.replaceAll(remote.map { it.toEntity() })
        } catch (e: Exception) {
            LogUtil.e("TaskRepository", "getLists remote failed", e)
        }
        emitAll(listDao.getListsForBoard(boardId).map { lists -> lists.map { it.toDomain() } })
    }

    override suspend fun createList(list: BoardList) = withContext(Dispatchers.IO) {
        val entity = list.toEntity()
        listDao.insert(entity)
        try {
            val created = api.createList(list.toDto())
            listDao.insert(created.toEntity())
        } catch (e: Exception) {
            val json = gson.toJson(list.toDto())
            pendingChangeDao.insert(PendingChangeEntity(entityId = list.id, entityType = "list", operation = "CREATE", payloadJson = json))
        }
    }

    override suspend fun updateList(list: BoardList) {
        listDao.insert(list.toEntity())
        try {
            api.updateList(list.id, list.toDto())
        } catch (e: Exception) {
            val json = gson.toJson(list.toDto())
            pendingChangeDao.insert(
                PendingChangeEntity(
                    entityId = list.id,
                    entityType = "list",
                    operation = "UPDATE",
                    payloadJson = json
                )
            )
        }
    }

    override suspend fun deleteList(listId: String) = withContext(Dispatchers.IO) {
        listDao.deleteById(listId)
        try {
            api.deleteList(listId)
        } catch (e: Exception) {
            val json = gson.toJson(mapOf("id" to listId))
            pendingChangeDao.insert(PendingChangeEntity(entityId = listId, entityType = "list", operation = "DELETE", payloadJson = json))
        }
    }

    override fun observeRemoteEvents() = wsManager.events

    // Pending flush with exponential backoff
    private suspend fun tryFlushPending() {
        suspend fun attemptFlushOnce(): Boolean {
            val pending = pendingChangeDao.getAll()
            if (pending.isEmpty()) return true

            val taskDtos = mutableListOf<TaskDto>()
            val boardDtos = mutableListOf<BoardDto>()
            val listDtos = mutableListOf<ListDto>()

            pending.forEach { pc ->
                try {
                    when (pc.entityType) {
                        "task" -> gson.fromJson(pc.payloadJson, TaskDto::class.java)?.let { taskDtos.add(it) }
                        "board" -> gson.fromJson(pc.payloadJson, BoardDto::class.java)?.let { boardDtos.add(it) }
                        "list" -> gson.fromJson(pc.payloadJson, ListDto::class.java)?.let { listDtos.add(it) }
                    }
                } catch (e: Exception) {
                    LogUtil.e("TaskRepository", "Failed to deserialize pending payload for id=${pc.entityId}", e)
                }
            }

            try {
                if (taskDtos.isNotEmpty()) {
                    val response = api.sync(taskDtos)
                    response.serverChanges.forEach { sc ->
                        try {
                            val local = taskDao.findById(sc.id)
                            if (local != null && local.updatedAt > sc.updatedAt) {
                                // local is newer -> conflict
                                ConflictManager.addConflict(com.google.mytaskmanager.data.conflict.Conflict(local.id, Gson().toJson(local), Gson().toJson(sc), "task"))
                            } else {
                                taskDao.upsert(sc.toEntity())
                            }
                        } catch (e: Exception) {
                            LogUtil.e("TaskRepository", "Error applying server change", e)
                        }
                    }
                }

                if (boardDtos.isNotEmpty()) {
                    boardDtos.forEach { dto ->
                        try {
                            val created = api.createBoard(dto)
                            boardDao.insert(created.toEntity())
                        } catch (e: Exception) {
                            LogUtil.e("TaskRepository", "Failed to create board during flush", e)
                            return false
                        }
                    }
                }

                if (listDtos.isNotEmpty()) {
                    listDtos.forEach { dto ->
                        try {
                            val created = api.createList(dto)
                            listDao.insert(created.toEntity())
                        } catch (e: Exception) {
                            LogUtil.e("TaskRepository", "Failed to create list during flush", e)
                            return false
                        }
                    }
                }

            } catch (e: Exception) {
                LogUtil.e("TaskRepository", "Flush attempt failed", e)
                return false
            }
            // delete only the pending items that were present at start to avoid deleting newly added pending changes.
            pending.forEach { pendingChangeDao.delete(it) }
            return true
        }

        val maxAttempts = 5
        val baseDelayMs = 1000L
        for (attempt in 1..maxAttempts) {
            val success = attemptFlushOnce()
            if (success) return
            val backoff = baseDelayMs * (2.0.pow(attempt.toDouble())).toLong()
            LogUtil.w("TaskRepository", "tryFlushPending attempt $attempt failed, retrying in ${backoff}ms")
            try {
                delay(backoff)
            } catch (ie: Exception) {
                LogUtil.e("TaskRepository", "Delay interrupted during backoff", ie)
            }
        }
        LogUtil.w("TaskRepository", "tryFlushPending failed after $maxAttempts attempts; will retry later")
    }
}
