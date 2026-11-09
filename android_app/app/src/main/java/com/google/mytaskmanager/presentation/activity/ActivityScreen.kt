package com.google.mytaskmanager.presentation.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.UiErrorHandler
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.Activity
import com.google.mytaskmanager.domain.repository.CollabRepository
import com.google.mytaskmanager.presentation.common.BaseViewModel
import com.google.mytaskmanager.presentation.logic.ActivityFormatter
import com.google.mytaskmanager.ui.components.Avatar
import com.google.mytaskmanager.ui.components.AppTopBar
import com.google.mytaskmanager.ui.components.EmptyState
import com.google.mytaskmanager.ui.components.ShimmerBox
import com.google.mytaskmanager.ui.icons.AppIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Filter groups of the activity log. */
enum class ActivityGroup(val label: String, val prefixes: List<String>) {
    ALL("All", emptyList()),
    TASKS("Tasks", listOf("TASK_", "COMMENT_")),
    COLUMNS("Board & columns", listOf("BOARD_", "COLUMN_")),
    PEOPLE("People", listOf("MEMBER_", "TEAM_"));

    /** @return true when [a] belongs to this group. */
    fun matches(a: Activity) = prefixes.isEmpty() || prefixes.any { a.rawType.startsWith(it) }
}

/** State of the activity screen. */
data class ActivityUiState(val loading: Boolean = true, val items: List<Activity> = emptyList(), val canLoadMore: Boolean = false, val loadingMore: Boolean = false, val group: ActivityGroup = ActivityGroup.ALL)

/** Loads the board's audit log (newest first, paged) and refreshes it live when the server pushes `activity_created`. */
@OptIn(FlowPreview::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val collab: CollabRepository,
    webSocket: WebSocketManager,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    private val boardId: String = checkNotNull(savedState["boardId"])
    private val pageSize = 50
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { webSocket.events.filter { it.boardId == boardId && it.type == "activity_created" }.debounce(400).collect { load(silent = true) } }
    }

    /** (Re)loads the first page. */
    fun load(silent: Boolean = false) {
        launchSafe("load activity", showMessage = !silent, onError = { _state.update { it.copy(loading = false) } }) {
            val page = collab.boardActivity(boardId, null, pageSize)
            _state.update { it.copy(loading = false, items = page, canLoadMore = page.size == pageSize) }
        }
    }

    /** Appends the next (older) page. */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.canLoadMore || s.items.isEmpty()) return
        _state.update { it.copy(loadingMore = true) }
        launchSafe("load more activity", onError = { _state.update { it.copy(loadingMore = false) } }) {
            val page = collab.boardActivity(boardId, s.items.last().createdAt, pageSize)
            _state.update { it.copy(items = it.items + page, canLoadMore = page.size == pageSize, loadingMore = false) }
        }
    }

    fun setGroup(g: ActivityGroup) { _state.update { it.copy(group = g) } }
}

/**
 * The board's history: who did what and when, grouped by day. Every rename, move, replan, delete, column change and
 * membership change from every user shows up here, live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onBack: () -> Unit, onOpenTask: (String) -> Unit, vm: ActivityViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val now = remember(state.items) { System.currentTimeMillis() }
    val shown = remember(state.items, state.group) { state.items.filter { state.group.matches(it) } }
    Scaffold(topBar = { AppTopBar("Activity", onBack = onBack, subtitle = "Everything that happened on this board") }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityGroup.entries.forEach { g -> FilterChip(selected = state.group == g, onClick = { vm.setGroup(g) }, label = { Text(g.label) }) }
            }
            when {
                state.loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(6) { ShimmerBox(Modifier.fillMaxWidth().height(56.dp)) } }
                shown.isEmpty() -> EmptyState(AppIcons.Activity, "Nothing here yet", "Changes made on this board will appear here.")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    var lastDay: String? = null
                    shown.forEach { a ->
                        val day = ActivityFormatter.dayLabel(a.createdAt, now)
                        if (day != lastDay) { lastDay = day; item(key = "day-$day-${a.id}") { Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) } }
                        item(key = a.id) { ActivityRow(a, now, onClick = if (a.taskId != null && a.rawType != "TASK_DELETED") ({ onOpenTask(a.taskId) }) else null) }
                    }
                    if (state.canLoadMore) item(key = "more") {
                        TextButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text(if (state.loadingMore) "Loading…" else "Load older activity") }
                    }
                }
            }
        }
    }
}

/** One log line: avatar, "**Actor** did something", relative time. Reused by the task history tab. */
@Composable
fun ActivityRow(a: Activity, now: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top
    ) {
        Avatar(a.actorName, size = 34.dp)
        Column(Modifier.weight(1f)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(a.actorName) }
                append(" "); append(ActivityFormatter.describe(a))
            }, style = MaterialTheme.typography.bodyMedium)
            Text(ActivityFormatter.relativeTime(a.createdAt, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
