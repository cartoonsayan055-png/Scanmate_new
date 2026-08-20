package com.synthbyte.scanmate.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synthbyte.scanmate.data.DocDao
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.utils.FilterType
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.ImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PageEditorViewModel @Inject constructor(
    private val dao: DocDao,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val appViewModel: AppErrorReporter
) : ViewModel() {
    private val docId: Long = savedStateHandle.get<Long>("docId")
        ?: savedStateHandle.get<String>("docId")?.toLongOrNull()
        ?: 0L
    private val pageId: Long = savedStateHandle.get<Long>("pageId")
        ?: savedStateHandle.get<String>("pageId")?.toLongOrNull()
        ?: 0L

    val page: Flow<Page?> = dao.getPage(pageId)

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _workingBitmap = MutableStateFlow<Bitmap?>(null)
    val workingBitmap: StateFlow<Bitmap?> = _workingBitmap.asStateFlow()

    fun clearError() {
        _errorState.value = null
    }

    private fun publishError(throwable: Throwable, fallback: String = "Unknown error") {
        val msg = throwable.localizedMessage ?: fallback
        _errorState.value = msg
        appViewModel.reportError(msg)
    }

    fun pushBitmap(bitmap: Bitmap?) {
        _workingBitmap.value = bitmap
    }

    fun loadPageBitmap(path: String) = viewModelScope.launch(Dispatchers.IO) {
        _isProcessing.value = true
        try {
            runCatching {
                val file = File(path)
                if (!file.exists() || file.length() == 0L) {
                    _workingBitmap.value = null
                    _errorState.value = "Page image file is missing"
                    return@runCatching
                }
                val bmp = ImageProcessor.decodeSampledBitmap(path, 2600, 2600)
                _workingBitmap.value = bmp
                if (bmp == null) _errorState.value = "Page image could not be decoded"
            }.onFailure { throwable -> publishError(throwable, "Failed to load page image") }
        } finally {
            _isProcessing.value = false
        }
    }

    fun applyFilter(filterId: String) = viewModelScope.launch(Dispatchers.IO) {
        val current = _workingBitmap.value ?: return@launch
        runCatching {
            val filter = runCatching { FilterType.valueOf(filterId) }.getOrDefault(FilterType.ORIGINAL)
            val filtered = ImageProcessor.applyFilter(current, filter)
            _workingBitmap.value = filtered
        }.onFailure { throwable -> publishError(throwable, "Filter failed") }
    }

    fun applyFilter(filter: FilterType) = applyFilter(filter.name)


    fun removeMarks(sensitivity: Float = 0.18f) = viewModelScope.launch(Dispatchers.IO) {
        val bmp = _workingBitmap.value ?: return@launch
        _isProcessing.value = true
        try {
            runCatching { _workingBitmap.value = ImageProcessor.removeMarksFromBitmap(bmp, sensitivity) }
                .onFailure { throwable -> publishError(throwable, "Mark removal failed") }
        } finally {
            _isProcessing.value = false
        }
    }

    fun removeShadow() = viewModelScope.launch(Dispatchers.IO) {
        val bmp = _workingBitmap.value ?: return@launch
        _isProcessing.value = true
        try {
            runCatching { _workingBitmap.value = ImageProcessor.removeShadowFromBitmap(bmp) }
                .onFailure { throwable -> publishError(throwable, "Shadow removal failed") }
        } finally {
            _isProcessing.value = false
        }
    }

    fun deskew() = viewModelScope.launch(Dispatchers.IO) {
        val bmp = _workingBitmap.value ?: return@launch
        _isProcessing.value = true
        try {
            runCatching { _workingBitmap.value = ImageProcessor.deskewBitmap(bmp) }
                .onFailure { throwable -> publishError(throwable, "Deskew failed") }
        } finally {
            _isProcessing.value = false
        }
    }

    suspend fun saveEditedPage(pageId: Long, bitmap: Bitmap): java.io.File? = withContext(Dispatchers.IO) {
        runCatching {
            val file = FileUtils.saveEditedBitmap(context, bitmap, "PAGE_${pageId}")
            if (file != null && file.exists() && file.length() > 0L) {
                dao.updatePageImage(pageId, file.absolutePath)
                dao.touchDocumentForPage(pageId)
            }
            file
        }.onFailure { throwable -> publishError(throwable) }
            .getOrNull()
    }

    suspend fun replacePageImage(pageId: Long, uri: Uri): java.io.File? = withContext(Dispatchers.IO) {
        runCatching {
            val file = FileUtils.copyUriToImageFile(context, uri)
            if (file != null && file.exists() && file.length() > 0L) {
                dao.updatePageImage(pageId, file.absolutePath)
                dao.touchDocumentForPage(pageId)
            }
            file
        }.onFailure { throwable -> publishError(throwable) }
            .getOrNull()
    }

    fun deleteCurrentPage(pageId: Long, onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            dao.deletePageById(pageId)
            renumberPagesInternal()
            dao.touchDocument(docId)
            withContext(Dispatchers.Main) { onDone() }
        }.onFailure { throwable -> publishError(throwable) }
    }

    suspend fun duplicatePage(page: Page) = withContext(Dispatchers.IO) {
        runCatching {
            val copied = FileUtils.duplicateImageFile(context, page.imagePath) ?: return@runCatching
            val pages = dao.getPagesForDocumentOnce(docId).sortedBy { it.pageOrder }
            val insertIndex = pages.indexOfFirst { it.id == page.id }.takeIf { it >= 0 }?.plus(1) ?: pages.size
            pages.forEachIndexed { index, existing ->
                val order = if (index >= insertIndex) index + 1 else index
                dao.updatePageOrder(existing.id, order)
            }
            dao.insertPage(Page(documentId = docId, imagePath = copied.absolutePath, pageOrder = insertIndex))
            renumberPagesInternal()
            dao.touchDocument(docId)
        }.onFailure { throwable -> publishError(throwable) }
    }

    suspend fun movePage(page: Page, direction: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val pages = dao.getPagesForDocumentOnce(docId).sortedBy { it.pageOrder }.toMutableList()
            val index = pages.indexOfFirst { it.id == page.id }
            if (index < 0) return@runCatching
            val newIndex = (index + direction).coerceIn(0, pages.lastIndex)
            if (index == newIndex) return@runCatching
            val current = pages.removeAt(index)
            pages.add(newIndex, current)
            pages.forEachIndexed { order, existing -> dao.updatePageOrder(existing.id, order) }
            dao.touchDocument(docId)
        }.onFailure { throwable -> publishError(throwable) }
    }

    suspend fun savePageOcr(page: Page, text: String) = withContext(Dispatchers.IO) {
        runCatching {
            dao.updateOcrText(docId, "Page ${page.pageOrder + 1}:\n$text")
            dao.touchDocument(docId)
        }.onFailure { throwable -> publishError(throwable) }
    }

    private suspend fun renumberPagesInternal() {
        dao.getPagesForDocumentOnce(docId).sortedBy { it.pageOrder }.forEachIndexed { index, page ->
            dao.updatePageOrder(page.id, index)
        }
    }
}
