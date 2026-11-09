package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import models.ApiErrorBody
import models.ApiErrorDetail
import plugins.DatabaseFactory
import plugins.installErrorHandling
import routes.*
import services.AuthService
import services.WebSocketHub

/** Process entry point. Configuration comes from environment variables, see [AppConfig]. */
fun main() {
    val cfg = AppConfig.load()
    embeddedServer(Netty, port = cfg.port, host = "0.0.0.0") { module(cfg) }.start(wait = true)
}

/**
 * Wires plugins, database, authentication and routes. Separated from [main] so tests can start the server
 * with their own [AppConfig] (e.g. an in-memory H2 database).
 *
 * @param cfg runtime configuration.
 */
fun Application.module(cfg: AppConfig = AppConfig.load()) {
    if (!cfg.isProduction && cfg.jwtSecret == AppConfig.DEV_SECRET) {
        logging.error("Config", "Using the built-in DEV JWT secret. Set JWT_SECRET (and APP_ENV=production) before deploying!")
    }
    installErrorHandling()
    install(WebSockets)
    install(ContentNegotiation) { json(Json { prettyPrint = false; isLenient = true; ignoreUnknownKeys = true; encodeDefaults = true }) }

    val ds = DatabaseFactory.init(cfg)
    environment.monitor.subscribe(io.ktor.server.application.ApplicationStopped) { ds.close() }

    val authService = AuthService(cfg.jwtSecret, cfg.accessTokenMs, cfg.refreshTokenMs)

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "taskmanager"
            verifier(JWT.require(Algorithm.HMAC256(cfg.jwtSecret)).build())
            validate { credential ->
                if (!credential.payload.getClaim("uid").asString().isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiErrorBody(ApiErrorDetail("UNAUTHORIZED", "Authentication required or token expired", emptyMap(), call.callId)))
            }
        }
    }

    routing {
        authRoutes(authService)
        teamRoutes()
        boardRoutes()
        taskRoutes()
        diagnosticsRoutes(cfg)

        authenticate("auth-jwt") {
            // Live updates. Authenticated with the same Bearer token as the REST API.
            webSocket("/ws") {
                val uid = call.userId()
                WebSocketHub.register(this, uid)
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Close) break // clients only listen; everything they send is ignored
                    }
                } catch (e: ClosedReceiveChannelException) {
                } finally {
                    WebSocketHub.unregister(this)
                }
            }
        }
    }
}
