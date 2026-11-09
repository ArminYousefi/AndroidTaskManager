package com.google.mytaskmanager.presentation.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.model.BoardList
import com.google.mytaskmanager.domain.model.Member
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.domain.rules.ColumnCategory
import com.google.mytaskmanager.domain.rules.Permission
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.presentation.logic.ActivityFormatter
import com.google.mytaskmanager.presentation.logic.BoardFilter
import com.google.mytaskmanager.presentation.logic.BoardLogic
import com.google.mytaskmanager.presentation.logic.DragMath
import com.google.mytaskmanager.presentation.logic.WipState
import com.google.mytaskmanager.ui.components.*
import com.google.mytaskmanager.ui.icons.AppIcons
import com.google.mytaskmanager.ui.theme.AppTheme
import com.google.mytaskmanager.ui.theme.Motion
import com.google.mytaskmanager.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Dialogs that act on one card. */
private sealed interface TaskDialog {
    data class Rename(val task: Task) : TaskDialog
    data class Replan(val task: Task) : TaskDialog
    data class Delete(val task: Task) : TaskDialog
    data class Priority(val task: Task) : TaskDialog
    data class MoveTo(val task: Task) : TaskDialog
}

/** Dialogs that act on one column (or add one). */
private sealed interface ColumnDialog {
    data object Add : ColumnDialog
    data class Rename(val list: BoardList) : ColumnDialog
    data class Wip(val list: BoardList) : ColumnDialog
    data class Delete(val column: ColumnUi) : ColumnDialog
}

private val ColumnWidth = 288.dp

/**
 * The Kanban board (Jira-style).
 *
 * - **Hold & move**: press and hold a card, drag it to any column/position and release. The column under the finger
 *   glows, a blue line shows the insertion point, the edges auto-scroll, and dropping into a DONE column celebrates.
 * - **Hold without moving**: opens the card menu - open, rename, replan, assign, priority, move to, delete.
 * - Columns carry a status colour, a card counter with WIP-limit warning, and an admin menu (rename, WIP limit,
 *   reorder, delete). Quick filters: search, only my issues, recently updated, overdue.
 * Everything is permission-aware: viewers can look but never edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(onBack: () -> Unit, onOpenTask: (String) -> Unit, onActivity: () -> Unit, onSettings: () -> Unit, vm: BoardViewModel = hiltViewModel()) {
    val board by vm.board.collectAsState()
    val columns by vm.columns.collectAsState()
    val state by vm.state.collectAsState()
    val members by vm.members.collectAsState()
    val me by vm.meId.collectAsState()
    val memberById = remember(members) { members.associateBy { it.userId } }

    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val controller = remember { DragController(touchSlopPx = slop * 2.5f) }
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val rowState = rememberLazyListState()
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var burst by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var burstCount by remember { mutableIntStateOf(0) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sheetTask by remember { mutableStateOf<Task?>(null) }
    var dialog by remember { mutableStateOf<TaskDialog?>(null) }
    var colDialog by remember { mutableStateOf<ColumnDialog?>(null) }

    fun can(p: Permission) = board?.can(p) == true
    LaunchedEffect(state.gone) { if (state.gone) onBack() }

    // subtle tick every time the finger enters another column
    LaunchedEffect(controller.hoverListId) { if (controller.isDragging) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    // edge auto-scroll while dragging: horizontally between columns, vertically inside the hovered column
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val edgePx = with(density) { 72.dp.toPx() }
    val dragging = controller.isDragging
    LaunchedEffect(dragging) {
        while (dragging && isActive) {
            val p = controller.pointer
            val hs = DragMath.edgeScrollSpeed(p.x, 0f, screenWidthPx, edgePx, 26f)
            if (hs != 0f) rowState.scrollBy(hs)
            val hover = controller.hoverListId
            val b = hover?.let { controller.columnBounds[it] }
            if (b != null) {
                val vs = DragMath.edgeScrollSpeed(p.y, b.top + edgePx, b.bottom, edgePx, 26f)
                if (vs != 0f) controller.listStates[hover]?.scrollBy(vs)
            }
            controller.recompute()
            delay(16)
        }
    }

    fun handleDrop(r: DropResult?) {
        if (r == null) return
        val task = columns.flatMap { it.tasks }.firstOrNull { it.id == r.taskId } ?: return
        if (r.hold) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sheetTask = task; return }
        val targetId = r.listId ?: return
        val target = columns.firstOrNull { it.list.id == targetId }?.list
        val source = columns.firstOrNull { it.list.id == task.listId }?.list
        if (target != null && target.category == ColumnCategory.DONE && source?.category != ColumnCategory.DONE && !reduceMotion) {
            burstCount += 1; burst = burstCount to controller.pointer
        }
        vm.dropTask(task.id, targetId, r.index)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = board?.name ?: "Board", onBack = onBack,
                subtitle = board?.let { "${it.key} · ${it.role.name.lowercase().replaceFirstChar { c -> c.uppercase() }}" },
                actions = {
                    BadgedBox(badge = { if (state.filter.isActive) Badge { Text("${state.filter.activeCount}") } }) {
                        IconButton(onClick = { showSearch = !showSearch }) { Icon(AppIcons.Search, "Search and filter") }
                    }
                    IconButton(onClick = onActivity) { Icon(AppIcons.Activity, "Activity") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.More, "Board menu") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; vm.refresh() })
                            DropdownMenuItem(text = { Text("Activity log") }, onClick = { menuOpen = false; onActivity() })
                            DropdownMenuItem(text = { Text(if (can(Permission.MANAGE_MEMBERS)) "Board settings & access" else "Members & settings") }, onClick = { menuOpen = false; onSettings() })
                            if (can(Permission.MANAGE_COLUMNS)) DropdownMenuItem(text = { Text("Add column") }, onClick = { menuOpen = false; colDialog = ColumnDialog.Add })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() }) {
            Column(Modifier.fillMaxSize()) {
                FilterBar(state.filter, members, showSearch, vm::setQuery, vm::toggleOnlyMine, vm::toggleRecent, vm::toggleOverdue, vm::clearFilters)
                when {
                    state.loading && columns.isEmpty() -> Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(2) { ShimmerBox(Modifier.width(ColumnWidth).height(420.dp), MaterialTheme.shapes.large) }
                    }
                    columns.isEmpty() -> EmptyState(AppIcons.BoardsGrid, "No columns", "This board has no columns yet.", actionLabel = if (can(Permission.MANAGE_COLUMNS)) "Add column" else null, onAction = { colDialog = ColumnDialog.Add })
                    else -> LazyRow(state = rowState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(columns, key = { it.list.id }) { col ->
                            ColumnView(
                                col, memberById, controller,
                                canCreate = can(Permission.CREATE_TASK), canMove = can(Permission.MOVE_TASK), canManage = can(Permission.MANAGE_COLUMNS),
                                isFirst = columns.first().list.id == col.list.id, isLast = columns.last().list.id == col.list.id,
                                onOpen = onOpenTask, onHold = { sheetTask = it }, onDrop = ::handleDrop,
                                onCreate = { title -> vm.createTask(col.list.id, title) },
                                onRename = { colDialog = ColumnDialog.Rename(col.list) }, onWip = { colDialog = ColumnDialog.Wip(col.list) },
                                onMove = { dir -> vm.moveColumn(col.list, dir) }, onDelete = { colDialog = ColumnDialog.Delete(col) }
                            )
                        }
                        if (can(Permission.MANAGE_COLUMNS)) item(key = "add-column") {
                            OutlinedButton(onClick = { colDialog = ColumnDialog.Add }, modifier = Modifier.width(220.dp).height(52.dp), shape = MaterialTheme.shapes.large) {
                                Icon(AppIcons.Add, null); Spacer(Modifier.width(8.dp)); Text("Add column")
                            }
                        }
                    }
                }
            }
            DragGhost(controller, overlayOrigin) { t -> TaskCard(t, memberById[t.assigneeId]) }
            burst?.let { (id, at) ->
                key(id) { CelebrationBurst(at, overlayOrigin, AppTheme.extra.done) { burst = null } }
            }
        }
    }

    // ------------------------------------------------------------ card menu (hold without moving)
    sheetTask?.let { t ->
        val canEdit = can(Permission.EDIT_TASK)
        ModalBottomSheet(onDismissRequest = { sheetTask = null }, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeBadge(t.type)
                    Text(t.key, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(t.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp), maxLines = 2)
                SheetAction("Open details") { sheetTask = null; onOpenTask(t.id) }
                if (canEdit) {
                    SheetAction("Rename") { sheetTask = null; dialog = TaskDialog.Rename(t) }
                    SheetAction("Replan", t.dueDate?.let { "Due ${ActivityFormatter.date(it.toString())}" } ?: "Set start and due dates") { sheetTask = null; dialog = TaskDialog.Replan(t) }
                    if (t.assigneeId == me) SheetAction("Unassign me") { sheetTask = null; vm.assign(t, null) }
                    else SheetAction("Assign to me") { sheetTask = null; vm.assign(t, me) }
                    SheetAction("Change priority", t.priority.name.lowercase().replaceFirstChar { it.uppercase() }) { sheetTask = null; dialog = TaskDialog.Priority(t) }
                }
                if (can(Permission.MOVE_TASK)) SheetAction("Move to…") { sheetTask = null; dialog = TaskDialog.MoveTo(t) }
                if (can(Permission.DELETE_TASK)) SheetAction("Delete", danger = true) { sheetTask = null; dialog = TaskDialog.Delete(t) }
            }
        }
    }

    // ------------------------------------------------------------ card dialogs
    when (val d = dialog) {
        is TaskDialog.Rename -> TextInputDialog("Rename ${d.task.key}", "Title", initial = d.task.title, singleLine = false,
            validate = { if (it.isBlank()) "Title can't be empty" else if (it.length > 255) "Max 255 characters" else null },
            onConfirm = { vm.renameTask(d.task, it); dialog = null }, onDismiss = { dialog = null })
        is TaskDialog.Replan -> ReplanDialog(d.task, onConfirm = { s, due -> vm.replanTask(d.task, s, due); dialog = null }, onDismiss = { dialog = null })
        is TaskDialog.Delete -> ConfirmDialog("Delete ${d.task.key}?", "“${d.task.title}” will be removed from the board. The deletion is recorded in the activity log.", "Delete", danger = true,
            onConfirm = { vm.deleteTask(d.task); dialog = null }, onDismiss = { dialog = null })
        is TaskDialog.Priority -> ChoiceDialog("Priority", TaskPriority.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onPick = { vm.setPriority(d.task, it); dialog = null }, onDismiss = { dialog = null }, selected = d.task.priority)
        is TaskDialog.MoveTo -> ChoiceDialog("Move ${d.task.key} to", columns.map { it.list }.filter { it.id != d.task.listId }, { it.title },
            onPick = { vm.moveToColumn(d.task, it); dialog = null }, onDismiss = { dialog = null })
        null -> Unit
    }

    // ------------------------------------------------------------ column dialogs
    when (val d = colDialog) {
        ColumnDialog.Add -> AddColumnDialog(onConfirm = { t, c -> vm.addColumn(t, c); colDialog = null }, onDismiss = { colDialog = null })
        is ColumnDialog.Rename -> TextInputDialog("Rename column", "Column name", initial = d.list.title,
            validate = { if (it.isBlank()) "Name can't be empty" else if (it.length > 50) "Max 50 characters" else null },
            onConfirm = { vm.renameColumn(d.list, it); colDialog = null }, onDismiss = { colDialog = null })
        is ColumnDialog.Wip -> TextInputDialog("WIP limit", "Max cards (empty = no limit)", initial = d.list.wipLimit?.toString().orEmpty(),
            help = "Shown in red when the column holds more cards than this.",
            validate = { if (it.isBlank() || it.toIntOrNull() in 1..999) null else "Enter a number from 1 to 999" },
            onConfirm = { vm.setWipLimit(d.list, it.toIntOrNull()); colDialog = null }, onDismiss = { colDialog = null })
        is ColumnDialog.Delete -> DeleteColumnDialog(d.column, columns.map { it.list }.filter { it.id != d.column.list.id },
            onConfirm = { moveTo, deleteTasks -> vm.deleteColumn(d.column.list, moveTo, deleteTasks); colDialog = null }, onDismiss = { colDialog = null })
        null -> Unit
    }
}

@Composable
private fun SheetAction(label: String, hint: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Search box (toggled), avatar stack of the people on the board, and the quick-filter chips. */
@Composable
private fun FilterBar(
    filter: BoardFilter, members: List<Member>, showSearch: Boolean,
    onQuery: (String) -> Unit, onMine: () -> Unit, onRecent: () -> Unit, onOverdue: () -> Unit, onClear: () -> Unit
) {
    Column {
        AnimatedVisibility(visible = showSearch) {
            OutlinedTextField(
                value = filter.query, onValueChange = onQuery, singleLine = true, shape = MaterialTheme.shapes.large,
                placeholder = { Text("Search title, key or label") }, leadingIcon = { Icon(AppIcons.Search, null) },
                trailingIcon = { if (filter.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(AppIcons.Close, "Clear") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (members.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), modifier = Modifier.padding(end = 8.dp)) {
                members.take(4).forEach { Box(Modifier.border(2.dp, MaterialTheme.colorScheme.background, CircleShape)) { Avatar(it.displayName, size = 28.dp) } }
                if (members.size > 4) Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("+${members.size - 4}", style = MaterialTheme.typography.labelSmall) }
            }
            FilterChip(selected = filter.onlyMine, onClick = onMine, label = { Text("Only my issues") })
            FilterChip(selected = filter.recentlyUpdated, onClick = onRecent, label = { Text("Recently updated") })
            FilterChip(selected = filter.overdue, onClick = onOverdue, label = { Text("Overdue") })
            if (filter.isActive) TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

/** One column: coloured status bar, title with counter (WIP aware), admin menu, cards, and "Create issue". */
@Composable
private fun ColumnView(
    col: ColumnUi, memberById: Map<String, Member>, controller: DragController,
    canCreate: Boolean, canMove: Boolean, canManage: Boolean, isFirst: Boolean, isLast: Boolean,
    onOpen: (String) -> Unit, onHold: (Task) -> Unit, onDrop: (DropResult?) -> Unit, onCreate: (String) -> Unit,
    onRename: () -> Unit, onWip: () -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit
) {
    val id = col.list.id
    val listState = rememberLazyListState()
    var listTop by remember { mutableFloatStateOf(0f) }
    val tasksNow by rememberUpdatedState(col.tasks)
    DisposableEffect(id) {
        controller.listStates[id] = listState
        controller.indexResolvers[id] = { y ->
            val dragId = controller.dragged?.id
            val others = tasksNow.filter { it.id != dragId }
            val visible = listState.layoutInfo.visibleItemsInfo.filter { info -> others.any { it.id == info.key } }
            if (visible.isEmpty()) 0 else {
                val first = others.indexOfFirst { it.id == visible.first().key }
                first + DragMath.insertionIndex(visible.map { listTop + it.offset + it.size / 2f }, y)
            }
        }
        onDispose { controller.listStates.remove(id); controller.indexResolvers.remove(id); controller.columnBounds.remove(id) }
    }

    val hovering = controller.isDragging && controller.hoverListId == id
    val glow by animateFloatAsState(if (hovering) 1f else 0f, Motion.quick(), label = "glow")
    val accent = AppTheme.extra.category(col.list.category)
    val draggedId = controller.dragged?.id
    val others = remember(col.tasks, draggedId) { col.tasks.filter { it.id != draggedId } }
    val indexInOthers = remember(others) { others.withIndex().associate { it.value.id to it.index } }

    Column(
        Modifier.width(ColumnWidth).fillMaxHeight().clip(MaterialTheme.shapes.large).background(AppTheme.extra.column)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f * glow), MaterialTheme.shapes.large)
            .onGloballyPositioned { controller.columnBounds[id] = it.boundsInRoot() }
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(accent))
        ColumnHeader(col, canManage, isFirst, isLast, onRename, onWip, onMove, onDelete)
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).onGloballyPositioned { listTop = it.positionInRoot().y },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(col.tasks, key = { it.id }) { t ->
                val idx = indexInOthers[t.id]
                DraggableCard(
                    t, memberById[t.assigneeId], controller, canMove, dimmed = t.id == draggedId,
                    lineAbove = hovering && idx != null && controller.hoverIndex == idx,
                    lineBelow = hovering && idx != null && idx == others.lastIndex && controller.hoverIndex >= others.size,
                    onOpen = { onOpen(t.id) }, onHold = onHold, onDrop = onDrop, modifier = Modifier.animateItem()
                )
            }
            if (col.tasks.isEmpty()) item(key = "empty") {
                Text(if (hovering) "Drop here" else "No issues", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            if (canCreate) item(key = "create") { InlineCreate(onCreate) }
        }
    }
}

@Composable
private fun ColumnHeader(col: ColumnUi, canManage: Boolean, isFirst: Boolean, isLast: Boolean, onRename: () -> Unit, onWip: () -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val wip = BoardLogic.wipState(col.totalCount, col.list.wipLimit)
    val countColor by animateColorAsState(
        when (wip) { WipState.EXCEEDED -> AppTheme.extra.danger; WipState.AT_LIMIT -> AppTheme.extra.warning; else -> MaterialTheme.colorScheme.onSurfaceVariant }, Motion.quick(), label = "wip"
    )
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(col.list.title.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Tag(if (col.list.wipLimit != null) "${col.totalCount}/${col.list.wipLimit}" else "${col.totalCount}", countColor, icon = if (wip == WipState.EXCEEDED) AppIcons.Warning else null)
        Spacer(Modifier.weight(1f))
        if (canManage) Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(AppIcons.More, "Column menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Set WIP limit") }, onClick = { menu = false; onWip() })
                if (!isFirst) DropdownMenuItem(text = { Text("Move left") }, onClick = { menu = false; onMove(-1) })
                if (!isLast) DropdownMenuItem(text = { Text("Move right") }, onClick = { menu = false; onMove(1) })
                DropdownMenuItem(text = { Text("Delete column", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/**
 * A card with the hold/drag gestures. Tap opens details. Long-press lifts the card; moving drags it; lifting the finger
 * without having moved opens the menu. Viewers (cannot move) still get the menu on long-press.
 */
@Composable
private fun DraggableCard(
    task: Task, assignee: Member?, controller: DragController, canMove: Boolean, dimmed: Boolean, lineAbove: Boolean, lineBelow: Boolean,
    onOpen: () -> Unit, onHold: (Task) -> Unit, onDrop: (DropResult?) -> Unit, modifier: Modifier = Modifier
) {
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val haptic = LocalHapticFeedback.current
    val currentTask by rememberUpdatedState(task)
    val openNow by rememberUpdatedState(onOpen)
    val holdNow by rememberUpdatedState(onHold)
    val dropNow by rememberUpdatedState(onDrop)
    Box(
        modifier
            .onGloballyPositioned { rootPos = it.positionInRoot(); size = it.size }
            .pointerInput(canMove) {
                detectTapGestures(
                    onTap = { openNow() },
                    onLongPress = if (!canMove) ({ _: Offset -> holdNow(currentTask) }) else null
                )
            }
            .pointerInput(canMove) {
                if (canMove) detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.start(currentTask, rootPos + local, local, size)
                    },
                    onDrag = { change, delta -> change.consume(); controller.dragBy(delta) },
                    onDragEnd = { dropNow(controller.finish()) },
                    onDragCancel = { controller.cancel() }
                )
            }
    ) { TaskCard(task, assignee, dimmed = dimmed, lineAbove = lineAbove, lineBelow = lineBelow) }
}

/** "+ Create issue" at the bottom of a column; expands into a text field (keeps open for fast entry). */
@Composable
private fun InlineCreate(onCreate: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun submit() { val t = text.trim(); if (t.isNotEmpty()) { onCreate(t); text = "" } }
    LaunchedEffect(expanded) { if (expanded) runCatching { focus.requestFocus() } }
    if (!expanded) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Icon(AppIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Create issue") }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().focusRequester(focus), shape = MaterialTheme.shapes.medium,
                placeholder = { Text("What needs to be done?") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() })
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { expanded = false; text = "" }) { Text("Cancel") }
                TextButton(onClick = { submit() }, enabled = text.isNotBlank()) { Text("Add") }
            }
        }
    }
}

/** Replan = set the start/due dates. Quick buttons cover the common cases; "Pick" opens a calendar. */
@Composable
internal fun ReplanDialog(task: Task, onConfirm: (Long?, Long?) -> Unit, onDismiss: () -> Unit) {
    var start by remember { mutableStateOf(task.startDate) }
    var due by remember { mutableStateOf(task.dueDate) }
    var picking by remember { mutableStateOf<String?>(null) }
    val now = remember { System.currentTimeMillis() }
    val invalid = start != null && due != null && due!! < start!!
    AlertDialog(
        onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge, title = { Text("Replan ${task.key}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DateRow("Start date", start, onPick = { picking = "start" }, onClear = { start = null })
                DateRow("Due date", due, onPick = { picking = "due" }, onClear = { due = null })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { due = BoardLogic.dayAsUtcMidnight(now, 0) }, label = { Text("Today") })
                    AssistChip(onClick = { due = BoardLogic.dayAsUtcMidnight(now, 1) }, label = { Text("Tomorrow") })
                    AssistChip(onClick = { due = BoardLogic.dayAsUtcMidnight(now, 7) }, label = { Text("Next week") })
                }
                if (invalid) Text("Due date can't be before the start date", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !invalid, onClick = { onConfirm(start, due) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking != null) DateDialog(initialMillis = if (picking == "start") start else due, allowClear = false,
        onConfirm = { v -> if (picking == "start") start = v else due = v; picking = null }, onDismiss = { picking = null })
}

@Composable
private fun DateRow(label: String, value: Long?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value?.let { ActivityFormatter.date(it.toString()) } ?: "Not set", style = MaterialTheme.typography.bodyLarge)
        }
        if (value != null) TextButton(onClick = onClear) { Text("Clear") }
        TextButton(onClick = onPick) { Text("Pick") }
    }
}

@Composable
internal fun AddColumnDialog(onConfirm: (String, ColumnCategory) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(ColumnCategory.IN_PROGRESS) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge, title = { Text("Add column") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(name, { name = it }, "Column name")
                Text("Counts as", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColumnCategory.entries.forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }) }
                }
                Text("“Done” columns count towards the board's progress.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && name.length <= 50, onClick = { onConfirm(name.trim(), cat) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Deleting a column that still has cards forces an explicit decision: move them to another column, or delete them too. */
@Composable
internal fun DeleteColumnDialog(column: ColumnUi, others: List<BoardList>, onConfirm: (String?, Boolean) -> Unit, onDismiss: () -> Unit) {
    if (others.isEmpty()) {
        ConfirmDialog("Can't delete", "A board needs at least one column.", "OK", onConfirm = onDismiss, onDismiss = onDismiss, dismissLabel = "Close")
        return
    }
    if (column.totalCount == 0) {
        ConfirmDialog("Delete “${column.list.title}”?", "The column is empty.", "Delete", danger = true, onConfirm = { onConfirm(null, false) }, onDismiss = onDismiss)
        return
    }
    var choice by remember { mutableIntStateOf(0) } // 0..others.lastIndex = move to, others.size = delete cards
    AlertDialog(
        onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge, title = { Text("Delete “${column.list.title}”?") },
        text = {
            Column {
                Text("It still has ${column.totalCount} issue(s). What should happen to them?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                others.forEachIndexed { i, l -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = choice == i, onClick = { choice = i }); Text("Move to “${l.title}”") } }
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = choice == others.size, onClick = { choice = others.size }); Text("Delete the issues too", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { if (choice < others.size) onConfirm(others[choice].id, false) else onConfirm(null, true) }) { Text("Delete column", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
