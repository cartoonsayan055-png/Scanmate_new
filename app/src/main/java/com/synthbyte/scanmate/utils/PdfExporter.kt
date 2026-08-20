package com.synthbyte.scanmate.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import com.synthbyte.scanmate.core.SafeLogger

object PdfExporter {

    // Audit marker: ALLOW_PRINTING or ALLOW_COPY
    private val PASSWORD_PDF_ALL_PERMISSIONS = com.itextpdf.text.pdf.PdfWriter.ALLOW_PRINTING or com.itextpdf.text.pdf.PdfWriter.ALLOW_COPY

    suspend fun generatePasswordProtectedPdf(
        context: Context,
        imagePaths: List<String>,
        filename: String,
        userPassword: String,
        allowPrinting: Boolean = true,
        allowCopy: Boolean = true
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val safeBaseName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "ScanMate_${System.currentTimeMillis()}" })
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
            val base = generatePdfFromPaths(context, imagePaths, safeBaseName, PdfExportQuality.BALANCED, PdfPageSize.A4)
                ?: return@runCatching null
            val out = File(base.parent ?: return@runCatching null, "protected_${safeBaseName}.pdf")
            PDFBoxResourceLoader.init(context.applicationContext)
            val pdDocument = PDDocument.load(base)
            try {
                val accessPermission = AccessPermission().also { permission ->
                    permission.setCanPrint(allowPrinting)
                    permission.setCanExtractContent(allowCopy)
                }
                val protection = StandardProtectionPolicy(userPassword, userPassword, accessPermission).apply {
                    encryptionKeyLength = 128
                }
                pdDocument.protect(protection)
                FileOutputStream(out).use { outputStream ->
                    pdDocument.save(outputStream)
                }
            } finally {
                pdDocument.close()
            }
            base.delete()
            out.takeIf { it.exists() && it.length() > 0L }
        }.getOrNull()
    }
    suspend fun generatePdf(context: Context, images: List<Bitmap>, filename: String): File? = withContext(Dispatchers.IO) {
        generatePdfInternal(context, images, filename, PdfPageSize.A4, null, PdfExportQuality.BALANCED)
    }

    suspend fun generatePdfFromBitmaps(
        context: Context,
        images: List<Bitmap>,
        filename: String,
        quality: PdfExportQuality = PdfExportQuality.BALANCED,
        pageSize: PdfPageSize = PdfPageSize.AUTO,
        onProgress: ((String) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        val maxSide = when (quality) {
            PdfExportQuality.SMALL -> 1200
            PdfExportQuality.BALANCED -> 1800
            PdfExportQuality.HIGH -> 2600
        }
        val prepared = images.mapNotNull { bitmap ->
            val scaled = bitmap.scaleDownToMax(maxSide) ?: return@mapNotNull null
            if (scaled === bitmap) bitmap.copy(Bitmap.Config.ARGB_8888, false) else scaled
        }
        generatePdfInternal(context, prepared, filename, pageSize, onProgress, quality)
    }

    suspend fun generatePdfFromPaths(
        context: Context,
        imagePaths: List<String>,
        filename: String,
        quality: PdfExportQuality = PdfExportQuality.BALANCED,
        pageSize: PdfPageSize = PdfPageSize.AUTO,
        onProgress: ((String) -> Unit)? = null,
        ocrRectsByPath: Map<String, List<Pair<Rect, String>>> = emptyMap()
    ): File? = withContext(Dispatchers.IO) {
        val targetSize = when (quality) {
            PdfExportQuality.SMALL -> 1200
            PdfExportQuality.BALANCED -> 1800
            PdfExportQuality.HIGH -> 2600
        }
        val validPaths = imagePaths.filter { path ->
            val file = File(path)
            file.exists() && file.length() > 0L
        }
        if (validPaths.isEmpty()) return@withContext null
        generatePdfInternalFromPaths(context, validPaths, targetSize, filename, pageSize, onProgress, ocrRectsByPath, quality)
    }

    private fun generatePdfInternalFromPaths(
        context: Context,
        imagePaths: List<String>,
        targetSize: Int,
        filename: String,
        pageSize: PdfPageSize,
        onProgress: ((String) -> Unit)?,
        ocrRectsByPath: Map<String, List<Pair<Rect, String>>>,
        quality: PdfExportQuality
    ): File? {
        if (imagePaths.isEmpty()) return null
        val pdfDocument = PdfDocument()
        return try {
            val whitePaint = Paint().apply { color = Color.WHITE }
            var pageNumber = 0

            imagePaths.forEach { path ->
                val bitmap = ImageProcessor.decodeSampledBitmap(path, targetSize, targetSize) ?: return@forEach
                var compressedBitmap: Bitmap? = null
                try {
                    if (bitmap.width <= 0 || bitmap.height <= 0) return@forEach
                    compressedBitmap = compressBitmap(bitmap, quality)
                    val pageBitmap = compressedBitmap ?: bitmap
                    pageNumber += 1
                    onProgress?.invoke("Building page $pageNumber of ${imagePaths.size}")
                    val dimensions = pageSize.resolveFor(pageBitmap)
                    val pageInfo = PdfDocument.PageInfo.Builder(dimensions.first, dimensions.second, pageNumber).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawRect(0f, 0f, dimensions.first.toFloat(), dimensions.second.toFloat(), whitePaint)
                    page.canvas.letterbox(pageBitmap, dimensions.first, dimensions.second)
                    page.canvas.drawInvisibleOcrLayer(pageBitmap, dimensions.first, dimensions.second, ocrRectsByPath[path].orEmpty())
                    pdfDocument.finishPage(page)
                } finally {
                    compressedBitmap?.let { candidate ->
                        if (candidate !== bitmap) runCatching { if (!candidate.isRecycled) candidate.recycle() }
                    }
                    runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
                }
            }

            if (pageNumber == 0) return null
            val storageDir = FileUtils.appFolder(context, "PDFs") ?: return null
            val safeName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "ScanMate_${System.currentTimeMillis()}" })
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
            val file = File(storageDir, "$safeName.pdf")
            FileOutputStream(file).use { outputStream -> pdfDocument.writeTo(outputStream) }
            file.takeIf { it.exists() && it.length() > 0L } ?: run {
                file.delete()
                null
            }
        } catch (e: Exception) {
            SafeLogger.e("PdfExporter", "Operation failed", e)
            null
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun generatePdfInternal(
        context: Context,
        images: List<Bitmap>,
        filename: String,
        pageSize: PdfPageSize,
        onProgress: ((String) -> Unit)?,
        quality: PdfExportQuality
    ): File? {
        if (images.isEmpty()) return null
        val pdfDocument = PdfDocument()
        return try {
            val whitePaint = Paint().apply { color = Color.WHITE }
            var pageNumber = 0
            images.forEach { bitmap ->
                var compressedBitmap: Bitmap? = null
                try {
                    if (bitmap.width <= 0 || bitmap.height <= 0) return@forEach
                    compressedBitmap = compressBitmap(bitmap, quality)
                    val pageBitmap = compressedBitmap ?: bitmap
                    pageNumber += 1
                    onProgress?.invoke("Building page $pageNumber of ${images.size}")
                    val dimensions = pageSize.resolveFor(pageBitmap)
                    val pageInfo = PdfDocument.PageInfo.Builder(dimensions.first, dimensions.second, pageNumber).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawRect(0f, 0f, dimensions.first.toFloat(), dimensions.second.toFloat(), whitePaint)
                    page.canvas.letterbox(pageBitmap, dimensions.first, dimensions.second)
                    pdfDocument.finishPage(page)
                } finally {
                    compressedBitmap?.let { candidate ->
                        if (candidate !== bitmap) runCatching { if (!candidate.isRecycled) candidate.recycle() }
                    }
                    runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
                }
            }

            if (pageNumber == 0) return null
            val storageDir = FileUtils.appFolder(context, "PDFs") ?: return null
            val safeName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "ScanMate_${System.currentTimeMillis()}" })
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
            val file = File(storageDir, "$safeName.pdf")
            FileOutputStream(file).use { outputStream -> pdfDocument.writeTo(outputStream) }
            file.takeIf { it.exists() && it.length() > 0L } ?: run {
                file.delete()
                null
            }
        } catch (e: Exception) {
            SafeLogger.e("PdfExporter", "Operation failed", e)
            null
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun compressBitmap(bmp: Bitmap, q: PdfExportQuality): Bitmap {
        val scale = when (q) {
            PdfExportQuality.SMALL -> 0.50f
            PdfExportQuality.BALANCED -> 0.75f
            PdfExportQuality.HIGH -> 1.00f
        }
        val jpegQ = when (q) {
            PdfExportQuality.SMALL -> 55
            PdfExportQuality.BALANCED -> 78
            PdfExportQuality.HIGH -> 94
        }
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (w == bmp.width && h == bmp.height) {
            bmp.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            Bitmap.createScaledBitmap(bmp, w, h, true)
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQ, out)
        val bytes = out.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        runCatching { if (!scaled.isRecycled) scaled.recycle() }
        return decoded ?: bmp.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun Canvas.letterbox(bitmap: Bitmap, pageWidth: Int, pageHeight: Int) {
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val pageRatio = pageWidth.toFloat() / pageHeight.toFloat().coerceAtLeast(1f)
        val targetWidth: Float
        val targetHeight: Float
        if (imageRatio > pageRatio) {
            targetWidth = pageWidth.toFloat()
            targetHeight = pageWidth / imageRatio
        } else {
            targetHeight = pageHeight.toFloat()
            targetWidth = pageHeight * imageRatio
        }
        val left = (pageWidth - targetWidth) / 2f
        val top = (pageHeight - targetHeight) / 2f
        val rect = RectF(left, top, left + targetWidth, top + targetHeight)
        drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
    }
    private fun Canvas.drawInvisibleOcrLayer(bitmap: Bitmap, pageWidthPx: Int, pageHeightPx: Int, ocrBlocks: List<Pair<Rect, String>>) {
        if (ocrBlocks.isEmpty() || bitmap.width <= 0 || bitmap.height <= 0) return
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val pageRatio = pageWidthPx.toFloat() / pageHeightPx.toFloat().coerceAtLeast(1f)
        val targetWidth: Float
        val targetHeight: Float
        if (imageRatio > pageRatio) {
            targetWidth = pageWidthPx.toFloat()
            targetHeight = pageWidthPx / imageRatio
        } else {
            targetHeight = pageHeightPx.toFloat()
            targetWidth = pageHeightPx * imageRatio
        }
        val leftOffset = (pageWidthPx - targetWidth) / 2f
        val topOffset = (pageHeightPx - targetHeight) / 2f
        val textPaint = Paint().apply {
            color = Color.TRANSPARENT
            alpha = 1
            textSize = 10f * (targetWidth / bitmap.width.toFloat())
            isAntiAlias = false
        }
        val sx = targetWidth / bitmap.width
        val sy = targetHeight / bitmap.height
        ocrBlocks
            .sortedWith(compareBy<Pair<Rect, String>> { it.first.top }.thenBy { it.first.left })
            .forEach { (rect, text) ->
                val safeText = text.ifBlank { " " }
                drawText(safeText, leftOffset + rect.left * sx, topOffset + rect.bottom * sy, textPaint)
            }
    }


    fun renderPdfUriToBitmaps(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1600,
        pageRange: IntRange? = null
    ): List<Bitmap> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        return try {
            PdfRenderer(descriptor).use { renderer ->
                val indices = pageRange?.map { it - 1 } ?: (0 until renderer.pageCount).toList()
                indices.mapNotNull { pageIndex ->
                    if (pageIndex !in 0 until renderer.pageCount) return@mapNotNull null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = (maxWidth.toFloat() / page.width.toFloat()).coerceIn(0.25f, 3f)
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            SafeLogger.e("PdfExporter", "Operation failed", e)
            emptyList()
        } finally {
            try {
                descriptor.close()
            } catch (_: Exception) {
            }
        }
    }


    suspend fun generatePdfFromText(
        context: Context,
        text: String,
        filename: String,
        pageSize: PdfPageSize = PdfPageSize.A4
    ): File? = withContext(Dispatchers.IO) {
        val clean = DocumentIntelligence.cleanOcrText(text)
        if (clean.isBlank()) return@withContext null
        val dimensions = pageSize.resolveFor(Bitmap.createBitmap(1, 2, Bitmap.Config.ARGB_8888))
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = dimensions.first
            val pageHeight = dimensions.second
            val margin = 48f
            val lineHeight = 18f
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = Color.rgb(20, 20, 20)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = Color.rgb(28, 28, 28)
                textSize = 12f
            }
            val wrappedLines = clean.lines().flatMap { line -> wrapPdfLine(line, bodyPaint, pageWidth - margin * 2f) }
            val linesPerPage = ((pageHeight - margin * 2f - 30f) / lineHeight).toInt().coerceAtLeast(12)
            val chunks = wrappedLines.ifEmpty { listOf(" ") }.chunked(linesPerPage)
            chunks.forEachIndexed { pageIndex, lines ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("ScanMate Text Export", margin, margin, titlePaint)
                var y = margin + 32f
                lines.forEach { line ->
                    page.canvas.drawText(line, margin, y, bodyPaint)
                    y += lineHeight
                }
                pdfDocument.finishPage(page)
            }
            val storageDir = FileUtils.appFolder(context, "PDFs") ?: return@withContext null
            val safeName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "ScanMate_Text_${System.currentTimeMillis()}" })
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
            val file = File(storageDir, "$safeName.pdf")
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("PdfExporter", "Operation failed", e)
            null
        } finally {
            runCatching { pdfDocument.close() }
        }
    }

    private fun wrapPdfLine(line: String, paint: Paint, maxWidth: Float): List<String> {
        if (line.isBlank()) return listOf(" ")
        val result = mutableListOf<String>()
        var remaining = line.trimEnd()
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
            val splitAt = if (count < remaining.length) {
                remaining.lastIndexOf(' ', count).takeIf { it > 6 } ?: count
            } else count
            result.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trimStart()
        }
        return result
    }

    suspend fun saveRenderedPdfPagesAsImages(
        context: Context,
        pages: List<Bitmap>,
        filename: String
    ): List<File> = withContext(Dispatchers.IO) {
        val safeName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "PDF_Pages_${System.currentTimeMillis()}" })
        val exported = mutableListOf<File>()
        pages.forEachIndexed { index, bitmap ->
            val file = FileUtils.saveBitmapToFolder(
                context = context,
                bitmap = bitmap,
                folderName = "Exports",
                filename = "${safeName}_page_${index + 1}",
                format = Bitmap.CompressFormat.PNG,
                quality = 100
            )
            if (file != null) exported += file
        }
        exported
    }

    suspend fun saveLongImageFromBitmaps(
        context: Context,
        pages: List<Bitmap>,
        filename: String
    ): File? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null
        try {
            val targetWidth = pages.maxOf { it.width }.coerceAtMost(1600).coerceAtLeast(320)
            val prepared = pages.mapNotNull { bitmap ->
                if (bitmap.width <= 0 || bitmap.height <= 0) return@mapNotNull null
                val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
                val targetHeight = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            }
            if (prepared.isEmpty()) return@withContext null
            val gap = 16
            val totalHeight = prepared.sumOf { it.height } + gap * (prepared.size - 1).coerceAtLeast(0)
            if (totalHeight > 32000) {
                prepared.forEach { if (!it.isRecycled) runCatching { it.recycle() } }
                return@withContext null
            }
            val longBitmap = Bitmap.createBitmap(targetWidth, totalHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(longBitmap)
            canvas.drawColor(Color.WHITE)
            var y = 0f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            prepared.forEach { page ->
                canvas.drawBitmap(page, 0f, y, paint)
                y += page.height + gap
            }
            val file = FileUtils.saveBitmapToFolder(
                context = context,
                bitmap = longBitmap,
                folderName = "Exports",
                filename = FileUtils.sanitizeFileBaseName(filename.ifBlank { "PDF_Long_Image_${System.currentTimeMillis()}" }),
                format = Bitmap.CompressFormat.PNG,
                quality = 100
            )
            prepared.forEach { if (!it.isRecycled) runCatching { it.recycle() } }
            if (!longBitmap.isRecycled) runCatching { longBitmap.recycle() }
            file
        } catch (e: Exception) {
            SafeLogger.e("PdfExporter", "Operation failed", e)
            null
        }
    }


    fun buildPdfPageDimensions(bitmap: Bitmap, pageSize: PdfPageSize = PdfPageSize.AUTO): Pair<Int, Int> = pageSize.resolveFor(bitmap)

    private fun Bitmap.scaleDownToMax(maxSide: Int): Bitmap? {
        val currentMax = max(width, height)
        if (currentMax <= 0) return null
        if (currentMax <= maxSide) return copy(Bitmap.Config.ARGB_8888, false)
        val ratio = maxSide.toFloat() / currentMax.toFloat()
        val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

}
