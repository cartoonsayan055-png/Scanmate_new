package com.synthbyte.scanmate.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.synthbyte.scanmate.core.SafeLogger

val ImageProcessingDispatcher = Dispatchers.Default.limitedParallelism(2)

object ImageProcessor {
    fun normalizeBitmapOrientation(source: Bitmap, file: File): Bitmap {
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
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    suspend fun saveEditedBitmap(context: Context, bitmap: Bitmap, sourceName: String = "EDITED"): File? {
        val name = "${sourceName}_${System.currentTimeMillis()}"
        return FileUtils.saveBitmapToFolder(context, bitmap, "Scans", name, Bitmap.CompressFormat.JPEG, 94)
    }

    fun duplicateImageFile(context: Context, sourcePath: String): File? {
        return try {
            val source = File(sourcePath)
            if (!source.exists() || source.length() == 0L) return null
            val copy = FileUtils.createUniqueImageFile(context)
            source.inputStream().use { input -> copy.outputStream().use { output -> input.copyTo(output) } }
            copy.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("ImageProcessor", "Operation failed", e)
            null
        }
    }

    fun decodeSampledBitmap(path: String, reqWidth: Int = 1600, reqHeight: Int = 1600): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            SafeLogger.e("ImageProcessor", "Operation failed", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    fun applyPerspectiveCorrection(
        file: File,
        corners: List<Offset>,
        previewWidth: Int,
        previewHeight: Int
    ): File? {
        if (corners.size != 4) return null
        val bitmap = decodeSampledBitmap(file.absolutePath, 3200, 3200) ?: return null
        val safePreviewWidth = previewWidth.coerceAtLeast(1)
        val safePreviewHeight = previewHeight.coerceAtLeast(1)
        val scaleX = bitmap.width.toFloat() / safePreviewWidth
        val scaleY = bitmap.height.toFloat() / safePreviewHeight
        val cornersAreNormalized = corners.all { it.x in 0f..1f && it.y in 0f..1f }
        fun mappedPoint(point: Offset): Offset = if (cornersAreNormalized) {
            Offset(point.x * bitmap.width, point.y * bitmap.height)
        } else {
            Offset(point.x * scaleX, point.y * scaleY)
        }

        val ordered = orderDocumentCorners(corners.map(::mappedPoint), bitmap.width, bitmap.height)
        if (!isValidDocumentPolygon(ordered, bitmap.width, bitmap.height)) {
            bitmap.recycle()
            return null
        }
        val warped = perspectiveWarp(bitmap, ordered) ?: run {
            bitmap.recycle()
            return null
        }
        bitmap.recycle()
        val enhanced = cleanDocumentBitmap(warped, preserveColor = true)
        if (enhanced !== warped && !warped.isRecycled) runCatching { warped.recycle() }
        val parent = file.parent ?: run {
            enhanced.recycle()
            return null
        }
        val out = File(parent, "warped_${file.name}")
        FileOutputStream(out).use { enhanced.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        enhanced.recycle()
        return out.takeIf { it.exists() && it.length() > 0L }
    }

    private fun orderDocumentCorners(points: List<Offset>, maxWidth: Int, maxHeight: Int): List<Offset> {
        val safe = points.map { Offset(it.x.coerceIn(0f, maxWidth.toFloat()), it.y.coerceIn(0f, maxHeight.toFloat())) }
        val topLeft = safe.minBy { it.x + it.y }
        val bottomRight = safe.maxBy { it.x + it.y }
        val topRight = safe.maxBy { it.x - it.y }
        val bottomLeft = safe.minBy { it.x - it.y }
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun distance(a: Offset, b: Offset): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropBitmapNormalized(source: Bitmap, leftPercent: Float, topPercent: Float, rightPercent: Float, bottomPercent: Float): Bitmap {
        if (source.width <= 1 || source.height <= 1) return source.copy(Bitmap.Config.ARGB_8888, false)

        val minWidthPx = min(96, source.width).coerceAtLeast(1)
        val minHeightPx = min(96, source.height).coerceAtLeast(1)
        var left = (source.width * leftPercent.coerceIn(0f, 0.95f)).roundToInt()
        var top = (source.height * topPercent.coerceIn(0f, 0.95f)).roundToInt()
        var right = (source.width * (1f - rightPercent.coerceIn(0f, 0.95f))).roundToInt()
        var bottom = (source.height * (1f - bottomPercent.coerceIn(0f, 0.95f))).roundToInt()

        left = left.coerceIn(0, source.width - 1)
        top = top.coerceIn(0, source.height - 1)
        right = right.coerceIn(left + 1, source.width)
        bottom = bottom.coerceIn(top + 1, source.height)

        if (right - left < minWidthPx) {
            val center = ((left + right) / 2f).roundToInt()
            left = (center - minWidthPx / 2).coerceIn(0, source.width - minWidthPx)
            right = (left + minWidthPx).coerceAtMost(source.width)
        }
        if (bottom - top < minHeightPx) {
            val center = ((top + bottom) / 2f).roundToInt()
            top = (center - minHeightPx / 2).coerceIn(0, source.height - minHeightPx)
            bottom = (top + minHeightPx).coerceAtMost(source.height)
        }

        return Bitmap.createBitmap(source, left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
    }

    fun autoCropDocument(source: Bitmap): Bitmap {
        if (source.width < 80 || source.height < 80) return source.copy(Bitmap.Config.ARGB_8888, false)
        val bounds = detectDocumentBounds(source) ?: return source.copy(Bitmap.Config.ARGB_8888, false)
        val left = bounds.left.roundToInt().coerceIn(0, source.width - 2)
        val top = bounds.top.roundToInt().coerceIn(0, source.height - 2)
        val right = bounds.right.roundToInt().coerceIn(left + 1, source.width)
        val bottom = bounds.bottom.roundToInt().coerceIn(top + 1, source.height)
        val width = (right - left).coerceAtLeast(64)
        val height = (bottom - top).coerceAtLeast(64)
        if (width < source.width * 0.30f || height < source.height * 0.30f) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val cropped = Bitmap.createBitmap(source, left, top, min(width, source.width - left), min(height, source.height - top))
        return cleanDocumentBitmap(cropped, preserveColor = true).also { cleaned ->
            if (cleaned !== cropped && !cropped.isRecycled) runCatching { cropped.recycle() }
        }
    }

    fun perspectiveCorrectBitmapNormalized(
        source: Bitmap,
        topLeftX: Float,
        topLeftY: Float,
        topRightX: Float,
        topRightY: Float,
        bottomRightX: Float,
        bottomRightY: Float,
        bottomLeftX: Float,
        bottomLeftY: Float
    ): Bitmap {
        if (source.width < 80 || source.height < 80) return source.copy(Bitmap.Config.ARGB_8888, false)
        val corners = listOf(
            Offset(topLeftX.coerceIn(0f, 0.45f), topLeftY.coerceIn(0f, 0.45f)),
            Offset(1f - topRightX.coerceIn(0f, 0.45f), topRightY.coerceIn(0f, 0.45f)),
            Offset(1f - bottomRightX.coerceIn(0f, 0.45f), 1f - bottomRightY.coerceIn(0f, 0.45f)),
            Offset(bottomLeftX.coerceIn(0f, 0.45f), 1f - bottomLeftY.coerceIn(0f, 0.45f))
        )
        return perspectiveCorrectBitmapFromCorners(source, corners)
    }

    fun perspectiveCorrectBitmapFromCorners(source: Bitmap, corners: List<Offset>): Bitmap {
        if (source.width < 80 || source.height < 80 || corners.size != 4) return source.copy(Bitmap.Config.ARGB_8888, false)
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val points = corners.map { point ->
            Offset(point.x.coerceIn(0f, 1f) * w, point.y.coerceIn(0f, 1f) * h)
        }
        val ordered = orderDocumentCorners(points, source.width, source.height)
        if (!isValidDocumentPolygon(ordered, source.width, source.height)) return source.copy(Bitmap.Config.ARGB_8888, false)
        val warped = perspectiveWarp(source, ordered)
            ?: return source.copy(Bitmap.Config.ARGB_8888, false)
        return cleanDocumentBitmap(warped, preserveColor = true).also { cleaned ->
            if (cleaned !== warped && !warped.isRecycled) runCatching { warped.recycle() }
        }
    }

    fun enhanceForOcr(source: Bitmap): Bitmap {
        val cropped = autoCropDocument(source)
        val cleaned = cleanDocumentBitmap(cropped, preserveColor = false)
        if (cleaned !== cropped && !cropped.isRecycled) runCatching { cropped.recycle() }
        return cleaned
    }

    private fun perspectiveWarp(source: Bitmap, ordered: List<Offset>): Bitmap? {
        if (ordered.size != 4) return null
        val tl = ordered[0]
        val tr = ordered[1]
        val br = ordered[2]
        val bl = ordered[3]
        val rawWidth = max(distance(tl, tr), distance(bl, br)).roundToInt().coerceAtLeast(320)
        val rawHeight = max(distance(tl, bl), distance(tr, br)).roundToInt().coerceAtLeast(320)
        val maxOutputSide = 3400
        val outputScale = min(1f, maxOutputSide.toFloat() / max(rawWidth, rawHeight).toFloat())
        val targetWidth = (rawWidth * outputScale).roundToInt().coerceAtLeast(320)
        val targetHeight = (rawHeight * outputScale).roundToInt().coerceAtLeast(320)
        val src = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return null
        return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { warped ->
            val canvas = Canvas(warped)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        }
    }

    private fun detectDocumentBounds(source: Bitmap): RectF? {
            val sampled = source.scaleDownToMax(900) ?: return null
            val width = sampled.width
            val height = sampled.height
            if (width < 80 || height < 80) {
                if (sampled !== source && !sampled.isRecycled) runCatching { sampled.recycle() }
                return null
            }
    
            fun finish(result: RectF?): RectF? {
                if (sampled !== source && !sampled.isRecycled) runCatching { sampled.recycle() }
                return result
            }
    
            val pixels = IntArray(width * height).also { sampled.getPixels(it, 0, width, 0, 0, width, height) }
            val luma = IntArray(pixels.size)
            for (i in pixels.indices) {
                val px = pixels[i]
                luma[i] = (Color.red(px) * 0.299f + Color.green(px) * 0.587f + Color.blue(px) * 0.114f).roundToInt().coerceIn(0, 255)
            }
    
            val step = max(1, max(width, height) / 300)
            val border = ArrayList<Int>(width * 2 + height * 2)
            for (x in 0 until width step step) {
                border += luma[x]
                border += luma[(height - 1) * width + x]
            }
            for (y in 0 until height step step) {
                border += luma[y * width]
                border += luma[y * width + width - 1]
            }
    
            val sortedLuma = luma.copyOf().also { it.sort() }
            val sortedBorder = border.sorted()
            val background = sortedBorder.percentile(0.50f)
            val p12 = sortedLuma.percentile(0.12f)
            val p22 = sortedLuma.percentile(0.22f)
            val p55 = sortedLuma.percentile(0.55f)
            val p86 = sortedLuma.percentile(0.86f)
            val contrastRange = (p86 - p12).coerceAtLeast(1)
            if (contrastRange < 18) return finish(null)
    
            val brightPageThreshold = max(background + 14, p55 + max(7, contrastRange / 8)).coerceIn(78, 246)
            val darkContentThreshold = min(background - 18, p22 + 12).coerceIn(18, 214)
            val rowProjection = IntArray(height)
            val colProjection = IntArray(width)
    
            for (y in 1 until height - 1 step step) {
                val row = y * width
                for (x in 1 until width - 1 step step) {
                    val index = row + x
                    val lum = luma[index]
                    val gx = abs(luma[index - 1] - luma[index + 1])
                    val gy = abs(luma[index - width] - luma[index + width])
                    val gradient = gx + gy
                    val likelyBrightPage = lum >= brightPageThreshold && lum > background + 6
                    val likelyDarkPageOrInk = background > 174 && lum <= darkContentThreshold
                    val strongEdge = gradient >= max(24, contrastRange / 5)
                    if (likelyBrightPage || likelyDarkPageOrInk || strongEdge) {
                        val weight = when {
                            likelyBrightPage -> 5
                            strongEdge -> 3
                            else -> 1
                        }
                        rowProjection[y] += weight
                        colProjection[x] += weight
                    }
                }
            }
    
            val horizontal = activeProjectionRange(colProjection, minLength = (width * 0.30f).roundToInt()) ?: return finish(null)
            val vertical = activeProjectionRange(rowProjection, minLength = (height * 0.30f).roundToInt()) ?: return finish(null)
            val detectedWidth = horizontal.second - horizontal.first + 1
            val detectedHeight = vertical.second - vertical.first + 1
            val sampleAreaRatio = detectedWidth.toFloat() * detectedHeight.toFloat() / (width.toFloat() * height.toFloat()).coerceAtLeast(1f)
            val sampleAspect = detectedWidth.toFloat() / detectedHeight.toFloat().coerceAtLeast(1f)
            if (sampleAreaRatio !in 0.20f..0.990f || sampleAspect !in 0.24f..4.25f) return finish(null)
    
            val projectionStrength = (
                rowProjection.sliceArray(vertical.first..vertical.second).average() +
                    colProjection.sliceArray(horizontal.first..horizontal.second).average()
                ) / 2.0
            val peakStrength = max(rowProjection.maxOrNull() ?: 0, colProjection.maxOrNull() ?: 0).coerceAtLeast(1)
            if (projectionStrength < peakStrength * 0.045) return finish(null)
    
            // Conservative outward padding: keep content over trimming too tightly.
            val marginX = max(8, (width * 0.045f).roundToInt())
            val marginY = max(8, (height * 0.045f).roundToInt())
            val sx = source.width.toFloat() / width.toFloat()
            val sy = source.height.toFloat() / height.toFloat()
            val left = ((horizontal.first - marginX).coerceAtLeast(0) * sx)
            val top = ((vertical.first - marginY).coerceAtLeast(0) * sy)
            val right = ((horizontal.second + marginX).coerceAtMost(width - 1) * sx)
            val bottom = ((vertical.second + marginY).coerceAtMost(height - 1) * sy)
    
            val boxWidth = right - left
            val boxHeight = bottom - top
            val areaRatio = (boxWidth * boxHeight) / (source.width.toFloat() * source.height.toFloat()).coerceAtLeast(1f)
            val aspect = boxWidth / boxHeight.coerceAtLeast(1f)
            if (boxWidth < source.width * 0.32f || boxHeight < source.height * 0.32f) return finish(null)
            return finish(if (areaRatio in 0.20f..0.995f && aspect in 0.24f..4.25f) RectF(left, top, right, bottom) else null)
        }

    private fun isValidDocumentPolygon(points: List<Offset>, maxWidth: Int, maxHeight: Int): Boolean {
            if (points.size != 4 || maxWidth <= 0 || maxHeight <= 0) return false
            if (points.any { it.x !in 0f..maxWidth.toFloat() || it.y !in 0f..maxHeight.toFloat() }) return false
            if (segmentsIntersect(points[0], points[1], points[2], points[3])) return false
            if (segmentsIntersect(points[1], points[2], points[3], points[0])) return false
    
            val area = abs(
                points.indices.sumOf { i ->
                    val a = points[i]
                    val b = points[(i + 1) % points.size]
                    (a.x * b.y - b.x * a.y).toDouble()
                }.toFloat()
            ) / 2f
            val imageArea = maxWidth.toFloat() * maxHeight.toFloat()
            val areaRatio = area / imageArea.coerceAtLeast(1f)
            val topWidth = distance(points[0], points[1])
            val bottomWidth = distance(points[3], points[2])
            val leftHeight = distance(points[0], points[3])
            val rightHeight = distance(points[1], points[2])
            val minSide = min(min(topWidth, bottomWidth), min(leftHeight, rightHeight))
            val aspect = max(topWidth, bottomWidth) / max(leftHeight, rightHeight).coerceAtLeast(1f)
    
            // Reject crossed, tiny, unstable, or extreme polygons and fall back safely.
            return areaRatio in 0.12f..0.99f &&
                aspect in 0.20f..5.00f &&
                minSide >= min(maxWidth, maxHeight) * 0.12f &&
                points[0].x < points[1].x &&
                points[3].x < points[2].x &&
                points[0].y < points[3].y &&
                points[1].y < points[2].y
        }
    
        private fun segmentsIntersect(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
            fun orientation(p: Offset, q: Offset, r: Offset): Float {
                return (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
            }
            val o1 = orientation(a, b, c)
            val o2 = orientation(a, b, d)
            val o3 = orientation(c, d, a)
            val o4 = orientation(c, d, b)
            return o1 * o2 < 0f && o3 * o4 < 0f
        }

    private fun activeProjectionRange(values: IntArray, minLength: Int): Pair<Int, Int>? {
            if (values.isEmpty()) return null
            val radius = max(3, values.size / 180).coerceAtMost(9)
            val smoothed = IntArray(values.size)
            for (i in values.indices) {
                var sum = 0
                var count = 0
                for (j in i - radius..i + radius) {
                    if (j in values.indices) {
                        sum += values[j]
                        count += 1
                    }
                }
                smoothed[i] = if (count == 0) values[i] else sum / count
            }
            val peak = smoothed.maxOrNull() ?: return null
            if (peak <= 0) return null
            val sorted = smoothed.copyOf().also { it.sort() }
            val noiseFloor = sorted.percentile(0.55f)
            val threshold = max(noiseFloor + 1, (peak * 0.14f).roundToInt()).coerceAtLeast(2)
            var bestStart = -1
            var bestEnd = -1
            var bestScore = Int.MIN_VALUE
            var start = -1
            var gap = 0
            val maxGap = max(6, values.size / 90).coerceAtMost(14)
    
            fun commit(endExclusive: Int) {
                if (start < 0) return
                val end = (endExclusive - gap - 1).coerceAtLeast(start)
                val length = end - start + 1
                if (length >= minLength) {
                    val score = (start..end).sumOf { smoothed[it] } + length * 7
                    if (score > bestScore) {
                        bestScore = score
                        bestStart = start
                        bestEnd = end
                    }
                }
                start = -1
                gap = 0
            }
            for (i in smoothed.indices) {
                if (smoothed[i] >= threshold) {
                    if (start < 0) start = i
                    gap = 0
                } else if (start >= 0) {
                    gap += 1
                    if (gap > maxGap) commit(i + 1)
                }
            }
            commit(smoothed.size)
            return if (bestStart >= 0 && bestEnd > bestStart) bestStart to bestEnd else null
        }

    private fun cleanDocumentBitmap(source: Bitmap, preserveColor: Boolean): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(width * height).also { source.getPixels(it, 0, width, 0, 0, width, height) }
        val lumas = IntArray(pixels.size)
        for (i in pixels.indices) {
            val px = pixels[i]
            lumas[i] = (Color.red(px) * 0.299f + Color.green(px) * 0.587f + Color.blue(px) * 0.114f).roundToInt().coerceIn(0, 255)
        }
        val sorted = lumas.copyOf().also { it.sort() }
        val shadow = sorted.percentile(0.10f).toFloat()
        val paper = sorted.percentile(0.90f).toFloat().coerceAtLeast(shadow + 35f)
        val range = (paper - shadow).coerceAtLeast(48f)
        val output = IntArray(pixels.size)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = Color.red(px)
            val g = Color.green(px)
            val b = Color.blue(px)
            val lum = lumas[i].toFloat()
            val normalized = ((lum - shadow) * 255f / range).coerceIn(0f, 255f)
            val boosted = when {
                normalized > 225f -> 255f
                normalized < 45f -> normalized * 0.60f
                else -> ((normalized - 128f) * 1.16f + 136f).coerceIn(0f, 255f)
            }
            output[i] = if (preserveColor) {
                val factor = (boosted / lum.coerceAtLeast(1f)).coerceIn(0.52f, 1.65f)
                Color.rgb((r * factor).roundToInt().coerceIn(0, 255), (g * factor).roundToInt().coerceIn(0, 255), (b * factor).roundToInt().coerceIn(0, 255))
            } else {
                val value = boosted.roundToInt().coerceIn(0, 255)
                Color.rgb(value, value, value)
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { result ->
            result.setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    private fun IntArray.percentile(fraction: Float): Int {
        if (isEmpty()) return 0
        val index = ((size - 1) * fraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, size - 1)
        return this[index]
    }

    private fun List<Int>.percentile(fraction: Float): Int {
        if (isEmpty()) return 0
        val index = ((size - 1) * fraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, size - 1)
        return this[index]
    }

    fun removeMarksFromBitmap(source: Bitmap, sensitivity: Float = 0.18f): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        val output = pixels.copyOf()
        val threshold = (255 * (1f - sensitivity)).toInt().coerceIn(160, 245)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val lum = (Color.red(pixels[idx]) * 0.299f + Color.green(pixels[idx]) * 0.587f + Color.blue(pixels[idx]) * 0.114f).toInt()
                if (lum < threshold) {
                    val ns = listOf(
                        pixels[(y - 1) * w + x],
                        pixels[(y + 1) * w + x],
                        pixels[y * w + x - 1],
                        pixels[y * w + x + 1],
                        pixels[(y - 1) * w + x - 1],
                        pixels[(y - 1) * w + x + 1],
                        pixels[(y + 1) * w + x - 1],
                        pixels[(y + 1) * w + x + 1]
                    )
                    val al = ns.map { Color.red(it) * 0.299f + Color.green(it) * 0.587f + Color.blue(it) * 0.114f }.average()
                    if (al > threshold + 30) {
                        output[idx] = Color.rgb(
                            ns.map { Color.red(it) }.average().toInt(),
                            ns.map { Color.green(it) }.average().toInt(),
                            ns.map { Color.blue(it) }.average().toInt()
                        )
                    }
                }
            }
        }
        return Bitmap.createBitmap(w, h, source.config ?: Bitmap.Config.ARGB_8888).also { it.setPixels(output, 0, w, 0, 0, w, h) }
    }

    fun removeShadowFromBitmap(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        val lums = pixels.map { Color.red(it) * 0.299f + Color.green(it) * 0.587f + Color.blue(it) * 0.114f }
        val p95 = lums.sorted().let { it[(it.size * 0.95f).toInt().coerceAtMost(it.lastIndex)] }
        val scale = if (p95 > 0f) 255f / p95 else 1f
        val output = pixels.map {
            Color.rgb(
                (Color.red(it) * scale).toInt().coerceIn(0, 255),
                (Color.green(it) * scale).toInt().coerceIn(0, 255),
                (Color.blue(it) * scale).toInt().coerceIn(0, 255)
            )
        }.toIntArray()
        return Bitmap.createBitmap(w, h, source.config ?: Bitmap.Config.ARGB_8888).also { it.setPixels(output, 0, w, 0, 0, w, h) }
    }

    fun deskewBitmap(source: Bitmap): Bitmap {
        val maxSide = 1200
        val sc = maxSide.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
        val work = if (sc < 1f) Bitmap.createScaledBitmap(source, (source.width * sc).toInt().coerceAtLeast(1), (source.height * sc).toInt().coerceAtLeast(1), true) else source
        val pixels = IntArray(work.width * work.height).also { work.getPixels(it, 0, work.width, 0, 0, work.width, work.height) }
        val binary = BooleanArray(pixels.size) { i -> Color.red(pixels[i]) < 128 }
        var bestAngle = 0f
        var bestScore = Double.NEGATIVE_INFINITY
        generateSequence(-10f) { if (it + 0.5f <= 10f) it + 0.5f else null }.forEach { angle ->
            val rad = Math.toRadians(angle.toDouble())
            val rowSums = IntArray(work.height)
            for (y in 0 until work.height) {
                for (x in 0 until work.width) {
                    val rx = (x * kotlin.math.cos(rad) - y * kotlin.math.sin(rad)).toInt()
                    val ry = (x * kotlin.math.sin(rad) + y * kotlin.math.cos(rad)).toInt()
                    if (rx in 0 until work.width && ry in 0 until work.height && binary[ry * work.width + rx]) rowSums[y]++
                }
            }
            val mean = rowSums.average()
            val v = rowSums.sumOf { value -> (value - mean) * (value - mean) } / work.height
            if (v > bestScore) {
                bestScore = v
                bestAngle = angle
            }
        }
        if (work !== source) runCatching { work.recycle() }
        if (kotlin.math.abs(bestAngle) < 0.4f) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(-bestAngle) }, true)
    }

    fun applyFilter(original: Bitmap, type: FilterType): Bitmap {
        val safe = original.copy(Bitmap.Config.ARGB_8888, false)
        if (type == FilterType.ORIGINAL) return safe
        if (type == FilterType.SHARPEN || type == FilterType.SHARP_SCAN) return sharpenBitmap(safe)
        if (type == FilterType.WHITEBOARD) {
            return cleanDocumentBitmap(safe, preserveColor = false).also {
                if (!safe.isRecycled) runCatching { safe.recycle() }
            }
        }

        val result = Bitmap.createBitmap(safe.width, safe.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val colorMatrix = ColorMatrix()

        when (type) {
            FilterType.ORIGINAL -> Unit
            FilterType.GRAYSCALE -> colorMatrix.setSaturation(0f)
            FilterType.BLACK_WHITE -> {
                colorMatrix.setSaturation(0f)
                colorMatrix.postConcat(contrastMatrix(1.85f, -85f))
            }
            FilterType.ENHANCED_COLOR, FilterType.MAGIC_COLOR -> {
                colorMatrix.setSaturation(1.35f)
                colorMatrix.postConcat(contrastMatrix(1.18f, 10f))
            }
            FilterType.LIGHTEN -> colorMatrix.set(contrastMatrixValues(1.08f, 34f))
            FilterType.SHARPEN, FilterType.SHARP_SCAN -> Unit
            FilterType.HIGH_CONTRAST -> colorMatrix.set(contrastMatrixValues(1.45f, -38f))
            FilterType.SOFT_SCAN -> {
                colorMatrix.setSaturation(0.82f)
                colorMatrix.postConcat(contrastMatrix(1.04f, 28f))
            }
            FilterType.RECEIPT_MODE -> {
                colorMatrix.setSaturation(0f)
                colorMatrix.postConcat(contrastMatrix(1.62f, -28f))
            }
            FilterType.BOOK_PAGE -> {
                colorMatrix.setSaturation(0.68f)
                colorMatrix.postConcat(contrastMatrix(1.12f, 38f))
            }
            FilterType.LOW_LIGHT_CLEANUP -> {
                colorMatrix.setSaturation(1.08f)
                colorMatrix.postConcat(contrastMatrix(1.16f, 54f))
            }
            FilterType.SHADOW_REDUCTION -> {
                colorMatrix.setSaturation(0.92f)
                colorMatrix.postConcat(contrastMatrix(1.1f, 42f))
            }
            FilterType.WHITEBOARD -> Unit
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(safe, 0f, 0f, paint)
        if (!safe.isRecycled) runCatching { safe.recycle() }
        return result
    }

    fun drawSignatureOnBitmap(pageBitmap: Bitmap, signatureBitmap: Bitmap, alignRight: Boolean = true): Bitmap {
        val result = pageBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val targetWidth = (pageBitmap.width * 0.34f).roundToInt().coerceAtLeast(120)
        val scale = targetWidth.toFloat() / signatureBitmap.width.toFloat().coerceAtLeast(1f)
        val targetHeight = (signatureBitmap.height * scale).roundToInt().coerceAtLeast(60)
        val margin = (pageBitmap.width * 0.06f).roundToInt().coerceAtLeast(24)
        val left = if (alignRight) pageBitmap.width - targetWidth - margin else margin
        val top = pageBitmap.height - targetHeight - margin
        val rect = RectF(left.toFloat(), top.toFloat(), (left + targetWidth).toFloat(), (top + targetHeight).toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(signatureBitmap, null, rect, paint)
        return result
    }

    fun drawWatermarkOnBitmap(source: Bitmap, text: String = "ScanMate"): Bitmap {
        val cleanText = text.trim().ifBlank { "ScanMate" }.take(70)
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val textSize = max(30f, source.width * 0.038f)
        val margin = max(28f, source.width * 0.045f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.argb(82, 0, 0, 0)
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            setShadowLayer(textSize * 0.08f, 1.5f, 1.5f, Color.argb(70, 255, 255, 255))
        }
        canvas.drawText(cleanText, source.width - margin, source.height - margin, paint)
        return result
    }

    fun drawNoteStampOnBitmap(source: Bitmap, text: String): Bitmap {
        val cleanText = text.trim().ifBlank { "Reviewed in ScanMate" }.take(140)
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val textSize = max(28f, source.width * 0.032f)
        val margin = max(24f, source.width * 0.035f)
        val maxChars = 34
        val lines = cleanText.chunked(maxChars).take(3)
        val lineHeight = textSize * 1.35f
        val boxWidth = source.width * 0.78f
        val boxHeight = lineHeight * lines.size.toFloat() + margin
        val rect = RectF(margin, margin, margin + boxWidth, margin + boxHeight)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(214, 255, 255, 255) }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 30, 30, 30)
            style = Paint.Style.STROKE
            strokeWidth = max(2f, source.width * 0.002f)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.argb(230, 20, 20, 20)
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawRoundRect(rect, 18f, 18f, boxPaint)
        canvas.drawRoundRect(rect, 18f, 18f, strokePaint)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, margin * 1.45f, margin + lineHeight * (index.toFloat() + 0.95f), textPaint)
        }
        return result
    }




    private fun contrastMatrix(contrast: Float, brightness: Float): ColorMatrix = ColorMatrix(contrastMatrixValues(contrast, brightness))

    private fun contrastMatrixValues(contrast: Float, brightness: Float): FloatArray = floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    )

    private fun sharpenBitmap(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.05f) })
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 42 }
        canvas.drawBitmap(source, 0f, 0f, overlayPaint)
        return result
    }

    private fun Bitmap.scaleDownToMax(maxSide: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val side = max(width, height)
        if (side <= maxSide) return this
        val ratio = maxSide.toFloat() / side.toFloat()
        val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    fun compressBitmap(source: Bitmap, quality: Int = 90, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray {
        return ByteArrayOutputStream().use { output ->
            source.compress(format, quality.coerceIn(1, 100), output)
            output.toByteArray()
        }
    }

    fun applyWatermark(source: Bitmap, text: String = "ScanMate"): Bitmap = drawWatermarkOnBitmap(source, text)
}
