package com.google.mytaskmanager.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mytaskmanager.core.AppException
import com.google.mytaskmanager.core.appCall
import com.google.mytaskmanager.data.local.dao.ErrorReportDao
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.dto.ErrorReportBatch
import com.google.mytaskmanager.data.remote.dto.ErrorReportDto
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodic safety net: sends queued offline task changes even when the app is closed. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: PendingChangeProcessor
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (processor.flush()) Result.success() else Result.retry()
}

/** Uploads queued crash/error reports (see `ErrorReporter`). Deletes what the server accepted; retries on transient failures. */
@HiltWorker
class ErrorUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: ErrorReportDao,
    private val api: ApiService
) : CoroutineWorker(context, params) {
    private val gson = Gson()

    override suspend fun doWork(): Result {
        while (true) {
            val rows = dao.batch(20)
            if (rows.isEmpty()) return Result.success()
            val reports = rows.mapNotNull { runCatching { gson.fromJson(it.json, ErrorReportDto::class.java) }.getOrNull() }
            try {
                if (reports.isNotEmpty()) appCall { api.uploadErrors(ErrorReportBatch(reports)) }
                dao.delete(rows.map { it.id })
            } catch (e: AppException) {
                // Rate limited / offline / server down -> try later. Anything else means the batch can never succeed.
                if (e.kind.retryable) return Result.retry()
                dao.delete(rows.map { it.id })
            }
        }
    }
}
