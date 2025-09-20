package services

import com.example.logging
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.TaskDto
import java.util.concurrent.CopyOnWriteArraySet

object WebSocketHub {
    private val sessions = CopyOnWriteArraySet<DefaultWebSocketServerSession>()
    private val mutex = Mutex()

    fun register(session: DefaultWebSocketServerSession) {
        sessions.add(session)
        logging.info("WebSocketHub", "Client connected, total=${sessions.size}")
    }

    fun unregister(session: DefaultWebSocketServerSession) {
        sessions.remove(session)
        logging.info("WebSocketHub", "Client disconnected, total=${sessions.size}")
    }

    suspend fun broadcastTaskEvent(event: String, task: TaskDto) {
        val payload = """{"event":"$event","task":${Json.encodeToString(task)}}"""
        broadcastRaw(payload)
    }

    private suspend fun broadcastRaw(msg: String) {
        mutex.withLock {
            val toRemove = mutableListOf<DefaultWebSocketServerSession>()
            sessions.forEach { s ->
                try {
                    s.send(Frame.Text(msg))
                } catch (t: Throwable) {
                    logging.error("WebSocketHub", "Failed to send to a client", t)
                    toRemove.add(s)
                }
            }
            toRemove.forEach { sessions.remove(it) }
        }
    }

    fun broadcastBoardEvent(event: String, board: models.BoardDto) {
        val msg = Json.encodeToString(mapOf("type" to event, "board" to board))
        runBlocking {
            mutex.withLock {
                val toRemove = mutableListOf<DefaultWebSocketServerSession>()
                sessions.forEach { s ->
                    try {
                        s.send(Frame.Text(msg))
                    } catch (t: Throwable) {
                        logging.error("WebSocketHub", "Failed to send to a client", t)
                        toRemove.add(s)
                    }
                }
                toRemove.forEach { sessions.remove(it) }
            }
        }
    }

}
