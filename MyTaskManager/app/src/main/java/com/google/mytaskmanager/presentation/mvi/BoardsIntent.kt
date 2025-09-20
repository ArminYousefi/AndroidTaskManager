package com.google.mytaskmanager.presentation.mvi

sealed class BoardsIntent {
    object Load : BoardsIntent()
    object Refresh : BoardsIntent()
    data class OpenBoard(val boardId: String): BoardsIntent()
}
