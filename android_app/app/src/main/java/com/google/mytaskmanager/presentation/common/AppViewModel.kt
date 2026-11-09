package com.google.mytaskmanager.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.MessageCenter
import com.google.mytaskmanager.core.UiErrorHandler
import com.google.mytaskmanager.data.local.settings.SettingsRepository
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.ThemeMode
import com.google.mytaskmanager.domain.repository.AuthRepository
import com.google.mytaskmanager.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-scoped state shared by the whole app: theme, session, global messages and the live connection.
 * It also owns the lifecycle of the WebSocket (connected while signed in) and flushes queued offline changes
 * every time the connection comes back.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    auth: AuthRepository,
    messageCenter: MessageCenter,
    private val webSocket: WebSocketManager,
    private val tasks: TaskRepository,
    private val errors: UiErrorHandler,
    private val reporter: com.google.mytaskmanager.core.ErrorReporter
) : ViewModel() {

    /** Remembers the current screen name so crash reports say where the user was. */
    fun onScreen(route: String?) { reporter.currentScreen = route }

    /** Selected theme; the UI applies it instantly. */
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTOMATIC)

    /** `null` until the stored session has been read (the UI shows a splash), then true/false. */
    val isLoggedIn: StateFlow<Boolean?> = auth.isLoggedIn.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether the intro slides were seen (decides login vs onboarding for signed-out users). */
    val onboardingSeen: StateFlow<Boolean?> = settings.onboardingSeen.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Global snackbar messages. */
    val messages: SharedFlow<com.google.mytaskmanager.core.UiMessage> = messageCenter.messages

    /** True while the live connection is up. */
    val online: StateFlow<Boolean> = webSocket.connected

    /** Number of offline changes waiting to be sent. */
    val pendingChanges: StateFlow<Int> = tasks.observePendingCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            auth.isLoggedIn.distinctUntilChanged().collect { loggedIn -> if (loggedIn) webSocket.connect() else webSocket.disconnect() }
        }
        viewModelScope.launch {
            webSocket.connected.filter { it }.collect {
                try { tasks.flushPending(); tasks.refreshBoards() } catch (t: Throwable) { errors.handle(t, "reconnect sync", show = false) }
            }
        }
    }
}
