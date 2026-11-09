package com.google.mytaskmanager.data.auth

import com.google.mytaskmanager.core.appCall
import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.local.db.AppDatabase
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.dto.*
import com.google.mytaskmanager.data.util.toDomain
import com.google.mytaskmanager.domain.model.User
import com.google.mytaskmanager.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Default [AuthRepository]: talks to the API and keeps the session in [AuthPreferences]. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val prefs: AuthPreferences,
    private val db: AppDatabase
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = prefs.authTokenFlow.map { !it.isNullOrBlank() }
    override val currentUser: Flow<User?> = prefs.userFlow

    override suspend fun login(username: String, password: String): User =
        startSession(appCall { api.login(LoginRequest(username.trim(), password)) })

    override suspend fun signup(username: String, password: String, email: String?, nickname: String?): User =
        startSession(appCall { api.signup(SignupRequest(username.trim(), password, email?.trim()?.ifEmpty { null }, nickname?.trim()?.ifEmpty { null })) })

    override suspend fun logout() {
        val refresh = prefs.refreshToken()
        // Best effort: even if the server cannot be reached the user must be able to sign out locally.
        runCatching { api.logout(LogoutRequest(refresh)) }
        prefs.clearSession()
        wipeCache()
    }

    override suspend fun refreshProfile(): User {
        val user = appCall { api.me() }.toDomain()
        prefs.saveUser(user)
        return user
    }

    override suspend fun updateNickname(nickname: String): User {
        val user = appCall { api.updateProfile(UpdateProfileRequest(nickname)) }.toDomain()
        prefs.saveUser(user)
        return user
    }

    override suspend fun changePassword(current: String, new: String) {
        val r = appCall { api.changePassword(ChangePasswordRequest(current, new)) }
        // The server revoked every old refresh token and issued a fresh pair for this device.
        prefs.saveToken(r.token)
        r.refreshToken?.let { prefs.saveRefreshToken(it) }
        prefs.saveUser(r.user.toDomain())
    }

    /** Wipes the previous user's cache first so two accounts on one phone never see each other's data. */
    private suspend fun startSession(r: AuthResponse): User {
        wipeCache()
        prefs.saveToken(r.token)
        r.refreshToken?.let { prefs.saveRefreshToken(it) }
        val user = r.user.toDomain()
        prefs.saveUser(user)
        return user
    }

    private suspend fun wipeCache() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
