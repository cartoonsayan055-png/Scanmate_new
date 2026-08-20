package com.synthbyte.scanmate.domain.usecase

import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    operator fun invoke(query: String): Flow<List<Document>> = repository.searchDocuments(query.trim())
}
