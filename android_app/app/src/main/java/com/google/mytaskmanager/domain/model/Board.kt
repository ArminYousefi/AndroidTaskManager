package com.google.mytaskmanager.domain.model

data class Board(
    val id: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val listCount: Int = 0,
    val taskCount: Int = 0
) {
    fun updatedAtText(): String {
        val diff = System.currentTimeMillis() - updatedAt
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "less than a minute ago"
            minutes < 60 -> "${'$'}minutes minutes ago"
            minutes < 60*24 -> "${'$'}{minutes/60} hours ago"
            minutes < 60*24*365 -> "${'$'}{minutes/(60*24)} days ago"
            else -> "over 1 year ago"
        }
    }
}
