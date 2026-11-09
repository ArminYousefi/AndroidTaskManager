package services

import domain.ActivityType
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import models.ActivityDto
import models.ActivityLog
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * The audit trail. Every state change in the system (task renamed / moved / replanned / deleted,
 * column changes, membership changes, ...) is recorded here and pushed live to board members.
 */
object ActivityService {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val detailsSerializer = MapSerializer(String.serializer(), String.serializer())

    /**
     * Writes one entry and broadcasts it to the board. Never throws: a failed audit write must not
     * fail the user's action (it is logged instead).
     *
     * @param boardId board the event belongs to.
     * @param taskId related task, if any.
     * @param actorId user who did it.
     * @param type what happened.
     * @param details human-readable values (key, title, from, to, ...).
     */
    fun record(boardId: String, taskId: String?, actorId: String, type: ActivityType, details: Map<String, String> = emptyMap()) {
        try {
            val dto = ActivityDto(
                id = UUID.randomUUID().toString(), boardId = boardId, taskId = taskId, actorId = actorId,
                actorName = AccessService.displayName(actorId), type = type.name,
                details = details.mapValues { it.value.take(300) }, createdAt = System.currentTimeMillis()
            )
            transaction {
                ActivityLog.insert {
                    it[ActivityLog.id] = dto.id
                    it[ActivityLog.boardId] = boardId
                    it[ActivityLog.taskId] = taskId
                    it[ActivityLog.actorId] = actorId
                    it[ActivityLog.actorName] = dto.actorName
                    it[ActivityLog.type] = dto.type
                    it[ActivityLog.details] = json.encodeToString(detailsSerializer, dto.details)
                    it[ActivityLog.createdAt] = dto.createdAt
                }
            }
            WebSocketHub.toBoard(boardId, "activity_created", "activity", json.encodeToJsonElement(ActivityDto.serializer(), dto))
        } catch (t: Throwable) {
            com.example.logging.error("ActivityService", "Failed to record $type", t)
        }
    }

    /**
     * Reads the log newest-first.
     *
     * @param boardId board to read.
     * @param taskId when non-null only entries about this task.
     * @param limit max rows (clamped to 1..200).
     * @param before only entries strictly older than this timestamp (cursor for "load more").
     */
    fun query(boardId: String, taskId: String?, limit: Int, before: Long?): List<ActivityDto> = transaction {
        var cond = ActivityLog.boardId eq boardId
        if (taskId != null) cond = cond and (ActivityLog.taskId eq taskId)
        if (before != null) cond = cond and (ActivityLog.createdAt less before)
        ActivityLog.select { cond }.orderBy(ActivityLog.createdAt to SortOrder.DESC).limit(limit.coerceIn(1, 200)).map {
            ActivityDto(
                id = it[ActivityLog.id], boardId = it[ActivityLog.boardId], taskId = it[ActivityLog.taskId],
                actorId = it[ActivityLog.actorId], actorName = it[ActivityLog.actorName], type = it[ActivityLog.type],
                details = runCatching { json.decodeFromString(detailsSerializer, it[ActivityLog.details]) }.getOrDefault(emptyMap()),
                createdAt = it[ActivityLog.createdAt]
            )
        }
    }
}
