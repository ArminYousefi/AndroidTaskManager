package com.google.mytaskmanager.domain.model

data class Task(
    val id: String,
    val boardId: String,
    val listId: String,
    val title: String,
    val description: String? = null,
    val position: Int = 0,
    val status: String = "TODO",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
