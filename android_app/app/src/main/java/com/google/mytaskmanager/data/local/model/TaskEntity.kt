package com.google.mytaskmanager.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(entity = ListEntity::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId"), Index("boardId")]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val boardId: String,
    val listId: String,
    val title: String,
    val description: String?,
    val position: Int,
    val status: String,
    val updatedAt: Long,
    val isDeleted: Boolean
)
