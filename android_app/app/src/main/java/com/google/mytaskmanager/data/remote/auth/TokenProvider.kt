package com.google.mytaskmanager.data.remote.auth

/**
 * In-memory copy of the access token so the OkHttp interceptor never has to block on disk I/O.
 * Written by [com.google.mytaskmanager.data.local.auth.AuthPreferences] on every save/clear.
 */
object TokenProvider {
    @Volatile
    var token: String? = null
        private set

    /** Replaces the cached token. */
    fun setToken(t: String?) { token = t }

    /** Forgets the cached token (logout / dead session). */
    fun clear() { token = null }
}
