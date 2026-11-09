package com.google.mytaskmanager.presentation.task

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
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.domain.rules.TaskType
import com.google.mytaskmanager.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Online-only parts of the issue view (comments, history) plus transient flags. */
data class TaskDetailUiState(
    val comments: List<Comment> = emptyList(), val history: List<Activity> = emptyList(),
    val sending: Boolean = false, val gone: Boolean = false, val loadingExtras: Boolean = true
)

/** Backs the issue screen: every edit goes through the same optimistic repository calls as the board. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val collab: CollabRepository,
    auth: AuthRepository,
    webSocket: WebSocketManager,
    private val messages: MessageCenter,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    private val taskId: String = checkNotNull(savedState["taskId"])
    private val _state = MutableStateFlow(TaskDetailUiState())
    val state: StateFlow<TaskDetailUiState> = _state.asStateFlow()

    val task: StateFlow<Task?> = tasks.observeTask(taskId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val board: StateFlow<Board?> = task.map { it?.boardId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else tasks.observeBoard(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lists: StateFlow<List<BoardList>> = task.map { it?.boardId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else tasks.observeLists(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val me: StateFlow<User?> = auth.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    /** People who can be assigned (everyone with access to the board). */
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    init {
        loadExtras()
        viewModelScope.launch {
            task.filterNotNull().map { it.boardId }.distinctUntilChanged().collect { id ->
                runCatching { _members.value = collab.boardMembers(id).distinctBy { it.userId } }
            }
        }
        viewModelScope.launch {
            webSocket.events.filter { it.boardId != null && it.boardId == task.value?.boardId }.debounce(400).collect {
                loadExtras(silent = true); runCatching { tasks.refreshBoard(it.boardId!!) }
            }
        }
    }

    /** Loads comments and this task's history. */
    fun loadExtras(silent: Boolean = false) {
        launchSafe("load issue details", showMessage = !silent, onError = { e ->
            _state.update { it.copy(loadingExtras = false, gone = e.kind == ErrorKind.NOT_FOUND) }
        }) {
            val c = collab.comments(taskId)
            val h = collab.taskActivity(taskId, null, 50)
            _state.update { it.copy(comments = c, history = h, loadingExtras = false) }
        }
    }

    private fun edit(what: String, change: (Task) -> Task) { task.value?.let { t -> launchSafe(what) { tasks.updateTask(change(t)) } } }
    fun rename(title: String) = edit("rename task") { it.copy(title = title) }
    fun setDescription(text: String) = edit("edit description") { it.copy(description = text.ifBlank { null }) }
    fun setPriority(p: TaskPriority) = edit("change priority") { it.copy(priority = p) }
    fun setType(t: TaskType) = edit("change type") { it.copy(type = t) }
    fun assign(userId: String?) = edit("assign task") { it.copy(assigneeId = userId) }
    fun replan(start: Long?, due: Long?) = edit("replan task") { it.copy(startDate = start, dueDate = due) }
    fun setLabels(labels: List<String>) = edit("edit labels") { it.copy(labels = labels) }

    /** Status transition: moves the issue to another column (bottom). */
    fun moveTo(list: BoardList) { task.value?.let { t -> if (t.listId != list.id) launchSafe("move task") { tasks.moveToColumnEnd(t, list) } } }

    /** Deletes the issue and leaves the screen. */
    fun delete() { task.value?.let { t -> launchSafe("delete task") { tasks.deleteTask(t.id); messages.info("${t.key} deleted"); _state.update { it.copy(gone = true) } } } }

    /** Posts a comment (online only). */
    fun addComment(text: String, onSent: () -> Unit) {
        _state.update { it.copy(sending = true) }
        launchSafe("add comment", onError = { _state.update { s -> s.copy(sending = false) } }) {
            val c = collab.addComment(taskId, text)
            _state.update { it.copy(comments = it.comments + c, sending = false) }
            onSent()
        }
    }

    /** Deletes a comment (author or board admin). */
    fun deleteComment(c: Comment) {
        launchSafe("delete comment") { collab.deleteComment(taskId, c.id); _state.update { it.copy(comments = it.comments - c) } }
    }
}

/** Convenience used by the issue screen: move to the end of [list]. */
private suspend fun TaskRepository.moveToColumnEnd(t: Task, list: BoardList) {
    val size = observeTasks(t.boardId).first().count { it.listId == list.id && it.id != t.id }
    moveTask(t.id, list.id, size)
}
