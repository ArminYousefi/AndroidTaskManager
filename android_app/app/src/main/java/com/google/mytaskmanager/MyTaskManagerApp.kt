package com.google.mytaskmanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.mytaskmanager.core.CrashHandler
import com.google.mytaskmanager.core.ErrorReporter
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.remote.auth.TokenProvider
import com.google.mytaskmanager.data.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Process start-up: crash handler, session restore, background sync scheduling.
 * Implements [Configuration.Provider] so WorkManager can create `@HiltWorker` classes (the default initializer is
 * removed in the manifest).
 */
@HiltAndroidApp
class MyTaskManagerApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var errorReporter: ErrorReporter
    @Inject lateinit var authPrefs: AuthPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var userId: String? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate() // Hilt injects the fields above here

        // 1) Crash capture first, so even start-up crashes are recorded.
        CrashHandler.install(this, screenProvider = { errorReporter.currentScreen }, userIdProvider = { userId })

        // 2) Restore the session token synchronously: the first API call may happen immediately.
        runBlocking { authPrefs.authTokenFlow.first()?.let { TokenProvider.setToken(it) } }
        appScope.launch { authPrefs.userIdFlow.collect { userId = it } }

        // 3) Upload a crash from the previous run, and send queued offline changes every 15 minutes when online.
        appScope.launch { errorReporter.ingestPreviousCrash() }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "pending_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }
}
