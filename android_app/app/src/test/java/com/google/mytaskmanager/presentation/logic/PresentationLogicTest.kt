package com.google.mytaskmanager.presentation.logic

import com.google.mytaskmanager.domain.model.Activity
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.domain.rules.ActivityType
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.domain.rules.TaskType
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for the activity sentences, board filters and drag & drop geometry. */
class PresentationLogicTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun act(type: String, vararg d: Pair<String, String>) =
        Activity("1", "b", "t", "u", "Ali", type, ActivityType.parseOrNull(type), mapOf(*d), 0)

    private fun task(id: String, title: String = id, assignee: String? = null, due: Long? = null, updated: Long = 0, labels: List<String> = emptyList()) =
        Task(id, "b", "l", "WEB-1", 1, title, null, 0, TaskPriority.MEDIUM, TaskType.TASK, assignee, null, null, due, labels, 0, updated)

    @Test fun moveSentence() {
        assertEquals("Ali moved WEB-3 from “To Do” to “In Progress”",
            ActivityFormatter.headline(act("TASK_MOVED", "key" to "WEB-3", "from" to "To Do", "to" to "In Progress"), utc))
    }

    @Test fun renameAndDeleteSentences() {
        assertEquals("renamed WEB-3 from “A” to “B”", ActivityFormatter.describe(act("TASK_RENAMED", "key" to "WEB-3", "from" to "A", "to" to "B"), utc))
        assertEquals("deleted WEB-3 “Fix login”", ActivityFormatter.describe(act("TASK_DELETED", "key" to "WEB-3", "title" to "Fix login"), utc))
    }

    @Test fun replanShowsDatesInTheGivenTimeZone() {
        // 2026-10-20T00:00:00Z and 2026-10-25T00:00:00Z
        val s = ActivityFormatter.describe(act("TASK_REPLANNED", "key" to "WEB-3", "fromDue" to "1792454400000", "toDue" to "1792886400000", "fromStart" to "", "toStart" to ""), utc)
        assertEquals("replanned WEB-3: due Oct 20, 2026 → Oct 25, 2026", s)
        val first = ActivityFormatter.describe(act("TASK_REPLANNED", "key" to "WEB-3", "fromDue" to "", "toDue" to "1792886400000", "fromStart" to "", "toStart" to ""), utc)
        assertEquals("replanned WEB-3: due none → Oct 25, 2026", first)
    }

    @Test fun assignAndUnassign() {
        assertEquals("assigned WEB-3 to Sara", ActivityFormatter.describe(act("TASK_ASSIGNED", "key" to "WEB-3", "from" to "", "to" to "Sara")))
        assertEquals("unassigned WEB-3 (was Sara)", ActivityFormatter.describe(act("TASK_ASSIGNED", "key" to "WEB-3", "from" to "Sara", "to" to "")))
    }

    @Test fun priorityAndMemberRoles() {
        assertEquals("changed the priority of WEB-3 from High to Lowest", ActivityFormatter.describe(act("TASK_PRIORITY_CHANGED", "key" to "WEB-3", "from" to "HIGH", "to" to "LOWEST")))
        assertEquals("added Sara as Viewer", ActivityFormatter.describe(act("MEMBER_ADDED", "user" to "Sara", "role" to "VIEWER")))
    }

    @Test fun unknownTypeFromNewerServerDoesNotCrash() {
        assertEquals("did something (brand new thing)", ActivityFormatter.describe(act("BRAND_NEW_THING")))
    }

    @Test fun relativeTimeAndDayLabels() {
        val now = 1_792_900_000_000L
        assertEquals("just now", ActivityFormatter.relativeTime(now - 10_000, now, utc))
        assertEquals("5 min ago", ActivityFormatter.relativeTime(now - 5 * 60_000, now, utc))
        assertEquals("3 h ago", ActivityFormatter.relativeTime(now - 3 * 3_600_000, now, utc))
        assertEquals("yesterday", ActivityFormatter.relativeTime(now - 30 * 3_600_000, now, utc))
        assertEquals("Today", ActivityFormatter.dayLabel(now - 1000, now, utc))
        assertEquals("Yesterday", ActivityFormatter.dayLabel(now - 86_400_000L, now, utc))
    }

    @Test fun filtersCombine() {
        val now = 10 * 86_400_000L
        val tasks = listOf(
            task("a", "Fix login bug", assignee = "me", updated = now - 1000),
            task("b", "Write docs", assignee = "other", due = now - 3 * 86_400_000L, updated = 0),
            task("c", "Design", assignee = "me", labels = listOf("ui"), due = now + 5000)
        )
        assertEquals(3, BoardLogic.apply(tasks, BoardFilter(), "me", now).size)
        assertEquals(listOf("a"), BoardLogic.apply(tasks, BoardFilter(query = "LOGIN"), "me", now).map { it.id })
        assertEquals(listOf("a", "c"), BoardLogic.apply(tasks, BoardFilter(onlyMine = true), "me", now).map { it.id })
        assertEquals(listOf("a"), BoardLogic.apply(tasks, BoardFilter(recentlyUpdated = true), "me", now).map { it.id })
        assertEquals(listOf("b"), BoardLogic.apply(tasks, BoardFilter(overdue = true), "me", now).map { it.id })
        assertEquals(listOf("c"), BoardLogic.apply(tasks, BoardFilter(query = "ui", onlyMine = true), "me", now).map { it.id })
        assertTrue(BoardFilter(onlyMine = true).isActive)
        assertFalse(BoardFilter().isActive)
    }

    @Test fun wipStates() {
        assertEquals(WipState.NONE, BoardLogic.wipState(9, null))
        assertEquals(WipState.OK, BoardLogic.wipState(2, 3))
        assertEquals(WipState.AT_LIMIT, BoardLogic.wipState(3, 3))
        assertEquals(WipState.EXCEEDED, BoardLogic.wipState(4, 3))
    }

    @Test fun dropColumnIsNearestEvenInGapsAndOffscreen() {
        val cols = mapOf("todo" to Span(0f, 100f), "doing" to Span(110f, 210f))
        assertEquals("todo", DragMath.columnAt(cols, 50f))
        assertEquals("doing", DragMath.columnAt(cols, 150f))
        assertEquals("todo", DragMath.columnAt(cols, 103f))
        assertEquals("doing", DragMath.columnAt(cols, 108f))
        assertEquals("doing", DragMath.columnAt(cols, 999f))
        assertEquals("todo", DragMath.columnAt(cols, -50f))
        assertNull(DragMath.columnAt(emptyMap(), 1f))
    }

    @Test fun insertionIndexCountsCardsAboveFinger() {
        val centers = listOf(50f, 150f, 250f)
        assertEquals(0, DragMath.insertionIndex(centers, 10f))
        assertEquals(1, DragMath.insertionIndex(centers, 100f))
        assertEquals(3, DragMath.insertionIndex(centers, 999f))
        assertEquals(0, DragMath.insertionIndex(emptyList(), 100f))
    }

    @Test fun edgeScrollEasesIn() {
        assertEquals(0f, DragMath.edgeScrollSpeed(500f, 0f, 1000f, 100f, 20f))
        assertEquals(-20f, DragMath.edgeScrollSpeed(0f, 0f, 1000f, 100f, 20f))
        assertEquals(-10f, DragMath.edgeScrollSpeed(50f, 0f, 1000f, 100f, 20f))
        assertEquals(20f, DragMath.edgeScrollSpeed(1000f, 0f, 1000f, 100f, 20f))
        assertEquals(10f, DragMath.edgeScrollSpeed(950f, 0f, 1000f, 100f, 20f))
    }

    @Test fun dueDatesAreCalendarDaysNotInstants() {
        val utc = TimeZone.getTimeZone("UTC")
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        val dueOct20 = 1792454400000L // 2026-10-20T00:00:00Z, what a date picker returns for "Oct 20"
        val noonOct20Utc = dueOct20 + 12 * 3_600_000L
        assertEquals(0L, task("x", due = dueOct20).daysUntilDue(noonOct20Utc, utc))
        assertFalse(task("x", due = dueOct20).isOverdue(noonOct20Utc, utc)) // due today is not overdue
        assertTrue(task("x", due = dueOct20).isOverdue(dueOct20 + 25 * 3_600_000L, utc)) // next day it is
        // 10:00 in Los Angeles on Oct 20 is already past 00:00Z of Oct 20, yet the task is still "due today"
        val laMorning = dueOct20 + 17 * 3_600_000L
        assertEquals(0L, task("x", due = dueOct20).daysUntilDue(laMorning, la))
        assertEquals(null, task("x").daysUntilDue(laMorning, la))
    }

    @Test fun quickDatesUseTheLocalCalendarDay() {
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        val utc = TimeZone.getTimeZone("UTC")
        val dueOct20 = 1792454400000L
        // 2026-10-20 23:00 in Los Angeles is already Oct 21 in UTC, but "today" for the user is still Oct 20
        val laLate = dueOct20 + 30 * 3_600_000L
        assertEquals(dueOct20, BoardLogic.dayAsUtcMidnight(laLate, 0, la))
        assertEquals(dueOct20 + 86_400_000L, BoardLogic.dayAsUtcMidnight(laLate, 1, la))
        assertEquals(dueOct20 + 7 * 86_400_000L, BoardLogic.dayAsUtcMidnight(dueOct20 + 3_600_000L, 7, utc))
    }

    @Test fun filteredDropIndexMapsToTheRealColumnOrder() {
        val all = listOf("a", "b", "c", "d", "e")
        val visible = listOf("b", "d") // filter hides a, c, e
        assertEquals(1, BoardLogic.realIndex(all, visible, 0)) // before b
        assertEquals(3, BoardLogic.realIndex(all, visible, 1)) // before d
        assertEquals(4, BoardLogic.realIndex(all, visible, 2)) // after d
        assertEquals(5, BoardLogic.realIndex(all, emptyList(), 0)) // nothing visible -> end
        assertEquals(2, BoardLogic.realIndex(all, all, 2)) // no filter: identity
    }

    @Test fun holdVersusDrag() {
        assertTrue(DragMath.isHold(3f, 24f))
        assertFalse(DragMath.isHold(60f, 24f))
    }
}
