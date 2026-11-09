package com.google.mytaskmanager.core

/** Broad category of a failure. The UI decides *how* to react (retry, re-login, show field errors) from this. */
enum class ErrorKind {
    /** No connectivity / DNS / connection refused. */
    NETWORK,

    /** The server did not answer in time. */
    TIMEOUT,

    /** Not signed in or the session expired (HTTP 401). */
    UNAUTHORIZED,

    /** Signed in but not allowed (HTTP 403). */
    FORBIDDEN,

    /** Resource missing or invisible to the user (HTTP 404). */
    NOT_FOUND,

    /** Input rejected (HTTP 400). [AppException.details] may name the failed fields/rules. */
    VALIDATION,

    /** State conflict, e.g. duplicate or non-empty column (HTTP 409). */
    CONFLICT,

    /** Too many requests (HTTP 429). */
    RATE_LIMITED,

    /** The backend failed (HTTP 5xx). Reported to developers automatically. */
    SERVER,

    /** Anything unexpected, including bugs. Reported to developers automatically. */
    UNKNOWN;

    /** True when repeating the same request later may succeed (so an offline change is queued instead of dropped). */
    val retryable: Boolean get() = this == NETWORK || this == TIMEOUT || this == SERVER || this == RATE_LIMITED

    /** True when the failure is worth sending to developers (it indicates a bug rather than user error). */
    val reportable: Boolean get() = this == SERVER || this == UNKNOWN

    companion object {
        /** @return the kind for an HTTP status code. */
        fun fromHttp(status: Int): ErrorKind = when (status) {
            400, 422 -> VALIDATION
            401 -> UNAUTHORIZED
            403 -> FORBIDDEN
            404 -> NOT_FOUND
            408 -> TIMEOUT
            409 -> CONFLICT
            429 -> RATE_LIMITED
            in 500..599 -> SERVER
            else -> UNKNOWN
        }
    }
}

/**
 * The single exception type that leaves the data layer. Everything (HTTP errors, I/O errors, malformed
 * responses) is converted to it by [toAppException], so ViewModels never deal with Retrofit or OkHttp types.
 *
 * @property kind category, see [ErrorKind].
 * @property code stable server code such as `INVALID_CURRENT_PASSWORD` or a local one such as `OFFLINE`.
 * @property userMessage text that is safe to show to the user as is.
 * @property details field-level info from the server (e.g. failed password rules).
 * @property requestId server request id, quoted in bug reports so developers can find the log line.
 */
class AppException(
    val kind: ErrorKind,
    val code: String,
    val userMessage: String,
    val details: Map<String, String> = emptyMap(),
    val requestId: String? = null,
    cause: Throwable? = null
) : Exception(userMessage, cause)

/** Turns an error category (+ optional server text) into a friendly, actionable sentence. */
object ErrorMessages {
    /**
     * @param kind the failure category.
     * @param serverMessage the server's own message, preferred for errors the user can fix (validation, conflict, ...).
     * @return the text to show.
     */
    fun userMessage(kind: ErrorKind, serverMessage: String?): String {
        val server = serverMessage?.takeIf { it.isNotBlank() }
        return when (kind) {
            ErrorKind.NETWORK -> "No connection. Check your internet and try again."
            ErrorKind.TIMEOUT -> "The server is taking too long to respond. Please try again."
            ErrorKind.UNAUTHORIZED -> server ?: "Your session has expired. Please sign in again."
            ErrorKind.FORBIDDEN -> server ?: "You don't have permission to do that."
            ErrorKind.NOT_FOUND -> server ?: "That item no longer exists."
            ErrorKind.VALIDATION -> server ?: "Some of the information isn't valid."
            ErrorKind.CONFLICT -> server ?: "That conflicts with the current state. Refresh and try again."
            ErrorKind.RATE_LIMITED -> server ?: "Too many attempts. Please wait a moment."
            ErrorKind.SERVER -> "Something went wrong on our side. We've been notified."
            ErrorKind.UNKNOWN -> "Something went wrong. We've been notified."
        }
    }
}
