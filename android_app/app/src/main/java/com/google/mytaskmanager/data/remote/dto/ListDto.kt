package com.google.mytaskmanager.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ListDto(
    val id: String,
    val boardId: String,
    val title: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long
)