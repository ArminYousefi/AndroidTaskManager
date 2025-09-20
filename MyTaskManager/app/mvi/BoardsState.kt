package com.google.mytaskmanager.presentation.mvi

import com.google.mytaskmanager.domain.model.Board

data class BoardsState(
    val isLoading: Boolean = false,
    val boards: List<Board> = emptyList(),
    val error: String? = null
)
