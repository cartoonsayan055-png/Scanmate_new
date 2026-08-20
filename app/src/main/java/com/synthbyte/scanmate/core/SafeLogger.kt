package com.synthbyte.scanmate.core

import android.util.Log
import com.synthbyte.scanmate.BuildConfig

/**
 * Debug-only logging facade. Release builds keep diagnostics out of logcat while still allowing
 * local developer builds to capture useful failure details.
 */
object SafeLogger {
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable == null) Log.d(tag, message) else Log.d(tag, message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
        }
    }
}
