package com.synthbyte.scanmate

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.synthbyte.scanmate.utils.DocumentIntelligence
import com.synthbyte.scanmate.utils.OcrHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrHelperTest {
    @Test
    fun estimateSkewAndRotateReturnsSourceUnchangedWhenWidthBelow100() {
        val source = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)

        val result = invokeBitmapPrivate("estimateSkewAndRotate", source)

        assertSame(source, result)
        source.recycle()
    }

    @Test
    fun estimateSkewAndRotateReturnsSourceUnchangedWhenBestAngleBelowThreshold() {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)

        val result = invokeBitmapPrivate("estimateSkewAndRotate", source)

        assertSame(source, result)
        source.recycle()
    }

    @Test
    fun preprocessForOcrReturnsBitmapForSmallSource() {
        val source = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)

        val result = invokeBitmapPrivate("preprocessForOcr", source)

        assertEquals(120, result.width)
        assertEquals(120, result.height)
        if (result !== source) result.recycle()
        source.recycle()
    }

    @Test
    fun extractBlocksFromFileReturnsEmptyListOnMissingFile() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missing = File(context.cacheDir, "missing_${System.nanoTime()}.jpg")

        val result = OcrHelper.extractBlocksFromFile(context, missing)

        assertTrue(result.isEmpty())
    }

    @Test
    fun cleanOcrTextRepairsUrlAndEmailSpacingConservatively() {
        val raw = "Visit itseries. com.pk or mail info @ example. com\n\nPreface"

        val result = DocumentIntelligence.cleanOcrText(raw)

        assertTrue(result.contains("itseries.com.pk"))
        assertTrue(result.contains("info@example.com"))
        assertTrue(result.endsWith("Preface"))
    }

    private fun invokeBitmapPrivate(methodName: String, source: Bitmap): Bitmap {
        val method = OcrHelper::class.java.getDeclaredMethod(methodName, Bitmap::class.java)
        method.isAccessible = true
        return method.invoke(OcrHelper, source) as Bitmap
    }
}
