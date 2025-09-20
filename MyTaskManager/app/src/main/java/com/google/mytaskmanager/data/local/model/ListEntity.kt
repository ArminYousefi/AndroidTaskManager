package com.google.mytaskmanager.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lists",
    foreignKeys = [ForeignKey(entity = BoardEntity::class, parentColumns = ["id"], childColumns = ["boardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("boardId")]
)
data class ListEntity(
    @PrimaryKey val id: String,
    val boardId: String,
    val title: String,
    val position: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
