package com.google.mytaskmanager.data.remote.auth

import com.google.mytaskmanager.data.local.auth.AuthPreferences
import com.google.mytaskmanager.data.remote.NetworkConfig
import com.google.mytaskmanager.util.LogUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import java.io.IOException

/**
 * Called by OkHttp on HTTP 401: exchanges the refresh token for a new access token and retries the request.
 *
 * Details that matter:
 * - Refresh tokens are *rotated* by the server, so refreshes are serialised. If another request already refreshed
 *   while this one was waiting, the new token is simply reused.
 * - Calls to the auth endpoints (login, signup, refresh) are never refreshed (a 401 on login means "wrong password", not "expired").
 * - The session is cleared only when the server *rejects* the refresh token. A network error while refreshing keeps
 *   the user signed in, so going through a tunnel does not log anybody out.
 */
class AuthAuthenticator(private val authPrefs: AuthPreferences) : Authenticator {
    private val lock = Any()
    private val plainClient = OkHttpClient()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("/auth/")) return null
        if (responseCount(response) >= 3) return null
        val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(lock) {
            val current = TokenProvider.token
            if (!current.isNullOrBlank() && current != sentToken) return retryWith(response, current)

            val refresh = runBlocking { authPrefs.refreshTokenFlow.first() }
            if (refresh.isNullOrBlank()) { clearSession(); return null }

            return try {
                val body = JSONObject().put("refreshToken", refresh).toString().toRequestBody("application/json".toMediaType())
                plainClient.newCall(Request.Builder().url(NetworkConfig.REFRESH_ENDPOINT).post(body).build()).execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val json = JSONObject(resp.body.string())
                            val newToken = json.getString("token")
                            runBlocking {
                                authPrefs.saveToken(newToken)
                                json.optString("refreshToken").takeIf { it.isNotBlank() }?.let { authPrefs.saveRefreshToken(it) }
                            }
                            retryWith(response, newToken)
                        }
                        resp.code in 400..499 -> { LogUtil.w("AuthAuthenticator", "Refresh rejected (${resp.code}); signing out"); clearSession(); null }
                        else -> null // 5xx: server trouble, keep the session and let the caller retry later
                    }
                }
            } catch (e: IOException) {
                LogUtil.w("AuthAuthenticator", "Refresh failed (offline?): ${e.javaClass.simpleName}")
                null
            } catch (t: Throwable) {
                LogUtil.e("AuthAuthenticator", "Unexpected refresh failure", t)
                null
            }
        }
    }

    private fun retryWith(response: Response, token: String): Request =
        response.request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun clearSession() {
        runBlocking { runCatching { authPrefs.clearSession() } }
    }

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var n = 0
        while (r != null) { n++; r = r.priorResponse }
        return n
    }
}
