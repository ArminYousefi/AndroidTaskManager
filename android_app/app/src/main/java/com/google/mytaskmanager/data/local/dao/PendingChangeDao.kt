package com.google.mytaskmanager.data.local.dao

import androidx.room.*
import com.google.mytaskmanager.data.local.model.PendingChangeEntity

@Dao
interface PendingChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(change: PendingChangeEntity)

    @Query("SELECT * FROM pending_changes ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingChangeEntity>

    @Delete
    suspend fun delete(change: PendingChangeEntity)

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
