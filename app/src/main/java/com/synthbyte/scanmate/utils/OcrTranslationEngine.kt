package com.synthbyte.scanmate.utils

import java.util.Locale

data class TranslationLanguage(
    val code: String,
    val label: String
)

object OcrTranslationEngine {
    val supportedLanguages = listOf(
        TranslationLanguage("ur", "Urdu"),
        TranslationLanguage("hi", "Hindi"),
        TranslationLanguage("ar", "Arabic"),
        TranslationLanguage("es", "Spanish"),
        TranslationLanguage("fr", "French"),
        TranslationLanguage("de", "German"),
        TranslationLanguage("en", "English")
    )

    fun languageName(code: String): String = supportedLanguages.firstOrNull { it.code == code }?.label ?: code.uppercase(Locale.US)

    fun onlinePrompt(text: String, targetLanguageCode: String): String {
        val language = languageName(targetLanguageCode)
        return """
            Translate the OCR text below into $language.
            Preserve headings, numbers, names, addresses, and line breaks when useful.
            Fix obvious OCR spacing mistakes, but do not invent missing facts.
            Return only the translated text.

            OCR text:
            ${DocumentIntelligence.cleanOcrText(text)}
        """.trimIndent()
    }

    fun offlineFallback(text: String, targetLanguageCode: String): String {
        val cleaned = DocumentIntelligence.cleanOcrText(text)
        val language = languageName(targetLanguageCode)
        val keywords = DocumentIntelligence.keywordList(cleaned, 10).joinToString(", ").ifBlank { "Not enough text" }
        return buildString {
            appendLine("Offline translation workspace")
            appendLine()
            appendLine("Target language: $language")
            appendLine("Status: Full translation needs online AI or a bundled translation model. ScanMate kept this offline-first, so no API key/network means no crash and no data upload.")
            appendLine()
            appendLine("Local OCR cleanup:")
            appendLine(cleaned.take(1800).ifBlank { "No readable OCR text detected." })
            appendLine()
            appendLine("Keywords: $keywords")
            appendLine("Suggested category: ${DocumentIntelligence.suggestedCategory(cleaned)}")
        }.trim()
    }
}
