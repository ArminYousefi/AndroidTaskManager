package routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import services.AuthService
import kotlinx.serialization.Serializable
import models.*

/**
 * Auth routes.
 *
 * - Adds explicit logging for incoming requests.
 * - Validates required fields and returns clear status codes and messages.
 */
fun Route.authRoutes(authService: AuthService) {
    println(">>> authRoutes MOUNTED <<<")

    route("/auth") {
        post("/signup") {
            println(">>> POST /auth/signup received")
            val req = try {
                call.receive<SignupRequest>()
            } catch (t: Throwable) {
                // couldn't parse JSON body
                t.printStackTrace()
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request body")
                )
                return@post
            }

            // simple validation
            if (req.username.isBlank() || req.password.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "username and password are required")
                )
                return@post
            }

            try {
                val resp = authService.signup(req.username.trim(), req.password, req.email?.trim())
                call.respond(HttpStatusCode.Created, resp)
            } catch (iae: IllegalArgumentException) {
                // service-level validation error
                println("signup failed: ${iae.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (iae.message ?: "invalid input")))
            } catch (e: Throwable) {
                // Duplicate-user or DB unique constraint may surface here — try to detect by message
                val msg = e.message ?: "internal error"
                println("signup exception: $msg")
                // If it's a duplicate user, return 409
                if (msg.contains("duplicate", ignoreCase = true) || msg.contains("unique", ignoreCase = true)) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "User already exists"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "server error", "detail" to msg))
                }
            }
        }

        post("/login") {
            println(">>> POST /auth/login received")
            val req = try {
                call.receive<LoginRequest>()
            } catch (t: Throwable) {
                t.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                return@post
            }

            if (req.username.isBlank() || req.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "username and password are required"))
                return@post
            }

            try {
                val resp = authService.login(req.username.trim(), req.password)
                call.respond(HttpStatusCode.OK, resp)
            } catch (iae: IllegalArgumentException) {
                // invalid credentials thrown as IllegalArgumentException in service
                println("login failed: ${iae.message}")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (iae.message ?: "Invalid credentials")))
            } catch (e: Throwable) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "server error", "detail" to (e.message ?: "")))
            }
        }

        post("/refresh") {
            println(">>> POST /auth/refresh received")
            val payload = try {
                call.receive<Map<String, String>>()
            } catch (t: Throwable) {
                t.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                return@post
            }
            val token = payload["refreshToken"]
            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing refreshToken"))
                return@post
            }
            try {
                val resp = authService.refresh(token)
                if (resp == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid refresh token"))
                } else {
                    call.respond(HttpStatusCode.OK, resp)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "server error"))
            }
        }
    }
}