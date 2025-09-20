package routes

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import services.AuthService
import services.WebSocketHub
import services.WebSocketService



