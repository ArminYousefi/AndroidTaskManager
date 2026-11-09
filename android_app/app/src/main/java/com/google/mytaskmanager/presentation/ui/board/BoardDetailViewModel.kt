package com.google.mytaskmanager.presentation.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.data.remote.websocket.WsEvent
import com.google.mytaskmanager.domain.model.BoardList
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BoardDetailViewModel @Inject constructor(
    private val repo: TaskRepository
) : ViewModel() {

    private var currentBoardId: String? = null
    private var listsJob: Job? = null
    private var tasksJob: Job? = null

    private val _lists = MutableStateFlow<List<BoardList>>(emptyList())
    val lists: StateFlow<List<BoardList>> = _lists.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Previously nothing in the app ever collected observeRemoteEvents(), so
        // even a working WebSocket connection had no visible effect. Now a
        // task/board push from the server triggers a re-fetch of this board's data.
        repo.observeRemoteEvents().onEach { event ->
            val boardId = currentBoardId ?: return@onEach
            when (event) {
                is WsEvent.TaskCreated, is WsEvent.TaskUpdated, is WsEvent.TaskDeleted -> refreshTasks(boardId)
                is WsEvent.BoardUpdated, is WsEvent.BoardDeleted -> refreshLists(boardId)
                else -> {}
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Loads lists/tasks for [boardId] exactly once per board id. Safe to call from
     * the composable body on every recomposition — it's a no-op if [boardId] is
     * already the currently-loaded board, unlike the old getLists()/getTasks()
     * functions, which built a brand-new cold Flow (and re-hit the network) on
     * every single recomposition.
     */
    fun load(boardId: String) {
        if (currentBoardId == boardId) return
        currentBoardId = boardId
        refreshLists(boardId)
        refreshTasks(boardId)
    }

    private fun refreshLists(boardId: String) {
        listsJob?.cancel()
        listsJob = repo.getListsForBoard(boardId).onEach { _lists.value = it }.launchIn(viewModelScope)
    }

    private fun refreshTasks(boardId: String) {
        tasksJob?.cancel()
        tasksJob = repo.getTasksForBoard(boardId).onEach { _tasks.value = it }.launchIn(viewModelScope)
    }

    fun createList(boardId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.createList(
                    BoardList(
                        id = UUID.randomUUID().toString(),
                        boardId = boardId,
                        title = title.trim(),
                        position = _lists.value.size,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createTask(boardId: String, listId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.createTask(
                    Task(
                        id = UUID.randomUUID().toString(),
                        boardId = boardId,
                        listId = listId,
                        title = title.trim(),
                        position = _tasks.value.count { it.listId == listId },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { repo.deleteTask(taskId) }
    }
}
