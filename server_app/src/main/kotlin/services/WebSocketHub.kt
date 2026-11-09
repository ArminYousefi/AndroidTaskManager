package services

import com.example.logging
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import models.WsEnvelope
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time push channel.
 *
 * Every event is a [WsEnvelope]. Unlike the first version (which broadcast to *every* socket),
 * events are now delivered only to users who may see the board, so one team's activity never leaks to
 * another. Publishing is fire-and-forget on an internal scope and never blocks or fails a request.
 *
 * Event types: `task_created|updated|moved|deleted`, `list_created|updated|deleted|reordered`,
 * `board_updated|deleted|emptied`, `activity_created`, `member_changed`, `board_membership_changed`.
 */
object WebSocketHub {
    private val sessions = ConcurrentHashMap<DefaultWebSocketServerSession, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true }

    /** Registers an authenticated socket for [userId]. */
    fun register(session: DefaultWebSocketServerSession, userId: String) {
        sessions[session] = userId
        logging.info("WebSocketHub", "Client connected, total=${sessions.size}")
    }

    /** Forgets [session]. */
    fun unregister(session: DefaultWebSocketServerSession) {
        sessions.remove(session)
        logging.info("WebSocketHub", "Client disconnected, total=${sessions.size}")
    }

    /**
     * Sends an event to everyone who can currently see [boardId].
     *
     * @param type event type, e.g. `task_updated`.
     * @param entity payload kind, e.g. `task`.
     * @param data JSON payload.
     */
    fun toBoard(boardId: String, type: String, entity: String, data: JsonElement) {
        scope.launch {
            val recipients = try { AccessService.memberIds(boardId) } catch (t: Throwable) { logging.error("WebSocketHub", "recipient lookup failed", t); return@launch }
            deliver(recipients, WsEnvelope(type, entity, boardId, data))
        }
    }

    /** Sends an event to specific users (e.g. someone just added to / removed from a board). */
    fun toUsers(userIds: Collection<String>, type: String, entity: String, boardId: String?, data: JsonElement) {
        scope.launch { deliver(userIds.toSet(), WsEnvelope(type, entity, boardId, data)) }
    }

    private suspend fun deliver(recipients: Set<String>, envelope: WsEnvelope) {
        val text = json.encodeToString(WsEnvelope.serializer(), envelope)
        sessions.forEach { (session, uid) ->
            if (uid in recipients) {
                try { session.send(Frame.Text(text)) } catch (t: Throwable) {
                    logging.error("WebSocketHub", "Failed to send, dropping socket", t)
                    sessions.remove(session)
                }
            }
        }
    }
}
