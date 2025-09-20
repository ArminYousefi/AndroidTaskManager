package com.google.mytaskmanager.di

import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideRetrofit(moshi: Moshi, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.example.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideWebSocketManager(client: OkHttpClient): WebSocketManager = WebSocketManager(client)
}
