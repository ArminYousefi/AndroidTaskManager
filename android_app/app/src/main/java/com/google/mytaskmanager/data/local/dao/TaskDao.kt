package com.google.mytaskmanager.data.local.dao

import androidx.room.*
import com.google.mytaskmanager.data.local.model.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE boardId = :boardId AND isDeleted = 0 ORDER BY position")
    fun getTasksForBoard(boardId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE listId = :listId AND isDeleted = 0 ORDER BY position")
    fun getTasksForList(listId: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TaskEntity?


    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun softDelete(taskId: String, updatedAt: Long = System.currentTimeMillis())
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(entities: List<TaskEntity>)

}
