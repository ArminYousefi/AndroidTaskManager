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

    private var loadBoardsJob: kotlinx.coroutines.Job? = null

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
        // Previously this launched a brand-new coroutine collecting
        // boardDao.getBoardsFlow() (via getBoardsUseCase()) on every single call to
        // refresh()/loadBoards(), without ever cancelling the previous collector.
        // Each refresh leaked another long-lived Room Flow subscription for the
        // lifetime of the ViewModel. Now we cancel the prior collector first.
        loadBoardsJob?.cancel()
        loadBoardsJob = viewModelScope.launch {
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
            // Optimistically hide it immediately; the reactive boardDao Flow this
            // ViewModel is already collecting will also emit once the local row is
            // actually removed inside repo.deleteBoard(), keeping state in sync.
            _state.update { it.copy(boards = _state.value.boards.filter { b -> b.id != id }) }
            try {
                repo.deleteBoard(id)
            } catch (t: Throwable) {
                // Board deletion failed entirely (including local DB) — reload from
                // source of truth instead of leaving stale optimistic UI state.
                loadBoards()
            }
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
        if (title.isBlank()) {
            _state.update { it.copy(error = "Board title can't be empty") }
            return
        }
        viewModelScope.launch {
            val newBoard = Board(
                id = java.util.UUID.randomUUID().toString(),
                title = title.trim(),
                updatedAt = System.currentTimeMillis()
            )
            repo.createBoard(newBoard) // save to DB + API; boardDao's Flow picks it up automatically
            _state.update { it.copy(showCreateDialog = false) }
        }
    }

    fun updateBoard(board: Board) {
        viewModelScope.launch {
            repo.updateBoard(board) // boardDao's Flow picks up the change automatically
            _state.update { it.copy(showEditDialog = false, editingBoard = null) }
        }
    }
}