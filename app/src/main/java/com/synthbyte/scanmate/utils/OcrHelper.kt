package com.synthbyte.scanmate.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val OCR_MAX_SIDE = 2300

data class OcrExtractionResult(
    val text: String,
    val confidencePercent: Int,
    val wordCount: Int,
    val qualityLabel: String
)

object OcrHelper {
    @Volatile
    private var recognizer: TextRecognizer? = null

    private fun getRecognizer(): TextRecognizer {
        return recognizer ?: synchronized(this) {
            recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }
        }
    }

    suspend fun extractTextFromBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): String {
        return extractStatsFromBitmap(bitmap, rotationDegrees).text
    }

    suspend fun extractTextFromFile(context: Context, file: File): String {
        return extractTextWithStatsFromFile(context, file).text
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun extractBlocksFromFile(context: Context, file: File): List<Pair<Rect, String>> {
        val source = FileUtils.decodeSampledBitmap(file.absolutePath, OCR_MAX_SIDE, OCR_MAX_SIDE) ?: return emptyList()
        val fixed = fixExifRotation(source, file)
        fun recycleBitmaps() {
            if (fixed !== source && !fixed.isRecycled) runCatching { fixed.recycle() }
            if (!source.isRecycled) runCatching { source.recycle() }
        }
        return suspendCancellableCoroutine { continuation ->
            getRecognizer().process(InputImage.fromBitmap(fixed, 0))
                .addOnSuccessListener { result ->
                    val rects = orderedOcrLines(result.textBlocks)
                        .map { line -> line.rect to postProcessOcrText(line.text) }
                        .filter { (_, text) -> text.isNotBlank() }
                    recycleBitmaps()
                    if (continuation.isActive) continuation.resume(rects)
                }
                .addOnFailureListener {
                    recycleBitmaps()
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        }
    }

    suspend fun extractTextWithStatsFromFile(context: Context, file: File): OcrExtractionResult {
        val source = FileUtils.decodeSampledBitmap(file.absolutePath, OCR_MAX_SIDE, OCR_MAX_SIDE)
            ?: return buildStats("OCR failed: Could not decode image", 0)
        return try {
            val fixed = fixExifRotation(source, file)
            try {
                runBestTextRecognition(fixed)
            } finally {
                if (fixed !== source && !fixed.isRecycled) runCatching { fixed.recycle() }
                if (!source.isRecycled) runCatching { source.recycle() }
            }
        } catch (e: Exception) {
            if (!source.isRecycled) runCatching { source.recycle() }
            buildStats("OCR failed: ${e.localizedMessage ?: "Unknown error"}", 0)
        }
    }

    suspend fun extractStatsFromBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): OcrExtractionResult {
        val rotated = if (rotationDegrees != 0) rotate(bitmap, rotationDegrees.toFloat()) else bitmap
        return try {
            runBestTextRecognition(rotated)
        } finally {
            if (rotated !== bitmap && !rotated.isRecycled) runCatching { rotated.recycle() }
        }
    }

    fun buildStats(text: String): OcrExtractionResult = buildStats(text, null)

    private fun buildStats(text: String, mlKitConfidence: Int?): OcrExtractionResult {
        val clean = DocumentIntelligence.cleanOcrText(postProcessOcrText(text))
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val confidence = when {
            clean.isBlank() || clean.startsWith("OCR failed", ignoreCase = true) -> 0
            mlKitConfidence != null -> mlKitConfidence.coerceIn(0, 100)
            words.size >= 120 -> 82
            words.size >= 40 -> 74
            words.size >= 12 -> 62
            else -> 45
        }
        val label = when {
            confidence >= 88 -> "High confidence"
            confidence >= 72 -> "Good confidence"
            confidence >= 55 -> "Needs review"
            confidence > 0 -> "Low confidence"
            else -> "No OCR text"
        }
        return OcrExtractionResult(clean, confidence, words.size, label)
    }

    fun closeRecognizer() {
        synchronized(this) {
            runCatching { recognizer?.close() }
            recognizer = null
        }
    }

    private suspend fun runTextRecognition(bitmap: Bitmap, rotationDegrees: Int): OcrExtractionResult =
        suspendCancellableCoroutine { continuation ->
            try {
                val activeRecognizer = getRecognizer()
                activeRecognizer.process(InputImage.fromBitmap(bitmap, rotationDegrees))
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) {
                            continuation.resume(buildStats(reconstructParagraphs(result.textBlocks), result.symbolConfidencePercent()))
                        }
                    }
                    .addOnFailureListener { e ->
                        if (continuation.isActive) continuation.resume(buildStats("OCR failed: ${e.localizedMessage ?: "Unknown error"}", 0))
                    }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(buildStats("OCR failed: ${e.localizedMessage ?: "Unknown error"}", 0))
            }
        }


    private fun reconstructParagraphs(blocks: List<com.google.mlkit.vision.text.Text.TextBlock>): String {
        if (blocks.isEmpty()) return ""
        val lines = orderedOcrLines(blocks)
        if (lines.isEmpty()) return blocks.joinToString("\n") { it.text }.trim()

        val medianHeight = lines.map { it.rect.height().coerceAtLeast(1) }.sorted().let { values -> values[values.size / 2].coerceAtLeast(1) }
        val rows = groupLinesIntoRows(lines, medianHeight)
        if (rows.isEmpty()) return ""

        val paragraphs = mutableListOf<MutableList<String>>()
        var currentParagraph = mutableListOf<String>()
        var lastBottom = rows.first().maxOf { it.rect.bottom }
        for (row in rows) {
            val rowTop = row.minOf { it.rect.top }
            val rowBottom = row.maxOf { it.rect.bottom }
            val gap = rowTop - lastBottom
            if (currentParagraph.isNotEmpty() && gap > medianHeight * 1.55f) {
                paragraphs += currentParagraph
                currentParagraph = mutableListOf()
            }
            val rowText = row.sortedBy { it.rect.left }
                .joinToString(" ") { it.text }
                .replace(Regex("[ \t]{2,}"), " ")
                .trim()
            if (rowText.isNotBlank()) currentParagraph += rowText
            lastBottom = max(lastBottom, rowBottom)
        }
        if (currentParagraph.isNotEmpty()) paragraphs += currentParagraph

        return postProcessOcrText(
            paragraphs.joinToString("\n\n") { paragraph ->
                mergeWrappedOcrLines(paragraph).trim()
            }
        ).trim()
    }

    private fun mergeWrappedOcrLines(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        val merged = mutableListOf<String>()
        for (line in lines.map { it.trim() }.filter { it.isNotBlank() }) {
            val previous = merged.lastOrNull()
            val currentLooksLikeHeading = line.length <= 42 && (
                line.all { it.isUpperCase() || it.isWhitespace() || it.isDigit() || it in ".:-" } ||
                    line.matches(Regex("(?i)^(unit|chapter|examples|system|computer|car|question|q\\.).*")) ||
                    line.matches(Regex("^\\d+\\.\\s+.*"))
                )
            val previousEndsSentence = previous?.lastOrNull() in listOf('.', '?', '!', ':')
            if (previous == null || currentLooksLikeHeading || previousEndsSentence && line.length < 60) {
                merged += line
            } else {
                merged[merged.lastIndex] = "$previous $line".replace(Regex("\\s{2,}"), " ")
            }
        }
        return merged.joinToString("\n")
    }

    private data class OcrLine(val rect: Rect, val text: String, val blockIndex: Int)

    private fun Text.toSortedText(): String {
        return reconstructParagraphs(textBlocks)
    }

    private fun orderedOcrLines(blocks: List<Text.TextBlock>): List<OcrLine> {
        val lines = blocks.flatMapIndexed { blockIndex, block ->
            val blockRect = block.boundingBox
            block.lines.mapNotNull { line ->
                val clean = postProcessOcrText(line.text.trim().replace(Regex("\\s+"), " "))
                if (clean.isBlank()) return@mapNotNull null
                val lineRect = line.boundingBox ?: line.elements
                    .mapNotNull { it.boundingBox }
                    .takeIf { it.isNotEmpty() }
                    ?.reduce { acc, rect -> union(acc, rect) }
                    ?: blockRect
                    ?: return@mapNotNull null
                OcrLine(lineRect, clean, blockIndex)
            }
        }
        if (lines.isEmpty()) return emptyList()
        val medianHeight = lines.map { it.rect.height().coerceAtLeast(1) }
            .sorted()
            .let { values -> values[values.size / 2].coerceAtLeast(1) }
        return groupLinesIntoRows(lines, medianHeight)
            .flatMap { row -> row.sortedWith(compareBy<OcrLine> { it.rect.left }.thenBy { it.blockIndex }) }
    }

    private fun groupLinesIntoRows(lines: List<OcrLine>, medianHeight: Int): List<List<OcrLine>> {
        if (lines.isEmpty()) return emptyList()
        val rowTolerance = max(6, (medianHeight * 0.65f).roundToInt())
        val rows = mutableListOf<MutableList<OcrLine>>()
        lines.sortedWith(compareBy<OcrLine> { it.rect.centerY() }.thenBy { it.rect.left }).forEach { line ->
            val centerY = line.rect.centerY()
            val row = rows.firstOrNull { existing ->
                val averageCenter = existing.map { it.rect.centerY() }.average()
                abs(centerY - averageCenter) <= rowTolerance
            }
            if (row == null) {
                rows += mutableListOf(line)
            } else {
                row += line
            }
        }
        return rows.sortedWith(
            compareBy<List<OcrLine>> { row -> row.minOf { it.rect.top } }
                .thenBy { row -> row.minOf { it.rect.left } }
        )
    }

    private fun union(a: Rect, b: Rect): Rect = Rect(
        min(a.left, b.left),
        min(a.top, b.top),
        max(a.right, b.right),
        max(a.bottom, b.bottom)
    )

    private fun postProcessOcrText(value: String): String {
    return OcrPostProcessor.normalize(value)
}

    private fun Text.symbolConfidencePercent(): Int? {
        val values = textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .flatMap { it.symbols }
            .mapNotNull { symbol ->
                val confidence = symbol.confidence
                if (confidence >= 0f) confidence else null
            }
        if (values.isEmpty()) return null
        return (values.average() * 100.0).roundToInt().coerceIn(0, 100)
    }

    // Previously this ran OCR on up to ~28 image variants (4 rotations x up to 7
    // filter combos) sequentially per page, holding every bitmap in memory at once.
    // That made scanning slow, crash-prone on mid/low-end devices, and biased the
    // "best result" picker toward documents containing English tech vocabulary.
    // Now: one OCR pass on the properly oriented + deskewed + cleaned image, with a
    // single upside-down fallback only if the first pass looks weak. Camera captures
    // are already EXIF-corrected before this runs, so 90/270 retries were redundant.
    private suspend fun runBestTextRecognition(source: Bitmap): OcrExtractionResult {
        val primary = buildOcrCandidate(source, 0f)
        val primaryResult = try {
            runTextRecognition(primary, 0)
        } finally {
            if (primary !== source && !primary.isRecycled) runCatching { primary.recycle() }
        }

        if (ocrLooksGood(primaryResult)) return primaryResult

        val flipped = buildOcrCandidate(source, 180f)
        val flippedResult = try {
            runTextRecognition(flipped, 0)
        } finally {
            if (flipped !== source && !flipped.isRecycled) runCatching { flipped.recycle() }
        }

        return if (ocrQualityScore(flippedResult) > ocrQualityScore(primaryResult)) flippedResult else primaryResult
    }

    private fun ocrLooksGood(result: OcrExtractionResult): Boolean {
        if (result.text.isBlank() || result.text.startsWith("OCR failed", ignoreCase = true)) return false
        return result.wordCount >= 6 && result.confidencePercent >= 40
    }

    private fun buildOcrCandidate(source: Bitmap, rotationDegrees: Float): Bitmap {
        val oriented = if (rotationDegrees == 0f) source else rotate(source, rotationDegrees)
        val deskewed = estimateSkewAndRotate(oriented)
        val scaled = deskewed.scaleDownToMax(OCR_MAX_SIDE)
        val enhanced = runCatching { ImageProcessor.enhanceForOcr(scaled) }
            .getOrNull() ?: scaled.copy(Bitmap.Config.ARGB_8888, false)

        if (scaled !== source && scaled !== enhanced && !scaled.isRecycled) runCatching { scaled.recycle() }
        if (deskewed !== source && deskewed !== oriented && deskewed !== scaled && deskewed !== enhanced && !deskewed.isRecycled) {
            runCatching { deskewed.recycle() }
        }
        if (oriented !== source && oriented !== deskewed && oriented !== enhanced && !oriented.isRecycled) runCatching { oriented.recycle() }

        return enhanced
    }

    private fun ocrQualityScore(result: OcrExtractionResult): Double {
        if (result.text.isBlank() || result.text.startsWith("OCR failed", ignoreCase = true)) return Double.NEGATIVE_INFINITY
        val clean = result.text
        val textLengthScore = min(clean.length, 1800) / 1800.0 * 20.0
        val wordScore = min(result.wordCount, 260) * 0.34
        val confidenceScore = result.confidencePercent * 1.18
        val structureBonus = clean.count { it == '\n' }.coerceAtMost(24) * 0.45
        val noisePenalty = clean.count { it == '\uFFFD' || it == '¤' || it == '|' }.coerceAtMost(30) * 1.5 +
            Regex("[A-Za-z][0-9][A-Za-z]").findAll(clean).count().coerceAtMost(18) * 1.7
        return confidenceScore + wordScore + textLengthScore + structureBonus - noisePenalty
    }

    private fun estimateSkewAndRotate(source: Bitmap): Bitmap {
        if (source.width < 100 || source.height < 100) return source
        val maxSampleSide = 720
        val sampleScale = min(1f, maxSampleSide.toFloat() / max(source.width, source.height).toFloat())
        val sample = if (sampleScale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * sampleScale).roundToInt().coerceAtLeast(1),
                (source.height * sampleScale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }

        val angles = listOf(0f) + generateSequence(-10f) { previous ->
            val next = previous + 0.5f
            if (next <= 10.0001f) next else null
        }.filter { kotlin.math.abs(it) >= 0.001f }.toList()
        var bestAngle = 0f
        var bestScore = Double.NEGATIVE_INFINITY
        angles.forEach { angle ->
            val rotated = rotateForSkewScore(sample, angle)
            val score = horizontalProjectionVariance(rotated)
            if (rotated !== sample && !rotated.isRecycled) runCatching { rotated.recycle() }
            if (score > bestScore) {
                bestScore = score
                bestAngle = angle
            }
        }
        if (sample !== source && !sample.isRecycled) runCatching { sample.recycle() }
        if (abs(bestAngle) < 0.4f) return source
        val matrix = Matrix().apply { postRotate(bestAngle) }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrNull() ?: source
    }

    private fun rotateForSkewScore(source: Bitmap, angle: Float): Bitmap {
        if (abs(angle) < 0.001f) return source
        val matrix = Matrix().apply { postRotate(angle) }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrNull() ?: source
    }

    private fun horizontalProjectionVariance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) return 0.0
        val pixels = IntArray(width * height)
        try {
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        } catch (e: Throwable) {
            return 0.0
        }
        val rowSums = DoubleArray(height)
        for (y in 0 until height) {
            var sum = 0.0
            var previousDark = false
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val luminance = (android.graphics.Color.red(color) * 0.299f + android.graphics.Color.green(color) * 0.587f + android.graphics.Color.blue(color) * 0.114f)
                val dark = luminance < 190f
                if (dark && !previousDark) sum += 1.0
                if (dark) sum += (255f - luminance) / 255f
                previousDark = dark
            }
            rowSums[y] = sum
        }
        val mean = rowSums.average()
        return rowSums.fold(0.0) { acc, value ->
            val delta = value - mean
            acc + delta * delta
        } / height.toDouble()
    }

    private fun fixExifRotation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun rotate(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun Bitmap.scaleDownToMax(maxSide: Int): Bitmap {
        val side = max(width, height)
        if (side <= maxSide) return this
        val ratio = maxSide.toFloat() / side.toFloat()
        val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }
}
