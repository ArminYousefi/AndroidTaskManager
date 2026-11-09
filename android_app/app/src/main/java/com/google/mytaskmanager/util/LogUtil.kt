package com.google.mytaskmanager.util

import android.util.Log

/**
 * Logging facade. Besides writing to Logcat it keeps the last [MAX] lines in memory ("breadcrumbs") so a crash or
 * error report can include what the app was doing just before. Never log secrets (tokens, passwords) through it.
 */
object LogUtil {
    private const val TAG = "MyTaskManager"
    private const val MAX = 40
    private val crumbs = ArrayDeque<String>()

    private fun crumb(level: String, tag: String, msg: String?) {
        synchronized(crumbs) {
            if (crumbs.size >= MAX) crumbs.removeFirst()
            crumbs.addLast("${System.currentTimeMillis() % 100_000_000} $level/$tag: ${msg?.take(200)}")
        }
    }

    /** @return the most recent log lines, oldest first, one per line. */
    fun breadcrumbs(): String = synchronized(crumbs) { crumbs.joinToString("\n") }

    /** Logs an error. */
    fun e(tag: String, msg: String?, tr: Throwable? = null) { Log.e("$TAG:$tag", msg ?: "null", tr); crumb("E", tag, msg) }

    /** Logs a warning. */
    fun w(tag: String, msg: String?) { Log.w("$TAG:$tag", msg ?: "null"); crumb("W", tag, msg) }

    /** Logs an info line. */
    fun i(tag: String, msg: String?) { Log.i("$TAG:$tag", msg ?: "null"); crumb("I", tag, msg) }
}
