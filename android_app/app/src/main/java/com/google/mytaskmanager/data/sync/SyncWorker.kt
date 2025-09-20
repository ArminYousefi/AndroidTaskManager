package com.google.mytaskmanager.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.mytaskmanager.data.local.db.AppDatabase
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.util.LogUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.google.mytaskmanager.data.util.toEntity

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: ApiService,
    private val db: AppDatabase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val pendingDao = db.pendingChangeDao()
            val taskDao = db.taskDao()
            val boardDao = db.boardDao()
            val listDao = db.listDao()
            val pending = pendingDao.getAll()
            if (pending.isEmpty()) return Result.success()

            val gson = Gson()

            val taskDtos = mutableListOf<com.google.mytaskmanager.data.remote.dto.TaskDto>()
            val boardDtos = mutableListOf<com.google.mytaskmanager.data.remote.dto.BoardDto>()
            val listDtos = mutableListOf<com.google.mytaskmanager.data.remote.dto.ListDto>()

            pending.forEach { pc ->
                try {
                    when (pc.entityType) {
                        "task" -> gson.fromJson(pc.payloadJson, com.google.mytaskmanager.data.remote.dto.TaskDto::class.java)?.let { taskDtos.add(it) }
                        "board" -> gson.fromJson(pc.payloadJson, com.google.mytaskmanager.data.remote.dto.BoardDto::class.java)?.let { boardDtos.add(it) }
                        "list" -> gson.fromJson(pc.payloadJson, com.google.mytaskmanager.data.remote.dto.ListDto::class.java)?.let { listDtos.add(it) }
                    }
                } catch (e: Exception) {
                    LogUtil.e("SyncWorker", "Failed to deserialize pending payload id=${pc.entityId}", e)
                }
            }

            if (taskDtos.isNotEmpty()) {
                val response = api.sync(taskDtos)
                response.serverChanges.forEach { sc -> taskDao.upsert(sc.toEntity()) }
            }
            // For boards/lists, attempt create endpoints
            boardDtos.forEach { dto ->
                try {
                    val created = api.createBoard(dto)
                    boardDao.insert(created.toEntity())
                } catch (e: Exception) {
                    LogUtil.e("SyncWorker", "Failed to create board during sync", e)
                }
            }
            listDtos.forEach { dto ->
                try {
                    val created = api.createList(dto)
                    listDao.insert(created.toEntity())
                } catch (e: Exception) {
                    LogUtil.e("SyncWorker", "Failed to create list during sync", e)
                }
            }

            // delete pending processed items
            pending.forEach { pendingDao.delete(it) }

            return Result.success()
        } catch (t: Throwable) {
            LogUtil.e("SyncWorker", "Sync failed", t)
            return Result.retry()
        }
    }
}
