package com.google.mytaskmanager.core

import com.google.mytaskmanager.util.LogUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place where an error becomes (a) a log line, (b) a developer report when it is a bug, and
 * (c) a friendly message for the user. ViewModels call [handle] and never decide any of this themselves.
 */
@Singleton
class UiErrorHandler @Inject constructor(
    private val messages: MessageCenter,
    private val reporter: ErrorReporter
) {
    /**
     * @param t the failure (any type; converted with [toAppException]).
     * @param context short label of what was being done, e.g. "move task". Used in logs/reports.
     * @param show when false the snackbar is skipped (the screen shows the error inline instead).
     * @return the converted [AppException] so the caller can branch on `kind`/`code`.
     */
    fun handle(t: Throwable, context: String, show: Boolean = true): AppException {
        val e = t.toAppException()
        LogUtil.e("UiError", "$context failed: ${e.kind}/${e.code}", if (e.kind.reportable) e else null)
        if (e.kind.reportable) reporter.report(e, note = context)
        if (show) messages.error(e)
        return e
    }
}
