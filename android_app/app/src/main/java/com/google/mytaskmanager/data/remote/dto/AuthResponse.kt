package com.google.mytaskmanager.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val refreshToken: String? = null,
    val user: UserDto
)
