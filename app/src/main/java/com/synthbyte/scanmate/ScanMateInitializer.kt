package com.synthbyte.scanmate

import android.content.Context
import androidx.startup.Initializer

/**
 * Lightweight App Startup hook reserved for warm, non-blocking SDK initialization. Heavy work stays
 * out of process start to protect cold-start time.
 */
class ScanMateInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // Intentionally lightweight. Camera, ML Kit, Room, and vault keys initialize lazily.
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
