package com.google.mytaskmanager.di

import android.content.Context
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.local.dao.*
import com.google.mytaskmanager.data.local.db.AppDatabase
import com.google.mytaskmanager.data.remote.NetworkConfig
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.auth.AuthAuthenticator
import com.google.mytaskmanager.data.remote.auth.AuthInterceptor
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Dependency graph: networking, persistence and session. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun authPreferences(@ApplicationContext ctx: Context): AuthPreferences = AuthPreferences(ctx)

    @Provides @Singleton fun moshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides @Singleton
    fun okHttp(authPrefs: AuthPreferences): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .authenticator(AuthAuthenticator(authPrefs))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(NetworkConfig.HTTP_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides @Singleton fun api(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    @Provides @Singleton fun webSocketManager(client: OkHttpClient): WebSocketManager = WebSocketManager(client)

    @Provides @Singleton fun database(@ApplicationContext ctx: Context): AppDatabase = AppDatabase.getInstance(ctx)
    @Provides fun boardDao(db: AppDatabase): BoardDao = db.boardDao()
    @Provides fun listDao(db: AppDatabase): ListDao = db.listDao()
    @Provides fun taskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun pendingChangeDao(db: AppDatabase): PendingChangeDao = db.pendingChangeDao()
    @Provides fun errorReportDao(db: AppDatabase): ErrorReportDao = db.errorReportDao()
}
