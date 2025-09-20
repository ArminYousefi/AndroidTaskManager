package com.google.mytaskmanager.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.data.auth.AuthRepository
import com.google.mytaskmanager.data.remote.websocket.WebSocketManager
import com.google.mytaskmanager.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val wsManager: WebSocketManager
) : ViewModel() {

    private val _authResult = MutableStateFlow<Result<User>?>(null)
    val authResult: StateFlow<Result<User>?> = _authResult

    val isLoggedIn: StateFlow<Boolean> =
        authRepo.isLoggedIn()
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Persisted login state from repository (reads saved tokens/preferences)
    val persistedIsLoggedIn = authRepo.isLoggedIn().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun login(username: String, password: String) {
        viewModelScope.launch {
            val res = authRepo.login(username, password)
            _authResult.value = res
            if (res.isSuccess) {
                // connect websocket after successful login
                try {
                    wsManager.setUrl("ws://10.0.2.2:8080/ws")
                    wsManager.connect()
                } catch (_: Throwable) { }
            }
        }
    }

    fun signup(username: String, password: String, email: String? = null) {
        viewModelScope.launch {
            val res = authRepo.signup(username, password, email)
            _authResult.value = res
            if (res.isSuccess) {
                try {
                    wsManager.setUrl("ws://10.0.2.2:8080/ws")
                    wsManager.connect()
                } catch (_: Throwable) { }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepo.logout()
            } finally {
                try { wsManager.disconnect() } catch (_: Throwable) {}
            }
            _authResult.value = null
        }
    }
}
