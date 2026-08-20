package com.synthbyte.scanmate.domain.usecase

import com.synthbyte.scanmate.core.ScanMateResult
import com.synthbyte.scanmate.domain.repository.DocumentRepository
import javax.inject.Inject

class DeleteDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(documentId: Long): ScanMateResult<Unit> = repository.deleteDocument(documentId)
}
