package com.synthbyte.scanmate.domain.repository

import com.synthbyte.scanmate.core.ScanMateResult
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.DocumentWithPages
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.data.QrHistory
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    val allDocuments: Flow<List<Document>>
    val favoriteDocuments: Flow<List<Document>>
    val pinnedDocuments: Flow<List<Document>>
    val recentDocuments: Flow<List<Document>>
    val firstPages: Flow<List<Page>>
    val pageCount: Flow<Int>
    val pdfCount: Flow<Int>
    val qrHistory: Flow<List<QrHistory>>

    fun searchDocuments(query: String): Flow<List<Document>>
    fun documentWithPages(documentId: Long): Flow<DocumentWithPages?>
    suspend fun renameDocument(id: Long, title: String): ScanMateResult<Unit>
    suspend fun deleteDocument(id: Long): ScanMateResult<Unit>
    suspend fun updatePageOrder(pages: List<Page>): ScanMateResult<Unit>
}
