package com.google.mytaskmanager.presentation.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.ErrorKind
import com.google.mytaskmanager.core.MessageCenter
import com.google.mytaskmanager.core.UiErrorHandler
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.Member
import com.google.mytaskmanager.domain.model.Team
import com.google.mytaskmanager.domain.model.User
import com.google.mytaskmanager.domain.repository.AuthRepository
import com.google.mytaskmanager.domain.repository.CollabRepository
import com.google.mytaskmanager.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the teams tab. */
data class TeamsUiState(val loading: Boolean = true, val teams: List<Team> = emptyList())

/** Lists the user's teams and creates new ones. */
@HiltViewModel
class TeamsViewModel @Inject constructor(
    private val collab: CollabRepository,
    webSocket: WebSocketManager,
    private val messages: MessageCenter,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    private val _state = MutableStateFlow(TeamsUiState())
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { webSocket.events.filter { it.type == "board_membership_changed" }.collect { load(silent = true) } }
    }

    /** Reloads the list. */
    fun load(silent: Boolean = false) {
        launchSafe("load teams", showMessage = !silent, onError = { _state.update { it.copy(loading = false) } }) {
            _state.update { it.copy(loading = false, teams = collab.teams()) }
        }
    }

    /** Creates a team (the creator becomes its admin). */
    fun create(name: String, description: String?, onCreated: (Team) -> Unit) {
        launchSafe("create team") { val t = collab.createTeam(name, description); messages.success("Team “${t.name}” created"); load(silent = true); onCreated(t) }
    }
}

/** State of one team's page. */
data class TeamDetailUiState(val loading: Boolean = true, val team: Team? = null, val members: List<Member> = emptyList(), val suggestions: List<User> = emptyList(), val gone: Boolean = false)

/** One team: members and their roles, rename, delete / leave. Server-side rules: team admins manage people, only the owner deletes. */
@HiltViewModel
class TeamDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val collab: CollabRepository,
    auth: AuthRepository,
    private val messages: MessageCenter,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    private val teamId: String = checkNotNull(savedState["teamId"])
    private val _state = MutableStateFlow(TeamDetailUiState())
    val state: StateFlow<TeamDetailUiState> = _state.asStateFlow()
    val me: StateFlow<User?> = auth.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var searchJob: Job? = null

    init { load() }

    /** Reloads team info and members. */
    fun load() {
        launchSafe("load team", onError = { e -> _state.update { it.copy(loading = false, gone = e.kind == ErrorKind.NOT_FOUND) } }) {
            val team = collab.teams().firstOrNull { it.id == teamId }
            if (team == null) { _state.update { it.copy(loading = false, gone = true) }; return@launchSafe }
            _state.update { it.copy(loading = false, team = team, members = collab.teamMembers(teamId)) }
        }
    }

    /** Debounced username search for the invite dialog. */
    fun search(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) { _state.update { it.copy(suggestions = emptyList()) }; return }
        searchJob = viewModelScope.launch { delay(250); runCatching { collab.searchUsers(q.trim()) }.onSuccess { r -> _state.update { it.copy(suggestions = r) } } }
    }

    fun add(username: String, role: String) { launchSafe("add team member") { collab.addTeamMember(teamId, username, role); messages.success("$username added"); load() } }
    fun setRole(m: Member, role: String) { launchSafe("change team role") { collab.updateTeamMemberRole(teamId, m.userId, role); load() } }
    fun remove(m: Member) { launchSafe("remove team member") { collab.removeTeamMember(teamId, m.userId); load() } }
    fun leave() { me.value?.let { u -> launchSafe("leave team") { collab.removeTeamMember(teamId, u.id); _state.update { it.copy(gone = true) } } } }
    fun rename(name: String, description: String?) { launchSafe("rename team") { collab.updateTeam(teamId, name, description); load() } }
    fun delete() { launchSafe("delete team") { collab.deleteTeam(teamId); messages.success("Team deleted"); _state.update { it.copy(gone = true) } } }
}
