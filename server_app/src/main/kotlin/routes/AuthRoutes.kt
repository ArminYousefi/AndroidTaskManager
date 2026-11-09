package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import models.*
import plugins.ApiException
import plugins.body
import services.AuthService

/**
 * Account endpoints.
 *
 * Public: `POST /auth/signup|login|refresh|logout`.
 * Authenticated: `GET|PATCH /users/me`, `POST /users/me/password`, `GET /users/search?q=`.
 */
fun Route.authRoutes(auth: AuthService) {
    route("/auth") {
        post("/signup") {
            val r = call.body<SignupRequest>()
            call.respond(HttpStatusCode.Created, auth.signup(r.username, r.password, r.email, r.nickname))
        }
        post("/login") {
            val r = call.body<LoginRequest>()
            call.respond(auth.login(r.username, r.password))
        }
        post("/refresh") {
            val r = call.body<RefreshRequest>()
            if (r.refreshToken.isBlank()) throw ApiException.BadRequest("INVALID_BODY", "refreshToken required")
            call.respond(auth.refresh(r.refreshToken))
        }
        post("/logout") {
            val r = runCatching { call.body<LogoutRequest>() }.getOrDefault(LogoutRequest())
            auth.logout(r.refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    authenticate("auth-jwt") {
        route("/users") {
            get("/me") { call.respond(auth.me(call.userId())) }
            patch("/me") { call.respond(auth.updateNickname(call.userId(), call.body<UpdateProfileRequest>().nickname)) }
            post("/me/password") {
                val r = call.body<ChangePasswordRequest>()
                call.respond(auth.changePassword(call.userId(), r.currentPassword, r.newPassword))
            }
            get("/search") { call.respond(auth.search(call.request.queryParameters["q"].orEmpty(), call.userId())) }
        }
    }
}
