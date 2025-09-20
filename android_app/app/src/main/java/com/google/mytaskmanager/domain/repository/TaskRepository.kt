package com.google.mytaskmanager.domain.repository

import com.google.mytaskmanager.domain.model.Board
import com.google.mytaskmanager.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import com.google.mytaskmanager.domain.model.BoardList

interface TaskRepository {
    fun getBoards(): Flow<List<Board>>
    fun getTasksForBoard(boardId: String): Flow<List<Task>>
    suspend fun createTask(task: Task)
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(taskId: String)
    suspend fun createBoard(board: Board)
    suspend fun updateBoard(board: Board)
    fun getListsForBoard(boardId: String): Flow<List<BoardList>>
    suspend fun createList(list: BoardList)
    suspend fun updateList(list: BoardList)
    suspend fun deleteList(listId: String)
    fun observeRemoteEvents(): SharedFlow<com.google.mytaskmanager.data.remote.websocket.WsEvent>
}
