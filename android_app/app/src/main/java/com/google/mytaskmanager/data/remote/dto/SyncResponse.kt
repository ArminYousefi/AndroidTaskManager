package com.google.mytaskmanager.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncResponse(
    val success: Boolean,
    val serverChanges: List<TaskDto> = emptyList(),
    val message: String? = null
)
