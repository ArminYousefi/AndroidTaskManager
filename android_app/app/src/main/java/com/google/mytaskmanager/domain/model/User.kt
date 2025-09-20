
package com.google.mytaskmanager.domain.model

data class User(
    val id: String,
    val username: String,
    val email: String? = null
)
