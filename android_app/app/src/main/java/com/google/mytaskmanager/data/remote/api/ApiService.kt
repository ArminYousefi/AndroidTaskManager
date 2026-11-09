package com.google.mytaskmanager.data.remote.api

import com.google.mytaskmanager.data.remote.dto.*
import retrofit2.http.*

/**
 * Retrofit definition of the backend. All calls are `suspend`; failures surface as `HttpException`/`IOException`
 * and are converted by `appCall { }` in the repositories. Endpoints returning 204 are declared as `Unit`.
 */
interface ApiService {
    // ---- auth & profile
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("auth/signup") suspend fun signup(@Body body: SignupRequest): AuthResponse
    @POST("auth/logout") suspend fun logout(@Body body: LogoutRequest)
    @GET("users/me") suspend fun me(): UserDto
    @PATCH("users/me") suspend fun updateProfile(@Body body: UpdateProfileRequest): UserDto
    @POST("users/me/password") suspend fun changePassword(@Body body: ChangePasswordRequest): AuthResponse
    @GET("users/search") suspend fun searchUsers(@Query("q") query: String): List<UserDto>

    // ---- boards
    @GET("boards") suspend fun getBoards(): List<BoardDto>
    @POST("boards") suspend fun createBoard(@Body body: CreateBoardRequest): BoardDto
    @PUT("boards/{id}") suspend fun updateBoard(@Path("id") id: String, @Body body: UpdateBoardRequest): BoardDto
    @DELETE("boards/{id}") suspend fun deleteBoard(@Path("id") id: String)
    @POST("boards/{id}/empty") suspend fun emptyBoard(@Path("id") id: String): EmptyBoardResponse
    @GET("boards/{id}/lists") suspend fun getLists(@Path("id") boardId: String): List<ListDto>
    @GET("boards/{id}/tasks") suspend fun getTasks(@Path("id") boardId: String): List<TaskDto>
    @POST("boards/{id}/lists/reorder") suspend fun reorderLists(@Path("id") boardId: String, @Body body: ReorderRequest): List<ListDto>
    @GET("boards/{id}/activity") suspend fun boardActivity(@Path("id") boardId: String, @Query("limit") limit: Int, @Query("before") before: Long?): List<ActivityDto>

    // ---- board members & teams
    @GET("boards/{id}/members") suspend fun boardMembers(@Path("id") boardId: String): List<MemberDto>
    @POST("boards/{id}/members") suspend fun addBoardMember(@Path("id") boardId: String, @Body body: AddMemberRequest): MemberDto
    @PUT("boards/{id}/members/{userId}") suspend fun updateBoardMember(@Path("id") boardId: String, @Path("userId") userId: String, @Body body: UpdateRoleRequest)
    @DELETE("boards/{id}/members/{userId}") suspend fun removeBoardMember(@Path("id") boardId: String, @Path("userId") userId: String)
    @GET("boards/{id}/teams") suspend fun boardTeams(@Path("id") boardId: String): List<BoardTeamDto>
    @POST("boards/{id}/teams") suspend fun addBoardTeam(@Path("id") boardId: String, @Body body: AddBoardTeamRequest)
    @PUT("boards/{id}/teams/{teamId}") suspend fun updateBoardTeam(@Path("id") boardId: String, @Path("teamId") teamId: String, @Body body: UpdateRoleRequest)
    @DELETE("boards/{id}/teams/{teamId}") suspend fun removeBoardTeam(@Path("id") boardId: String, @Path("teamId") teamId: String)

    // ---- columns
    @POST("lists") suspend fun createList(@Body body: ListDto): ListDto
    @PUT("lists/{id}") suspend fun updateList(@Path("id") id: String, @Body body: ListDto): ListDto
    @DELETE("lists/{id}") suspend fun deleteList(@Path("id") id: String, @Query("moveTo") moveTo: String?, @Query("deleteTasks") deleteTasks: Boolean)

    // ---- tasks
    @POST("tasks") suspend fun createTask(@Body body: TaskDto): TaskDto
    @GET("tasks/{id}") suspend fun getTask(@Path("id") id: String): TaskDto
    @PUT("tasks/{id}") suspend fun updateTask(@Path("id") id: String, @Body body: TaskDto): TaskDto
    @POST("tasks/{id}/move") suspend fun moveTask(@Path("id") id: String, @Body body: MoveTaskRequest): TaskDto
    @DELETE("tasks/{id}") suspend fun deleteTask(@Path("id") id: String)
    @GET("tasks/{id}/comments") suspend fun comments(@Path("id") id: String): List<CommentDto>
    @POST("tasks/{id}/comments") suspend fun addComment(@Path("id") id: String, @Body body: CreateCommentRequest): CommentDto
    @DELETE("tasks/{id}/comments/{commentId}") suspend fun deleteComment(@Path("id") id: String, @Path("commentId") commentId: String)
    @GET("tasks/{id}/activity") suspend fun taskActivity(@Path("id") id: String, @Query("limit") limit: Int, @Query("before") before: Long?): List<ActivityDto>

    // ---- teams
    @GET("teams") suspend fun teams(): List<TeamDto>
    @POST("teams") suspend fun createTeam(@Body body: TeamRequest): TeamDto
    @PUT("teams/{id}") suspend fun updateTeam(@Path("id") id: String, @Body body: TeamRequest): TeamDto
    @DELETE("teams/{id}") suspend fun deleteTeam(@Path("id") id: String)
    @GET("teams/{id}/members") suspend fun teamMembers(@Path("id") id: String): List<MemberDto>
    @POST("teams/{id}/members") suspend fun addTeamMember(@Path("id") id: String, @Body body: AddMemberRequest): MemberDto
    @PUT("teams/{id}/members/{userId}") suspend fun updateTeamMember(@Path("id") id: String, @Path("userId") userId: String, @Body body: UpdateRoleRequest)
    @DELETE("teams/{id}/members/{userId}") suspend fun removeTeamMember(@Path("id") id: String, @Path("userId") userId: String)

    // ---- diagnostics
    @POST("diagnostics/errors") suspend fun uploadErrors(@Body body: ErrorReportBatch): StoredResponse
}
