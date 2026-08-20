package com.synthbyte.scanmate.data.repository

import com.synthbyte.scanmate.core.ScanMateResult
import com.synthbyte.scanmate.data.DocDao
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.DocumentWithPages
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.data.QrHistory
import com.synthbyte.scanmate.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocDao
) : DocumentRepository {
    override val allDocuments: Flow<List<Document>> = dao.getAllDocuments()
    override val favoriteDocuments: Flow<List<Document>> = dao.getFavoriteDocuments()
    override val pinnedDocuments: Flow<List<Document>> = dao.getPinnedDocuments()
    override val recentDocuments: Flow<List<Document>> = dao.getRecentDocuments()
    override val firstPages: Flow<List<Page>> = dao.getFirstPagePerDocument()
    override val pageCount: Flow<Int> = dao.getPageCountFlow()
    override val pdfCount: Flow<Int> = dao.getPdfCountFlow()
    override val qrHistory: Flow<List<QrHistory>> = dao.getQrHistory()

    override fun searchDocuments(query: String): Flow<List<Document>> = dao.searchDocuments(query)

    override fun documentWithPages(documentId: Long): Flow<DocumentWithPages?> = dao.getDocumentWithPages(documentId)

    override suspend fun renameDocument(id: Long, title: String): ScanMateResult<Unit> = runDao("Could not rename document") {
        dao.renameDocument(id, title.trim().ifBlank { "Untitled Scan" })
    }

    override suspend fun deleteDocument(id: Long): ScanMateResult<Unit> = runDao("Could not delete document") {
        dao.deleteDocumentById(id)
    }

    override suspend fun updatePageOrder(pages: List<Page>): ScanMateResult<Unit> = runDao("Could not save page order") {
        pages.forEachIndexed { order, page -> dao.updatePageOrder(page.id, order) }
    }

    private suspend inline fun runDao(message: String, crossinline block: suspend () -> Unit): ScanMateResult<Unit> = try {
        block()
        ScanMateResult.Success(Unit)
    } catch (throwable: Throwable) {
        ScanMateResult.Error(throwable.localizedMessage ?: message, throwable)
    }
}
