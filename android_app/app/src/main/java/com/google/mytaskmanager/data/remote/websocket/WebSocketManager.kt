package com.google.mytaskmanager.data.remote.websocket

import com.google.mytaskmanager.data.remote.auth.TokenProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import com.google.mytaskmanager.util.LogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

sealed class WsEvent {
    data class TaskCreated(val taskJson: String): WsEvent()
    data class TaskUpdated(val taskJson: String): WsEvent()
    object Connected: WsEvent()
    object Disconnected: WsEvent()
    data class Raw(val json: String): WsEvent()
}

class WebSocketManager(private val client: OkHttpClient) {
    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 50)
    val events = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var resolvedUrl: String? = null

    fun setUrl(wsUrl: String) {
        resolvedUrl = wsUrl
    }

    fun connect() {
        if (isConnected.get()) {
            LogUtil.i("WebSocketManager", "Already connected")
            return
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    val url = resolvedUrl ?: defaultWsUrl()
                    val token = TokenProvider.token  // 👈 always pull latest

                    LogUtil.i("WebSocketManager", "Connecting to $url (attempt=${attempt + 1}) with token=${token?.take(10)}...")

                    val builder = Request.Builder().url(url)
                    if (!token.isNullOrBlank()) {
                        builder.addHeader("Authorization", "Bearer $token")
                        LogUtil.i("WebSocketManager", "Attaching header: Authorization=Bearer ${token.take(10)}...")
                    } else {
                        LogUtil.w("WebSocketManager", "No token available, connecting unauthenticated")
                    }
                    val request = builder.build()

                    val listener = object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            this@WebSocketManager.webSocket = webSocket
                            isConnected.set(true)
                            _connected.value = true
                            attempt = 0
                            LogUtil.i("WebSocketManager", "WebSocket opened")
                            scope.launch { _events.emit(WsEvent.Connected) }
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            LogUtil.i("WebSocketManager", "Message: ${text.take(200)}")
                            scope.launch { handleIncomingMessage(text) }
                        }

                        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                            val text = bytes.utf8()
                            LogUtil.i("WebSocketManager", "Binary message received")
                            scope.launch { handleIncomingMessage(text) }
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            LogUtil.i("WebSocketManager", "Closing: $code / $reason")
                            cleanup()
                            webSocket.close(code, reason)
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            LogUtil.i("WebSocketManager", "Closed: $code / $reason")
                            cleanup()
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            LogUtil.e("WebSocketManager", "Failure", t)
                            cleanup()
                        }
                    }

                    client.newWebSocket(request, listener)

                    while (isActive && isConnected.get()) {
                        delay(1000)
                    }
                } catch (t: Throwable) {
                    LogUtil.e("WebSocketManager", "Connect loop error", t)
                }

                attempt = (attempt + 1).coerceAtMost(10)
                val backoff = (2.0.pow(attempt.toDouble()) * 1000).toLong().coerceAtMost(60_000L)
                LogUtil.i("WebSocketManager", "Reconnecting in ${backoff}ms")
                delay(backoff)
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        cleanup()
    }

    fun send(text: String): Boolean {
        val ws = webSocket
        return if (ws != null && isConnected.get()) {
            ws.send(text)
        } else {
            false
        }
    }

    private fun cleanup() {
        isConnected.set(false)
        _connected.value = false
        scope.launch { _events.emit(WsEvent.Disconnected) }
    }

    private suspend fun handleIncomingMessage(text: String) {
        try {
            _events.emit(WsEvent.Raw(text))
            val lower = text.lowercase()
            when {
                lower.contains("taskcreated") -> {
                    _events.emit(WsEvent.TaskCreated(text))
                }
                lower.contains("taskupdated") -> {
                    _events.emit(WsEvent.TaskUpdated(text))
                }
            }
        } catch (t: Throwable) {
            LogUtil.e("WebSocketManager", "handleIncomingMessage failed", t)
        }
    }

    private fun defaultWsUrl(): String = "ws://10.0.2.2:8080/ws"
}