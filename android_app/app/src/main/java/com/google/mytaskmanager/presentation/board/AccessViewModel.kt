package com.google.mytaskmanager.presentation.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.MessageCenter
import com.google.mytaskmanager.core.UiErrorHandler
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.BoardTeam
import com.google.mytaskmanager.domain.model.Member
import com.google.mytaskmanager.domain.model.Team
import com.google.mytaskmanager.domain.model.User
import com.google.mytaskmanager.domain.repository.CollabRepository
import com.google.mytaskmanager.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Who has access to the board, through which route. */
data class AccessUiState(
    val loading: Boolean = true, val members: List<Member> = emptyList(), val teams: List<BoardTeam> = emptyList(),
    val myTeams: List<Team> = emptyList(), val suggestions: List<User> = emptyList()
)

/** Manages board access: people (direct) and teams, each with Viewer / Member / Admin. All calls are authorised by the server. */
@HiltViewModel
class AccessViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val collab: CollabRepository,
    webSocket: WebSocketManager,
    private val messages: MessageCenter,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    private val boardId: String = checkNotNull(savedState["boardId"])
    private val _state = MutableStateFlow(AccessUiState())
    val state: StateFlow<AccessUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        load()
        viewModelScope.launch { webSocket.events.filter { it.boardId == boardId && it.entity == "member" }.collect { load(silent = true) } }
    }

    /** Reloads members, attached teams and the user's own teams (candidates to attach). */
    fun load(silent: Boolean = false) {
        launchSafe("load access", showMessage = !silent, onError = { _state.update { it.copy(loading = false) } }) {
            val m = collab.boardMembers(boardId)
            val t = collab.boardTeams(boardId)
            val mine = runCatching { collab.teams() }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, members = m, teams = t, myTeams = mine.filter { team -> t.none { bt -> bt.teamId == team.id } }) }
        }
    }

    /** Debounced user search for the invite dialog. */
    fun search(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) { _state.update { it.copy(suggestions = emptyList()) }; return }
        searchJob = viewModelScope.launch {
            delay(250)
            runCatching { collab.searchUsers(q.trim()) }.onSuccess { r -> _state.update { it.copy(suggestions = r) } }
        }
    }

    fun addMember(username: String, role: String) { launchSafe("add member") { collab.addBoardMember(boardId, username, role); messages.success("$username added"); load(silent = true) } }
    fun setRole(m: Member, role: String) { launchSafe("change role") { collab.updateBoardMemberRole(boardId, m.userId, role); load(silent = true) } }
    fun remove(m: Member) { launchSafe("remove member") { collab.removeBoardMember(boardId, m.userId); messages.success("${m.displayName} removed"); load(silent = true) } }
    fun leave(userId: String, onLeft: () -> Unit) { launchSafe("leave board") { collab.removeBoardMember(boardId, userId); onLeft() } }
    fun addTeam(teamId: String, role: String) { launchSafe("add team") { collab.addBoardTeam(boardId, teamId, role); load(silent = true) } }
    fun setTeamRole(t: BoardTeam, role: String) { launchSafe("change team role") { collab.updateBoardTeamRole(boardId, t.teamId, role); load(silent = true) } }
    fun removeTeam(t: BoardTeam) { launchSafe("remove team") { collab.removeBoardTeam(boardId, t.teamId); load(silent = true) } }
}
