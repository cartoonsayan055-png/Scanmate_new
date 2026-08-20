package com.synthbyte.scanmate.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import java.io.File

object FileUtils {
    fun createUniqueImageFile(context: Context): File = FileCore.createUniqueImageFile(context)
    fun appRootFolder(context: Context): File? = FileCore.appRootFolder(context)
    fun appFolder(context: Context, name: String): File? = FileCore.appFolder(context, name)
    fun listManagedFiles(context: Context): List<File> = FileCore.listManagedFiles(context)
    fun shareFile(context: Context, file: File, mimeType: String = mimeTypeFor(file)) = FileCore.shareFile(context, file, mimeType)
    fun openFile(context: Context, file: File, mimeType: String = mimeTypeFor(file)) = FileCore.openFile(context, file, mimeType)
    fun shareText(context: Context, text: String, title: String = "Share Text") = FileCore.shareText(context, text, title)
    fun copyUriToImageFile(context: Context, uri: Uri): File? = FileCore.copyUriToImageFile(context, uri)
    fun copyFileToFolder(context: Context, source: File, folderName: String, preferredName: String = source.nameWithoutExtension): File? = FileCore.copyFileToFolder(context, source, folderName, preferredName)
    fun moveFileToFolder(context: Context, source: File, folderName: String, preferredName: String = source.nameWithoutExtension): File? = FileCore.moveFileToFolder(context, source, folderName, preferredName)
    suspend fun saveTextFile(context: Context, text: String, filename: String): File? = FileCore.saveTextFile(context, text, filename)
    suspend fun saveBitmapAsPng(context: Context, bitmap: Bitmap, filename: String): File? = FileCore.saveBitmapAsPng(context, bitmap, filename)
    suspend fun saveBitmapToFolder(context: Context, bitmap: Bitmap, folderName: String, filename: String, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 92): File? = FileCore.saveBitmapToFolder(context, bitmap, folderName, filename, format, quality)

    suspend fun saveEditedBitmap(context: Context, bitmap: Bitmap, sourceName: String = "EDITED"): File? = ImageProcessor.saveEditedBitmap(context, bitmap, sourceName)
    fun duplicateImageFile(context: Context, sourcePath: String): File? = ImageProcessor.duplicateImageFile(context, sourcePath)
    fun decodeSampledBitmap(path: String, reqWidth: Int = 1600, reqHeight: Int = 1600): Bitmap? = ImageProcessor.decodeSampledBitmap(path, reqWidth, reqHeight)
    fun normalizeBitmapOrientation(source: Bitmap, file: File): Bitmap = ImageProcessor.normalizeBitmapOrientation(source, file)
    fun applyPerspectiveCorrection(file: File, corners: List<Offset>, previewWidth: Int, previewHeight: Int): File? = ImageProcessor.applyPerspectiveCorrection(file, corners, previewWidth, previewHeight)
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap = ImageProcessor.rotateBitmap(source, degrees)
    fun cropBitmapNormalized(source: Bitmap, leftPercent: Float, topPercent: Float, rightPercent: Float, bottomPercent: Float): Bitmap = ImageProcessor.cropBitmapNormalized(source, leftPercent, topPercent, rightPercent, bottomPercent)
    fun autoCropDocument(source: Bitmap): Bitmap = ImageProcessor.autoCropDocument(source)
    fun perspectiveCorrectBitmapNormalized(source: Bitmap, topLeftX: Float, topLeftY: Float, topRightX: Float, topRightY: Float, bottomRightX: Float, bottomRightY: Float, bottomLeftX: Float, bottomLeftY: Float): Bitmap = ImageProcessor.perspectiveCorrectBitmapNormalized(source, topLeftX, topLeftY, topRightX, topRightY, bottomRightX, bottomRightY, bottomLeftX, bottomLeftY)
    fun perspectiveCorrectBitmapFromCorners(source: Bitmap, corners: List<Offset>): Bitmap = ImageProcessor.perspectiveCorrectBitmapFromCorners(source, corners)
    fun applyFilter(original: Bitmap, type: FilterType): Bitmap = ImageProcessor.applyFilter(original, type)
    fun drawSignatureOnBitmap(pageBitmap: Bitmap, signatureBitmap: Bitmap, alignRight: Boolean = true): Bitmap = ImageProcessor.drawSignatureOnBitmap(pageBitmap, signatureBitmap, alignRight)
    fun drawWatermarkOnBitmap(source: Bitmap, text: String = "ScanMate"): Bitmap = ImageProcessor.drawWatermarkOnBitmap(source, text)
    fun drawNoteStampOnBitmap(source: Bitmap, text: String): Bitmap = ImageProcessor.drawNoteStampOnBitmap(source, text)
    fun compressBitmap(source: Bitmap, quality: Int = 90, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray = ImageProcessor.compressBitmap(source, quality, format)
    fun applyWatermark(source: Bitmap, text: String = "ScanMate"): Bitmap = ImageProcessor.applyWatermark(source, text)
    fun removeMarksFromBitmap(source: Bitmap, sensitivity: Float = 0.18f): Bitmap = ImageProcessor.removeMarksFromBitmap(source, sensitivity)
    fun removeShadowFromBitmap(source: Bitmap): Bitmap = ImageProcessor.removeShadowFromBitmap(source)
    fun deskewBitmap(source: Bitmap): Bitmap = ImageProcessor.deskewBitmap(source)

    suspend fun generatePdf(context: Context, images: List<Bitmap>, filename: String): File? = PdfExporter.generatePdf(context, images, filename)
    suspend fun generatePdfFromBitmaps(context: Context, bitmaps: List<Bitmap>, filename: String, quality: PdfExportQuality = PdfExportQuality.BALANCED, pageSize: PdfPageSize = PdfPageSize.AUTO, onProgress: ((String) -> Unit)? = null): File? = PdfExporter.generatePdfFromBitmaps(context, bitmaps, filename, quality, pageSize, onProgress)
    suspend fun generatePdfFromPaths(context: Context, imagePaths: List<String>, filename: String, quality: PdfExportQuality = PdfExportQuality.BALANCED, pageSize: PdfPageSize = PdfPageSize.AUTO, onProgress: ((String) -> Unit)? = null, ocrRectsByPath: Map<String, List<Pair<android.graphics.Rect, String>>> = emptyMap()): File? = PdfExporter.generatePdfFromPaths(context, imagePaths, filename, quality, pageSize, onProgress, ocrRectsByPath)
    suspend fun generatePasswordProtectedPdf(context: Context, imagePaths: List<String>, filename: String, userPassword: String, allowPrinting: Boolean = true, allowCopy: Boolean = true): File? = PdfExporter.generatePasswordProtectedPdf(context, imagePaths, filename, userPassword, allowPrinting, allowCopy)
    fun renderPdfUriToBitmaps(context: Context, uri: Uri, maxWidth: Int = 1800, pageRange: IntRange? = null): List<Bitmap> = PdfExporter.renderPdfUriToBitmaps(context, uri, maxWidth, pageRange)
    suspend fun generatePdfFromText(context: Context, text: String, filename: String, pageSize: PdfPageSize = PdfPageSize.A4): File? = PdfExporter.generatePdfFromText(context, text, filename, pageSize)
    suspend fun saveRenderedPdfPagesAsImages(context: Context, pages: List<Bitmap>, filename: String): List<File> = PdfExporter.saveRenderedPdfPagesAsImages(context, pages, filename)
    suspend fun saveLongImageFromBitmaps(context: Context, pages: List<Bitmap>, filename: String): File? = PdfExporter.saveLongImageFromBitmaps(context, pages, filename)
    fun buildPdfPageDimensions(bitmap: Bitmap, pageSize: PdfPageSize = PdfPageSize.AUTO): Pair<Int, Int> = PdfExporter.buildPdfPageDimensions(bitmap, pageSize)

    suspend fun saveXlsxFromText(context: Context, text: String, filename: String): File? = DocxExporter.saveXlsxFromText(context, text, filename)
    suspend fun savePptxFromBitmaps(context: Context, pages: List<Bitmap>, filename: String): File? = DocxExporter.savePptxFromBitmaps(context, pages, filename)
    suspend fun saveDocxText(context: Context, text: String, filename: String): File? = DocxExporter.saveDocxText(context, text, filename)

    fun getDisplayName(context: Context, uri: Uri): String = FileCore.getDisplayName(context, uri)
    fun mimeTypeFor(file: File): String = FileCore.mimeTypeFor(file)
    fun sanitizeFileBaseName(value: String): String = FileCore.sanitizeFileBaseName(value)
}

enum class FilterType(val label: String) {
    ORIGINAL("Original"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W"),
    ENHANCED_COLOR("Enhanced"),
    MAGIC_COLOR("Magic Color"),
    LIGHTEN("Lighten"),
    SHARPEN("Sharpen"),
    HIGH_CONTRAST("High Contrast"),
    SOFT_SCAN("Soft Scan"),
    RECEIPT_MODE("Receipt"),
    BOOK_PAGE("Book Page"),
    LOW_LIGHT_CLEANUP("Low Light"),
    SHADOW_REDUCTION("Shadow Fix"),
    SHARP_SCAN("Sharp Scan"),
    WHITEBOARD("Whiteboard")
}

enum class PdfPageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    AUTO("Auto", 0, 0),
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008);

    fun resolveFor(bitmap: Bitmap): Pair<Int, Int> = when (this) {
        AUTO -> bitmap.width.coerceAtLeast(1) to bitmap.height.coerceAtLeast(1)
        else -> widthPt to heightPt
    }
}

enum class PdfExportQuality(val label: String, val description: String) {
    SMALL("Small", "Compressed for sharing"),
    BALANCED("Balanced", "Clear text with modest size"),
    HIGH("High", "Maximum detail")
}
