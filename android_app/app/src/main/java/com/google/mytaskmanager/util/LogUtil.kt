package com.google.mytaskmanager.util

import android.util.Log

object LogUtil {
    private const val TAG = "MyTaskManager"
    fun e(tag: String, msg: String?, tr: Throwable? = null) {
        Log.e("$TAG:$tag", msg ?: "null", tr)
    }
    fun w(tag: String, msg: String?) { Log.w("$TAG:$tag", msg ?: "null") }
    fun i(tag: String, msg: String?) { Log.i("$TAG:$tag", msg ?: "null") }
}
