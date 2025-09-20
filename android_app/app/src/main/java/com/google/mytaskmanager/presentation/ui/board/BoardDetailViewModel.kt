package com.google.mytaskmanager.presentation.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.domain.model.BoardList
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class BoardDetailViewModel @Inject constructor(
    private val repo: TaskRepository
) : ViewModel() {

    fun getLists(boardId: String): Flow<List<BoardList>> = repo.getListsForBoard(boardId)

    fun getTasks(boardId: String): Flow<List<Task>> = repo.getTasksForBoard(boardId)
}
