package com.synthbyte.scanmate.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synthbyte.scanmate.data.DocDao
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.FilterType
import com.synthbyte.scanmate.utils.ImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val dao: DocDao,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    fun applyPreviewFilter(bitmap: Bitmap, filter: FilterType) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            _previewBitmap.value = ImageProcessor.applyFilter(bitmap, filter)
        }.onFailure { _previewBitmap.value = bitmap }
    }

    suspend fun sharpenCapturedImageFile(photoFile: File): File = withContext(Dispatchers.IO) {
        // Downsample first: a raw camera capture can be 12-48MP, and decoding it
        // unsampled here risked OOM crashes on mid/low-end devices during sharpen.
        val decoded = ImageProcessor.decodeSampledBitmap(photoFile.absolutePath, 3200, 3200)
            ?: return@withContext photoFile
        val bitmap = ImageProcessor.normalizeBitmapOrientation(decoded, photoFile)
        val sharpened = runCatching { ImageProcessor.applyFilter(bitmap, FilterType.SHARPEN) }
            .getOrDefault(bitmap)
        _previewBitmap.value = sharpened.copy(Bitmap.Config.ARGB_8888, false)
        FileOutputStream(photoFile).use { output ->
            sharpened.compress(Bitmap.CompressFormat.JPEG, 100, output)
        }
        if (sharpened !== bitmap && !sharpened.isRecycled) sharpened.recycle()
        if (bitmap !== decoded && !bitmap.isRecycled) bitmap.recycle()
        if (!decoded.isRecycled) decoded.recycle()
        photoFile
    }

    suspend fun importUris(uris: List<Uri>): List<File> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> FileUtils.copyUriToImageFile(context, uri) }
    }

    suspend fun saveScannedDocument(files: List<File>, defaultWorkspace: String): Long = withContext(Dispatchers.IO) {
        val validFiles = files.filter { it.exists() && it.length() > 0L }
        require(validFiles.isNotEmpty()) { "No valid scanned pages" }
        val now = System.currentTimeMillis()
        val id = dao.insertDocument(
            Document(
                title = "Scanned ${validFiles.size} page${if (validFiles.size == 1) "" else "s"}",
                timestamp = now,
                updatedAt = now,
                type = "SCAN",
                workspace = defaultWorkspace.ifBlank { "Inbox" }
            )
        )
        validFiles.forEachIndexed { index, file ->
            dao.insertPage(Page(documentId = id, imagePath = file.absolutePath, pageOrder = index))
        }
        id
    }
}
