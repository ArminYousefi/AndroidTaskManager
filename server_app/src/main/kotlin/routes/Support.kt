package routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import plugins.ApiException

/**
 * @return id of the authenticated caller (the `uid` claim of the access token).
 * @throws ApiException.Unauthorized when the request carries no valid token.
 */
fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()?.payload?.getClaim("uid")?.asString()?.takeIf { it.isNotBlank() }
        ?: throw ApiException.Unauthorized()

/** @return the path parameter [name]. @throws ApiException.BadRequest when missing. */
fun ApplicationCall.param(name: String): String =
    parameters[name] ?: throw ApiException.BadRequest("MISSING_PARAMETER", "Missing path parameter '$name'")
