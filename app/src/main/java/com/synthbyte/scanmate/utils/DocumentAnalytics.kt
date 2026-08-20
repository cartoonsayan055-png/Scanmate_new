package com.synthbyte.scanmate.utils

import com.synthbyte.scanmate.data.DocumentWithPages
import java.io.File
import java.util.Locale
import kotlin.math.ceil

data class DocumentInsights(
    val pageCount: Int,
    val wordCount: Int,
    val characterCount: Int,
    val estimatedReadingMinutes: Int,
    val storageKb: Long,
    val keywordPreview: String,
    val qualityLabel: String
)

object DocumentAnalyticsEngine {
    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "have", "your", "you",
        "are", "was", "were", "will", "can", "has", "had", "but", "not", "all",
        "a", "an", "of", "to", "in", "on", "is", "it", "or", "as", "by"
    )

    fun analyze(document: DocumentWithPages): DocumentInsights {
        val text = document.document.ocrText.orEmpty()
        val words = text.split(Regex("\\s+"))
            .map { it.trim().lowercase(Locale.US).trim(',', '.', ':', ';', '!', '?', '(', ')', '[', ']') }
            .filter { it.length > 1 }
        val storageBytes = document.pages.sumOf { page -> File(page.imagePath).takeIf { it.exists() }?.length() ?: 0L }
        val keywordPreview = buildKeywordPreview(words, document.document.tags)
        val qualityLabel = when {
            document.pages.isEmpty() -> "Needs pages"
            text.isBlank() -> "Image-only"
            words.size >= 350 -> "Research-ready"
            words.size >= 80 -> "OCR-ready"
            else -> "Light scan"
        }
        return DocumentInsights(
            pageCount = document.pages.size,
            wordCount = words.size,
            characterCount = text.length,
            estimatedReadingMinutes = ceil(words.size / 220.0).toInt().coerceAtLeast(if (words.isEmpty()) 0 else 1),
            storageKb = storageBytes / 1024L,
            keywordPreview = keywordPreview,
            qualityLabel = qualityLabel
        )
    }

    private fun buildKeywordPreview(words: List<String>, existingTags: String): String {
        val tagWords = existingTags.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (tagWords.isNotEmpty()) return tagWords.take(5).joinToString(", ")
        return words
            .filter { it !in stopWords && it.length > 3 && it.none { char -> char.isDigit() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5)
            .joinToString(", ") { it.key }
            .ifBlank { "Add OCR or tags" }
    }
}
