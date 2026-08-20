package com.synthbyte.scanmate.utils

import java.util.Locale

/**
 * Lightweight offline document intelligence used as a graceful fallback when online AI is unavailable.
 * It never sends data anywhere and keeps ScanMate usable as an offline-first scanner.
 */
enum class AiWorkflow(val label: String, val description: String, val promptPrefix: String) {
    SUMMARY(
        "Summarize",
        "Clear bullet summary for long OCR text",
        "Summarize this document into concise, useful bullet points. Keep names, dates, totals and decisions accurate:"
    ),
    HOMEWORK(
        "Homework Helper",
        "Explain questions step-by-step",
        "Act as a helpful tutor. Explain the question step-by-step, show reasoning clearly, and keep the answer student-friendly:"
    ),
    RECEIPT(
        "Receipt Analyzer",
        "Find totals, merchant clues and dates",
        "Analyze this receipt. Extract merchant, date, items, subtotal, tax, total and any payment clues:"
    ),
    INVOICE(
        "Invoice Analyzer",
        "Extract invoice fields and action items",
        "Analyze this invoice. Extract invoice number, parties, due date, amounts, tax, terms and payment action items:"
    ),
    OCR_CLEANUP(
        "Clean OCR",
        "Fix spacing and broken OCR lines",
        "Clean this OCR output. Fix spacing, broken lines and obvious OCR formatting issues while preserving original meaning:"
    ),
    OCR_TRANSLATE(
        "Translate OCR",
        "Translate extracted text with online AI",
        "Translate this OCR text into clear English and preserve names, numbers, tables, totals and dates. If it is already English, rewrite it into cleaner English:"
    ),
    DOCUMENT_CHAT(
        "Document Chat",
        "Answer from pasted document text",
        "Answer the user's question using only the document text below. If the answer is not in the text, say that clearly:"
    ),
    TITLE_KEYWORDS(
        "Title + Keywords",
        "Generate title, category and tags",
        "Create a short document title, category, and 8 useful keywords for this OCR text:"
    )
}

object DocumentIntelligence {

    data class BusinessCardResult(
        val name: String?,
        val email: String?,
        val phone: String?,
        val website: String?,
        val rawLines: List<String>
    )

    fun extractBusinessCard(ocrText: String): BusinessCardResult {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
val emailR = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
val phoneR = Regex("""(\+?[0-9][\s\-.]?){7,15}""")
val urlR = Regex("""(https?://|www\.)[^\s]+""")
        val email = lines.firstNotNullOfOrNull { emailR.find(it)?.value }
        val phone = lines.firstNotNullOfOrNull { phoneR.find(it)?.value }
        val url = lines.firstNotNullOfOrNull { urlR.find(it)?.value }
        val nameR = Regex("""^[A-Z][a-zA-Z'\-]+(\s[A-Z][a-zA-Z'\-]+){1,4}(\s(Jr|Sr|II|III|IV|V|PhD|MD|Esq)\.?)?$""")
        val name = lines.firstOrNull { line ->
            !line.contains("@") && !phoneR.containsMatchIn(line) && !urlR.containsMatchIn(line) &&
                line.length in 3..60 &&
                (nameR.matches(line.trim()) || (line.trim().split(" ").size in 2..5 && line.trim().first().isUpperCase()))
        }
        return BusinessCardResult(name, email, phone, url, lines)
    }

    data class QualityReport(val score: Int, val label: String, val issues: List<String>, val tips: List<String>)

    fun scoreDocumentQuality(ocrText: String, avgConfidence: Float, pageCount: Int): QualityReport {
        val issues = mutableListOf<String>()
        val tips = mutableListOf<String>()
        var score = 100
        if (avgConfidence < 0.5f) {
            issues.add("Low OCR confidence")
            tips.add("Rescan in better lighting")
            score -= 30
        } else if (avgConfidence < 0.75f) {
            issues.add("Moderate OCR confidence")
            tips.add("Try High Contrast filter")
            score -= 15
        }
        val words = ocrText.split(Regex("""\s+""")).count { it.length > 1 }
        if (words < 10 && ocrText.isNotBlank()) {
            issues.add("Very little text detected")
            tips.add("Check alignment")
            score -= 20
        }
        val noise = ocrText.count { !it.isLetterOrDigit() && !it.isWhitespace() }.toFloat() / ocrText.length.coerceAtLeast(1)
        if (noise > 0.3f) {
            issues.add("High noise ratio")
            tips.add("Apply B&W filter")
            score -= 15
        }
        if (pageCount == 0) {
            issues.add("No pages")
            score = 0
        }
        score = score.coerceIn(0, 100)
        val label = when {
            score >= 85 -> "Excellent"
            score >= 65 -> "Good"
            score >= 40 -> "Fair"
            else -> "Poor"
        }
        return QualityReport(score, label, issues, tips)
    }
    val workflows: List<AiWorkflow> = AiWorkflow.entries

    fun buildPrompt(workflow: AiWorkflow, sourceText: String): String = "${workflow.promptPrefix}\n\n${sourceText.trim()}"

    fun buildOfflineResponse(workflow: AiWorkflow, sourceText: String): String {
        val clean = cleanOcrText(sourceText)
        if (clean.isBlank()) return "Paste OCR text or type a prompt first."
        return when (workflow) {
            AiWorkflow.SUMMARY -> buildSummary(clean)
            AiWorkflow.HOMEWORK -> buildHomeworkHelper(clean)
            AiWorkflow.RECEIPT -> buildReceiptAnalysis(clean)
            AiWorkflow.INVOICE -> buildInvoiceAnalysis(clean)
            AiWorkflow.OCR_CLEANUP -> clean
            AiWorkflow.OCR_TRANSLATE -> buildTranslationFallback(clean)
            AiWorkflow.DOCUMENT_CHAT -> buildDocumentChatFallback(clean)
            AiWorkflow.TITLE_KEYWORDS -> buildTitleKeywords(clean)
        }
    }

    fun cleanOcrText(text: String): String = text
        .replace(Regex("[ \t]+"), " ")
        .replace(Regex(" ?\n ?"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .replace(Regex("(?i)\\b([a-z0-9._%+-]+)\\s*@\\s*([a-z0-9.-]+)\\s*\\.\\s*([a-z]{2,})\\b")) { match ->
            "${match.groupValues[1]}@${match.groupValues[2]}.${match.groupValues[3]}"
        }
        .replace(Regex("(?i)\\b([a-z0-9-]+(?:\\s*\\.\\s*[a-z0-9-]+)+)\\s*\\.\\s*([a-z]{2,})\\b")) { match ->
            "${match.groupValues[1].replace(Regex("\\s*\\.\\s*"), ".")}.${match.groupValues[2]}"
        }
        .replace(Regex("(?i)\\b(https?)\\s*:\\s*/\\s*/\\s*")) { match -> "${match.groupValues[1].lowercase()}://" }
        .replace(Regex("(?i)\\b(www)\\s*\\.\\s*")) { "www." }
        .lines()
        .joinToString("\n") { it.trim() }
        .trim()

    fun suggestedTitle(text: String): String {
        val clean = cleanOcrText(text)
        val firstUsefulLine = clean.lines()
            .map { it.trim().trim('-', ':', '.', ',') }
            .firstOrNull { line -> line.length in 5..60 && line.any { it.isLetter() } }
        return firstUsefulLine?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            ?: "ScanMate Document ${System.currentTimeMillis()}"
    }

    fun keywordList(text: String, limit: Int = 10): List<String> {
        val stopWords = setOf(
            "the", "and", "for", "you", "are", "with", "this", "that", "from", "have", "your", "page", "scan",
            "document", "was", "were", "will", "shall", "can", "not", "all", "any", "but", "into", "their",
            "there", "then", "than", "about", "after", "before", "also", "when", "where", "what", "which"
        )
        return cleanOcrText(text)
            .lowercase(Locale.getDefault())
            .split(Regex("[^a-z0-9]+"))
            .filter { token -> token.length >= 4 && token !in stopWords && !token.all { it.isDigit() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString() } }
    }

    fun suggestedCategory(text: String): String {
        val lower = text.lowercase(Locale.getDefault())
        return when {
            listOf("invoice", "amount due", "payment terms", "bill to", "invoice no").any { it in lower } -> "Invoice"
            listOf("receipt", "subtotal", "total", "cash", "change", "merchant").any { it in lower } -> "Receipt"
            listOf("question", "exercise", "chapter", "marks", "answer").any { it in lower } -> "Study"
            listOf("agreement", "contract", "clause", "terms", "party").any { it in lower } -> "Legal"
            listOf("patient", "clinic", "doctor", "medicine", "diagnosis").any { it in lower } -> "Medical"
            else -> "General"
        }
    }

    private fun buildSummary(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.length > 8 }
        val sentences = text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim().trim('-', '•') }
            .filter { it.length > 25 }
            .take(7)
        val source = if (sentences.isNotEmpty()) sentences else lines.take(7)
        return buildString {
            appendLine("Offline summary")
            appendLine()
            source.forEach { appendLine("• $it") }
            if (source.isEmpty()) appendLine("• ${text.take(220)}")
            appendLine()
            appendLine("Keywords: ${keywordList(text, 8).joinToString(", ").ifBlank { "Not enough text" }}")
        }.trim()
    }

    private fun buildHomeworkHelper(text: String): String = buildString {
        appendLine("Offline homework helper")
        appendLine()
        appendLine("What I detected:")
        appendLine("• Possible topic/category: ${suggestedCategory(text)}")
        appendLine("• Main keywords: ${keywordList(text, 8).joinToString(", ").ifBlank { "Not enough keywords" }}")
        appendLine()
        appendLine("Study approach:")
        appendLine("1. Read the question carefully and underline the command word, such as define, explain, solve, compare, or prove.")
        appendLine("2. Identify known values, formulas, dates, names, or definitions from the text.")
        appendLine("3. Write the answer in small steps, then check units, spelling, and final conclusion.")
        appendLine()
        appendLine("Cleaned text:")
        appendLine(text.take(900))
    }.trim()

    private fun buildReceiptAnalysis(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val moneyPattern = Regex("(?:rs\\.?|pkr|usd|[$])?\\s*\\d{1,3}(?:[, ]?\\d{3})*(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
        val moneyLines = lines.filter { moneyPattern.containsMatchIn(it) }.takeLast(10)
        val likelyTotal = lines.lastOrNull { line -> line.contains("total", true) || line.contains("amount", true) }
            ?: moneyLines.lastOrNull()
        return buildString {
            appendLine("Offline receipt analysis")
            appendLine()
            appendLine("• Likely merchant/header: ${lines.firstOrNull().orEmpty().ifBlank { "Not detected" }}")
            appendLine("• Likely total line: ${likelyTotal.orEmpty().ifBlank { "Not detected" }}")
            appendLine("• Money-related lines:")
            moneyLines.ifEmpty { listOf("Not enough receipt-like values detected") }.forEach { appendLine("  - $it") }
            appendLine()
            appendLine("Tip: run online AI for deeper item-by-item extraction if needed.")
        }.trim()
    }

    private fun buildInvoiceAnalysis(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val invoiceLine = lines.firstOrNull { it.contains("invoice", true) }
        val dueLine = lines.firstOrNull { it.contains("due", true) || it.contains("pay", true) }
        val amountLine = lines.lastOrNull { it.contains("total", true) || it.contains("amount", true) || it.contains("balance", true) }
        return buildString {
            appendLine("Offline invoice analysis")
            appendLine()
            appendLine("• Invoice clue: ${invoiceLine.orEmpty().ifBlank { "Not detected" }}")
            appendLine("• Due/payment clue: ${dueLine.orEmpty().ifBlank { "Not detected" }}")
            appendLine("• Amount clue: ${amountLine.orEmpty().ifBlank { "Not detected" }}")
            appendLine("• Suggested category: ${suggestedCategory(text)}")
            appendLine("• Useful keywords: ${keywordList(text, 8).joinToString(", ").ifBlank { "Not enough text" }}")
        }.trim()
    }

    private fun buildTranslationFallback(text: String): String = buildString {
        appendLine("Offline translation prep")
        appendLine()
        appendLine("ScanMate cleaned the OCR locally. Full translation needs online AI because offline translation models are not bundled in this lightweight build.")
        appendLine("Category: ${suggestedCategory(text)}")
        appendLine("Keywords: ${keywordList(text, 8).joinToString(", ").ifBlank { "Not enough text" }}")
        appendLine()
        appendLine("Cleaned OCR preview:")
        appendLine(text.take(1000))
    }.trim()

    private fun buildDocumentChatFallback(text: String): String = buildString {
        appendLine("Offline document chat fallback")
        appendLine()
        appendLine("I can prepare the document locally, but question-answer chat needs online AI for accurate answering.")
        appendLine("Detected title: ${suggestedTitle(text)}")
        appendLine("Category: ${suggestedCategory(text)}")
        appendLine("Keywords: ${keywordList(text, 10).joinToString(", ").ifBlank { "Not enough text" }}")
        appendLine()
        appendLine("Preview:")
        appendLine(text.take(1000))
    }.trim()

    private fun buildTitleKeywords(text: String): String = buildString {
        appendLine("Offline organization result")
        appendLine()
        appendLine("Title: ${suggestedTitle(text)}")
        appendLine("Category: ${suggestedCategory(text)}")
        appendLine("Keywords: ${keywordList(text, 12).joinToString(", ").ifBlank { "Not enough text" }}")
    }.trim()
}
