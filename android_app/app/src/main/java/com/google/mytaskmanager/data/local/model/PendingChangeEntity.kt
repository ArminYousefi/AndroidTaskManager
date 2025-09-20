package com.google.mytaskmanager.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityId: String,
    val entityType: String,
    val operation: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)