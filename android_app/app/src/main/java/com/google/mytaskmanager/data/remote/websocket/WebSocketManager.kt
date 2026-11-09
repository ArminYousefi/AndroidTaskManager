package com.google.mytaskmanager.data.remote.websocket

import com.google.mytaskmanager.data.remote.NetworkConfig
import com.google.mytaskmanager.data.remote.auth.TokenProvider
import com.google.mytaskmanager.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A push message from the server.
 *
 * @property type e.g. `task_moved`, `list_updated`, `activity_created`, `board_membership_changed`.
 * @property entity payload kind (`task`, `list`, `board`, `activity`, `member`).
 * @property boardId board the event belongs to (null for account-level events).
 */
data class WsEvent(val type: String, val entity: String, val boardId: String?)

/**
 * Keeps one authenticated WebSocket open while the user is signed in and re-connects with exponential backoff
 * (1s, 2s, 4s ... max 30s). Screens collect [events] and refresh the data they show; they never parse the payload,
 * which keeps this robust against server changes.
 */
class WebSocketManager(baseClient: OkHttpClient) {
    private val client = baseClient.newBuilder().pingInterval(25, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    private val _connected = MutableStateFlow(false)
    private var socket: WebSocket? = null
    private var job: Job? = null
    @Volatile private var wanted = false

    /** Server events. */
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    /** True while the socket is open (drives the online/offline indicator). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Starts (or restarts) the connection loop. Safe to call repeatedly. */
    @Synchronized
    fun connect() {
        wanted = true
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = 1_000L
            while (wanted) {
                if (TokenProvider.token.isNullOrBlank()) { delay(2_000); continue }
                val closed = kotlinx.coroutines.CompletableDeferred<Boolean>()
                val request = Request.Builder().url(NetworkConfig.WS_URL).build()
                socket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { backoff = 1_000L; _connected.value = true; LogUtil.i("WebSocket", "connected") }
                    override fun onMessage(webSocket: WebSocket, text: String) { parse(text)?.let { _events.tryEmit(it) } }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { _connected.value = false; closed.complete(true) }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        _connected.value = false
                        LogUtil.w("WebSocket", "failure: ${t.javaClass.simpleName} ${response?.code ?: ""}")
                        closed.complete(true)
                    }
                })
                closed.await()
                if (!wanted) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** Closes the socket and stops reconnecting (call on logout). */
    @Synchronized
    fun disconnect() {
        wanted = false
        socket?.close(1000, "logout")
        socket = null
        job?.cancel()
        job = null
        _connected.value = false
    }

    private fun parse(text: String): WsEvent? = try {
        val o = JSONObject(text)
        WsEvent(o.getString("type"), o.optString("entity"), o.optString("boardId").takeIf { it.isNotBlank() && it != "null" })
    } catch (t: Throwable) { LogUtil.w("WebSocket", "ignored malformed message"); null }
}
