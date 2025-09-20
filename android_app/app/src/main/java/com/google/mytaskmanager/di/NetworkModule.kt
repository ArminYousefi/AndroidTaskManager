package com.google.mytaskmanager.di

import android.content.Context
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.auth.AuthInterceptor
import com.google.mytaskmanager.data.remote.auth.AuthAuthenticator
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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthPreferences(@ApplicationContext context: Context): AuthPreferences =
        AuthPreferences(context)

    @Provides
    @Singleton
    fun provideAuthInterceptor(authPrefs: AuthPreferences): AuthInterceptor =
        AuthInterceptor(authPrefs)

    @Provides
    @Singleton
    fun provideAuthAuthenticator(authPrefs: AuthPreferences): AuthAuthenticator =
        AuthAuthenticator(authPrefs)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        interceptor: AuthInterceptor,
        authenticator: AuthAuthenticator
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .authenticator(authenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())  // <-- this is required
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(moshi: Moshi, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideWebSocketManager(client: OkHttpClient): WebSocketManager =
        WebSocketManager(client)
}