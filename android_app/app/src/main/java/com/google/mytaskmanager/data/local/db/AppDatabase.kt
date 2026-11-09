package com.google.mytaskmanager.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.mytaskmanager.data.local.dao.*
import com.google.mytaskmanager.data.local.entity.*

/**
 * The on-device cache. The server is the source of truth, so the schema may be rebuilt on upgrade
 * (`fallbackToDestructiveMigration`): everything is re-downloaded after login.
 * Version 2 introduced multi-user boards, issue keys/priorities/dates and the error-report queue.
 */
@Database(
    entities = [BoardEntity::class, ListEntity::class, TaskEntity::class, PendingChangeEntity::class, ErrorReportEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun listDao(): ListDao
    abstract fun taskDao(): TaskDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun errorReportDao(): ErrorReportDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** @return the single database instance of the process. */
        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mytaskmanager.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
