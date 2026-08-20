package com.synthbyte.scanmate.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.EnumMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.synthbyte.scanmate.core.SafeLogger

object QRUtils {
    fun generateQRCode(
        text: String,
        size: Int = 768,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
        addCenterBadge: Boolean = false
    ): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 2)
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            val bitMatrix: BitMatrix = QRCodeWriter().encode(text.trim(), BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until bitMatrix.width) {
                for (y in 0 until bitMatrix.height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) foregroundColor else backgroundColor)
                }
            }
            if (addCenterBadge) addSafeCenterBadge(bitmap, backgroundColor, foregroundColor) else bitmap
        } catch (e: Exception) {
            SafeLogger.e("MediaUtils", "Operation failed", e)
            null
        }
    }

    private fun addSafeCenterBadge(bitmap: Bitmap, backgroundColor: Int, foregroundColor: Int): Bitmap {
        val canvas = Canvas(bitmap)
        val size = bitmap.width.coerceAtMost(bitmap.height)
        val badgeSize = size * 0.14f
        val left = (bitmap.width - badgeSize) / 2f
        val top = (bitmap.height - badgeSize) / 2f
        val rect = RectF(left, top, left + badgeSize, top + badgeSize)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            style = Paint.Style.STROKE
            strokeWidth = size * 0.01f
        }
        canvas.drawRoundRect(rect, badgeSize * 0.22f, badgeSize * 0.22f, bgPaint)
        canvas.drawRoundRect(rect, badgeSize * 0.22f, badgeSize * 0.22f, strokePaint)
        return bitmap
    }
}

object ZipUtils {
    suspend fun createZip(context: Context, sourceFiles: List<File>, zipFileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = FileUtils.appFolder(context, "Backups") ?: return@withContext null
            val safeName = zipFileName.ifBlank { "ScanMate_Backup_${System.currentTimeMillis()}" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .removeSuffix(".zip")
            val zipFile = File(storageDir, "$safeName.zip")
            val files = sourceFiles
                .filter { it.exists() && it.isFile && it.length() > 0L }
                .filterNot { it.extension.equals("zip", ignoreCase = true) }
                .distinctBy { it.absolutePath }
            if (files.isEmpty()) return@withContext null

            val usedEntryNames = mutableSetOf<String>()
            val manifest = JSONArray()
            ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zos ->
                files.forEach { file ->
                    val parent = file.parentFile?.name
                        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Files"
                    val baseName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    var entryName = "$parent/$baseName"
                    var counter = 1
                    while (!usedEntryNames.add(entryName)) {
                        entryName = "$parent/${file.nameWithoutExtension}_$counter.${file.extension}".replace("..", ".")
                        counter += 1
                    }
                    manifest.put(
                        JSONObject()
                            .put("path", entryName)
                            .put("name", file.name)
                            .put("type", FileUtils.mimeTypeFor(file))
                            .put("sizeBytes", file.length())
                            .put("createdOrModifiedAt", file.lastModified())
                            .put("ocrConfidence", JSONObject.NULL)
                    )
                    FileInputStream(file).buffered().use { fis ->
                        val entry = ZipEntry(entryName)
                        zos.putNextEntry(entry)
                        fis.copyTo(zos, bufferSize = 32 * 1024)
                        zos.closeEntry()
                    }
                }
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(
                    JSONObject()
                        .put("app", "ScanMate")
                        .put("schemaVersion", 1)
                        .put("generatedAt", System.currentTimeMillis())
                        .put("files", manifest)
                        .toString(2)
                        .toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()
            }
            zipFile.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("MediaUtils", "Operation failed", e)
            null
        }
    }
}
