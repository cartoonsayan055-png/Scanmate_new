package com.synthbyte.scanmate.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.synthbyte.scanmate.core.SafeLogger
import com.synthbyte.scanmate.data.AppDatabase
import com.synthbyte.scanmate.utils.DocumentIntelligence
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.OcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentOcrWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val documentId = inputData.getLong(KEY_DOCUMENT_ID, 0L)
        if (documentId <= 0L) {
            return@withContext Result.failure(Data.Builder().putString(KEY_ERROR, "Document is missing").build())
        }

        val dao = AppDatabase.getDatabase(applicationContext).docDao()
        val pages = dao.getPagesForDocumentOnce(documentId).sortedBy { it.pageOrder }
        if (pages.isEmpty()) {
            return@withContext Result.failure(Data.Builder().putString(KEY_ERROR, "No pages available for OCR").build())
        }

        runCatching {
            val extracted = pages.mapIndexedNotNull { index, page ->
                setProgress(Data.Builder().putInt(KEY_PROGRESS, ((index + 1) * 100 / pages.size).coerceIn(1, 99)).build())
                val file = File(page.imagePath)
                if (!file.exists() || file.length() == 0L) return@mapIndexedNotNull null
                val direct = OcrHelper.extractTextFromFile(applicationContext, file)
                val text = if (direct.startsWith("OCR failed", ignoreCase = true)) {
                    FileUtils.decodeSampledBitmap(file.absolutePath, 1800, 1800)?.let { bitmap ->
                        try {
                            OcrHelper.extractTextFromBitmap(bitmap)
                        } finally {
                            if (!bitmap.isRecycled) runCatching { bitmap.recycle() }
                        }
                    }.orEmpty()
                } else {
                    direct
                }
                text.takeIf { it.isNotBlank() && !it.startsWith("OCR failed", ignoreCase = true) }
                    ?.let { "Page ${index + 1}:\n$it" }
            }.joinToString(separator = "\n\n")

            if (extracted.isBlank()) {
                return@runCatching Result.failure(Data.Builder().putString(KEY_ERROR, "No readable text found").build())
            }

            val stats = OcrHelper.buildStats(extracted)
            val document = dao.getDocumentOnce(documentId)
            dao.updateOcrText(documentId, stats.text)
            dao.updateCategoryTags(
                id = documentId,
                category = DocumentIntelligence.suggestedCategory(stats.text),
                tags = DocumentIntelligence.keywordList(stats.text, 8).joinToString(", "),
                workspace = document?.workspace?.ifBlank { "Inbox" } ?: "Inbox"
            )
            setProgress(Data.Builder().putInt(KEY_PROGRESS, 100).build())
            Result.success(
                Data.Builder()
                    .putString(KEY_STATUS, stats.qualityLabel)
                    .putString(KEY_PREVIEW_TEXT, stats.text.take(8_000))
                    .putInt(KEY_CONFIDENCE, stats.confidencePercent)
                    .putInt(KEY_WORD_COUNT, stats.wordCount)
                    .build()
            )
        }.getOrElse { throwable ->
            SafeLogger.e(TAG, "Document OCR worker failed", throwable)
            Result.failure(Data.Builder().putString(KEY_ERROR, throwable.localizedMessage ?: "OCR failed").build())
        }
    }

    companion object {
        private const val TAG = "DocumentOcrWorker"
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_PREVIEW_TEXT = "preview_text"
        const val KEY_CONFIDENCE = "confidence"
        const val KEY_WORD_COUNT = "word_count"
        const val KEY_ERROR = "error"
    }
}
