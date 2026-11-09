package routes

import config.AppConfig
import domain.SlidingWindowLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import models.ErrorReportBatch
import models.HealthDto
import org.jetbrains.exposed.sql.transactions.transaction
import plugins.ApiException
import plugins.body
import services.DiagnosticsService
import java.security.MessageDigest

/** Upload budget per client IP. Declared at top level: a `val` directly before `get(...)` is parsed as a property getter. */
private val uploadLimiter = SlidingWindowLimiter(maxEvents = 30, windowMs = 60_000)

/**
 * Operations endpoints.
 *
 * - `GET /health` - liveness + DB check (used by Docker/monitoring).
 * - `POST /diagnostics/errors` - the app uploads crash/error reports. A token is optional (a crash can happen before
 *   login) but uploads are rate-limited per client IP (30 requests/min).
 * - `GET /diagnostics/errors` - developers read them; requires header `X-Dev-Key` equal to env `DEV_API_KEY`
 *   (the endpoint answers 404 when no key is configured).
 */
fun Route.diagnosticsRoutes(cfg: AppConfig) {
    get("/health") {
        val dbOk = runCatching { transaction { exec("SELECT 1") }; true }.getOrDefault(false)
        call.respond(if (dbOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, HealthDto(if (dbOk) "ok" else "degraded", dbOk, "2.0.0", System.currentTimeMillis()))
    }

    authenticate("auth-jwt", optional = true) {
        post("/diagnostics/errors") {
            val ip = call.request.local.remoteHost
            if (!uploadLimiter.tryAcquire(ip)) throw ApiException.TooManyRequests()
            val batch = call.body<ErrorReportBatch>()
            val uid = runCatching { call.userId() }.getOrNull()
            val stored = DiagnosticsService.ingest(batch.reports, uid, call.callId)
            call.respond(HttpStatusCode.Accepted, mapOf("stored" to stored))
        }
    }

    get("/diagnostics/errors") {
        val expected = cfg.devApiKey ?: throw ApiException.NotFound("Endpoint not found")
        val given = call.request.headers["X-Dev-Key"].orEmpty()
        if (!MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())) throw ApiException.Forbidden("Invalid developer key")
        val q = call.request.queryParameters
        call.respond(DiagnosticsService.list(q["limit"]?.toIntOrNull() ?: 50, q["source"]))
    }
}
