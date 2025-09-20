package com.google.mytaskmanager.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TaskDto(
    val id: String,
    val boardId: String,
    val listId: String,
    val title: String,
    val description: String? = null,
    val position: Int = 0,
    val status: String = "TODO",
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
