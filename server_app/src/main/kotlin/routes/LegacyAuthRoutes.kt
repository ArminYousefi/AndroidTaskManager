
package routes

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import services.AuthService
import models.*

fun Route.legacyAuthRoutes(authService: AuthService) {
    // Backwards-compatible handlers at /signup and /login
    post("/signup") {
        val req = call.receive<SignupRequest>()
        try {
            val resp = authService.signup(req.username, req.password, req.email)
            call.respond(resp)
        } catch (e: Throwable) {
            call.respondText(e.message ?: "error", status = io.ktor.http.HttpStatusCode.BadRequest)
        }
    }

    post("/login") {
        val req = call.receive<LoginRequest>()
        try {
            val resp = authService.login(req.username, req.password)
            call.respond(resp)
        } catch (e: Throwable) {
            call.respondText(e.message ?: "error", status = io.ktor.http.HttpStatusCode.Unauthorized)
        }
    }
}
