package com.google.mytaskmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.google.mytaskmanager.data.local.model.ListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Query("SELECT * FROM lists WHERE boardId = :boardId ORDER BY position")
    fun getListsForBoard(boardId: String): Flow<List<ListEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: ListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lists: List<ListEntity>)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun replaceAll(lists: List<ListEntity>) {
        // Simple replace: insertAll with REPLACE strategy will overwrite existing ones
        insertAll(lists)
    }
}
