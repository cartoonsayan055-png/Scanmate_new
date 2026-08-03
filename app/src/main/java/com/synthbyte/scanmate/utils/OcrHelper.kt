package com.synthbyte.scanmate.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
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

    private suspend fun runBestTextRecognition(source: Bitmap): OcrExtractionResult {
        val candidates = buildOcrCandidates(source)
        if (candidates.isEmpty()) return buildStats("", 0)
        var best: OcrExtractionResult? = null
        try {
            for ((_, bitmap) in candidates) {
                val result = runTextRecognition(bitmap, 0)
                if (best == null || ocrQualityScore(result) > ocrQualityScore(best!!)) {
                    best = result
                }
            }
        } finally {
            candidates.forEach { (_, bitmap) ->
                if (bitmap !== source && !bitmap.isRecycled) runCatching { bitmap.recycle() }
            }
        }
        return best ?: buildStats("", 0)
    }

    @Suppress("unused")
    private fun preprocessForOcr(source: Bitmap): Bitmap {
        return runCatching { ImageProcessor.enhanceForOcr(source) }
            .getOrElse { source.copy(Bitmap.Config.ARGB_8888, false) }
    }

    private fun buildOcrCandidates(source: Bitmap): List<Pair<String, Bitmap>> {
        val result = mutableListOf<Pair<String, Bitmap>>()
        val rotations = listOf(
            "normal" to 0f,
            "upside-down" to 180f,
            "rotate-right" to 90f,
            "rotate-left" to 270f
        )

        for ((rotationLabel, degrees) in rotations) {
            val oriented = if (degrees == 0f) source else rotate(source, degrees)
            val deskewed = estimateSkewAndRotate(oriented)
            val scaled = deskewed.scaleDownToMax(OCR_MAX_SIDE)
            val base = scaled.copy(Bitmap.Config.ARGB_8888, false)

            result += "$rotationLabel-base" to base
            runCatching { ImageProcessor.enhanceForOcr(base) }.getOrNull()?.let { result += "$rotationLabel-document-clean" to it }
            runCatching { FileUtils.applyFilter(base, FilterType.BOOK_PAGE) }.getOrNull()?.let { result += "$rotationLabel-book-page" to it }
            runCatching { FileUtils.applyFilter(base, FilterType.SHARP_SCAN) }.getOrNull()?.let { result += "$rotationLabel-sharp-scan" to it }

            if (degrees == 0f || degrees == 180f) {
                val gray = toGrayscaleBitmap(base)
                result += "$rotationLabel-grayscale" to gray
                runCatching { adaptiveBinarizeBitmap(gray) }.getOrNull()?.let { result += "$rotationLabel-adaptive-bw" to it }
                runCatching { FileUtils.applyFilter(gray, FilterType.HIGH_CONTRAST) }.getOrNull()?.let { result += "$rotationLabel-high-contrast" to it }
            }

            if (scaled !== source && scaled !== oriented && scaled !== deskewed && !scaled.isRecycled) runCatching { scaled.recycle() }
            if (deskewed !== source && deskewed !== oriented && deskewed !== scaled && !deskewed.isRecycled) runCatching { deskewed.recycle() }
            if (oriented !== source && oriented !== deskewed && oriented !== scaled && !oriented.isRecycled) runCatching { oriented.recycle() }
        }

        return result.distinctBy { (_, bitmap) -> System.identityHashCode(bitmap) }
    }

    private fun ocrQualityScore(result: OcrExtractionResult): Double {
        if (result.text.isBlank() || result.text.startsWith("OCR failed", ignoreCase = true)) return Double.NEGATIVE_INFINITY
        val clean = result.text
        val textLengthScore = min(clean.length, 1800) / 1800.0 * 20.0
        val wordScore = min(result.wordCount, 260) * 0.34
        val confidenceScore = result.confidencePercent * 1.18
        val structureBonus = clean.count { it == '\n' }.coerceAtMost(24) * 0.45
        val englishBonus = listOf("system", "computer", "hardware", "software", "component", "example", "function", "question", "answer", "chapter", "definition")
            .count { clean.contains(it, ignoreCase = true) } * 2.2
        val readingOrderBonus = Regex("(?i)\\b(q\\.?|question|chapter|unit|definition|examples?)\\b").findAll(clean)
            .count()
            .coerceAtMost(10) * 1.4
        val upsideDownPenalty = listOf("uoitseuq", "retpahc", "metsys", "retupmoc", "elpmaxe", "noitinifed")
            .count { clean.contains(it, ignoreCase = true) } * 14.0
        val noisePenalty = clean.count { it == '�' || it == '¤' || it == '|' }.coerceAtMost(30) * 1.5 +
            Regex("[A-Za-z][0-9][A-Za-z]").findAll(clean).count().coerceAtMost(18) * 1.7 +
            Regex("[A-Z]{2}[a-z]{2,}").findAll(clean).count().coerceAtMost(18) * 0.7
        return confidenceScore + wordScore + textLengthScore + structureBonus + englishBonus + readingOrderBonus - upsideDownPenalty - noisePenalty
    }

    private fun toGrayscaleBitmap(source: Bitmap): Bitmap {
        val gray = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return gray
    }

    private fun adaptiveBinarizeBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height).also { source.getPixels(it, 0, width, 0, 0, width, height) }
        val lumas = IntArray(pixels.size)
        for (i in pixels.indices) {
            val px = pixels[i]
            lumas[i] = (Color.red(px) * 0.299f + Color.green(px) * 0.587f + Color.blue(px) * 0.114f).roundToInt().coerceIn(0, 255)
        }
        val sorted = lumas.copyOf().also { it.sort() }
        val low = sorted.percentile(0.18f)
        val high = sorted.percentile(0.86f).coerceAtLeast(low + 30)
        val threshold = ((low * 0.42f + high * 0.58f) - 6f).roundToInt().coerceIn(70, 218)
        val output = IntArray(pixels.size)
        for (i in lumas.indices) {
            val value = if (lumas[i] < threshold) 0 else 255
            output[i] = Color.rgb(value, value, value)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    private fun IntArray.percentile(fraction: Float): Int {
        if (isEmpty()) return 0
        val index = ((size - 1) * fraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, size - 1)
        return this[index]
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
