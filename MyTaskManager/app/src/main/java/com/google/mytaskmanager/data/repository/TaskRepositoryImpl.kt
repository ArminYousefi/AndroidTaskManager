package com.google.mytaskmanager.data.repository

import com.google.mytaskmanager.data.local.model.PendingChangeEntity
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.dto.TaskDto
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.Board
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.domain.repository.TaskRepository
import com.google.gson.Gson
import com.google.mytaskmanager.data.local.dao.BoardDao
import com.google.mytaskmanager.data.local.dao.ListDao
import com.google.mytaskmanager.data.local.dao.PendingChangeDao
import com.google.mytaskmanager.data.local.dao.TaskDao
import com.google.mytaskmanager.data.remote.dto.BoardDto
import com.google.mytaskmanager.data.remote.dto.ListDto
import com.google.mytaskmanager.data.remote.websocket.WsEvent
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
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            wsManager.events.collect { evt ->
                when (evt) {
                    is WsEvent.TaskCreated -> taskDao.upsert(evt.task.toEntity())
                    is WsEvent.TaskUpdated -> taskDao.upsert(evt.task.toEntity())
                    // TODO: add list-related WS events if backend supports
                    else -> Unit
                }
            }
        }
        wsManager.connect()
        scope.launch { tryFlushPending() }
    }

    // ------------------------
    // Boards
    // ------------------------

    override fun getBoards(): Flow<List<Board>> = flow {
        try {
            val remote = api.getBoards()
            boardDao.replaceAll(remote.map { it.toEntity() })
        } catch (_: Exception) {
            // offline → fallback to local only
        }
        emitAll(boardDao.getBoardsFlow().map { boards -> boards.map { it.toDomain() } })
    }

    override suspend fun createBoard(board: Board) = withContext(Dispatchers.IO) {
        val entity = board.toEntity()
        boardDao.insert(entity)

        try {
            val created = api.createBoard(board.toDto())
            boardDao.insert(created.toEntity()) // overwrite with server version
        } catch (e: Exception) {
            val json = gson.toJson(board.toDto())
            pendingChangeDao.insert(
                PendingChangeEntity(board.id, "board", "CREATE", json)
            )
        }
    }

    // ------------------------
    // Lists (BoardList)
    // ------------------------

    override fun getListsForBoard(boardId: String): Flow<List<BoardList>> = flow {
        try {
            val remote = api.getLists(boardId)
            listDao.replaceAll(remote.map { it.toEntity() })
        } catch (_: Exception) {
            // offline → fallback
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
            pendingChangeDao.insert(
                PendingChangeEntity(list.id, "list", "CREATE", json)
            )
        }
    }

    override suspend fun updateList(list: BoardList) = withContext(Dispatchers.IO) {
        val entity = list.toEntity()
        listDao.insert(entity)

        try {
            val updated = api.updateList(list.id, list.toDto())
            listDao.insert(updated.toEntity())
        } catch (e: Exception) {
            val json = gson.toJson(list.toDto())
            pendingChangeDao.insert(
                PendingChangeEntity(list.id, "list", "UPDATE", json)
            )
        }
    }

    override suspend fun deleteList(listId: String) = withContext(Dispatchers.IO) {
        listDao.deleteById(listId)
        try {
            api.deleteList(listId)
        } catch (e: Exception) {
            val json = gson.toJson(mapOf("id" to listId))
            pendingChangeDao.insert(
                PendingChangeEntity(listId, "list", "DELETE", json)
            )
        }
    }

    // ------------------------
    // Tasks
    // ------------------------

    override fun getTasksForBoard(boardId: String): Flow<List<Task>> = flow {
        try {
            val remote = api.getTasks(boardId)
            taskDao.replaceAll(remote.map { it.toEntity() })
        } catch (_: Exception) {
            // offline → fallback
        }
        emitAll(taskDao.getTasksForBoard(boardId).map { tasks -> tasks.map { it.toDomain() } })
    }

    override suspend fun createTask(task: Task) = withContext(Dispatchers.IO) {
        val entity = task.toEntity()
        taskDao.upsert(entity)

        try {
            val created = api.createTask(task.toDto())
            taskDao.upsert(created.toEntity())
        } catch (e: Exception) {
            val json = gson.toJson(task.toDto())
            pendingChangeDao.insert(PendingChangeEntity(task.id, "task", "CREATE", json))
        }
    }

    override suspend fun updateTask(task: Task) = withContext(Dispatchers.IO) {
        val entity = task.toEntity()
        taskDao.upsert(entity)

        try {
            val updated = api.updateTask(task.id, task.toDto())
            taskDao.upsert(updated.toEntity())
        } catch (e: Exception) {
            val json = gson.toJson(task.toDto())
            pendingChangeDao.insert(PendingChangeEntity(task.id, "task", "UPDATE", json))
        }
    }

    override suspend fun deleteTask(taskId: String): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        taskDao.softDelete(taskId, now)
        val dto = TaskDto(taskId, "", "", "", null, 0, "", now, true)

        try {
            api.updateTask(taskId, dto)
        } catch (e: Exception) {
            val json = gson.toJson(dto)
            pendingChangeDao.insert(PendingChangeEntity(taskId, "task", "DELETE", json))
        }
    }

    // ------------------------
    // WebSocket
    // ------------------------

    override fun observeRemoteEvents() = wsManager.events

    // ------------------------
    // Sync pending
    // ------------------------

    private suspend fun tryFlushPending() {
        val pending = pendingChangeDao.getAll()
        if (pending.isEmpty()) return

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
            } catch (_: Exception) {}
        }

        try {
            if (taskDtos.isNotEmpty()) {
                val response = api.sync(taskDtos)
                response.serverChanges.forEach { sc -> taskDao.upsert(sc.toEntity()) }
            }

            if (boardDtos.isNotEmpty()) {
                boardDtos.forEach { dto ->
                    try {
                        val created = api.createBoard(dto)
                        boardDao.insert(created.toEntity())
                    } catch (_: Exception) { return }
                }
            }

            if (listDtos.isNotEmpty()) {
                listDtos.forEach { dto ->
                    try {
                        val created = api.createList(dto)
                        listDao.insert(created.toEntity())
                    } catch (_: Exception) { return }
                }
            }

            pending.forEach { pendingChangeDao.delete(it) }
        } catch (_: Exception) {
            // keep pending
        }
    }
}
