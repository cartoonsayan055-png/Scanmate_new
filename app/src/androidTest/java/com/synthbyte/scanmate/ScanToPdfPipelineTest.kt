package com.synthbyte.scanmate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.OcrHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ScanToPdfPipelineTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun pipelineProducesNonEmptyPdf() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val bmp = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawText(
                    "Invoice Total: 99.99",
                    50f,
                    100f,
                    Paint().apply {
                        color = Color.BLACK
                        textSize = 40f
                    },
                )
            }
        }

        val file = FileUtils.saveBitmapToFolder(
            ctx,
            bmp,
            "Scans",
            "test_${System.nanoTime()}",
        )

        assertNotNull(file)

        val blocks = OcrHelper.extractBlocksFromFile(ctx, file!!)
        assertTrue(blocks.isNotEmpty())

        val pdf = FileUtils.generatePdfFromPaths(
            ctx,
            listOf(file.absolutePath),
            "test_pdf_${System.nanoTime()}",
            ocrRectsByPath = mapOf(file.absolutePath to blocks),
        )

        assertNotNull(pdf)
        assertTrue(pdf!!.length() > 1000L)

        bmp.recycle()
        file.delete()
        pdf.delete()
    }
}
