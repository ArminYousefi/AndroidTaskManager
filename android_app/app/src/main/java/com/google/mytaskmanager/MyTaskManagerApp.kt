package com.google.mytaskmanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.mytaskmanager.data.conflict.ConflictManager
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.remote.auth.TokenProvider
import com.google.mytaskmanager.data.sync.SyncWorker
import com.google.mytaskmanager.util.LogUtil
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MyTaskManagerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // --- Hydrate token early ---
        val prefs = AuthPreferences(this)
        runBlocking(Dispatchers.IO) {
            try {
                val token = prefs.authTokenFlow.firstOrNull()
                if (!token.isNullOrBlank()) {
                    TokenProvider.setToken(token)
                    LogUtil.i("MyTaskManagerApp", "Rehydrated token on startup: ${token.take(10)}...")
                } else {
                    LogUtil.i("MyTaskManagerApp", "No persisted token found on startup")
                }
            } catch (t: Throwable) {
                LogUtil.e("MyTaskManagerApp", "Token rehydration failed", t)
            }
        }

        // --- Init your conflict manager ---
        ConflictManager.init(this)

        // --- Schedule periodic sync every 15 minutes ---
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync_pending_changes",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
