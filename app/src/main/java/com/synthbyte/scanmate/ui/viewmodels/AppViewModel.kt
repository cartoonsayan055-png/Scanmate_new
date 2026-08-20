package com.synthbyte.scanmate.ui.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appErrorBus: AppErrorBus
) : ViewModel(), AppErrorReporter {
    val globalError: StateFlow<String?> = appErrorBus.globalError

    override fun reportError(message: String) {
        appErrorBus.reportError(message)
    }

    override fun clearError() {
        appErrorBus.clearError()
    }
}
