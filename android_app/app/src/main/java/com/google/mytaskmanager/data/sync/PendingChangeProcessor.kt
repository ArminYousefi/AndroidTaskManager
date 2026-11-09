package com.google.mytaskmanager.data.sync

import com.google.gson.Gson
import com.google.mytaskmanager.core.AppException
import com.google.mytaskmanager.core.ErrorReporter
import com.google.mytaskmanager.core.appCall
import com.google.mytaskmanager.data.local.dao.PendingChangeDao
import com.google.mytaskmanager.data.local.dao.TaskDao
import com.google.mytaskmanager.data.local.entity.PendingChangeEntity
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.dto.MoveTaskRequest
import com.google.mytaskmanager.data.remote.dto.TaskDto
import com.google.mytaskmanager.data.util.toEntity
import com.google.mytaskmanager.util.LogUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Operation names stored in [PendingChangeEntity.operation]. */
object PendingOp {
    const val TASK_CREATE = "TASK_CREATE"
    const val TASK_UPDATE = "TASK_UPDATE"
    const val TASK_MOVE = "TASK_MOVE"
    const val TASK_DELETE = "TASK_DELETE"
}

/**
 * Replays queued offline changes **in the order they were made**, each through the proper endpoint
 * (the old implementation sent everything to the board-create endpoint).
 *
 * - success: the row is removed and the server's version of the task replaces the local one;
 * - transient failure (offline, timeout, 5xx): stop and keep the rest for the next attempt, so order is preserved;
 * - permanent failure (e.g. 403 because the role changed, 404 because the board was deleted): the change is dropped
 *   and reported, the rest continues.
 */
@Singleton
class PendingChangeProcessor @Inject constructor(
    private val api: ApiService,
    private val dao: PendingChangeDao,
    private val taskDao: TaskDao,
    private val reporter: ErrorReporter
) {
    private val mutex = Mutex()
    private val gson = Gson()

    /** @return true when the queue is empty afterwards. */
    suspend fun flush(): Boolean = mutex.withLock {
        for (change in dao.all()) {
            try {
                apply(change)
                dao.delete(change.id)
            } catch (e: AppException) {
                if (e.kind.retryable) { LogUtil.i("PendingChanges", "paused at ${change.operation}: ${e.kind}"); return@withLock false }
                LogUtil.w("PendingChanges", "dropping ${change.operation}: ${e.kind}/${e.code}")
                reporter.report(e, "WARN", "dropped pending ${change.operation}")
                dao.delete(change.id)
                if (change.operation == PendingOp.TASK_CREATE) taskDao.delete(change.entityId) // the task never reached the server
            }
        }
        true
    }

    private suspend fun apply(c: PendingChangeEntity) {
        when (c.operation) {
            PendingOp.TASK_CREATE -> store(appCall { api.createTask(gson.fromJson(c.payload, TaskDto::class.java)) })
            PendingOp.TASK_UPDATE -> store(appCall { api.updateTask(c.entityId, gson.fromJson(c.payload, TaskDto::class.java)) })
            PendingOp.TASK_MOVE -> store(appCall { api.moveTask(c.entityId, gson.fromJson(c.payload, MoveTaskRequest::class.java)) })
            PendingOp.TASK_DELETE -> appCall { api.deleteTask(c.entityId) }
            else -> LogUtil.w("PendingChanges", "unknown operation ${c.operation}")
        }
    }

    /** Keeps the local row aligned with the server's canonical version (key, number, positions). */
    private suspend fun store(dto: TaskDto) { if (taskDao.get(dto.id) != null) taskDao.upsert(dto.toEntity()) }
}
