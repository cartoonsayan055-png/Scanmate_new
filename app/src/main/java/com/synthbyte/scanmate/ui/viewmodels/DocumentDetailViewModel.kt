package com.synthbyte.scanmate.ui.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.synthbyte.scanmate.data.DocDao
import com.synthbyte.scanmate.data.DocumentWithPages
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.utils.DocumentIntelligence
import com.synthbyte.scanmate.utils.EncryptedVaultUtils
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.OcrHelper
import com.synthbyte.scanmate.utils.PdfExportQuality
import com.synthbyte.scanmate.utils.PdfPageSize
import com.synthbyte.scanmate.workers.DocumentOcrWorker
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

sealed interface ExportState {
    data object Idle : ExportState
    data class Loading(val message: String? = null) : ExportState
    data class PdfSuccess(val file: File) : ExportState
    data class DocxSuccess(val file: File) : ExportState
    data class OcrSuccess(val text: String, val qualityLabel: String) : ExportState
    data class VaultSuccess(val itemCount: Int) : ExportState
    data class QualitySuccess(val report: DocumentIntelligence.QualityReport) : ExportState
    data class Error(val message: String) : ExportState
}

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val dao: DocDao,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val appViewModel: AppErrorReporter
) : ViewModel() {

    private val docId: Long = savedStateHandle.get<Long>("docId")
        ?: savedStateHandle.get<String>("docId")?.toLongOrNull()
        ?: 0L

    val documentWithPages: Flow<DocumentWithPages?> = dao.getDocumentWithPages(docId)

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    private fun publishError(throwable: Throwable) {
        publishErrorMessage(throwable.localizedMessage ?: "Unknown error")
    }

    private fun publishErrorMessage(message: String) {
        _exportState.value = ExportState.Error(message)
        appViewModel.reportError(message)
    }

    fun exportDocx(dwp: DocumentWithPages?) = viewModelScope.launch {
        runCatching {
            if (dwp == null) {
                publishErrorMessage("No document available for DOCX export")
                return@runCatching
            }

            val text = dwp.document.ocrText.orEmpty().trim()
            if (text.isBlank()) {
                publishErrorMessage("Run OCR first, then export DOCX")
                return@runCatching
            }

            _exportState.value = ExportState.Loading("Preparing DOCX…")

            val file = withContext(Dispatchers.IO) {
                FileUtils.saveDocxText(
                    context = context,
                    text = text,
                    filename = dwp.document.title.ifBlank {
                        "ScanMate_${dwp.document.id}_${System.currentTimeMillis()}"
                    }
                )
            }

            if (file != null) {
                _exportState.value = ExportState.DocxSuccess(file)
            } else {
                publishErrorMessage("DOCX export failed")
            }
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun exportPdf(
        dwp: DocumentWithPages?,
        quality: PdfExportQuality,
        filename: String,
        pageSize: PdfPageSize = PdfPageSize.A4
    ) = viewModelScope.launch {
        runCatching {
            if (dwp == null) return@runCatching

            val pages = dwp.pages.sortedBy { it.pageOrder }
            if (pages.isEmpty()) {
                publishErrorMessage("No pages found to export")
                return@runCatching
            }

            _exportState.value = ExportState.Loading("Preparing PDF…")

            val imagePaths = pages.map { it.imagePath }

            val ocrBlocksByPath: Map<String, List<Pair<android.graphics.Rect, String>>> =
                withContext(Dispatchers.IO) {
                    imagePaths.associateWith { path ->
                        val pageFile = File(path)
                        if (pageFile.exists() && pageFile.length() > 0L) {
                            OcrHelper.extractBlocksFromFile(context, pageFile)
                        } else {
                            emptyList()
                        }
                    }
                }

            _exportState.value = ExportState.Loading("Building PDF…")

            val pdfFile = withContext(Dispatchers.IO) {
                FileUtils.generatePdfFromPaths(
                    context = context,
                    imagePaths = imagePaths,
                    filename = filename.ifBlank { FileUtils.sanitizeFileBaseName(dwp.document.title) },
                    quality = quality,
                    pageSize = pageSize,
                    onProgress = { message -> _exportState.value = ExportState.Loading(message) },
                    ocrRectsByPath = ocrBlocksByPath
                )
            }

            if (pdfFile != null) {
                _exportState.value = ExportState.PdfSuccess(pdfFile)
            } else {
                publishErrorMessage("PDF export failed. Check that pages are valid images.")
            }
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun exportProtectedPdf(
        dwp: DocumentWithPages?,
        password: String,
        filename: String,
        allowPrinting: Boolean = true,
        allowCopy: Boolean = true
    ) = viewModelScope.launch {
        if (dwp == null || dwp.pages.isEmpty()) {
            publishErrorMessage("No pages to export")
            return@launch
        }

        _exportState.value = ExportState.Loading("Encrypting PDF…")

        runCatching {
            val paths = dwp.pages.sortedBy { it.pageOrder }.map { it.imagePath }
            val file = FileUtils.generatePasswordProtectedPdf(
                context,
                paths,
                filename,
                password,
                allowPrinting,
                allowCopy
            )
            _exportState.value =
                if (file != null) ExportState.PdfSuccess(file) else ExportState.Error("PDF encryption failed")
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun scoreQuality(dwp: DocumentWithPages?) = viewModelScope.launch {
        if (dwp == null) return@launch

        val text = dwp.document.ocrText.orEmpty()
        val confidence = OcrHelper.buildStats(text).confidencePercent / 100f
        _exportState.value = ExportState.QualitySuccess(
            DocumentIntelligence.scoreDocumentQuality(text, confidence, dwp.pages.size)
        )
    }

    fun extractOcr(dwp: DocumentWithPages?) = viewModelScope.launch {
        runCatching {
            if (dwp == null || dwp.pages.isEmpty()) {
                publishErrorMessage("No pages available for OCR")
                return@runCatching
            }

            val request = OneTimeWorkRequestBuilder<DocumentOcrWorker>()
                .setInputData(workDataOf(DocumentOcrWorker.KEY_DOCUMENT_ID to dwp.document.id))
                .addTag("ocr")
                .addTag("ocr_document_${dwp.document.id}")
                .build()

            val workManager = WorkManager.getInstance(context)

            workManager.enqueueUniqueWork(
                "ocr_document_${dwp.document.id}",
                ExistingWorkPolicy.REPLACE,
                request
            )

            _exportState.value = ExportState.Loading("OCR is running safely in the background…")

            workManager.getWorkInfoByIdLiveData(request.id).asFlow().collect { info ->
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val text = info.outputData
                            .getString(DocumentOcrWorker.KEY_PREVIEW_TEXT)
                            .orEmpty()
                        val label = info.outputData
                            .getString(DocumentOcrWorker.KEY_STATUS)
                            .orEmpty()
                            .ifBlank { "OCR complete" }

                        _exportState.value = ExportState.OcrSuccess(text, label)
                        return@collect
                    }

                    WorkInfo.State.FAILED -> {
                        publishErrorMessage(
                            info.outputData.getString(DocumentOcrWorker.KEY_ERROR) ?: "OCR failed"
                        )
                        return@collect
                    }

                    WorkInfo.State.CANCELLED -> {
                        publishErrorMessage("OCR was cancelled")
                        return@collect
                    }

                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(DocumentOcrWorker.KEY_PROGRESS, 0)
                        _exportState.value = ExportState.Loading("OCR running… $progress%")
                    }

                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED,
                    null -> Unit
                }
            }
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun setFavorite(favorite: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { dao.setFavorite(docId, favorite) }
            .onFailure { throwable -> publishError(throwable) }
    }

    fun setPinned(pinned: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { dao.setPinned(docId, pinned) }
            .onFailure { throwable -> publishError(throwable) }
    }

    fun rename(title: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { dao.renameDocument(docId, title.trim().ifBlank { "Untitled Scan" }) }
            .onFailure { throwable -> publishError(throwable) }
    }

    fun updateMeta(category: String, tags: String, workspace: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            dao.updateCategoryTags(
                id = docId,
                category = category.trim().ifBlank { "General" },
                tags = tags.trim(),
                workspace = workspace.trim().ifBlank { "Inbox" }
            )
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            dao.deleteDocumentById(docId)
            withContext(Dispatchers.Main) { onDeleted() }
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun updateOcr(text: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { dao.updateOcrText(docId, text) }
            .onFailure { throwable -> publishError(throwable) }
    }

    fun moveDocumentToVault(
        dwp: DocumentWithPages?,
        onMoved: () -> Unit = {}
    ) = viewModelScope.launch {
        runCatching {
            if (dwp == null) {
                publishErrorMessage("No document available for vault")
                return@runCatching
            }

            val pageFiles = dwp.pages
                .sortedBy { it.pageOrder }
                .map { File(it.imagePath) }
                .filter { it.exists() && it.length() > 0L }

            val ocrText = dwp.document.ocrText.orEmpty().trim().takeIf { it.isNotBlank() }

            if (pageFiles.isEmpty() && ocrText == null) {
                publishErrorMessage("Nothing to move to vault")
                return@runCatching
            }

            _exportState.value = ExportState.Loading("Moving document to Secure Vault…")

            val savedItems = withContext(Dispatchers.IO) {
                EncryptedVaultUtils.saveDocumentBundle(
                    context = context,
                    documentTitle = dwp.document.title,
                    sourceFiles = pageFiles,
                    ocrText = ocrText,
                    moveOriginals = true
                )
            }

            val expectedMinimum = pageFiles.size + if (ocrText != null) 1 else 0
            if (savedItems.size < expectedMinimum) {
                publishErrorMessage("Vault move incomplete. Original files were kept where possible.")
                return@runCatching
            }

            withContext(Dispatchers.IO) { dao.deleteDocumentById(dwp.document.id) }

            _exportState.value = ExportState.VaultSuccess(savedItems.size)
            withContext(Dispatchers.Main) { onMoved() }
        }.onFailure { throwable -> publishError(throwable) }
    }

    suspend fun updateOcrAndMetadata(
        text: String,
        currentWorkspace: String
    ) = withContext(Dispatchers.IO) {
        dao.updateOcrText(docId, text)

        val suggested = DocumentIntelligence.suggestedCategory(text)
        val keywords = DocumentIntelligence.keywordList(text, 8).joinToString(", ")

        dao.updateCategoryTags(
            docId,
            suggested,
            keywords,
            currentWorkspace.ifBlank { "Inbox" }
        )
    }

    fun updatePageImage(pageId: Long, imagePath: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val file = File(imagePath)
            if (!file.exists() || file.length() == 0L) {
                publishErrorMessage("Page image file is missing")
                return@runCatching
            }

            dao.updatePageImage(pageId, imagePath)
            dao.touchDocumentForPage(pageId)
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun deletePage(pageId: Long, onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            deletePageInternal(pageId)
            withContext(Dispatchers.Main) { onDone() }
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun duplicatePage(page: Page, onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            duplicatePageInternal(page)
            withContext(Dispatchers.Main) { onDone() }
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun movePage(page: Page, direction: Int, onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            movePageInternal(page, direction)
            withContext(Dispatchers.Main) { onDone() }
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun reorderPage(pageId: Long, newOrder: Int) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val currentPages = dao.getPagesForDocumentOnce(docId)
                .sortedBy { it.pageOrder }
                .toMutableList()

            val index = currentPages.indexOfFirst { it.id == pageId }
            if (index < 0) return@runCatching

            val page = currentPages.removeAt(index)
            currentPages.add(newOrder.coerceIn(0, currentPages.size), page)

            currentPages.forEachIndexed { order, existing ->
                dao.updatePageOrder(existing.id, order)
            }

            dao.touchDocument(docId)
        }.onFailure { throwable -> publishError(throwable) }
    }

    fun reorderPages(pages: List<Page>, onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val knownIds = dao.getPagesForDocumentOnce(docId).map { it.id }.toSet()
            val safePages = pages
                .filter { it.id in knownIds }
                .distinctBy { it.id }

            if (safePages.size != knownIds.size) {
                publishErrorMessage("Page order could not be saved safely")
                return@runCatching
            }

            safePages.forEachIndexed { order, page ->
                dao.updatePageOrder(page.id, order)
            }

            dao.touchDocument(docId)
            withContext(Dispatchers.Main) { onDone() }
        }.onFailure { throwable -> publishError(throwable) }
    }

    private suspend fun deletePageInternal(pageId: Long) {
        dao.deletePageById(pageId)
        renumberPages()
        dao.touchDocument(docId)
    }

    private suspend fun duplicatePageInternal(page: Page) {
        val copied = FileUtils.duplicateImageFile(context, page.imagePath) ?: return

        val pages = dao.getPagesForDocumentOnce(docId).sortedBy { it.pageOrder }
        val insertIndex = pages
            .indexOfFirst { it.id == page.id }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: pages.size

        pages.forEachIndexed { index, existing ->
            val order = if (index >= insertIndex) index + 1 else index
            dao.updatePageOrder(existing.id, order)
        }

        dao.insertPage(
            Page(
                documentId = docId,
                imagePath = copied.absolutePath,
                pageOrder = insertIndex
            )
        )

        renumberPages()
        dao.touchDocument(docId)
    }

    private suspend fun movePageInternal(page: Page, direction: Int) {
        val pages = dao.getPagesForDocumentOnce(docId)
            .sortedBy { it.pageOrder }
            .toMutableList()

        val index = pages.indexOfFirst { it.id == page.id }
        if (index < 0) return

        val newIndex = (index + direction).coerceIn(0, pages.lastIndex)
        if (index == newIndex) return

        val current = pages.removeAt(index)
        pages.add(newIndex, current)

        pages.forEachIndexed { order, existing ->
            dao.updatePageOrder(existing.id, order)
        }

        dao.touchDocument(docId)
    }

    private suspend fun renumberPages() {
        dao.getPagesForDocumentOnce(docId)
            .sortedBy { it.pageOrder }
            .forEachIndexed { index, page ->
                dao.updatePageOrder(page.id, index)
            }
    }

    override fun onCleared() {
        OcrHelper.closeRecognizer()
        super.onCleared()
    }
}
