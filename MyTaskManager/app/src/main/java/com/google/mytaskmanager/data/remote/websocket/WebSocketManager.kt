package com.google.mytaskmanager.data.remote.websocket

import com.google.mytaskmanager.data.remote.dto.TaskDto
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.get

sealed class WsEvent {
    data class TaskCreated(val task: TaskDto): WsEvent()
    data class TaskUpdated(val task: TaskDto): WsEvent()
    data class ConnectionError(val message: String): WsEvent()
    object Connected: WsEvent()
    object Disconnected: WsEvent()
}

@Singleton
class WebSocketManager @Inject constructor(private val client: OkHttpClient) {
    private var socket: WebSocket? = null
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events

    private var url = "wss://your.api/ws"

    fun connect(wsUrl: String = url) {
        disconnect()
        url = wsUrl
        val request = Request.Builder().url(wsUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch { _events.emit(WsEvent.Connected) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val wrapper = gson.fromJson(text, Map::class.java)
                    val type = (wrapper["type"] as? String) ?: return
                    val payload = wrapper["payload"]
                    val json = gson.toJson(payload)
                    when(type) {
                        "task_created" -> {
                            val dto = gson.fromJson(json, TaskDto::class.java)
                            scope.launch { _events.emit(WsEvent.TaskCreated(dto)) }
                        }
                        "task_updated" -> {
                            val dto = gson.fromJson(json, TaskDto::class.java)
                            scope.launch { _events.emit(WsEvent.TaskUpdated(dto)) }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { _events.emit(WsEvent.ConnectionError(t.message ?: "unknown")) }
                reconnectWithBackoff()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch { _events.emit(WsEvent.Disconnected) }
            }
        })
    }

    fun send(eventType: String, payload: Any) {
        val map = mapOf("type" to eventType, "payload" to payload)
        val text = gson.toJson(map)
        socket?.send(text)
    }

    fun disconnect() {
        socket?.close(1000, "client")
        socket = null
    }

    private fun reconnectWithBackoff() {
        scope.launch {
            delay(2000)
            connect(url)
        }
    }
}
