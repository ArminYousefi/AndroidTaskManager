package com.google.mytaskmanager.data.local.dao

import androidx.room.*
import androidx.room.Embedded
import com.google.mytaskmanager.data.local.model.BoardEntity
import com.google.mytaskmanager.data.local.model.TaskEntity
import com.google.mytaskmanager.domain.model.Board
import kotlinx.coroutines.flow.Flow

/** Board plus counts aggregated from the lists/tasks tables — see getBoardsWithCountsFlow(). */
data class BoardWithCounts(
    @Embedded val board: BoardEntity,
    val listCount: Int,
    val taskCount: Int
)

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY updatedAt DESC")
    fun getBoardsFlow(): Flow<List<BoardEntity>>

    // Board.listCount/taskCount were always 0 before — nothing ever populated
    // them. This aggregates the real counts from the local lists/tasks tables so
    // the UI can show accurate numbers without an extra server round-trip.
    @Query(
        """
        SELECT b.*,
          (SELECT COUNT(*) FROM lists l WHERE l.boardId = b.id) AS listCount,
          (SELECT COUNT(*) FROM tasks t WHERE t.boardId = b.id AND t.isDeleted = 0) AS taskCount
        FROM boards b
        ORDER BY b.updatedAt DESC
        """
    )
    fun getBoardsWithCountsFlow(): Flow<List<BoardWithCounts>>

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
