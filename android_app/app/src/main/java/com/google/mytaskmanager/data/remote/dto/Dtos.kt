package com.google.mytaskmanager.data.remote.dto

import com.squareup.moshi.JsonClass

/*
 * Wire format of the backend (see server_app/.../models/Dtos.kt). Every field has a default so that an older
 * or newer server never crashes JSON parsing.
 */

@JsonClass(generateAdapter = true) data class UserDto(val id: String = "", val username: String = "", val nickname: String? = null, val email: String? = null)
@JsonClass(generateAdapter = true) data class AuthResponse(val token: String = "", val refreshToken: String? = null, val user: UserDto = UserDto())
@JsonClass(generateAdapter = true) data class LoginRequest(val username: String, val password: String)
@JsonClass(generateAdapter = true) data class SignupRequest(val username: String, val password: String, val email: String? = null, val nickname: String? = null)
@JsonClass(generateAdapter = true) data class RefreshRequest(val refreshToken: String)
@JsonClass(generateAdapter = true) data class LogoutRequest(val refreshToken: String?)
@JsonClass(generateAdapter = true) data class UpdateProfileRequest(val nickname: String)
@JsonClass(generateAdapter = true) data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@JsonClass(generateAdapter = true)
data class BoardDto(
    val id: String = "", val name: String = "", val key: String = "BRD", val description: String? = null, val ownerId: String = "",
    val myRole: String = "VIEWER", val createdAt: Long = 0, val updatedAt: Long = 0,
    val listCount: Int = 0, val taskCount: Int = 0, val doneCount: Int = 0
)
@JsonClass(generateAdapter = true) data class CreateBoardRequest(val id: String?, val name: String, val key: String?, val description: String?, val template: String?)
@JsonClass(generateAdapter = true) data class UpdateBoardRequest(val name: String, val description: String?)
@JsonClass(generateAdapter = true) data class EmptyBoardResponse(val removed: Int = 0)

@JsonClass(generateAdapter = true)
data class ListDto(
    val id: String = "", val boardId: String = "", val title: String = "", val position: Int = 0,
    val category: String = "TODO", val wipLimit: Int? = null, val createdAt: Long = 0, val updatedAt: Long = 0
)
@JsonClass(generateAdapter = true) data class ReorderRequest(val ids: List<String>)

@JsonClass(generateAdapter = true)
data class TaskDto(
    val id: String = "", val boardId: String = "", val listId: String = "", val key: String = "", val number: Int = 0,
    val title: String = "", val description: String? = null, val position: Int = 0, val status: String = "TODO",
    val priority: String = "MEDIUM", val type: String = "TASK", val assigneeId: String? = null, val reporterId: String? = null,
    val startDate: Long? = null, val dueDate: Long? = null, val labels: List<String> = emptyList(),
    val createdAt: Long = 0, val updatedAt: Long = 0, val isDeleted: Boolean = false
)
@JsonClass(generateAdapter = true) data class MoveTaskRequest(val listId: String, val position: Int)

@JsonClass(generateAdapter = true) data class CommentDto(val id: String = "", val taskId: String = "", val authorId: String = "", val authorName: String = "", val body: String = "", val createdAt: Long = 0)
@JsonClass(generateAdapter = true) data class CreateCommentRequest(val body: String)

@JsonClass(generateAdapter = true)
data class ActivityDto(
    val id: String = "", val boardId: String = "", val taskId: String? = null, val actorId: String = "", val actorName: String = "",
    val type: String = "", val details: Map<String, String> = emptyMap(), val createdAt: Long = 0
)

@JsonClass(generateAdapter = true) data class TeamDto(val id: String = "", val name: String = "", val description: String? = null, val ownerId: String = "", val myRole: String = "MEMBER", val memberCount: Int = 0)
@JsonClass(generateAdapter = true) data class TeamRequest(val name: String, val description: String?)
@JsonClass(generateAdapter = true) data class MemberDto(val userId: String = "", val username: String = "", val nickname: String? = null, val role: String = "MEMBER", val source: String = "direct")
@JsonClass(generateAdapter = true) data class AddMemberRequest(val username: String, val role: String)
@JsonClass(generateAdapter = true) data class UpdateRoleRequest(val role: String)
@JsonClass(generateAdapter = true) data class BoardTeamDto(val teamId: String = "", val teamName: String = "", val role: String = "MEMBER", val memberCount: Int = 0)
@JsonClass(generateAdapter = true) data class AddBoardTeamRequest(val teamId: String, val role: String)

@JsonClass(generateAdapter = true)
data class ErrorReportDto(
    val id: String? = null, val level: String = "ERROR", val message: String = "", val stackTrace: String? = null,
    val screen: String? = null, val appVersion: String? = null, val osVersion: String? = null, val device: String? = null,
    val userId: String? = null, val thread: String? = null, val breadcrumbs: String? = null, val occurredAt: Long = 0
)
@JsonClass(generateAdapter = true) data class ErrorReportBatch(val reports: List<ErrorReportDto>)

@JsonClass(generateAdapter = true) data class ApiErrorDetail(val code: String = "", val message: String = "", val details: Map<String, String> = emptyMap(), val requestId: String? = null)
@JsonClass(generateAdapter = true) data class ApiErrorBody(val error: ApiErrorDetail = ApiErrorDetail())
@JsonClass(generateAdapter = true) data class StoredResponse(val stored: Int = 0)
