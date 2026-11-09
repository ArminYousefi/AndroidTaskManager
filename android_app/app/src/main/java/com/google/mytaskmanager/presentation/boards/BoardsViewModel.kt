package com.google.mytaskmanager.presentation.boards

import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.MessageCenter
import com.google.mytaskmanager.core.UiErrorHandler
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.Board
import com.google.mytaskmanager.domain.repository.TaskRepository
import com.google.mytaskmanager.domain.rules.BoardTemplate
import com.google.mytaskmanager.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state of the boards list. */
data class BoardsUiState(val loading: Boolean = true, val refreshing: Boolean = false, val query: String = "", val grid: Boolean = true)

/** Lists the boards the user can access and performs the board-level actions of the card menu. */
@OptIn(FlowPreview::class)
@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val messages: MessageCenter,
    webSocket: WebSocketManager,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    private val _state = MutableStateFlow(BoardsUiState())
    val state: StateFlow<BoardsUiState> = _state.asStateFlow()

    /** Boards matching the search box, newest activity first. */
    val boards: StateFlow<List<Board>> = combine(tasks.observeBoards(), _state) { list, s ->
        val q = s.query.trim().lowercase()
        if (q.isEmpty()) list else list.filter { it.name.lowercase().contains(q) || it.key.lowercase().contains(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh(initial = true)
        // Live updates: counts, names, membership. Debounced so a burst of events causes a single reload.
        viewModelScope.launch {
            webSocket.events.filter { it.entity != "activity" }.debounce(500).collect { runCatching { tasks.refreshBoards() } }
        }
    }

    /** Reloads from the server. */
    fun refresh(initial: Boolean = false) {
        _state.update { it.copy(refreshing = !initial) }
        launchSafe("load boards", showMessage = !initial, onError = { _state.update { s -> s.copy(loading = false, refreshing = false) } }) {
            tasks.refreshBoards()
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun setQuery(q: String) { _state.update { it.copy(query = q) } }
    fun toggleGrid() { _state.update { it.copy(grid = !it.grid) } }

    /** Creates a board from a template; [onCreated] receives it so the UI can open it. */
    fun create(name: String, key: String?, description: String?, template: BoardTemplate, onCreated: (Board) -> Unit) {
        launchSafe("create board") {
            val b = tasks.createBoard(name, key, description, template)
            messages.success("Board “${b.name}” created")
            onCreated(b)
        }
    }

    fun rename(board: Board, name: String) { launchSafe("rename board") { tasks.updateBoard(board.id, name, board.description); messages.success("Board renamed") } }
    fun delete(board: Board) { launchSafe("delete board") { tasks.deleteBoard(board.id); messages.success("Board deleted") } }
    fun empty(board: Board) { launchSafe("empty board") { val n = tasks.emptyBoard(board.id); messages.success("Removed $n tasks") } }
}
