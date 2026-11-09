package com.google.mytaskmanager.data.remote

import com.google.mytaskmanager.BuildConfig

/**
 * Backend addresses, derived from a single build-time value: `BuildConfig.SERVER_URL`.
 * Change it with `-PserverUrl=http://192.168.1.2:8080/` (see app/build.gradle); nothing is hard-coded in code.
 */
object NetworkConfig {
    /** Base URL with trailing slash - Retrofit resolves endpoint paths relative to it. */
    val HTTP_BASE_URL: String = BuildConfig.SERVER_URL

    /** WebSocket endpoint: same host, `ws://` (or `wss://` for https) + `/ws`. */
    val WS_URL: String = HTTP_BASE_URL.replaceFirst("http", "ws") + "ws"

    /** Token refresh endpoint used by the OkHttp authenticator. */
    val REFRESH_ENDPOINT: String = HTTP_BASE_URL + "auth/refresh"
}
