package plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import models.ApiErrorBody
import models.ApiErrorDetail
import org.slf4j.LoggerFactory
import services.DiagnosticsService
import java.util.UUID

/**
 * Base class of every *expected* failure. Throw one of the subclasses from any service or route;
 * [installErrorHandling] turns it into the uniform JSON error body with the right HTTP status.
 *
 * @property status HTTP status to answer with.
 * @property code stable machine-readable code the app can switch on (e.g. `VALIDATION_ERROR`).
 * @property details optional field-level info (e.g. which password rules failed).
 */
open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: Map<String, String> = emptyMap()
) : RuntimeException(message) {
    /** 400 - malformed request. */
    class BadRequest(code: String = "BAD_REQUEST", message: String, details: Map<String, String> = emptyMap()) :
        ApiException(HttpStatusCode.BadRequest, code, message, details)

    /** 400 - semantic validation failure. */
    class Validation(message: String, details: Map<String, String> = emptyMap()) :
        ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", message, details)

    /** 401 - missing/invalid credentials or token. */
    class Unauthorized(message: String = "Authentication required", code: String = "UNAUTHORIZED") :
        ApiException(HttpStatusCode.Unauthorized, code, message)

    /** 403 - authenticated but not allowed. */
    class Forbidden(message: String = "You do not have permission to do this") :
        ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", message)

    /** 404 - missing resource (also used to hide the existence of boards the caller cannot see). */
    class NotFound(message: String = "Not found") :
        ApiException(HttpStatusCode.NotFound, "NOT_FOUND", message)

    /** 409 - state conflict (duplicate user, non-empty column, ...). */
    class Conflict(message: String, code: String = "CONFLICT") :
        ApiException(HttpStatusCode.Conflict, code, message)

    /** 429 - rate limit hit. */
    class TooManyRequests(message: String = "Too many requests, slow down") :
        ApiException(HttpStatusCode.TooManyRequests, "RATE_LIMITED", message)
}

private val log = LoggerFactory.getLogger("ErrorHandling")

/**
 * Installs request ids, request logging and the global exception mapping.
 *
 * - Every response carries `X-Request-Id`; the same id is in the log lines and in the error body.
 * - [ApiException] -> its status/code/message.
 * - Anything else -> HTTP 500 with a generic message (no stack trace or SQL leaks to the client);
 *   the full exception is logged and stored in the `error_reports` table for developers.
 */
fun Application.installErrorHandling() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.length in 8..64 }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) { callIdMdc("call-id") }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            if (cause is ApiException) {
                call.respond(cause.status, ApiErrorBody(ApiErrorDetail(cause.code, cause.message, cause.details, call.callId)))
            } else {
                log.error("Unhandled exception on ${call.request.local.method.value} ${call.request.local.uri}", cause)
                DiagnosticsService.recordServerError(cause, call.callId, call.request.local.uri)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiErrorBody(ApiErrorDetail("INTERNAL_ERROR", "Something went wrong on our side. Please try again.", emptyMap(), call.callId))
                )
            }
        }
    }
}

/**
 * Receives and decodes the JSON body as [T].
 *
 * @throws ApiException.BadRequest (`INVALID_BODY`) for malformed JSON, missing required fields or wrong types -
 * so a bad client payload is always a clean 400, never a 500.
 */
suspend inline fun <reified T : Any> ApplicationCall.body(): T =
    try {
        receive<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw ApiException.BadRequest("INVALID_BODY", "Malformed or incomplete request body")
    }
