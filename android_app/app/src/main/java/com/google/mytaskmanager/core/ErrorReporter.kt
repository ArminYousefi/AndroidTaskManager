package com.google.mytaskmanager.core

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.mytaskmanager.BuildConfig
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.local.dao.ErrorReportDao
import com.google.mytaskmanager.data.local.entity.ErrorReportEntity
import com.google.mytaskmanager.data.local.settings.SettingsRepository
import com.google.mytaskmanager.data.remote.dto.ErrorReportDto
import com.google.mytaskmanager.data.sync.ErrorUploadWorker
import com.google.mytaskmanager.util.LogUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects crashes and unexpected errors and delivers them to the developers' server (`POST /diagnostics/errors`).
 *
 * Flow: [report] (or a fatal crash via [CrashHandler]) -> row in the local `error_reports` queue -> [ErrorUploadWorker]
 * uploads when the network is available and deletes what was accepted. Reports include app/OS/device info, the current
 * screen, a stack trace and the last log lines ("breadcrumbs"), but never tokens or passwords.
 * The user can switch reporting off in Settings.
 */
@Singleton
class ErrorReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ErrorReportDao,
    private val settings: SettingsRepository,
    private val authPrefs: AuthPreferences,
    private val messages: MessageCenter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    /** Name of the screen the user is on; updated by navigation. */
    @Volatile var currentScreen: String? = null

    /**
     * Queues a non-fatal report. Returns immediately; never throws.
     *
     * @param t what went wrong.
     * @param level `ERROR` or `WARN`.
     * @param note extra context, e.g. which operation was running.
     */
    fun report(t: Throwable, level: String = "ERROR", note: String? = null) {
        scope.launch {
            runCatching {
                if (!settings.diagnosticsEnabled.first()) return@launch
                val ex = t as? AppException
                val msg = buildString {
                    append(t.javaClass.simpleName).append(": ").append(t.message ?: "")
                    if (note != null) append(" | ").append(note)
                    if (ex?.requestId != null) append(" | requestId=").append(ex.requestId)
                }
                enqueue(build(level, msg, (ex?.cause ?: t).stackTraceToString(), Thread.currentThread().name))
            }.onFailure { LogUtil.w("ErrorReporter", "could not queue report: ${it.javaClass.simpleName}") }
        }
    }

    /**
     * Moves a crash written by [CrashHandler] during the previous run into the upload queue.
     * @return true when a crash from the last run was found (so the UI can say sorry).
     */
    suspend fun ingestPreviousCrash(): Boolean {
        val file = CrashHandler.crashFile(context)
        if (!file.exists()) return false
        val lines = runCatching { file.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
        file.delete()
        if (lines.isEmpty()) return false
        if (settings.diagnosticsEnabled.first()) {
            lines.forEach { dao.insert(ErrorReportEntity(json = it, createdAt = System.currentTimeMillis())) }
            dao.trim(200)
            scheduleUpload()
        }
        messages.info("The app closed unexpectedly last time. Sorry about that!")
        return true
    }

    /** Builds the report envelope with device/app info. Public so [CrashHandler] produces the same format. */
    suspend fun build(level: String, message: String, stack: String, thread: String): ErrorReportDto =
        buildReport(level, message, stack, thread, currentScreen, authPrefs.userIdFlow.first())

    private suspend fun enqueue(dto: ErrorReportDto) {
        dao.insert(ErrorReportEntity(json = gson.toJson(dto), createdAt = System.currentTimeMillis()))
        dao.trim(200)
        scheduleUpload()
    }

    /** Schedules (or re-schedules) the upload job; it waits for connectivity and retries with backoff. */
    fun scheduleUpload() {
        val request = OneTimeWorkRequestBuilder<ErrorUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("upload_error_reports", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        /** Shared by the reporter and the crash handler (which cannot use Hilt/Room while the process is dying). */
        fun buildReport(level: String, message: String, stack: String, thread: String, screen: String?, userId: String?) = ErrorReportDto(
            level = level, message = message.take(4_000), stackTrace = stack.take(20_000), screen = screen,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}", userId = userId, thread = thread,
            breadcrumbs = LogUtil.breadcrumbs().takeLast(8_000), occurredAt = System.currentTimeMillis()
        )
    }
}

/**
 * Last-resort handler for uncaught exceptions. It must be fast and dependency-free because the process is about to
 * die: it appends one JSON line to `files/pending_crash.jsonl` and then lets the previous handler (system dialog)
 * run. [ErrorReporter.ingestPreviousCrash] uploads it on the next launch.
 */
object CrashHandler {
    /** @return the file holding crashes not yet queued. */
    fun crashFile(context: Context): File = File(context.filesDir, "pending_crash.jsonl")

    /** Installs the handler; call once from `Application.onCreate`. */
    fun install(context: Context, screenProvider: () -> String?, userIdProvider: () -> String?) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val file = crashFile(context.applicationContext)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val report = ErrorReporter.buildReport(
                    "FATAL", "${throwable.javaClass.name}: ${throwable.message}", throwable.stackTraceToString(),
                    thread.name, screenProvider(), userIdProvider()
                )
                file.appendText(Gson().toJson(report) + "\n")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
