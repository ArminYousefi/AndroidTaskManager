package com.example

object logging {
    fun info(tag: String, msg: String) { println("INFO: [$tag] $msg") }
    fun error(tag: String, msg: String, t: Throwable? = null) {
        System.err.println("ERROR: [$tag] $msg")
        t?.printStackTrace()
    }
}
