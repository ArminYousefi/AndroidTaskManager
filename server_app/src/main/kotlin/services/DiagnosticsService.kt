package services

import models.ErrorReportDto
import models.ErrorReports
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/** Stores crash/error reports (from the app and from uncaught server exceptions) so developers can inspect them. */
object DiagnosticsService {
    /** Max reports accepted per upload. */
    const val MAX_BATCH = 20

    /**
     * Persists reports uploaded by the app. Field sizes are clamped so a buggy client cannot fill the disk.
     *
     * @param reports reports from the client.
     * @param userId authenticated user, if the upload carried a valid token.
     * @param requestId the HTTP request id for correlation.
     * @return how many reports were stored.
     */
    fun ingest(reports: List<ErrorReportDto>, userId: String?, requestId: String?): Int {
        val batch = reports.take(MAX_BATCH)
        val now = System.currentTimeMillis()
        transaction {
            batch.forEach { r ->
                ErrorReports.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[source] = "android"
                    it[level] = r.level.take(16).ifBlank { "ERROR" }
                    it[message] = r.message.take(4_000)
                    it[stackTrace] = r.stackTrace?.take(20_000)
                    it[screen] = r.screen?.take(100)
                    it[appVersion] = r.appVersion?.take(50)
                    it[osVersion] = r.osVersion?.take(50)
                    it[device] = r.device?.take(100)
                    it[ErrorReports.userId] = userId ?: r.userId?.take(36)
                    it[thread] = r.thread?.take(100)
                    it[breadcrumbs] = r.breadcrumbs?.take(8_000)
                    it[occurredAt] = r.occurredAt.takeIf { t -> t > 0 } ?: now
                    it[receivedAt] = now
                    it[ErrorReports.requestId] = requestId?.take(64)
                }
            }
        }
        return batch.size
    }

    /** Records an uncaught server-side exception. Never throws. */
    fun recordServerError(t: Throwable, requestId: String?, uri: String?) {
        try {
            val now = System.currentTimeMillis()
            transaction {
                ErrorReports.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[source] = "server"
                    it[level] = "ERROR"
                    it[message] = (t.toString()).take(4_000)
                    it[stackTrace] = t.stackTraceToString().take(20_000)
                    it[screen] = uri?.take(100)
                    it[occurredAt] = now
                    it[receivedAt] = now
                    it[ErrorReports.requestId] = requestId?.take(64)
                }
            }
        } catch (inner: Throwable) {
            com.example.logging.error("Diagnostics", "Could not store server error", inner)
        }
    }

    /**
     * Newest-first listing for developers.
     *
     * @param limit 1..200 rows.
     * @param source optional filter: `android` or `server`.
     */
    fun list(limit: Int, source: String?): List<ErrorReportDto> = transaction {
        val q = if (source.isNullOrBlank()) ErrorReports.selectAll() else ErrorReports.select { ErrorReports.source eq source }
        q.orderBy(ErrorReports.receivedAt to SortOrder.DESC).limit(limit.coerceIn(1, 200)).map {
            ErrorReportDto(
                id = it[ErrorReports.id], level = it[ErrorReports.level], message = it[ErrorReports.message],
                stackTrace = it[ErrorReports.stackTrace], screen = it[ErrorReports.screen], appVersion = it[ErrorReports.appVersion],
                osVersion = it[ErrorReports.osVersion], device = it[ErrorReports.device], userId = it[ErrorReports.userId],
                thread = it[ErrorReports.thread], breadcrumbs = it[ErrorReports.breadcrumbs], occurredAt = it[ErrorReports.occurredAt],
                source = it[ErrorReports.source], receivedAt = it[ErrorReports.receivedAt], requestId = it[ErrorReports.requestId]
            )
        }
    }
}
