package com.google.mytaskmanager.presentation.logic

import com.google.mytaskmanager.domain.model.Task

/**
 * Quick filters of the board (Jira: search box + "Only My Issues" + "Recently Updated" ...).
 *
 * @property query matches title, key and labels (case-insensitive).
 * @property onlyMine only issues assigned to the signed-in user.
 * @property recentlyUpdated only issues changed in the last 24 hours.
 * @property overdue only issues whose due date has passed.
 */
data class BoardFilter(
    val query: String = "",
    val onlyMine: Boolean = false,
    val recentlyUpdated: Boolean = false,
    val overdue: Boolean = false
) {
    /** True when any filter narrows the board (so the UI can show "clear filters"). */
    val isActive: Boolean get() = query.isNotBlank() || onlyMine || recentlyUpdated || overdue

    /** Number of switched-on filters, for the badge. */
    val activeCount: Int get() = listOf(query.isNotBlank(), onlyMine, recentlyUpdated, overdue).count { it }
}

/** How full a column is relative to its WIP limit. */
enum class WipState { NONE, OK, AT_LIMIT, EXCEEDED }

/** Pure board helpers (unit-tested). */
object BoardLogic {
    private const val DAY_MS = 86_400_000L

    /**
     * Applies [filter] to [tasks].
     *
     * @param me id of the signed-in user (needed for [BoardFilter.onlyMine]).
     * @param now clock, injectable for tests.
     */
    fun apply(tasks: List<Task>, filter: BoardFilter, me: String?, now: Long = System.currentTimeMillis()): List<Task> {
        if (!filter.isActive) return tasks
        val q = filter.query.trim().lowercase()
        return tasks.filter { t ->
            (q.isEmpty() || t.title.lowercase().contains(q) || t.key.lowercase().contains(q) || t.labels.any { it.lowercase().contains(q) }) &&
                (!filter.onlyMine || (me != null && t.assigneeId == me)) &&
                (!filter.recentlyUpdated || t.updatedAt >= now - DAY_MS) &&
                (!filter.overdue || t.isOverdue(now))
        }
    }

    /**
     * Today's local calendar date (plus [plusDays]) as UTC-midnight millis - the format due dates are stored in.
     * Used by the quick "Today / Tomorrow / Next week" buttons of the replan dialog.
     */
    fun dayAsUtcMidnight(now: Long, plusDays: Int = 0, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long =
        (Math.floorDiv(now + zone.getOffset(now), DAY_MS) + plusDays) * DAY_MS

    /**
     * Converts an insertion index among the *visible* (filtered) cards into an index among *all* cards of the column.
     * Needed because the server orders the full column while the user may be looking at a filtered subset.
     *
     * @param allIds every card of the column in order, **without** the dragged card.
     * @param visibleIds the visible cards in order, without the dragged card (a subsequence of [allIds]).
     * @param visibleIndex insertion index among [visibleIds].
     */
    fun realIndex(allIds: List<String>, visibleIds: List<String>, visibleIndex: Int): Int = when {
        visibleIds.isEmpty() -> allIds.size
        visibleIndex < visibleIds.size -> allIds.indexOf(visibleIds[visibleIndex]).let { if (it < 0) allIds.size else it }
        else -> allIds.indexOf(visibleIds.last()).let { if (it < 0) allIds.size else it + 1 }
    }

    /** @return the column's WIP state for [count] issues and an optional [limit]. */
    fun wipState(count: Int, limit: Int?): WipState = when {
        limit == null -> WipState.NONE
        count > limit -> WipState.EXCEEDED
        count == limit -> WipState.AT_LIMIT
        else -> WipState.OK
    }
}

/** A horizontal extent (left..right) of a column on screen. */
data class Span(val start: Float, val end: Float) {
    /** @return true when [x] lies inside. */
    fun contains(x: Float) = x >= start && x <= end
}

/** Geometry used by the drag & drop board; pure so it can be tested without a device. */
object DragMath {
    /**
     * Which column is under the finger. Falls back to the *nearest* column when the finger is in a gap or beyond the
     * edges, so a drop never "misses".
     *
     * @param columns column id -> horizontal extent in screen coordinates.
     * @param x finger x.
     * @return the column id, or null when there are no columns.
     */
    fun columnAt(columns: Map<String, Span>, x: Float): String? {
        columns.entries.firstOrNull { it.value.contains(x) }?.let { return it.key }
        return columns.minByOrNull { (_, s) -> if (x < s.start) s.start - x else x - s.end }?.key
    }

    /**
     * Insertion index inside a column: how many cards have their vertical centre above the finger.
     *
     * @param cardCentersY centres of the column's cards (without the dragged card), in order.
     * @param y finger y.
     */
    fun insertionIndex(cardCentersY: List<Float>, y: Float): Int = cardCentersY.count { it < y }

    /**
     * Auto-scroll speed while dragging near an edge: 0 in the middle, up to ±[maxSpeed] at the very edge
     * (proportional, so it eases in instead of jumping).
     *
     * @param pos finger position along the scroll axis.
     * @param min start of the scrollable area.
     * @param max end of the scrollable area.
     * @param threshold width of the "hot zone" at each edge.
     * @return negative = scroll towards [min], positive = towards [max].
     */
    fun edgeScrollSpeed(pos: Float, min: Float, max: Float, threshold: Float, maxSpeed: Float): Float = when {
        pos < min + threshold -> -maxSpeed * ((min + threshold - pos) / threshold).coerceIn(0f, 1f)
        pos > max - threshold -> maxSpeed * ((pos - (max - threshold)) / threshold).coerceIn(0f, 1f)
        else -> 0f
    }

    /** True when the finger barely moved between long-press and release: that is a "hold" (open the menu), not a drag. */
    fun isHold(totalMovePx: Float, slopPx: Float): Boolean = totalMovePx < slopPx
}
