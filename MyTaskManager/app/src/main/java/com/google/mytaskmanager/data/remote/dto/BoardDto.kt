package com.google.mytaskmanager.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BoardDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)