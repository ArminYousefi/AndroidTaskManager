
package com.google.mytaskmanager.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val username: String,
    val email: String? = null
)
