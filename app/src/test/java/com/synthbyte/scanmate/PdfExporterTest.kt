package com.synthbyte.scanmate

import android.graphics.Bitmap
import com.synthbyte.scanmate.utils.PdfExporter
import com.synthbyte.scanmate.utils.PdfPageSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfExporterTest {
    @Test
    fun buildPdfPageDimensionsAutoReturnsBitmapDimensions() {
        val bitmap = Bitmap.createBitmap(321, 654, Bitmap.Config.ARGB_8888)

        val dimensions = PdfExporter.buildPdfPageDimensions(bitmap, PdfPageSize.AUTO)

        assertEquals(321 to 654, dimensions)
        bitmap.recycle()
    }

    @Test
    fun buildPdfPageDimensionsA4Returns595By842() {
        val bitmap = Bitmap.createBitmap(321, 654, Bitmap.Config.ARGB_8888)

        val dimensions = PdfExporter.buildPdfPageDimensions(bitmap, PdfPageSize.A4)

        assertEquals(595 to 842, dimensions)
        bitmap.recycle()
    }

    @Test
    fun buildPdfPageDimensionsLetterReturns612By792() {
        val bitmap = Bitmap.createBitmap(321, 654, Bitmap.Config.ARGB_8888)

        val dimensions = PdfExporter.buildPdfPageDimensions(bitmap, PdfPageSize.LETTER)

        assertEquals(612 to 792, dimensions)
        bitmap.recycle()
    }
}
