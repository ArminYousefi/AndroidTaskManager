package com.google.mytaskmanager.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.google.mytaskmanager.data.local.dao.BoardDao
import com.google.mytaskmanager.data.local.dao.ListDao
import com.google.mytaskmanager.data.local.dao.PendingChangeDao
import com.google.mytaskmanager.data.local.dao.TaskDao
import com.google.mytaskmanager.data.local.model.BoardEntity
import com.google.mytaskmanager.data.local.model.ListEntity
import com.google.mytaskmanager.data.local.model.PendingChangeEntity
import com.google.mytaskmanager.data.local.model.TaskEntity

@Database(
    entities = [BoardEntity::class, ListEntity::class, TaskEntity::class, PendingChangeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun listDao(): ListDao
    abstract fun taskDao(): TaskDao
    abstract fun pendingChangeDao(): PendingChangeDao
}
