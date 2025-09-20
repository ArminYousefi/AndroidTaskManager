package com.google.mytaskmanager.domain.usecase

import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.domain.repository.TaskRepository

class CreateTaskUseCase @javax.inject.Inject constructor(private val repo: TaskRepository) {
    suspend operator fun invoke(task: Task) = repo.createTask(task)
}
