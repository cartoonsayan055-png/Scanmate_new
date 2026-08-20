package com.synthbyte.scanmate

import app.cash.turbine.test
import com.synthbyte.scanmate.core.ScanMateResult
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.DocumentWithPages
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.data.QrHistory
import com.synthbyte.scanmate.domain.repository.DocumentRepository
import com.synthbyte.scanmate.domain.usecase.DeleteDocumentUseCase
import com.synthbyte.scanmate.domain.usecase.RenameDocumentUseCase
import com.synthbyte.scanmate.domain.usecase.ReorderPagesUseCase
import com.synthbyte.scanmate.domain.usecase.SearchDocumentsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentUseCaseTest {
    private val repository = FakeDocumentRepository(
        initialDocuments = listOf(
            Document(id = 1, title = "Physics Notes", ocrText = "Force energy motion"),
            Document(id = 2, title = "Math Homework", ocrText = "Quadratic formula")
        )
    )

    @Test
    fun searchDocumentsTrimsQueryAndMatchesTitleOrOcr() = runTest {
        val useCase = SearchDocumentsUseCase(repository)

        useCase("  force  ").test {
            assertEquals(listOf(1L), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun renameDocumentPersistsNonBlankTitle() = runTest {
        val result = RenameDocumentUseCase(repository)(1L, "Updated Notes")

        assertTrue(result is ScanMateResult.Success)
        assertEquals("Updated Notes", repository.documents.value.first { it.id == 1L }.title)
    }

    @Test
    fun deleteDocumentRemovesTheTargetDocument() = runTest {
        val result = DeleteDocumentUseCase(repository)(2L)

        assertTrue(result is ScanMateResult.Success)
        assertEquals(listOf(1L), repository.documents.value.map { it.id })
    }

    @Test
    fun reorderPagesSavesNewOrder() = runTest {
        val pages = listOf(
            Page(id = 10, documentId = 1, imagePath = "page-2.jpg", pageOrder = 1),
            Page(id = 11, documentId = 1, imagePath = "page-1.jpg", pageOrder = 0)
        )

        val result = ReorderPagesUseCase(repository)(pages)

        assertTrue(result is ScanMateResult.Success)
        assertEquals(listOf(10L to 0, 11L to 1), repository.pageOrders)
    }

    private class FakeDocumentRepository(initialDocuments: List<Document>) : DocumentRepository {
        val documents = MutableStateFlow(initialDocuments)
        val pageOrders = mutableListOf<Pair<Long, Int>>()

        override val allDocuments: Flow<List<Document>> = documents
        override val favoriteDocuments: Flow<List<Document>> = documents.map { list -> list.filter { it.isFavorite } }
        override val pinnedDocuments: Flow<List<Document>> = documents.map { list -> list.filter { it.isPinned } }
        override val recentDocuments: Flow<List<Document>> = documents
        override val firstPages: Flow<List<Page>> = MutableStateFlow(emptyList())
        override val pageCount: Flow<Int> = MutableStateFlow(0)
        override val pdfCount: Flow<Int> = MutableStateFlow(0)
        override val qrHistory: Flow<List<QrHistory>> = MutableStateFlow(emptyList())

        override fun searchDocuments(query: String): Flow<List<Document>> = documents.map { list ->
            val needle = query.lowercase()
            list.filter { document ->
                document.title.lowercase().contains(needle) || document.ocrText.orEmpty().lowercase().contains(needle)
            }
        }

        override fun documentWithPages(documentId: Long): Flow<DocumentWithPages?> = documents.map { list ->
            list.firstOrNull { it.id == documentId }?.let { DocumentWithPages(document = it, pages = emptyList()) }
        }

        override suspend fun renameDocument(id: Long, title: String): ScanMateResult<Unit> {
            documents.value = documents.value.map { document -> if (document.id == id) document.copy(title = title) else document }
            return ScanMateResult.Success(Unit)
        }

        override suspend fun deleteDocument(id: Long): ScanMateResult<Unit> {
            documents.value = documents.value.filterNot { it.id == id }
            return ScanMateResult.Success(Unit)
        }

        override suspend fun updatePageOrder(pages: List<Page>): ScanMateResult<Unit> {
            pageOrders.clear()
            pageOrders += pages.mapIndexed { index, page -> page.id to index }
            return ScanMateResult.Success(Unit)
        }
    }
}
