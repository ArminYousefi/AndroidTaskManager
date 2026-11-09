package com.google.mytaskmanager.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mytaskmanager.core.AppException
import com.google.mytaskmanager.core.UiErrorHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Base class for ViewModels. [launchSafe] guarantees that an exception inside a coroutine can never crash the app:
 * it is routed through [UiErrorHandler] (log + developer report + snackbar) and optionally handed to the caller.
 */
abstract class BaseViewModel(protected val errors: UiErrorHandler) : ViewModel() {

    /**
     * Launches [block] in `viewModelScope` with uniform error handling.
     *
     * @param what short description for logs ("rename task").
     * @param showMessage show a snackbar on failure (default true).
     * @param onError called with the converted exception, e.g. to roll back UI state or show inline errors.
     */
    protected fun launchSafe(
        what: String,
        showMessage: Boolean = true,
        onError: (AppException) -> Unit = {},
        block: suspend () -> Unit
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onError(errors.handle(t, what, showMessage))
        }
    }
}
