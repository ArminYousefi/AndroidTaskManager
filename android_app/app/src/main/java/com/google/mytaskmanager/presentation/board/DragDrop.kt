package com.google.mytaskmanager.presentation.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.presentation.logic.DragMath
import com.google.mytaskmanager.presentation.logic.Span
import com.google.mytaskmanager.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Outcome of lifting the finger.
 *
 * @property listId column under the finger (null if nothing was hit).
 * @property index insertion index among the visible cards of that column, without the dragged card.
 * @property hold true when the finger barely moved: the user *held* the card, so show the action menu instead of moving it.
 */
data class DropResult(val taskId: String, val listId: String?, val index: Int, val hold: Boolean)

/**
 * Drag & drop state of one board, shared by every card and column.
 *
 * Gesture: press and hold a card -> it lifts (haptic tick) -> drag anywhere -> release to drop. If the finger did not
 * move between the long-press and the release, the gesture counts as a *hold* and opens the card menu
 * (rename / replan / delete ...) - one gesture, two outcomes, exactly like Jira's mobile board.
 *
 * Columns register their screen bounds and an "index at y" resolver; the controller only does geometry (see
 * [DragMath], unit-tested), the screen decides what to do with the [DropResult].
 */
@Stable
class DragController(private val touchSlopPx: Float) {
    var dragged by mutableStateOf<Task?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var grab by mutableStateOf(Offset.Zero)
        private set
    var cardSize by mutableStateOf(IntSize.Zero)
        private set
    var hoverListId by mutableStateOf<String?>(null)
        private set
    var hoverIndex by mutableStateOf(0)
        private set
    private var travelled = 0f

    /** Column id -> bounds in root coordinates (filled by each column). */
    val columnBounds = HashMap<String, Rect>()

    /** Column id -> function turning a root-Y into an insertion index. */
    val indexResolvers = HashMap<String, (Float) -> Int>()

    /** Column id -> its vertical scroll state (for auto-scroll while dragging). */
    val listStates = HashMap<String, LazyListState>()

    /** True while a card is lifted. */
    val isDragging: Boolean get() = dragged != null

    /** Called after the long press: lifts [task]. [fingerRoot]/[fingerLocal] are the touch point in root/card coordinates. */
    fun start(task: Task, fingerRoot: Offset, fingerLocal: Offset, size: IntSize) {
        dragged = task; pointer = fingerRoot; grab = fingerLocal; cardSize = size; travelled = 0f
        recompute()
    }

    /** Finger moved by [delta] pixels. */
    fun dragBy(delta: Offset) { pointer += delta; travelled += delta.getDistance(); recompute() }

    /** Re-evaluates which column/index is under the finger (also called while auto-scrolling). */
    fun recompute() {
        if (dragged == null) return
        val col = DragMath.columnAt(columnBounds.mapValues { Span(it.value.left, it.value.right) }, pointer.x)
        hoverListId = col
        hoverIndex = col?.let { indexResolvers[it]?.invoke(pointer.y) } ?: 0
    }

    /** Finger lifted: returns what to do and resets. Null when nothing was being dragged. */
    fun finish(): DropResult? {
        val t = dragged ?: return null
        val r = DropResult(t.id, hoverListId, hoverIndex, hold = DragMath.isHold(travelled, touchSlopPx))
        reset(); return r
    }

    /** Gesture cancelled by the system. */
    fun cancel() = reset()

    private fun reset() { dragged = null; hoverListId = null; hoverIndex = 0; travelled = 0f }
}

/**
 * The floating copy of the card that follows the finger: scales up, tilts slightly and gains a deep shadow, with a
 * springy pick-up so the card feels physically lifted off the board.
 *
 * @param overlayOrigin root position of the box this ghost is drawn in (to convert root -> local coordinates).
 */
@Composable
fun DragGhost(controller: DragController, overlayOrigin: Offset, content: @Composable (Task) -> Unit) {
    val task = controller.dragged ?: return
    val lift = remember(task.id) { Animatable(0f) }
    LaunchedEffect(task.id) { lift.animateTo(1f, Motion.bouncy()) }
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset((controller.pointer.x - controller.grab.x - overlayOrigin.x).roundToInt(), (controller.pointer.y - controller.grab.y - overlayOrigin.y).roundToInt()) }
            .width(with(density) { controller.cardSize.width.toDp() })
            .graphicsLayer {
                val s = 1f + 0.05f * lift.value
                scaleX = s; scaleY = s; rotationZ = -2.5f * lift.value
                shadowElevation = 28.dp.toPx() * lift.value
                shape = RoundedCornerShape(16.dp); clip = false; alpha = 0.97f
            }
    ) { content(task) }
}

/**
 * Reward moment: when a card lands in a DONE column, a ring and confetti dots burst from where it dropped
 * (peak-end rule: finishing work should feel good). Skipped when animations are disabled by the caller.
 */
@Composable
fun CelebrationBurst(center: Offset, overlayOrigin: Offset, color: Color, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(700, easing = FastOutSlowInEasing)); onDone() }
    Canvas(Modifier.fillMaxSize()) {
        val c = center - overlayOrigin
        val fade = 1f - p.value
        drawCircle(color.copy(alpha = 0.40f * fade), radius = 36.dp.toPx() + 96.dp.toPx() * p.value, center = c, style = Stroke(4.dp.toPx()))
        repeat(12) { i ->
            val a = i * (2 * PI / 12)
            val d = 28.dp.toPx() + 120.dp.toPx() * p.value
            val tint = if (i % 3 == 0) Color(0xFFFBBF24) else if (i % 3 == 1) Color(0xFF4FD1C5) else color
            drawCircle(tint.copy(alpha = fade), radius = 5.5.dp.toPx() * (1f - 0.5f * p.value), center = c + Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat()))
        }
    }
}
