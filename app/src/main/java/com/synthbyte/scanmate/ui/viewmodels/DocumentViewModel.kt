package com.synthbyte.scanmate.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synthbyte.scanmate.data.DocDao
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.data.QrHistory
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.OcrHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val dao: DocDao,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val allDocuments: Flow<List<Document>> = dao.getAllDocuments()
    val favoriteDocuments: Flow<List<Document>> = dao.getFavoriteDocuments()
    val pinnedDocuments: Flow<List<Document>> = dao.getPinnedDocuments()
    val recentDocuments: Flow<List<Document>> = dao.getRecentDocuments()
    val allPages: Flow<List<Page>> = dao.getFirstPagePerDocument()
    val pageCount: Flow<Int> = dao.getPageCountFlow()
    val pdfCount: Flow<Int> = dao.getPdfCountFlow()
    val qrHistory: Flow<List<QrHistory>> = dao.getQrHistory()

    val searchQuery = MutableStateFlow("")
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    val searchResults: StateFlow<List<Document>> = searchQuery
        .debounce(150L)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else dao.searchDocuments(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }

    fun addToSearchHistory(query: String) {
        if (query.isBlank() || query.length < 2) return
        val updated = (_searchHistory.value - query + query).takeLast(5)
        _searchHistory.value = updated
    }


    fun createDocumentFromUris(
        uris: List<Uri>,
        defaultWorkspace: String = "Inbox",
        onCreated: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val copiedFiles = uris.mapNotNull { uri -> FileUtils.copyUriToImageFile(context, uri) }
            if (copiedFiles.isEmpty()) {
                withContext(Dispatchers.Main) { onError("No gallery images could be imported") }
                return@launch
            }

            val now = System.currentTimeMillis()
            val docId = dao.insertDocument(
                Document(
                    title = "Imported ${copiedFiles.size} page${if (copiedFiles.size == 1) "" else "s"}",
                    timestamp = now,
                    updatedAt = now,
                    type = "IMAGE",
                    workspace = defaultWorkspace.ifBlank { "Inbox" }
                )
            )
            copiedFiles.forEachIndexed { index, file ->
                dao.insertPage(Page(documentId = docId, imagePath = file.absolutePath, pageOrder = index))
            }
            withContext(Dispatchers.Main) { onCreated(docId) }
        }
    }

    fun renameDocument(id: Long, title: String) {
        val safeTitle = title.trim().ifBlank { "Untitled Scan" }
        viewModelScope.launch(Dispatchers.IO) { dao.renameDocument(id, safeTitle) }
    }

    fun toggleFavorite(document: Document) {
        viewModelScope.launch(Dispatchers.IO) { dao.setFavorite(document.id, !document.isFavorite) }
    }

    fun togglePinned(document: Document) {
        viewModelScope.launch(Dispatchers.IO) { dao.setPinned(document.id, !document.isPinned) }
    }

    fun deleteDocument(id: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDocumentById(id)
            withContext(Dispatchers.Main) { onDeleted() }
        }
    }

    fun setWorkspace(documentIds: List<Long>, workspace: String, onDone: () -> Unit = {}) {
        if (documentIds.isEmpty()) return
        val safeWorkspace = workspace.trim().ifBlank { "Inbox" }
        viewModelScope.launch(Dispatchers.IO) {
            dao.moveDocumentsToWorkspace(documentIds, safeWorkspace)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun setFavoriteBulk(documentIds: List<Long>, favorite: Boolean, onDone: () -> Unit = {}) {
        if (documentIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.setFavoriteBulk(documentIds, favorite)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun setPinnedBulk(documentIds: List<Long>, pinned: Boolean, onDone: () -> Unit = {}) {
        if (documentIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.setPinnedBulk(documentIds, pinned)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun deleteDocuments(documentIds: List<Long>, onDeleted: () -> Unit = {}) {
        if (documentIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDocumentsByIds(documentIds)
            withContext(Dispatchers.Main) { onDeleted() }
        }
    }

    fun restoreDocument(document: Document, pages: List<Page>, onRestored: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertDocument(document.copy(updatedAt = System.currentTimeMillis()))
            pages.sortedBy { it.pageOrder }.forEach { page ->
                dao.insertPage(page)
            }
            withContext(Dispatchers.Main) { onRestored() }
        }
    }

    fun insertQrHistory(value: String, type: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { dao.insertQrHistory(QrHistory(value = value, type = type)) }
            .onFailure { /* silent — QR history loss is non-critical */ }
    }

    fun clearQrHistory() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { dao.clearQrHistory() }
    }

    override fun onCleared() {
        OcrHelper.closeRecognizer()
        super.onCleared()
    }
}
