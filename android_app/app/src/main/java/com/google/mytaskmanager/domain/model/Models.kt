package com.google.mytaskmanager.domain.model

import com.google.mytaskmanager.domain.rules.ActivityType
import com.google.mytaskmanager.domain.rules.BoardRole
import com.google.mytaskmanager.domain.rules.ColumnCategory
import com.google.mytaskmanager.domain.rules.Permission
import com.google.mytaskmanager.domain.rules.RolePolicy
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.domain.rules.TaskType
import com.google.mytaskmanager.domain.rules.TeamRole
import java.util.TimeZone

/**
 * The signed-in user. [username] is the immutable login id; [nickname] is the editable display name.
 */
data class User(val id: String, val username: String, val nickname: String? = null, val email: String? = null) {
    /** What the UI shows as the person's name: nickname when set, otherwise the username. */
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: username
}

/**
 * A board (Jira: project + board).
 *
 * @property key short prefix of issue ids, e.g. `WEB` in `WEB-12`.
 * @property role the signed-in user's effective role on this board.
 */
data class Board(
    val id: String, val name: String, val key: String, val description: String?, val ownerId: String,
    val role: BoardRole, val createdAt: Long, val updatedAt: Long,
    val listCount: Int = 0, val taskCount: Int = 0, val doneCount: Int = 0
) {
    /** Share of finished issues, 0f..1f (0 when the board is empty). */
    val progress: Float get() = if (taskCount == 0) 0f else (doneCount.toFloat() / taskCount).coerceIn(0f, 1f)

    /** @return true when the current user's role allows [permission]. */
    fun can(permission: Permission): Boolean = RolePolicy.can(role, permission)
}

/** A board column. [wipLimit] is the optional "max issues" constraint. */
data class BoardList(
    val id: String, val boardId: String, val title: String, val position: Int,
    val category: ColumnCategory, val wipLimit: Int?, val updatedAt: Long = 0
)

/** An issue. */
data class Task(
    val id: String, val boardId: String, val listId: String, val key: String, val number: Int,
    val title: String, val description: String?, val position: Int,
    val priority: TaskPriority, val type: TaskType, val assigneeId: String?, val reporterId: String?,
    val startDate: Long?, val dueDate: Long?, val labels: List<String>, val createdAt: Long, val updatedAt: Long
) {
    /**
     * Whole calendar days from today until the due date (negative = overdue, 0 = due today), or null without a due date.
     * Due dates are calendar dates stored as UTC midnight (what the date picker returns), so they are compared with
     * today's *local* date: a task due today only turns overdue after midnight, in every time zone.
     */
    fun daysUntilDue(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long? =
        dueDate?.let { Math.floorDiv(it, DAY_MS) - Math.floorDiv(now + zone.getOffset(now), DAY_MS) }

    /** True when the due date was before today - drives the red "overdue" styling. */
    fun isOverdue(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Boolean =
        (daysUntilDue(now, zone) ?: 0L) < 0L

    private companion object { const val DAY_MS = 86_400_000L }
}

/** A comment on an issue. */
data class Comment(val id: String, val taskId: String, val authorId: String, val authorName: String, val body: String, val createdAt: Long)

/**
 * One audit-log entry.
 *
 * @property type parsed type, or null when the server is newer than the app (shown generically).
 * @property details raw values (key, title, from, to, ...) used by `ActivityFormatter`.
 */
data class Activity(
    val id: String, val boardId: String, val taskId: String?, val actorId: String, val actorName: String,
    val rawType: String, val type: ActivityType?, val details: Map<String, String>, val createdAt: Long
)

/** A team of users. */
data class Team(val id: String, val name: String, val description: String?, val ownerId: String, val role: TeamRole, val memberCount: Int)

/**
 * A person with access to a board or team.
 *
 * @property role role name (`VIEWER|MEMBER|ADMIN` for boards, `MEMBER|ADMIN` for teams).
 * @property source `direct`, or `team:<name>` when board access comes from a team.
 */
data class Member(val userId: String, val username: String, val nickname: String?, val role: String, val source: String = "direct") {
    /** Display name with the same fallback rule as [User.displayName]. */
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: username

    /** True when this entry comes from a team grant (cannot be edited per person). */
    val viaTeam: Boolean get() = source != "direct"
}

/** A team that has been given access to a board. */
data class BoardTeam(val teamId: String, val teamName: String, val role: BoardRole, val memberCount: Int)

/** Light/Dark/Automatic theme preference. [AUTOMATIC] follows the system setting. */
enum class ThemeMode(val label: String) { LIGHT("Light"), DARK("Dark"), AUTOMATIC("Automatic") }
