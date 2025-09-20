package com.google.mytaskmanager.data.remote.api

import com.google.mytaskmanager.data.remote.dto.BoardDto
import com.google.mytaskmanager.data.remote.dto.TaskDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT

interface ApiService {
    @GET("boards")
    suspend fun getBoards(): List<BoardDto>

    // NEW
    @POST("boards")
    suspend fun createBoard(@Body board: BoardDto): BoardDto

    @GET("boards/{boardId}/tasks")
    suspend fun getTasks(@Path("boardId") boardId: String): List<TaskDto>

    @POST("tasks")
    suspend fun createTask(@Body task: TaskDto): TaskDto

    @PUT("tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body task: TaskDto): TaskDto

    @POST("sync")
    suspend fun sync(@Body changes: List<TaskDto>): com.google.mytaskmanager.data.remote.dto.SyncResponse
}
