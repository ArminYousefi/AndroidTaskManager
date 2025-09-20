package com.google.mytaskmanager.domain.usecase

import com.google.mytaskmanager.domain.model.Board
import com.google.mytaskmanager.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

class GetBoardsUseCase @javax.inject.Inject constructor(private val repo: TaskRepository) {
    operator fun invoke(): Flow<List<Board>> = repo.getBoards()
}
