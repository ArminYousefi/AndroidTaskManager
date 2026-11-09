package com.google.mytaskmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached board. [myRole] is the signed-in user's role (BoardRole name). */
@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey val id: String, val name: String, val key: String, val description: String?, val ownerId: String,
    val myRole: String, val createdAt: Long, val updatedAt: Long, val listCount: Int, val taskCount: Int, val doneCount: Int
)

/** Cached column. */
@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String, val boardId: String, val title: String, val position: Int,
    val category: String, val wipLimit: Int?, val updatedAt: Long
)

/** Cached issue. Labels are stored comma-separated (the server forbids commas inside a label). */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String, val boardId: String, val listId: String, val key: String, val number: Int,
    val title: String, val description: String?, val position: Int, val priority: String, val type: String,
    val assigneeId: String?, val reporterId: String?, val startDate: Long?, val dueDate: Long?,
    val labels: String, val createdAt: Long, val updatedAt: Long
)

/**
 * A task change made while offline (or that failed transiently), replayed in order by the sync worker.
 *
 * @property operation `TASK_CREATE|TASK_UPDATE|TASK_MOVE|TASK_DELETE`.
 * @property payload JSON of the request body (TaskDto or MoveTaskRequest; empty for delete).
 */
@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityId: String, val boardId: String, val operation: String, val payload: String, val createdAt: Long
)

/** An error report waiting to be uploaded to the developers' server. [json] is an `ErrorReportDto`. */
@Entity(tableName = "error_reports")
data class ErrorReportEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val json: String, val createdAt: Long)
