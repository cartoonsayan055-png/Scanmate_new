package com.synthbyte.scanmate.utils

import android.util.Log

/**
 * Small safe logging wrapper used by UI and scanner screens.
 *
 * Keep this object lightweight and dependency-free so it works in Debug and Release builds.
 * Do not log secrets, API keys, file contents, document OCR text, or user-private data here.
 */
object SafeLogger {
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
