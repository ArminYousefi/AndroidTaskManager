package com.google.mytaskmanager.presentation.ui.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.usecase.GetBoardsUseCase
import com.google.mytaskmanager.presentation.mvi.BoardsState
import com.google.mytaskmanager.domain.repository.TaskRepository
import com.google.mytaskmanager.domain.model.Board
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val getBoardsUseCase: GetBoardsUseCase,
    private val repo: TaskRepository, // ✅ inject repository
    private val wsManager: WebSocketManager
) : ViewModel() {

    private val _state = MutableStateFlow(BoardsState())
    val state: StateFlow<BoardsState> = _state.asStateFlow()

    init {
        observeConnection()
        loadBoards()
    }

    private fun observeConnection() {
        viewModelScope.launch {
            wsManager.connected.collect { connected ->
                _state.update { it.copy(isConnected = connected) }
            }
        }
    }

    private fun loadBoards() {
        viewModelScope.launch {
            getBoardsUseCase()
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                .collect { list -> _state.update { it.copy(isLoading = false, boards = list) } }
        }
    }

    fun refresh() = loadBoards()

    fun toggleView() {
        _state.update { it.copy(isGridView = !it.isGridView) }
    }

    fun showCreateDialog() { _state.update { it.copy(showCreateDialog = true) } }
    fun dismissCreateDialog() { _state.update { it.copy(showCreateDialog = false) } }

    fun showEditDialog(boardId: String) {
        val board = _state.value.boards.find { it.id == boardId }
        _state.update { it.copy(showEditDialog = true, editingBoard = board) }
    }
    fun dismissEditDialog() { _state.update { it.copy(showEditDialog = false, editingBoard = null) } }

    fun syncBoards() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            try {
                getBoardsUseCase().first()
            } catch (_: Throwable) {}
            _state.update { it.copy(isSyncing = false) }
        }
    }

    fun connectOrSync() {
        viewModelScope.launch {
            if (wsManager.connected.first()) {
                syncBoards()
            } else {
                try {
                    wsManager.connect()
                    val connected = wsManager.connected.first()
                    if (connected) syncBoards()
                } catch (_: Throwable) {}
            }
        }
    }

    fun deleteBoard(id: String) {
        viewModelScope.launch {
            try {
                // TODO: repo.deleteBoard(id) once implemented
            } catch (_: Throwable) {}
            _state.update { it.copy(boards = _state.value.boards.filter { it.id != id }) }
        }
    }

    fun emptyBoard(boardId: String) {
        viewModelScope.launch {
            try {
                // TODO: repo.emptyBoard(boardId) once implemented
            } catch (_: Throwable) {}
        }
    }

    fun createBoard(title: String) {
        viewModelScope.launch {
            val newBoard = Board( // ✅ build Board here
                id = java.util.UUID.randomUUID().toString(),
                title = title,
                updatedAt = System.currentTimeMillis()
            )
            repo.createBoard(newBoard) // save to DB + API
            _state.update { current ->
                current.copy(
                    boards = current.boards + newBoard,
                    showCreateDialog = false
                )
            }
        }
    }

    fun updateBoard(board: Board) {
        viewModelScope.launch {
            repo.updateBoard(board) // ✅ use repo
            _state.update { current ->
                current.copy(
                    boards = current.boards.map {
                        if (it.id == board.id) board else it
                    },
                    showEditDialog = false,
                    editingBoard = null
                )
            }
        }
    }
}