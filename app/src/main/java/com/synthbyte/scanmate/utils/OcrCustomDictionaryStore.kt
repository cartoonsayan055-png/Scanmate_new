package com.synthbyte.scanmate.utils

import android.content.Context

/**
 * Local-only custom OCR dictionary storage.
 * No account, backend, Firebase, or cloud dependency.
 */
class OcrCustomDictionaryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWords(): Set<String> {
        return prefs.getStringSet(KEY_WORDS, emptySet()).orEmpty()
            .mapNotNull { normalizeWord(it) }
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)
    }

    fun addWord(word: String): Boolean {
        val clean = normalizeWord(word) ?: return false
        val next = getWords().toMutableSet().also { it += clean }
        prefs.edit().putStringSet(KEY_WORDS, next).apply()
        return true
    }

    fun removeWord(word: String): Boolean {
        val clean = normalizeWord(word) ?: return false
        val next = getWords().toMutableSet()
        val removed = next.removeAll { it.equals(clean, ignoreCase = true) }
        if (removed) prefs.edit().putStringSet(KEY_WORDS, next).apply()
        return removed
    }

    fun clear() {
        prefs.edit().remove(KEY_WORDS).apply()
    }

    private fun normalizeWord(word: String): String? {
        val clean = word.trim().trim('.', ',', ':', ';', '!', '?')
        if (clean.length < 5) return null
        if (!clean.any { it.isLetter() }) return null
        if (clean.any { it.isDigit() || it == '@' || it == '/' || it == '_' }) return null
        return clean
    }

    companion object {
        private const val PREFS_NAME = "scanmate_ocr_dictionary"
        private const val KEY_WORDS = "custom_words"
    }
}
