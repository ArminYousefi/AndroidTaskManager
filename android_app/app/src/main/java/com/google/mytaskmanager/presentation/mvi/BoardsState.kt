package com.google.mytaskmanager.presentation.mvi

import com.google.mytaskmanager.domain.model.Board

data class BoardsState(
    val isLoading: Boolean = false,
    val boards: List<Board> = emptyList(),
    val error: String? = null,
    val isGridView: Boolean = true,
    val isSyncing: Boolean = false,
    val isConnected: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val editingBoard: Board? = null
)
