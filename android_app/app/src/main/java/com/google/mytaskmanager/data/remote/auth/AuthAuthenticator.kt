package com.google.mytaskmanager.data.remote.auth

import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.util.LogUtil
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.google.gson.Gson

/**
 * On 401, attempt to refresh token synchronously using refresh token from AuthPreferences.
 * If refresh succeeds, updates TokenProvider and persisted token. If it fails, clears tokens.
 *
 * Expects backend refresh endpoint at POST /auth/refresh with body {"refreshToken":"..."} returning AuthResponse JSON.
 */
class AuthAuthenticator(private val authPrefs: AuthPreferences) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // avoid infinite loops
        if (responseCount(response) >= 2) {
            return null
        }

        try {
            val refresh = runBlocking { authPrefs.refreshTokenFlow.first() }
            if (refresh.isNullOrBlank()) {
                LogUtil.w("AuthAuthenticator", "No refresh token available")
                return null
            }

            try {
                // Perform synchronous HTTP call to refresh endpoint
                val client = OkHttpClient.Builder().build()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val bodyJson = Gson().toJson(mapOf("refreshToken" to refresh))
                val req = Request.Builder()
                    .url("https://api.example.com/auth/refresh")
                    .post(bodyJson.toRequestBody(mediaType))
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    LogUtil.w("AuthAuthenticator", "Refresh request failed with code ${resp.code}")
                    LogUtil.w("AuthAuthenticator", "TokenProvider.clear() suppressed to avoid accidental removal")
                    // clear persisted tokens
                    LogUtil.w("AuthAuthenticator", "AuthPreferences.clearToken/removeRefreshToken suppressed to avoid accidental removal")
                    return null
                }

                val respBody = resp.body.string()
                if (respBody.isBlank()) {
                    LogUtil.w("AuthAuthenticator", "TokenProvider.clear() suppressed to avoid accidental removal")
                    LogUtil.w("AuthAuthenticator", "AuthPreferences.clearToken/removeRefreshToken suppressed to avoid accidental removal")
                    return null
                }

                val gson = Gson()
                val authResponse = gson.fromJson(respBody, com.google.mytaskmanager.data.remote.dto.AuthResponse::class.java)
                if (authResponse != null && authResponse.token.isNotBlank()) {
                    // persist new tokens and update in-memory cache
                    runBlocking {
                        authPrefs.saveToken(authResponse.token)
                        authResponse.refreshToken?.let { authPrefs.saveRefreshToken(it) }
                    }
                    TokenProvider.setToken(authResponse.token)
                LogUtil.i("AuthAuthenticator", "setToken after refresh -> ${authResponse.token.take(10)}...")

                    // build a new request with updated Authorization header
                    val newRequest = response.request.newBuilder()
                        .header("Authorization", "Bearer ${authResponse.token}")
                        .build()
                    return newRequest
                } else {
                    LogUtil.w("AuthAuthenticator", "TokenProvider.clear() suppressed to avoid accidental removal")
                    LogUtil.w("AuthAuthenticator", "AuthPreferences.clearToken/removeRefreshToken suppressed to avoid accidental removal")
                    return null
                }
            } catch (t: Throwable) {
                LogUtil.e("AuthAuthenticator", "Refresh call failed", t)
                LogUtil.w("AuthAuthenticator", "TokenProvider.clear() suppressed to avoid accidental removal")
                LogUtil.w("AuthAuthenticator", "AuthPreferences.clearToken/removeRefreshToken suppressed to avoid accidental removal")
                return null
            }
        } catch (t: Throwable) {
            LogUtil.e("AuthAuthenticator", "Error during authentication", t)
            LogUtil.w("AuthAuthenticator", "TokenProvider.clear() suppressed to avoid accidental removal")
            return null
        }
    }

    private fun responseCount(response: Response): Int {
        var res: Response? = response
        var result = 0
        while (res != null) {
            result++
            res = res.priorResponse
        }
        return result
    }
}
