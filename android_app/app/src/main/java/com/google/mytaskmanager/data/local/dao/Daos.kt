package com.google.mytaskmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.google.mytaskmanager.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/** Cached boards. */
@Dao
abstract class BoardDao {
    @Query("SELECT * FROM boards ORDER BY updatedAt DESC") abstract fun observeAll(): Flow<List<BoardEntity>>
    @Query("SELECT * FROM boards WHERE id = :id") abstract suspend fun get(id: String): BoardEntity?
    @Query("SELECT * FROM boards WHERE id = :id") abstract fun observe(id: String): Flow<BoardEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsert(board: BoardEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertAll(boards: List<BoardEntity>)
    @Query("DELETE FROM boards WHERE id = :id") abstract suspend fun delete(id: String)
    @Query("DELETE FROM boards") abstract suspend fun clear()
    @Query("SELECT id FROM boards") abstract suspend fun ids(): List<String>

    /** Makes the cache equal to [boards]: upserts them and removes boards the server no longer returns. */
    @Transaction
    open suspend fun replaceAll(boards: List<BoardEntity>) {
        val keep = boards.map { it.id }.toSet()
        ids().filter { it !in keep }.forEach { delete(it) }
        upsertAll(boards)
    }
}

/** Cached columns. */
@Dao
abstract class ListDao {
    @Query("SELECT * FROM lists WHERE boardId = :boardId ORDER BY position ASC") abstract fun observeForBoard(boardId: String): Flow<List<ListEntity>>
    @Query("SELECT * FROM lists WHERE id = :id") abstract suspend fun get(id: String): ListEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertAll(lists: List<ListEntity>)
    @Query("DELETE FROM lists WHERE boardId = :boardId") abstract suspend fun deleteForBoard(boardId: String)
    @Query("DELETE FROM lists") abstract suspend fun clear()

    /** Replaces all columns of [boardId] with [lists]. */
    @Transaction
    open suspend fun replaceForBoard(boardId: String, lists: List<ListEntity>) { deleteForBoard(boardId); upsertAll(lists) }
}

/** Cached issues. */
@Dao
abstract class TaskDao {
    @Query("SELECT * FROM tasks WHERE boardId = :boardId ORDER BY position ASC, createdAt ASC") abstract fun observeForBoard(boardId: String): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id") abstract fun observe(id: String): Flow<TaskEntity?>
    @Query("SELECT * FROM tasks WHERE id = :id") abstract suspend fun get(id: String): TaskEntity?
    @Query("SELECT * FROM tasks WHERE boardId = :boardId") abstract suspend fun forBoard(boardId: String): List<TaskEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsert(task: TaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertAll(tasks: List<TaskEntity>)
    @Query("DELETE FROM tasks WHERE id = :id") abstract suspend fun delete(id: String)
    @Query("DELETE FROM tasks WHERE boardId = :boardId") abstract suspend fun deleteForBoard(boardId: String)
    @Query("DELETE FROM tasks") abstract suspend fun clear()

    /** Replaces all issues of [boardId] with [tasks]. */
    @Transaction
    open suspend fun replaceForBoard(boardId: String, tasks: List<TaskEntity>) { deleteForBoard(boardId); upsertAll(tasks) }
}

/** The offline change queue. */
@Dao
interface PendingChangeDao {
    @Insert suspend fun insert(change: PendingChangeEntity): Long
    @Query("SELECT * FROM pending_changes ORDER BY id ASC") suspend fun all(): List<PendingChangeEntity>
    @Query("SELECT COUNT(*) FROM pending_changes") fun observeCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM pending_changes WHERE boardId = :boardId") suspend fun countForBoard(boardId: String): Int
    @Query("DELETE FROM pending_changes WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM pending_changes WHERE entityId = :entityId") suspend fun deleteForEntity(entityId: String)
    @Query("DELETE FROM pending_changes") suspend fun clear()
}

/** Error reports waiting for upload. */
@Dao
interface ErrorReportDao {
    @Insert suspend fun insert(report: ErrorReportEntity): Long
    @Query("SELECT * FROM error_reports ORDER BY id ASC LIMIT :limit") suspend fun batch(limit: Int): List<ErrorReportEntity>
    @Query("DELETE FROM error_reports WHERE id IN (:ids)") suspend fun delete(ids: List<Long>)
    @Query("SELECT COUNT(*) FROM error_reports") suspend fun count(): Int

    /** Keeps the queue bounded: deletes the oldest rows beyond [keep]. */
    @Query("DELETE FROM error_reports WHERE id NOT IN (SELECT id FROM error_reports ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
