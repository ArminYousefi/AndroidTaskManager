package com.google.mytaskmanager.data.auth

import android.util.Log
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.auth.TokenProvider
import com.google.mytaskmanager.data.remote.dto.LoginRequest
import com.google.mytaskmanager.data.remote.dto.SignupRequest
import com.google.mytaskmanager.data.util.toDomain
import com.google.mytaskmanager.domain.model.User
import com.google.mytaskmanager.util.LogUtil
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AuthRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val authPrefs: AuthPreferences
) : AuthRepository {

    private val currentUserFlow = MutableStateFlow<User?>(null)

    init {
        // restore persisted login state from AuthPreferences
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = authPrefs.authTokenFlow.first()
                val username = authPrefs.usernameFlow.first()
                if (!token.isNullOrBlank() && !username.isNullOrBlank()) {
                    
                    // hydrate TokenProvider from persisted token
                    TokenProvider.setToken(token)
                    LogUtil.i("AuthRepository", "init -> restored token into TokenProvider (partial): ${token?.take(10)}...")// restore both user and token
                    com.google.mytaskmanager.data.remote.auth.TokenProvider.token = token
                    currentUserFlow.value = User(id = username, username = username, email = null)
                }
            } catch (_: Throwable) {
                // ignore restore errors
            }
        }
    }

    override suspend fun login(username: String, password: String): Result<User> {
        return try {
            val response = api.login(LoginRequest(username, password))
            authPrefs.saveToken(response.token)
            val user = response.user.toDomain()
            currentUserFlow.value = user
            TokenProvider.setToken(response.token)
            LogUtil.i("AuthRepository", "signup -> saved token and updated TokenProvider (partial): ${response.token.take(10)}...")
            TokenProvider.setToken(response.token)
            LogUtil.i("AuthRepository", "login -> saved token and updated TokenProvider (partial): ${response.token.take(10)}...")
            Result.success(user)
        } catch (t: Throwable) {
            Log.e("AuthRepository", "Login failed", t)
            Result.failure(t)
        }
    }

    override suspend fun signup(username: String, password: String, email: String?): Result<User> {
        return try {
            val response = api.signup(SignupRequest(username, password, email))
            authPrefs.saveToken(response.token)
            val user = response.user.toDomain()
            currentUserFlow.value = user
            TokenProvider.setToken(response.token)
            LogUtil.i("AuthRepository", "signup -> saved token and updated TokenProvider (partial): ${response.token.take(10)}...")
            TokenProvider.setToken(response.token)
            LogUtil.i("AuthRepository", "login -> saved token and updated TokenProvider (partial): ${response.token.take(10)}...")
            Result.success(user)
        } catch (t: Throwable) {
            Log.e("AuthRepository", "Signup failed $t", t)
            Result.failure(t)
        }
    }

    override suspend fun logout() {
        authPrefs.clearToken()
        LogUtil.i("AuthRepository", "logout -> cleared TokenProvider and preferences")
        currentUserFlow.value = null
    }


    override fun isLoggedIn(): Flow<Boolean> {
        return authPrefs.authTokenFlow.map { !it.isNullOrBlank() }
    }

    override fun currentUser(): Flow<User?> {
        // Emit currentUserFlow if you want full user object, but fall back to username from prefs
        return currentUserFlow
    }
}
