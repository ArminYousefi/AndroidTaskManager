package com.google.mytaskmanager.data.conflict

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.google.mytaskmanager.data.remote.dto.TaskDto
import com.google.mytaskmanager.data.local.model.TaskEntity
import com.google.gson.Gson
import com.google.mytaskmanager.data.local.model.PendingChangeEntity
import com.google.mytaskmanager.data.local.db.AppDatabase
import com.google.mytaskmanager.util.LogUtil
import android.content.Context
import com.google.mytaskmanager.data.util.toEntity
import kotlinx.coroutines.launch

data class Conflict(val localId: String, val localJson: String, val serverJson: String, val entityType: String)

object ConflictManager {
    private val _conflicts = MutableSharedFlow<Conflict>(extraBufferCapacity = 100)
    val conflicts = _conflicts.asSharedFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun addConflict(conflict: Conflict) {
        _conflicts.tryEmit(conflict)
    }

    fun resolveKeepLocal(conflict: Conflict) {
        try {
            val ctx = appContext ?: run { LogUtil.w("ConflictManager", "No app context for resolving conflicts"); return }
            val db = AppDatabase.getInstance(ctx)
            val gson = Gson()
            val local = gson.fromJson(conflict.localJson, TaskEntity::class.java)
            // upsert local entity
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { db.taskDao().upsert(local) }
            // re-queue pending change to push local to server
            val dto = TaskDto(local.id, local.boardId, local.listId, local.title, local.description, local.position, local.status, local.updatedAt, local.isDeleted)
            val payload = gson.toJson(dto)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { db.pendingChangeDao().insert(PendingChangeEntity(entityId = local.id, entityType = "task", operation = "UPDATE", payloadJson = payload)) }
        } catch (t: Throwable) {
            LogUtil.e("ConflictManager", "resolveKeepLocal failed", t)
        }
    }

    fun resolveKeepServer(conflict: Conflict) {
        try {
            val ctx = appContext ?: run { LogUtil.w("ConflictManager", "No app context for resolving conflicts"); return }
            val db = AppDatabase.getInstance(ctx)
            val gson = Gson()
            val dto = gson.fromJson(conflict.serverJson, TaskDto::class.java)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { db.taskDao().upsert(dto.toEntity()) }
            // remove pending local change for this id (best-effort)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { val pending = db.pendingChangeDao().getAll().filter { it.entityId == dto.id }; pending.forEach { db.pendingChangeDao().delete(it) } }
        } catch (t: Throwable) {
            LogUtil.e("ConflictManager", "resolveKeepServer failed", t)
        }
    }
}
