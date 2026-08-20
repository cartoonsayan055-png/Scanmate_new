package com.synthbyte.scanmate.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Applies FLAG_SECURE while a sensitive Compose screen is visible. */
@Composable
fun SecureScreenEffect(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val activity = view.context as? Activity
        if (enabled) {
            activity?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (enabled) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
