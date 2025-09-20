package com.google.mytaskmanager.di

import android.content.Context
import androidx.room.Room
import com.google.mytaskmanager.data.local.dao.BoardDao
import com.google.mytaskmanager.data.local.dao.ListDao
import com.google.mytaskmanager.data.local.dao.PendingChangeDao
import com.google.mytaskmanager.data.local.dao.TaskDao
import com.google.mytaskmanager.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "task_manager.db").build()

    @Provides fun provideBoardDao(db: AppDatabase): BoardDao = db.boardDao()
    @Provides fun provideListDao(db: AppDatabase): ListDao = db.listDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun providePendingChangeDao(db: AppDatabase): PendingChangeDao = db.pendingChangeDao()
}
