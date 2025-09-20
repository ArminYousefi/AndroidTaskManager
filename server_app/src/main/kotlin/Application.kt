package com.example

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import services.WebSocketHub
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.json.Json
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import models.*
import routes.authRoutes
import routes.boardRoutes
import services.AuthService
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; isLenient = true })
    }

    // Database (from env or defaults)
    val dbUrl = System.getenv("JDBC_DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/taskmanagerapp"
    val dbUser = System.getenv("DB_USER") ?: "postgres"
    val dbPass = System.getenv("DB_PASS") ?: "123456"
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = dbUrl
        username = dbUser
        password = dbPass
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 3
    }
    val ds = HikariDataSource(hikariConfig)
    Database.connect(ds)

    // Create tables if not exist
    transaction {
        SchemaUtils.create(Users, RefreshTokens, Boards, Lists, Tasks)
    }

    // JWT config (env with fallback defaults)
    val jwtSecret = System.getenv("JWT_SECRET") ?: "my-hardcoded-secret-key"
    val accessMs = (System.getenv("ACCESS_TOKEN_MS") ?: "900000").toLong() // 15 min default
    val refreshMs = (System.getenv("REFRESH_TOKEN_MS") ?: (30L * 24 * 60 * 60 * 1000).toString()).toLong() // 30 days default

    val authService = AuthService(jwtSecret, accessMs, refreshMs)

    // 🔹 Install Authentication plugin
    install(Authentication) {
        jwt {
            realm = "taskmanager"
            verifier(JWT.require(Algorithm.HMAC256(jwtSecret)).build())
            validate { credential ->
                val userId = credential.payload.getClaim("uid").asString()
                if (!userId.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
        }
    }

    // ✅ Register all routes in one routing block
    routing {
        get("/") { call.respondText("Ktor backend running") }

        // WebSocket
        webSocket("/ws") {
            val authHeader = call.request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized (no bearer)"))
                return@webSocket
            }

            val token = authHeader.removePrefix("Bearer ").trim()
            val userId = authService.verifyToken(token)
            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }

            println("✅ WebSocket connection established for user $userId")
            WebSocketHub.register(this)

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        println("📩 Received: ${frame.readText().take(100)}")
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                println("ℹ️ Client disconnected: $userId")
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                WebSocketHub.unregister(this)
                println("❌ WebSocket closed for $userId")
            }
        }

        // REST routes
        authRoutes(authService)
        boardRoutes()
    }
}
