package com.synthbyte.scanmate.core

sealed interface ScanMateResult<out T> {
    data class Success<T>(val data: T) : ScanMateResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : ScanMateResult<Nothing>
    data object Loading : ScanMateResult<Nothing>
}

inline fun <T> scanMateRunCatching(
    fallbackMessage: String,
    block: () -> T
): ScanMateResult<T> = try {
    ScanMateResult.Success(block())
} catch (throwable: Throwable) {
    ScanMateResult.Error(throwable.localizedMessage ?: fallbackMessage, throwable)
}
