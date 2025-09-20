package services

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.TaskDto

object WebSocketService {
    private val connections = mutableSetOf<DefaultWebSocketServerSession>()
    private val mutex = Mutex()

    suspend fun register(session: DefaultWebSocketServerSession) {
        mutex.withLock { connections.add(session) }
    }

    suspend fun unregister(session: DefaultWebSocketServerSession) {
        mutex.withLock { connections.remove(session) }
    }

    suspend fun broadcastTaskEvent(event: String, task: TaskDto) {
        val payload = Json.encodeToString(mapOf("event" to event, "task" to task))
        val toSend = mutableListOf<DefaultWebSocketServerSession>()
        mutex.withLock {
            toSend.addAll(connections)
        }
        toSend.forEach { session ->
            try {
                session.send(Frame.Text(payload))
            } catch (t: Throwable) {
                // ignore send failures
            }
        }
    }
}
