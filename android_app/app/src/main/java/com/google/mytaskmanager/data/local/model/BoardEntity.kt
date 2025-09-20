package com.google.mytaskmanager.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis()
)
