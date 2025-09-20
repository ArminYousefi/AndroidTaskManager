package com.google.mytaskmanager.data.remote.api

import com.google.mytaskmanager.data.remote.dto.AuthResponse
import com.google.mytaskmanager.data.remote.dto.BoardDto
import com.google.mytaskmanager.data.remote.dto.TaskDto
import com.google.mytaskmanager.data.remote.dto.ListDto
import com.google.mytaskmanager.data.remote.dto.LoginRequest
import com.google.mytaskmanager.data.remote.dto.SignupRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
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
    @PUT("boards/{id}")
    suspend fun updateBoard(@Path("id") id: String, @Body board: BoardDto): BoardDto


    @GET("boards/{boardId}/tasks")
    suspend fun getTasks(@Path("boardId") boardId: String): List<TaskDto>

    @POST("tasks")
    suspend fun createTask(@Body task: TaskDto): TaskDto

    @PUT("tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body task: TaskDto): TaskDto

    @POST("sync")
    suspend fun sync(@Body changes: List<TaskDto>): com.google.mytaskmanager.data.remote.dto.SyncResponse

    @GET("boards/{boardId}/lists")
    suspend fun getLists(@Path("boardId") boardId: String): List<ListDto>

    @POST("lists")
    suspend fun createList(@Body list: ListDto): ListDto

    @PUT("lists/{id}")
    suspend fun updateList(@Path("id") id: String, @Body list: ListDto): ListDto

    @DELETE("lists/{id}")
    suspend fun deleteList(@Path("id") id: String)


// Authentication
@POST("auth/login")
suspend fun login(@Body credentials: LoginRequest): AuthResponse

    @POST("auth/signup")
    suspend fun signup(@Body user: SignupRequest): AuthResponse
}
