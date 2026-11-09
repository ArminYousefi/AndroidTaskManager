package com.google.mytaskmanager.di

import com.google.mytaskmanager.data.auth.AuthRepositoryImpl
import com.google.mytaskmanager.data.repository.CollabRepositoryImpl
import com.google.mytaskmanager.data.repository.TaskRepositoryImpl
import com.google.mytaskmanager.domain.repository.AuthRepository
import com.google.mytaskmanager.domain.repository.CollabRepository
import com.google.mytaskmanager.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds repository interfaces to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun auth(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun tasks(impl: TaskRepositoryImpl): TaskRepository
    @Binds @Singleton abstract fun collab(impl: CollabRepositoryImpl): CollabRepository
}
