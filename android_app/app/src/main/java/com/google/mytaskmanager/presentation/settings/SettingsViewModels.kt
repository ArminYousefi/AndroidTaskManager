package com.google.mytaskmanager.presentation.settings

import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.MessageCenter
import com.google.mytaskmanager.core.UiErrorHandler
import com.google.mytaskmanager.data.local.settings.SettingsRepository
import com.google.mytaskmanager.domain.model.ThemeMode
import com.google.mytaskmanager.domain.model.User
import com.google.mytaskmanager.domain.repository.AuthRepository
import com.google.mytaskmanager.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Transient UI state of the settings screen. */
data class SettingsUiState(val savingNickname: Boolean = false, val nicknameError: String? = null, val loggingOut: Boolean = false)

/** Profile, appearance, privacy and sign-out. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val messages: MessageCenter,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    val user: StateFlow<User?> = auth.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val theme: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.AUTOMATIC)
    val diagnostics: StateFlow<Boolean> = settings.diagnosticsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { launchSafe("refresh profile", showMessage = false) { auth.refreshProfile() } }

    /** Applies the theme immediately (the whole app re-composes) and remembers it. */
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    /** Turns developer crash/error reporting on or off. */
    fun setDiagnostics(enabled: Boolean) { viewModelScope.launch { settings.setDiagnosticsEnabled(enabled) } }

    /** Saves the nickname; [onSaved] runs on success so the dialog can close, errors stay inline in the dialog. */
    fun saveNickname(nickname: String, onSaved: () -> Unit) {
        _state.update { it.copy(savingNickname = true, nicknameError = null) }
        launchSafe("update nickname", showMessage = false,
            onError = { e -> _state.update { it.copy(savingNickname = false, nicknameError = e.userMessage) } }) {
            auth.updateNickname(nickname)
            _state.update { it.copy(savingNickname = false) }
            messages.success("Nickname updated")
            onSaved()
        }
    }

    /** Clears an inline nickname error when the dialog is reopened. */
    fun resetNicknameError() { _state.update { it.copy(nicknameError = null) } }

    /** Signs out. Navigation to the login screen happens automatically when the session disappears. */
    fun logout() {
        _state.update { it.copy(loggingOut = true) }
        launchSafe("logout", onError = { _state.update { s -> s.copy(loggingOut = false) } }) { auth.logout() }
    }
}

/** Form state of the change-password screen. [currentError] belongs under the "current password" field. */
data class ChangePasswordUiState(val loading: Boolean = false, val currentError: String? = null, val error: String? = null)

/** Verifies the current password and applies the new one. */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val messages: MessageCenter,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    val user: StateFlow<User?> = auth.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    /** Hides stale errors while the user edits. */
    fun clearErrors() { _state.update { it.copy(currentError = null, error = null) } }

    /** Sends the change; [onSuccess] navigates back. A wrong current password is shown under that field. */
    fun submit(current: String, new: String, onSuccess: () -> Unit) {
        if (_state.value.loading) return
        _state.update { ChangePasswordUiState(loading = true) }
        launchSafe("change password", showMessage = false, onError = { e ->
            _state.update {
                if (e.code == "INVALID_CURRENT_PASSWORD") ChangePasswordUiState(currentError = e.userMessage) else ChangePasswordUiState(error = e.userMessage)
            }
        }) {
            auth.changePassword(current, new)
            _state.update { ChangePasswordUiState() }
            messages.success("Password changed. Other devices were signed out.")
            onSuccess()
        }
    }
}
