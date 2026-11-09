package models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Public view of a user. */
@Serializable data class UserDto(val id: String, val username: String, val nickname: String? = null, val email: String? = null)

/** Returned by signup / login / refresh / change-password. */
@Serializable data class AuthResponse(val token: String, val refreshToken: String? = null, val user: UserDto)

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class SignupRequest(val username: String, val password: String, val email: String? = null, val nickname: String? = null)
@Serializable data class RefreshRequest(val refreshToken: String = "")
@Serializable data class LogoutRequest(val refreshToken: String? = null)
@Serializable data class UpdateProfileRequest(val nickname: String)
@Serializable data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/** Board as seen by one user: `myRole` is that user's effective role; counts feed the progress bar. */
@Serializable
data class BoardDto(
    val id: String,
    val name: String,
    val key: String = "BRD",
    val description: String? = null,
    val ownerId: String = "",
    val myRole: String = "ADMIN",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val listCount: Int = 0,
    val taskCount: Int = 0,
    val doneCount: Int = 0
)

@Serializable data class CreateBoardRequest(val id: String? = null, val name: String, val key: String? = null, val description: String? = null, val template: String? = null)
@Serializable data class UpdateBoardRequest(val name: String, val description: String? = null)

/** A board column. */
@Serializable
data class ListDto(
    val id: String = "",
    val boardId: String,
    val title: String,
    val position: Int = 0,
    val category: String = "TODO",
    val wipLimit: Int? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable data class ReorderRequest(val ids: List<String>)

/** An issue. `key` (e.g. WEB-12) and `status` (column category) are derived by the server and ignored on input. */
@Serializable
data class TaskDto(
    val id: String = "",
    val boardId: String,
    val listId: String,
    val key: String = "",
    val number: Int = 0,
    val title: String,
    val description: String? = null,
    val position: Int = 0,
    val status: String = "TODO",
    val priority: String = "MEDIUM",
    val type: String = "TASK",
    val assigneeId: String? = null,
    val reporterId: String? = null,
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val labels: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val isDeleted: Boolean = false
)

@Serializable data class MoveTaskRequest(val listId: String, val position: Int)
@Serializable data class SyncResponse(val success: Boolean, val serverChanges: List<TaskDto> = emptyList(), val message: String? = null)

@Serializable data class CommentDto(val id: String, val taskId: String, val authorId: String, val authorName: String, val body: String, val createdAt: Long)
@Serializable data class CreateCommentRequest(val body: String)

/** One audit-log entry. `details` holds human-readable values such as key, title, from, to. */
@Serializable
data class ActivityDto(
    val id: String,
    val boardId: String,
    val taskId: String? = null,
    val actorId: String,
    val actorName: String,
    val type: String,
    val details: Map<String, String> = emptyMap(),
    val createdAt: Long
)

@Serializable data class TeamDto(val id: String, val name: String, val description: String? = null, val ownerId: String, val myRole: String = "MEMBER", val memberCount: Int = 0)
@Serializable data class TeamRequest(val name: String, val description: String? = null)

/** A person with access to a board or team. `source` is `direct` or `team:<teamName>` (board access via a team). */
@Serializable data class MemberDto(val userId: String, val username: String, val nickname: String? = null, val role: String, val source: String = "direct")
@Serializable data class AddMemberRequest(val username: String, val role: String = "MEMBER")
@Serializable data class UpdateRoleRequest(val role: String)

@Serializable data class BoardTeamDto(val teamId: String, val teamName: String, val role: String, val memberCount: Int = 0)
@Serializable data class AddBoardTeamRequest(val teamId: String, val role: String = "MEMBER")

/** One crash or error uploaded by the Android app. */
@Serializable
data class ErrorReportDto(
    val id: String? = null,
    val level: String = "ERROR",
    val message: String = "",
    val stackTrace: String? = null,
    val screen: String? = null,
    val appVersion: String? = null,
    val osVersion: String? = null,
    val device: String? = null,
    val userId: String? = null,
    val thread: String? = null,
    val breadcrumbs: String? = null,
    val occurredAt: Long = 0,
    val source: String? = null,
    val receivedAt: Long? = null,
    val requestId: String? = null
)
@Serializable data class ErrorReportBatch(val reports: List<ErrorReportDto> = emptyList())

/** Uniform error body for every non-2xx response. */
@Serializable data class ApiErrorDetail(val code: String, val message: String, val details: Map<String, String> = emptyMap(), val requestId: String? = null)
@Serializable data class ApiErrorBody(val error: ApiErrorDetail)

@Serializable data class HealthDto(val status: String, val db: Boolean, val version: String, val time: Long)

/** Envelope of every WebSocket push. `boardId` lets clients ignore events for boards they are not viewing. */
@Serializable data class WsEnvelope(val type: String, val entity: String, val boardId: String? = null, val data: JsonElement)
