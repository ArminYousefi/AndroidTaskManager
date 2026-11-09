package plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import config.AppConfig
import models.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.and

/** Connects to the database and creates/upgrades the schema. */
object DatabaseFactory {
    /** All tables, in dependency order. */
    val tables = arrayOf(
        Users, RefreshTokens, Teams, TeamMembers, Boards, BoardMembers, BoardTeams,
        Lists, Tasks, Comments, ActivityLog, ErrorReports
    )

    /**
     * Opens the connection pool, creates missing tables/columns and back-fills data created by
     * older versions of the server (board owners become ADMIN members, old issues get numbers).
     *
     * @param cfg application configuration.
     * @return the pool, so callers can close it on shutdown.
     */
    fun init(cfg: AppConfig): HikariDataSource {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = cfg.dbUrl
            username = cfg.dbUser
            password = cfg.dbPass
            driverClassName = if (cfg.dbUrl.startsWith("jdbc:h2")) "org.h2.Driver" else "org.postgresql.Driver"
            maximumPoolSize = 10
        })
        Database.connect(ds)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(*tables)
            backfillLegacyData()
        }
        return ds
    }

    /** Idempotent upgrade of rows written before multi-user support existed. */
    private fun backfillLegacyData() {
        val now = System.currentTimeMillis()
        Boards.selectAll().toList().forEach { b ->
            val boardId = b[Boards.id]
            val ownerId = b[Boards.userId]
            val hasOwnerRow = BoardMembers.select { (BoardMembers.boardId eq boardId) and (BoardMembers.userId eq ownerId) }.any()
            if (!hasOwnerRow) {
                BoardMembers.insert { it[BoardMembers.boardId] = boardId; it[BoardMembers.userId] = ownerId; it[BoardMembers.role] = "ADMIN" }
            }
            if (b[Boards.createdAt] == 0L) Boards.update({ Boards.id eq boardId }) { it[Boards.createdAt] = b[Boards.updatedAt].takeIf { t -> t > 0 } ?: now }
            val unnumbered = Tasks.select { (Tasks.boardId eq boardId) and (Tasks.number eq 0) }
                .orderBy(Tasks.updatedAt to SortOrder.ASC).map { it[Tasks.id] }
            if (unnumbered.isNotEmpty()) {
                var seq = b[Boards.taskSeq]
                unnumbered.forEach { tid -> seq += 1; Tasks.update({ Tasks.id eq tid }) { it[Tasks.number] = seq } }
                Boards.update({ Boards.id eq boardId }) { it[Boards.taskSeq] = seq }
            }
        }
    }
}
