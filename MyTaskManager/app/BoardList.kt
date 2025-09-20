package com.google.mytaskmanager.domain.model

data class BoardList(
    val id: String,
    val boardId: String,
    val title: String,
    val position: Int,
    val updatedAt: Long
)