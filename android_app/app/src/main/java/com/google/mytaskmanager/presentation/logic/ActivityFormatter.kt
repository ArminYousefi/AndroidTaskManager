package com.google.mytaskmanager.presentation.logic

import com.google.mytaskmanager.domain.model.Activity
import com.google.mytaskmanager.domain.rules.ActivityType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Turns an audit-log [Activity] into a human sentence ("moved WEB-3 from “To Do” to “In Progress”").
 * Pure and deterministic (time zone and clock are parameters) so it is unit-tested.
 */
object ActivityFormatter {
    private fun q(s: String?) = "“${s.orEmpty()}”"

    /** "HIGHEST" -> "Highest", "IN_PROGRESS" -> "In progress". */
    fun pretty(raw: String?): String {
        val t = raw.orEmpty().lowercase().replace('_', ' ')
        return t.replaceFirstChar { it.uppercase() }
    }

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /** Formats a due/start date (a calendar date stored as UTC midnight) as `Oct 20, 2026`; blank/invalid gives "none". */
    fun date(millis: String?, zone: TimeZone = UTC): String {
        val ms = millis?.toLongOrNull() ?: return "none"
        return SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = zone }.format(ms)
    }

    /**
     * The action part of the sentence, without the actor's name.
     *
     * @param a the entry.
     * @param zone time zone used to display dates (replans).
     */
    fun describe(a: Activity, zone: TimeZone = TimeZone.getDefault()): String {
        val d = a.details
        val key = d["key"].orEmpty()
        return when (a.type) {
            ActivityType.BOARD_CREATED -> "created the board ${q(d["name"])}"
            ActivityType.BOARD_RENAMED -> "renamed the board from ${q(d["from"])} to ${q(d["to"])}"
            ActivityType.BOARD_UPDATED -> "updated the board ${d["field"] ?: "details"}"
            ActivityType.BOARD_EMPTIED -> "removed all ${d["count"] ?: ""} tasks from the board".replace("  ", " ")
            ActivityType.COLUMN_CREATED -> "added the column ${q(d["column"])}"
            ActivityType.COLUMN_RENAMED -> "renamed the column ${q(d["from"])} to ${q(d["to"])}"
            ActivityType.COLUMN_DELETED -> "deleted the column ${q(d["column"])}" + (d["moved"]?.toIntOrNull()?.takeIf { it > 0 }?.let { " (moved $it tasks)" } ?: "")
            ActivityType.COLUMN_REORDERED -> "reordered the columns"
            ActivityType.COLUMN_WIP_CHANGED -> "changed the WIP limit of ${q(d["column"])} from ${d["from"]} to ${d["to"]}"
            ActivityType.COLUMN_CATEGORY_CHANGED -> "changed the category of ${q(d["column"])} to ${pretty(d["to"])}"
            ActivityType.TASK_CREATED -> "created $key ${q(d["title"])}" + (d["column"]?.let { " in ${q(it)}" } ?: "")
            ActivityType.TASK_RENAMED -> "renamed $key from ${q(d["from"])} to ${q(d["to"])}"
            ActivityType.TASK_MOVED -> "moved $key from ${q(d["from"])} to ${q(d["to"])}"
            ActivityType.TASK_REPLANNED -> {
                val parts = mutableListOf<String>()
                if (d["fromDue"] != d["toDue"]) parts += "due ${date(d["fromDue"])} → ${date(d["toDue"])}"
                if (d["fromStart"] != d["toStart"]) parts += "start ${date(d["fromStart"])} → ${date(d["toStart"])}"
                "replanned $key" + if (parts.isEmpty()) "" else ": " + parts.joinToString(", ")
            }
            ActivityType.TASK_DELETED -> "deleted $key ${q(d["title"])}"
            ActivityType.TASK_ASSIGNED -> when {
                d["to"].isNullOrBlank() -> "unassigned $key (was ${d["from"]})"
                else -> "assigned $key to ${d["to"]}"
            }
            ActivityType.TASK_PRIORITY_CHANGED -> "changed the priority of $key from ${pretty(d["from"])} to ${pretty(d["to"])}"
            ActivityType.TASK_TYPE_CHANGED -> "changed the type of $key from ${pretty(d["from"])} to ${pretty(d["to"])}"
            ActivityType.TASK_DESCRIPTION_CHANGED -> "edited the description of $key"
            ActivityType.TASK_LABELS_CHANGED -> "updated the labels of $key" + (d["to"]?.takeIf { it.isNotBlank() }?.let { " to $it" } ?: "")
            ActivityType.COMMENT_ADDED -> "commented on $key: ${q(d["preview"])}"
            ActivityType.COMMENT_DELETED -> "deleted a comment on ${q(d["title"])}"
            ActivityType.MEMBER_ADDED -> "added ${d["user"]} as ${pretty(d["role"])}"
            ActivityType.MEMBER_REMOVED -> "removed ${d["user"]} from the board"
            ActivityType.MEMBER_ROLE_CHANGED -> "changed ${d["user"]}'s role to ${pretty(d["role"])}"
            ActivityType.TEAM_ADDED -> "gave team ${q(d["team"])} access as ${pretty(d["role"])}"
            ActivityType.TEAM_REMOVED -> "removed team ${q(d["team"])} from the board"
            ActivityType.TEAM_ROLE_CHANGED -> "changed team ${q(d["team"])} to ${pretty(d["role"])}"
            null -> "did something (${a.rawType.lowercase().replace('_', ' ')})"
        }
    }

    /** Full line: actor + action. */
    fun headline(a: Activity, zone: TimeZone = TimeZone.getDefault()): String = "${a.actorName} ${describe(a, zone)}"

    /** "just now", "5 min ago", "3 h ago", "yesterday", or "Oct 12" for older entries. */
    fun relativeTime(createdAt: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val diff = (now - createdAt).coerceAtLeast(0)
        val min = diff / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 60 * 24 -> "${min / 60} h ago"
            min < 60 * 48 -> "yesterday"
            else -> SimpleDateFormat("MMM d", Locale.US).apply { timeZone = zone }.format(createdAt)
        }
    }

    /** Day heading used to group the activity list: "Today", "Yesterday" or "Oct 12, 2026". */
    fun dayLabel(createdAt: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = zone }
        val day = f.format(createdAt)
        return when (day) {
            f.format(now) -> "Today"
            f.format(now - 86_400_000L) -> "Yesterday"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = zone }.format(createdAt)
        }
    }
}
