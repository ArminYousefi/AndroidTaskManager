package com.google.mytaskmanager.presentation.ui.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.domain.usecase.GetBoardsUseCase
import com.google.mytaskmanager.presentation.mvi.BoardsIntent
import com.google.mytaskmanager.presentation.mvi.BoardsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val getBoardsUseCase: GetBoardsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BoardsState())
    val state: StateFlow<BoardsState> = _state.asStateFlow()

    private val intents = Channel<BoardsIntent>(Channel.UNLIMITED)

    init { processIntents(); intents.trySend(BoardsIntent.Load) }

    fun send(intent: BoardsIntent) { intents.trySend(intent) }

    private fun processIntents() {
        viewModelScope.launch {
            for (intent in intents) when(intent) {
                is BoardsIntent.Load -> loadBoards()
                is BoardsIntent.Refresh -> refreshBoards()
                is BoardsIntent.OpenBoard -> { /* navigation handled via effect in real app */ }
            }
        }
    }

    private suspend fun loadBoards() {
        _state.update { it.copy(isLoading = true, error = null) }
        getBoardsUseCase()
            .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            .collect { list -> _state.update { it.copy(isLoading = false, boards = list) } }
    }

    private suspend fun refreshBoards() { loadBoards() }
}
