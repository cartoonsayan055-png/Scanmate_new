package com.synthbyte.scanmate

import com.synthbyte.scanmate.utils.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class FileUtilsFacadeTest {
    @Test
    fun mimeTypeForReturnsApplicationPdfForPdfFile() {
        assertEquals("application/pdf", FileUtils.mimeTypeFor(File("report.pdf")))
    }

    @Test
    fun mimeTypeForReturnsImageJpegForJpgFile() {
        assertEquals("image/jpeg", FileUtils.mimeTypeFor(File("photo.jpg")))
    }

    @Test
    fun sanitizeFileBaseNameStripsIllegalCharacters() {
        val sanitized = FileUtils.sanitizeFileBaseName("Receipt: May/2026?*")

        assertEquals("Receipt_May_2026", sanitized)
        assertFalse(sanitized.contains(':'))
        assertFalse(sanitized.contains('/'))
        assertFalse(sanitized.contains('?'))
        assertFalse(sanitized.contains('*'))
    }
}
