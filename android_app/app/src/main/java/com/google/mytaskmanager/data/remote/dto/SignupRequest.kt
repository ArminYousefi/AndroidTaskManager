package com.google.mytaskmanager.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignupRequest(
    val username: String,
    val password: String,
    val email: String? = null
)