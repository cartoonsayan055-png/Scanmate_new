package com.synthbyte.scanmate.ui.viewmodels

import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

interface AppErrorReporter {
    fun reportError(message: String)
    fun clearError()
}

@ActivityRetainedScoped
class AppErrorBus @Inject constructor() : AppErrorReporter {
    private val _globalError = MutableStateFlow<String?>(null)
    val globalError: StateFlow<String?> = _globalError.asStateFlow()

    override fun reportError(message: String) {
        _globalError.value = message
    }

    override fun clearError() {
        _globalError.value = null
    }
}
