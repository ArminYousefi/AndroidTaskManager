package com.google.mytaskmanager.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Visual style of a [UiMessage]. */
enum class MessageKind { INFO, SUCCESS, ERROR }

/**
 * A transient message for the global snackbar.
 *
 * @property text what to show.
 * @property kind style/colour.
 * @property detail optional small print, e.g. "Ref: 3f2a..." so a user can quote it to support.
 */
data class UiMessage(val text: String, val kind: MessageKind = MessageKind.INFO, val detail: String? = null)

/**
 * App-wide channel for feedback. Any layer can post; the root scaffold shows it. This is what makes error handling
 * uniform: a failed request anywhere ends up as the same friendly snackbar.
 */
@Singleton
class MessageCenter @Inject constructor() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)

    /** Stream consumed by the root UI. */
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Posts [message]; never suspends or fails (drops the oldest if the UI is not collecting). */
    fun show(message: UiMessage) { _messages.tryEmit(message) }

    /** Convenience for a plain success toast. */
    fun success(text: String) = show(UiMessage(text, MessageKind.SUCCESS))

    /** Convenience for an informational message. */
    fun info(text: String) = show(UiMessage(text, MessageKind.INFO))

    /** Shows [e] to the user (with the request id as small print when the server provided one). */
    fun error(e: AppException) = show(UiMessage(e.userMessage, MessageKind.ERROR, e.requestId?.let { "Ref: ${it.take(8)}" }))
}
