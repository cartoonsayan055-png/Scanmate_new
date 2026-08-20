package com.synthbyte.scanmate

import com.synthbyte.scanmate.utils.DocxExporter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class DocxExporterTest {
    @Test
    fun exportDocxCreatesValidOfficePackageWithoutPoiRuntime() {
        val file = File.createTempFile("scanmate_docx_test_", ".docx")
        file.deleteOnExit()

        DocxExporter.exportDocx("Hello & <ScanMate>\nSecond line", file)

        assertTrue(file.exists())
        assertTrue(file.length() > 500L)
        ZipFile(file).use { zip ->
            assertNotNull(zip.getEntry("[Content_Types].xml"))
            assertNotNull(zip.getEntry("_rels/.rels"))
            assertNotNull(zip.getEntry("word/document.xml"))
            assertNotNull(zip.getEntry("word/styles.xml"))
            assertNotNull(zip.getEntry("word/settings.xml"))
            val documentXml = zip.getInputStream(zip.getEntry("word/document.xml"))
                .bufferedReader()
                .use { it.readText() }
            assertTrue(documentXml.contains("Hello &amp; &lt;ScanMate&gt;"))
            assertTrue(documentXml.contains("Second line"))
            assertFalse(documentXml.contains("<ScanMate>"))
        }
    }
}
