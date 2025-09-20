package com.google.mytaskmanager.data.local.auth


import com.google.mytaskmanager.data.remote.auth.TokenProvider
import com.google.mytaskmanager.util.LogUtil
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

object AuthPrefsKeys {
    val TOKEN = stringPreferencesKey("auth_token")
    val REFRESH = stringPreferencesKey("refresh_token")
    val USERNAME = stringPreferencesKey("username")
}

class AuthPreferences(private val context: Context) {

    val authTokenFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs -> prefs[AuthPrefsKeys.TOKEN] }

    val refreshTokenFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs -> prefs[AuthPrefsKeys.REFRESH] }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[AuthPrefsKeys.TOKEN] = token
        }
        // hydrate in-memory provider so interceptors/ws have immediate access
        TokenProvider.setToken(token)
        LogUtil.i(
            "AuthPreferences",
            "saveToken -> token saved to DataStore and TokenProvider (partial): ${token.take(10)}..."
        )
    }

    suspend fun saveRefreshToken(refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[AuthPrefsKeys.REFRESH] = refresh
        }
    }

    suspend fun removeRefreshToken() {
        context.dataStore.edit { prefs -> prefs.remove(AuthPrefsKeys.REFRESH) }
    }

    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(AuthPrefsKeys.TOKEN)
        }
        // clear in-memory token as well
        TokenProvider.clear()
        LogUtil.i("AuthPreferences", "clearToken -> token removed from DataStore and TokenProvider")
    }

    suspend fun saveUsername(username: String) {
        context.dataStore.edit { prefs ->
            prefs[AuthPrefsKeys.USERNAME] = username
        }
    }

    val usernameFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { prefs -> prefs[AuthPrefsKeys.USERNAME] }
}
