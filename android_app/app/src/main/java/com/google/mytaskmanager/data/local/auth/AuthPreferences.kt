package com.google.mytaskmanager.data.local.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.mytaskmanager.data.remote.auth.TokenProvider
import com.google.mytaskmanager.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.authStore by preferencesDataStore(name = "auth_prefs")

/** Persistent session: tokens plus a cached copy of the signed-in user's profile (so the UI works offline). */
class AuthPreferences(private val context: Context) {
    private object Keys {
        val TOKEN = stringPreferencesKey("auth_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val NICKNAME = stringPreferencesKey("nickname")
        val EMAIL = stringPreferencesKey("email")
    }

    private val data: Flow<Preferences> = context.authStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    /** Current access token (null = signed out). */
    val authTokenFlow: Flow<String?> = data.map { it[Keys.TOKEN] }.distinctUntilChanged()

    /** Current refresh token. */
    val refreshTokenFlow: Flow<String?> = data.map { it[Keys.REFRESH] }

    /** Cached profile of the signed-in user, or null when signed out. */
    val userFlow: Flow<User?> = data.map { p ->
        val id = p[Keys.USER_ID]; val name = p[Keys.USERNAME]
        if (id == null || name == null) null else User(id, name, p[Keys.NICKNAME], p[Keys.EMAIL])
    }.distinctUntilChanged()

    /** Id of the signed-in user (used to filter "only my issues"). */
    val userIdFlow: Flow<String?> = data.map { it[Keys.USER_ID] }.distinctUntilChanged()

    /** Stores the access token and publishes it to [TokenProvider]. */
    suspend fun saveToken(token: String) {
        context.authStore.edit { it[Keys.TOKEN] = token }
        TokenProvider.setToken(token)
    }

    /** Stores the refresh token. */
    suspend fun saveRefreshToken(refresh: String) { context.authStore.edit { it[Keys.REFRESH] = refresh } }

    /** Stores (or updates) the cached profile. */
    suspend fun saveUser(user: User) {
        context.authStore.edit {
            it[Keys.USER_ID] = user.id; it[Keys.USERNAME] = user.username
            if (user.nickname != null) it[Keys.NICKNAME] = user.nickname else it.remove(Keys.NICKNAME)
            if (user.email != null) it[Keys.EMAIL] = user.email else it.remove(Keys.EMAIL)
        }
    }

    /** @return the stored refresh token (suspends once). */
    suspend fun refreshToken(): String? = data.map { it[Keys.REFRESH] }.first()

    /** Signs out locally: forgets tokens and profile. */
    suspend fun clearSession() {
        context.authStore.edit { it.clear() }
        TokenProvider.clear()
    }
}
