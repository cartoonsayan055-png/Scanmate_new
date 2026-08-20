package com.synthbyte.scanmate.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.synthbyte.scanmate.R
import com.synthbyte.scanmate.core.SafeLogger
import com.synthbyte.scanmate.data.AppDatabase
import com.synthbyte.scanmate.utils.OcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OcrWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pagePath = inputData.getString(KEY_PAGE_PATH).orEmpty()
        val documentId = inputData.getLong(KEY_DOCUMENT_ID, 0L)
        val file = File(pagePath)
        if (documentId <= 0L || !file.exists()) {
            return@withContext Result.failure(Data.Builder().putString(KEY_ERROR, "OCR source page is missing").build())
        }

        publishProgress(5, "Preparing OCR")
        runCatching {
            val stats = OcrHelper.extractTextWithStatsFromFile(applicationContext, file)
            publishProgress(80, stats.qualityLabel)
            AppDatabase.getDatabase(applicationContext).docDao().updateOcrText(documentId, stats.text)
            publishProgress(100, "OCR complete")
            Result.success(
                Data.Builder()
                    .putInt(KEY_CONFIDENCE, stats.confidencePercent)
                    .putInt(KEY_WORD_COUNT, stats.wordCount)
                    .build()
            )
        }.getOrElse { throwable ->
            SafeLogger.e(TAG, "Background OCR failed", throwable)
            Result.failure(Data.Builder().putString(KEY_ERROR, throwable.localizedMessage ?: "OCR failed").build())
        }
    }

    private suspend fun publishProgress(percent: Int, label: String) {
        setProgress(Data.Builder().putInt(KEY_PROGRESS, percent).putString(KEY_STATUS, label).build())
        showNotification(percent, label)
    }

    private fun showNotification(percent: Int, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "ScanMate OCR", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ScanMate")
            .setContentText(label)
            .setOnlyAlertOnce(true)
            .setOngoing(percent in 1..99)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "OcrWorker"
        private const val CHANNEL_ID = "scanmate_ocr_progress"
        private const val NOTIFICATION_ID = 1003
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_PAGE_PATH = "page_path"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_CONFIDENCE = "confidence"
        const val KEY_WORD_COUNT = "word_count"
        const val KEY_ERROR = "error"
    }
}
