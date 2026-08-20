package com.synthbyte.scanmate.domain.usecase

import com.synthbyte.scanmate.core.ScanMateResult
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.domain.repository.DocumentRepository
import javax.inject.Inject

class ReorderPagesUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(pages: List<Page>): ScanMateResult<Unit> = repository.updatePageOrder(pages)
}
