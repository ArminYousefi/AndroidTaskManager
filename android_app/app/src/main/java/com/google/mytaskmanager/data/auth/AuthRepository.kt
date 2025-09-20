
package com.google.mytaskmanager.data.auth

import com.google.mytaskmanager.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<User>
    suspend fun signup(username: String, password: String, email: String? = null): Result<User>
    suspend fun logout()
    fun isLoggedIn(): Flow<Boolean>
    fun currentUser(): Flow<User?>
}
