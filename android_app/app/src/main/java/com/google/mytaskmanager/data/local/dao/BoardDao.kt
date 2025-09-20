package com.google.mytaskmanager.data.local.dao

import androidx.room.*
import com.google.mytaskmanager.data.local.model.BoardEntity
import com.google.mytaskmanager.data.local.model.TaskEntity
import com.google.mytaskmanager.domain.model.Board
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY updatedAt DESC")
    fun getBoardsFlow(): Flow<List<BoardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(board: BoardEntity)

    @Update
    suspend fun update(board: BoardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(boards: List<BoardEntity>)

    @Query("DELETE FROM boards WHERE id = :id")
    suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(boards: List<BoardEntity>)

}
