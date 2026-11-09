package com.google.mytaskmanager.presentation.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.ErrorKind
import com.google.mytaskmanager.core.MessageCenter
import com.google.mytaskmanager.core.UiErrorHandler
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.*
import com.google.mytaskmanager.domain.repository.AuthRepository
import com.google.mytaskmanager.domain.repository.CollabRepository
import com.google.mytaskmanager.domain.repository.TaskRepository
import com.google.mytaskmanager.domain.rules.ColumnCategory
import com.google.mytaskmanager.domain.rules.Ordering
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.presentation.common.BaseViewModel
import com.google.mytaskmanager.presentation.logic.BoardFilter
import com.google.mytaskmanager.presentation.logic.BoardLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One rendered column: [tasks] are the filtered cards, [totalCount] is the real count used for the WIP limit. */
data class ColumnUi(val list: BoardList, val tasks: List<Task>, val totalCount: Int)

/** Screen state besides the data flows. [gone] becomes true when the board was deleted or access was revoked. */
data class BoardUiState(val loading: Boolean = true, val refreshing: Boolean = false, val filter: BoardFilter = BoardFilter(), val gone: Boolean = false)

/**
 * Everything the board screen does. Task changes are optimistic (the card moves instantly) and rolled back with an
 * explanation if the server refuses; column/board changes go through the server first.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class BoardViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val collab: CollabRepository,
    auth: AuthRepository,
    webSocket: WebSocketManager,
    private val messages: MessageCenter,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    /** Id from the navigation argument. */
    val boardId: String = checkNotNull(savedState["boardId"])

    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    val board: StateFlow<Board?> = tasks.observeBoard(boardId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val lists: StateFlow<List<BoardList>> = tasks.observeLists(boardId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val allTasks: StateFlow<List<Task>> = tasks.observeTasks(boardId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Signed-in user's id (for "only my issues" and "assign to me"). */
    val meId: StateFlow<String?> = auth.currentUser.map { it?.id }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _members = MutableStateFlow<List<Member>>(emptyList())

    /** Everyone with access, one entry per person (strongest role wins), for avatars and assignee names. */
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    /** Columns left to right with their (filtered) cards. */
    val columns: StateFlow<List<ColumnUi>> = combine(lists, allTasks, _state, meId) { ls, ts, s, me ->
        val byList = ts.groupBy { it.listId }
        ls.sortedBy { it.position }.map { l ->
            val all = byList[l.id].orEmpty().sortedBy { it.position }
            ColumnUi(l, BoardLogic.apply(all, s.filter, me), all.size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh(initial = true)
        loadMembers()
        viewModelScope.launch {
            webSocket.events.filter { it.boardId == boardId && it.entity != "activity" }.debounce(300).collect { e ->
                runCatching { tasks.refreshBoard(boardId) }.onFailure { if ((it as? com.google.mytaskmanager.core.AppException)?.kind == ErrorKind.NOT_FOUND) _state.update { s -> s.copy(gone = true) } }
                if (e.entity == "member" || e.type == "board_updated") { runCatching { tasks.refreshBoards() }; loadMembers() }
            }
        }
        viewModelScope.launch {
            webSocket.events.filter { it.type == "board_deleted" && it.boardId == boardId }.collect { _state.update { s -> s.copy(gone = true) } }
        }
    }

    private fun loadMembers() {
        launchSafe("load members", showMessage = false) {
            _members.value = collab.boardMembers(boardId).distinctBy { it.userId }
        }
    }

    /** Reloads the board from the server. */
    fun refresh(initial: Boolean = false) {
        _state.update { it.copy(refreshing = !initial) }
        launchSafe("load board", showMessage = !initial, onError = { e ->
            _state.update { s -> s.copy(loading = false, refreshing = false, gone = e.kind == ErrorKind.NOT_FOUND) }
        }) {
            if (board.value == null) tasks.refreshBoards()
            tasks.refreshBoard(boardId)
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }

    // ---------------------------------------------------------------- filters
    fun setQuery(q: String) = _state.update { it.copy(filter = it.filter.copy(query = q)) }
    fun toggleOnlyMine() = _state.update { it.copy(filter = it.filter.copy(onlyMine = !it.filter.onlyMine)) }
    fun toggleRecent() = _state.update { it.copy(filter = it.filter.copy(recentlyUpdated = !it.filter.recentlyUpdated)) }
    fun toggleOverdue() = _state.update { it.copy(filter = it.filter.copy(overdue = !it.filter.overdue)) }
    fun clearFilters() = _state.update { it.copy(filter = BoardFilter()) }

    // ---------------------------------------------------------------- tasks
    /** Quick-create at the bottom of a column. */
    fun createTask(listId: String, title: String) { launchSafe("create task") { tasks.createTask(boardId, listId, title) } }

    /** Rename (logged as TASK_RENAMED on the server). */
    fun renameTask(task: Task, title: String) { if (title != task.title) launchSafe("rename task") { tasks.updateTask(task.copy(title = title)) } }

    /** Replan = change start/due date (logged as TASK_REPLANNED). */
    fun replanTask(task: Task, startDate: Long?, dueDate: Long?) { launchSafe("replan task") { tasks.updateTask(task.copy(startDate = startDate, dueDate = dueDate)); messages.success("${task.key} replanned") } }

    /** Change priority. */
    fun setPriority(task: Task, priority: TaskPriority) { launchSafe("change priority") { tasks.updateTask(task.copy(priority = priority)) } }

    /** Assign to [userId] (null = unassign). */
    fun assign(task: Task, userId: String?) { launchSafe("assign task") { tasks.updateTask(task.copy(assigneeId = userId)) } }

    /** Delete (logged as TASK_DELETED). */
    fun deleteTask(task: Task) { launchSafe("delete task") { tasks.deleteTask(task.id); messages.info("${task.key} deleted") } }

    /**
     * Result of a drag & drop. [visibleIndex] is the insertion index among the *visible* cards of the target column
     * (without the dragged card); it is converted to a real index when a filter hides some cards.
     */
    fun dropTask(taskId: String, toListId: String, visibleIndex: Int) {
        val col = columns.value.firstOrNull { it.list.id == toListId } ?: return
        val allIds = allTasks.value.filter { it.listId == toListId && it.id != taskId }.sortedBy { it.position }.map { it.id }
        val visibleIds = col.tasks.filter { it.id != taskId }.map { it.id }
        val index = BoardLogic.realIndex(allIds, visibleIds, visibleIndex)
        moveTask(taskId, toListId, index, col.list)
    }

    /** Move via the menu ("Move to...") - always to the bottom of the column. */
    fun moveToColumn(task: Task, list: BoardList) {
        val size = allTasks.value.count { it.listId == list.id && it.id != task.id }
        moveTask(task.id, list.id, size, list)
    }

    private fun moveTask(taskId: String, toListId: String, index: Int, target: BoardList) {
        val before = allTasks.value.firstOrNull { it.id == taskId }
        launchSafe("move task") {
            tasks.moveTask(taskId, toListId, index)
            val count = allTasks.value.count { it.listId == toListId }
            if (before != null && before.listId != toListId && target.wipLimit != null && count > target.wipLimit) {
                messages.info("“${target.title}” is over its limit of ${target.wipLimit}")
            }
        }
    }

    // ---------------------------------------------------------------- columns (admin)
    fun addColumn(title: String, category: ColumnCategory) { launchSafe("add column") { tasks.createList(boardId, title, category, null) } }
    fun renameColumn(list: BoardList, title: String) { launchSafe("rename column") { tasks.updateList(list.copy(title = title)) } }
    fun setWipLimit(list: BoardList, limit: Int?) { launchSafe("set WIP limit") { tasks.updateList(list.copy(wipLimit = limit)) } }
    fun setColumnCategory(list: BoardList, category: ColumnCategory) { launchSafe("change column category") { tasks.updateList(list.copy(category = category)) } }

    /** Moves a column one step left (-1) or right (+1). */
    fun moveColumn(list: BoardList, direction: Int) {
        val ids = columns.value.map { it.list.id }
        val from = ids.indexOf(list.id)
        val to = from + direction
        if (from < 0 || to !in ids.indices) return
        launchSafe("reorder columns") { tasks.reorderLists(boardId, Ordering.moveTo(ids, list.id, to)) }
    }

    /** Deletes a column; its cards move to [moveTo] or are deleted with it ([deleteTasks]). */
    fun deleteColumn(list: BoardList, moveTo: String?, deleteTasks: Boolean) { launchSafe("delete column") { tasks.deleteList(list.id, moveTo, deleteTasks) } }

    // ---------------------------------------------------------------- board-level (admin)
    /** Renames the board / edits its description. */
    fun updateBoard(name: String, description: String?) { launchSafe("update board") { tasks.updateBoard(boardId, name, description); messages.success("Board updated") } }

    /** Deletes every task of the board. */
    fun emptyBoard() { launchSafe("empty board") { val n = tasks.emptyBoard(boardId); messages.success("Removed $n tasks") } }

    /** Deletes the whole board and leaves the screen. */
    fun deleteBoard() { launchSafe("delete board") { tasks.deleteBoard(boardId); messages.success("Board deleted"); _state.update { it.copy(gone = true) } } }
}
