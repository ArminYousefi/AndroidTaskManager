package com.google.mytaskmanager.data.remote.auth

/**
 * Simple in-memory token cache to avoid blocking interceptors.
 * Updated from AuthRepository when login/logout happens.
 */
object TokenProvider {
    @Volatile
    var token: String? = null
        internal set  // prevent external classes from directly setting it

    fun setToken(t: String?) {
        token = t
        com.google.mytaskmanager.util.LogUtil.i("TokenProvider", "setToken -> ${t?.take(10)}...")
    }

    fun clear() {
        token = null
        com.google.mytaskmanager.util.LogUtil.i("TokenProvider", "clear -> token cleared")
    }
}

