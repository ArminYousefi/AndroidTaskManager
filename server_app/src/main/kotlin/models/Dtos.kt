package models

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(val id: String, val username: String, val email: String? = null)

@Serializable
data class AuthResponse(val token: String, val refreshToken: String? = null, val user: UserDto)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class SignupRequest(val username: String, val password: String, val email: String? = null)

@Serializable
data class BoardDto(val id: String, val name: String, val description: String? = null, val createdAt: Long, val updatedAt: Long)

@Serializable
data class ListDto(val id: String, val boardId: String, val title: String, val position: Int, val createdAt: Long, val updatedAt: Long)

@Serializable
data class TaskDto(val id: String, val boardId: String, val listId: String, val title: String, val description: String? = null, val position: Int = 0, val status: String = "TODO", val updatedAt: Long, val isDeleted: Boolean = false)

@Serializable
data class SyncResponse(val success: Boolean, val serverChanges: List<TaskDto> = emptyList(), val message: String? = null)
