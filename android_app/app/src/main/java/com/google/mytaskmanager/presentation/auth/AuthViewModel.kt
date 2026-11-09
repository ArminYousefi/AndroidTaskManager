package com.google.mytaskmanager.presentation.auth

import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.UiErrorHandler
import com.google.mytaskmanager.data.local.settings.SettingsRepository
import com.google.mytaskmanager.domain.repository.AuthRepository
import com.google.mytaskmanager.presentation.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the login / sign-up forms. [error] is shown inline in the form (not as a snackbar). */
data class AuthUiState(val loading: Boolean = false, val error: String? = null)

/**
 * Drives login and sign-up. On success the stored session flips `AuthRepository.isLoggedIn`, and the root navigation
 * moves to the main screen by itself - this ViewModel never navigates.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    errors: UiErrorHandler
) : BaseViewModel(errors) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Signs in. */
    fun login(username: String, password: String) = submit("login") { auth.login(username, password) }

    /** Creates an account and signs in. */
    fun signup(username: String, password: String, email: String?, nickname: String?) = submit("sign up") { auth.signup(username, password, email, nickname) }

    /** Hides a stale error when the user edits a field. */
    fun clearError() { _state.update { it.copy(error = null) } }

    /** Remembers that the intro was seen so later logouts go straight to login. */
    fun markOnboardingSeen() { viewModelScope.launch { settings.setOnboardingSeen() } }

    private fun submit(what: String, block: suspend () -> Unit) {
        if (_state.value.loading) return
        _state.update { AuthUiState(loading = true) }
        launchSafe(what, showMessage = false, onError = { e -> _state.update { AuthUiState(error = e.userMessage) } }) {
            block()
            _state.update { AuthUiState() }
        }
    }
}
