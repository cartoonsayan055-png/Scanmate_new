package com.synthbyte.scanmate.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.synthbyte.scanmate.core.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileCore {
    private const val ROOT_FOLDER_NAME = "ScanMate AI"

    private val managedFolders = listOf(
        "Scans",
        "Imported",
        "PDFs",
        "QR Codes",
        "OCR Text",
        "Backups",
        "Signatures",
        "Vault",
        "Exports"
    )

    fun appRootFolder(context: Context): File? {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir ?: return null
        val root = File(baseDir, ROOT_FOLDER_NAME)
        if (!root.exists() && !root.mkdirs()) return null
        ensureDefaultFolders(root)
        return root
    }

    fun createUniqueImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val imageFileName = "SCAN_${timeStamp}"
        val storageDir = appFolder(context, "Scans") ?: context.cacheDir
        return uniqueFile(storageDir, imageFileName, ".jpg")
    }

    fun appFolder(context: Context, name: String): File? {
        val root = appRootFolder(context) ?: return null
        val folderName = normalizeFolderName(name)
        val dir = File(root, folderName)
        if (!dir.exists() && !dir.mkdirs()) return null
        return dir
    }

    fun listManagedFiles(context: Context): List<File> {
        val modernFiles = managedFolders.flatMap { folder ->
            appFolder(context, folder)?.walkTopDown()?.filter { it.isFile }?.toList() ?: emptyList()
        }
        val legacyFolders = listOf("Scans", "PDFs", "QRCodes", "OCR", "Backups", "Signatures", "Vault", "Exports")
        val legacyFiles = legacyFolders.flatMap { folder ->
            context.getExternalFilesDir(folder)?.walkTopDown()?.filter { it.isFile }?.toList() ?: emptyList()
        }
        return (modernFiles + legacyFiles)
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
    }

    fun shareFile(context: Context, file: File, mimeType: String = mimeTypeFor(file)) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "File is missing or empty", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share file"))
        } catch (e: Exception) {
            SafeLogger.e("FileCore", "Share file failed", e)
            Toast.makeText(context, "No app found to share this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile(context: Context, file: File, mimeType: String = mimeTypeFor(file)) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "File is missing or empty", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            SafeLogger.e("FileCore", "Open file failed", e)
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText(context: Context, text: String, title: String = "Share Text") {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            SafeLogger.e("FileCore", "Share text failed", e)
            Toast.makeText(context, "No app found to share text", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyUriToImageFile(context: Context, uri: Uri): File? {
        return try {
            val displayName = sanitizeFileBaseName(getDisplayName(context, uri).substringBeforeLast('.'))
                .ifBlank { "IMPORTED_${System.currentTimeMillis()}" }
            val file = uniqueFile(appFolder(context, "Imported") ?: context.cacheDir, displayName, ".jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return null
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("FileCore", "File operation failed", e)
            null
        }
    }

    suspend fun saveTextFile(context: Context, text: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = appFolder(context, "OCR Text") ?: return@withContext null
            val safeName = sanitizeFileBaseName(filename.ifBlank { "OCR_${System.currentTimeMillis()}" })
                .removeSuffix(".txt")
            val file = uniqueFile(storageDir, safeName, ".txt")
            FileOutputStream(file).use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("FileCore", "File operation failed", e)
            null
        }
    }

    suspend fun saveBitmapAsPng(context: Context, bitmap: Bitmap, filename: String): File? =
        saveBitmapToFolder(context, bitmap, "QR Codes", filename, Bitmap.CompressFormat.PNG, 100)

    suspend fun saveBitmapToFolder(
        context: Context,
        bitmap: Bitmap,
        folderName: String,
        filename: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 92
    ): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = appFolder(context, folderName) ?: return@withContext null
            val safeName = sanitizeFileBaseName(filename.ifBlank { "ScanMate_${System.currentTimeMillis()}" })
                .substringBeforeLast('.')
            val extension = when (format) {
                Bitmap.CompressFormat.PNG -> ".png"
                Bitmap.CompressFormat.WEBP -> ".webp"
                else -> ".jpg"
            }
            val file = uniqueFile(storageDir, safeName, extension)
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(format, quality.coerceIn(1, 100), out)) return@withContext null
            }
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("FileCore", "File operation failed", e)
            null
        }
    }

    fun copyFileToFolder(context: Context, source: File, folderName: String, preferredName: String = source.nameWithoutExtension): File? {
        return copyOrMoveFileToFolder(context, source, folderName, preferredName, deleteSource = false)
    }

    fun moveFileToFolder(context: Context, source: File, folderName: String, preferredName: String = source.nameWithoutExtension): File? {
        return copyOrMoveFileToFolder(context, source, folderName, preferredName, deleteSource = true)
    }

    private fun copyOrMoveFileToFolder(
        context: Context,
        source: File,
        folderName: String,
        preferredName: String,
        deleteSource: Boolean
    ): File? {
        return try {
            if (!source.exists() || source.length() == 0L) return null
            val dir = appFolder(context, folderName) ?: return null
            if (source.parentFile?.canonicalPath == dir.canonicalPath && source.exists()) return source
            val ext = source.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: extensionForMime(mimeTypeFor(source))
            val target = uniqueFile(dir, sanitizeFileBaseName(preferredName).substringBeforeLast('.'), ext)
            source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            if (!target.exists() || target.length() <= 0L) return null
            if (deleteSource && source.canonicalPath != target.canonicalPath) runCatching { source.delete() }
            target
        } catch (e: Exception) {
            SafeLogger.e("FileCore", "File operation failed", e)
            null
        }
    }

    fun getDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Selected file"
    }

    fun mimeTypeFor(file: File): String = when (file.extension.lowercase(Locale.US)) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "txt" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"
        "vault" -> "application/octet-stream"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    fun sanitizeFileBaseName(value: String): String = value
        .trim()
        .ifBlank { "ScanMate_${System.currentTimeMillis()}" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_', '.', '-')
        .ifBlank { "ScanMate_${System.currentTimeMillis()}" }
        .take(90)

    private fun normalizeFolderName(name: String): String = when (name.trim().lowercase(Locale.US).replace("_", " ")) {
        "qr", "qrcode", "qrcodes", "qr codes" -> "QR Codes"
        "ocr", "ocr text", "texts", "text" -> "OCR Text"
        "scan", "scans", "captured" -> "Scans"
        "pdf", "pdfs" -> "PDFs"
        "backup", "backups" -> "Backups"
        "signature", "signatures" -> "Signatures"
        "vault" -> "Vault"
        "import", "imports", "imported" -> "Imported"
        "export", "exports" -> "Exports"
        else -> sanitizeFolderSegment(name)
    }

    private fun sanitizeFolderSegment(value: String): String = value
        .trim()
        .ifBlank { "Exports" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(60)

    private fun ensureDefaultFolders(root: File) {
        managedFolders.forEach { folder -> File(root, folder).mkdirs() }
    }

    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        if (!dir.exists()) dir.mkdirs()
        val cleanBase = sanitizeFileBaseName(baseName).substringBeforeLast('.').ifBlank { "ScanMate_${System.currentTimeMillis()}" }
        val cleanExt = if (extension.startsWith('.')) extension else ".$extension"
        var candidate = File(dir, "$cleanBase$cleanExt")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${cleanBase}_$counter$cleanExt")
            counter += 1
        }
        return candidate
    }

    private fun extensionForMime(mimeType: String): String = when (mimeType) {
        "application/pdf" -> ".pdf"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "text/plain" -> ".txt"
        else -> ".bin"
    }
}
