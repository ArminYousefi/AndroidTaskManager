package com.google.mytaskmanager.di

import android.content.Context
import com.google.mytaskmanager.data.auth.AuthRepository
import com.google.mytaskmanager.data.auth.AuthRepositoryImpl
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.remote.auth.AuthInterceptor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import javax.inject.Singleton

// AuthModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}

