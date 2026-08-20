package com.synthbyte.scanmate.utils

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.CRC32
import java.util.zip.ZipOutputStream
import com.synthbyte.scanmate.core.SafeLogger

object DocxExporter {
    suspend fun saveXlsxFromText(context: Context, text: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = FileUtils.appFolder(context, "Exports") ?: return@withContext null
            val safeName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "ScanMate_Table_${System.currentTimeMillis()}" })
                .removeSuffix(".xlsx")
                .removeSuffix(".XLSX")
            val file = File(storageDir, "$safeName.xlsx")
            val rows = textToTableRows(text)
            val sheetRows = rows.mapIndexed { rowIndex, cells ->
                val cellXml = cells.mapIndexed { colIndex, value ->
                    val ref = "${excelColumnName(colIndex + 1)}${rowIndex + 1}"
                    "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escapeXml(value)}</t></is></c>"
                }.joinToString("")
                "<row r=\"${rowIndex + 1}\">$cellXml</row>"
            }.joinToString("\n")
            ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
                zip.putXmlEntry("[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                        <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                        <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                        <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
                    </Types>
                """.trimIndent())
                zip.putXmlEntry("_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                        <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
                    </Relationships>
                """.trimIndent())
                zip.putXmlEntry("docProps/core.xml", officeCoreXml(safeName))
                zip.putXmlEntry("docProps/app.xml", officeAppXml())
                zip.putXmlEntry("xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                        <sheets><sheet name="ScanMate OCR" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                """.trimIndent())
                zip.putXmlEntry("xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                """.trimIndent())
                zip.putXmlEntry("xl/worksheets/sheet1.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                        <sheetData>$sheetRows</sheetData>
                    </worksheet>
                """.trimIndent())
            }
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("DocxExporter", "Operation failed", e)
            null
        }
    }

    suspend fun savePptxFromBitmaps(context: Context, pages: List<Bitmap>, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val validPages = pages.filter { it.width > 0 && it.height > 0 }
            if (validPages.isEmpty()) return@withContext null
            val storageDir = FileUtils.appFolder(context, "Exports") ?: return@withContext null
            val safeName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "ScanMate_Slides_${System.currentTimeMillis()}" })
                .removeSuffix(".pptx")
                .removeSuffix(".PPTX")
            val file = File(storageDir, "$safeName.pptx")
            val slideCx = 9144000L
            val slideCy = 6858000L
            ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
                val slideOverrides = validPages.indices.joinToString("\n") { index ->
                    "<Override PartName=\"/ppt/slides/slide${index + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>"
                }
                zip.putXmlEntry("[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Default Extension="png" ContentType="image/png"/>
                        <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
                        <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
                        <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
                        <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
                        <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                        <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
                        $slideOverrides
                    </Types>
                """.trimIndent())
                zip.putXmlEntry("_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
                        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                        <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
                    </Relationships>
                """.trimIndent())
                zip.putXmlEntry("docProps/core.xml", officeCoreXml(safeName))
                zip.putXmlEntry("docProps/app.xml", officeAppXml())
                val slideIds = validPages.indices.joinToString("\n") { index ->
                    "<p:sldId id=\"${256 + index}\" r:id=\"rId${index + 2}\"/>"
                }
                val presentationRels = buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                    appendLine("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                    appendLine("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
                    validPages.indices.forEach { index ->
                        appendLine("<Relationship Id=\"rId${index + 2}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide${index + 1}.xml\"/>")
                    }
                    appendLine("</Relationships>")
                }
                zip.putXmlEntry("ppt/presentation.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                        <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
                        <p:sldIdLst>$slideIds</p:sldIdLst>
                        <p:sldSz cx="$slideCx" cy="$slideCy" type="screen4x3"/>
                        <p:notesSz cx="6858000" cy="9144000"/>
                    </p:presentation>
                """.trimIndent())
                zip.putXmlEntry("ppt/_rels/presentation.xml.rels", presentationRels.trim())
                zip.putXmlEntry("ppt/slideMasters/slideMaster1.xml", slideMasterXml())
                zip.putXmlEntry("ppt/slideMasters/_rels/slideMaster1.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
                    </Relationships>
                """.trimIndent())
                zip.putXmlEntry("ppt/slideLayouts/slideLayout1.xml", slideLayoutXml())
                zip.putXmlEntry("ppt/slideLayouts/_rels/slideLayout1.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
                    </Relationships>
                """.trimIndent())
                zip.putXmlEntry("ppt/theme/theme1.xml", officeThemeXml())
                validPages.forEachIndexed { index, bitmap ->
                    zip.putXmlEntry("ppt/slides/slide${index + 1}.xml", slideWithImageXml(index + 1, slideCx, slideCy))
                    zip.putXmlEntry("ppt/slides/_rels/slide${index + 1}.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image${index + 1}.png"/>
                            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                        </Relationships>
                    """.trimIndent())
                    zip.putNextEntry(ZipEntry("ppt/media/image${index + 1}.png"))
                    val buffer = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
                    zip.write(buffer.toByteArray())
                    zip.closeEntry()
                }
            }
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("DocxExporter", "Operation failed", e)
            null
        }
    }

    private fun textToTableRows(text: String): List<List<String>> {
        val cleaned = DocumentIntelligence.cleanOcrText(text)
        val rows = cleaned.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val tabCells = trimmed.split('\t').map { it.trim() }.filter { it.isNotBlank() }
            val spacedCells = trimmed.split(Regex("\\s{2,}")).map { it.trim() }.filter { it.isNotBlank() }
            when {
                tabCells.size > 1 -> tabCells
                spacedCells.size > 1 -> spacedCells
                else -> listOf(trimmed)
            }
        }
        return rows.ifEmpty { listOf(listOf("No readable OCR text")) }.take(500)
    }

    private fun excelColumnName(index: Int): String {
        var value = index
        val result = StringBuilder()
        while (value > 0) {
            val rem = (value - 1) % 26
            result.insert(0, ('A'.code + rem).toChar())
            value = (value - 1) / 26
        }
        return result.toString()
    }

    private fun ZipOutputStream.putXmlEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.trim().toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun officeCoreXml(title: String): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <dc:title>${escapeXml(title)}</dc:title>
            <dc:creator>ScanMate</dc:creator>
            <cp:lastModifiedBy>ScanMate</cp:lastModifiedBy>
        </cp:coreProperties>
    """.trimIndent()

    private fun officeAppXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
            <Application>ScanMate</Application>
        </Properties>
    """.trimIndent()

    private fun slideWithImageXml(index: Int, cx: Long, cy: Long): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <p:cSld>
                <p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/><a:chOff x="0" y="0"/><a:chExt cx="$cx" cy="$cy"/></a:xfrm></p:grpSpPr>
                    <p:pic>
                        <p:nvPicPr><p:cNvPr id="${index + 1}" name="ScanMate page $index"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>
                        <p:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
                        <p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
                    </p:pic>
                </p:spTree>
            </p:cSld>
            <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
        </p:sld>
    """.trimIndent()

    private fun slideMasterXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
            <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld>
            <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
            <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
            <p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles>
        </p:sldMaster>
    """.trimIndent()

    private fun slideLayoutXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
            <p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld>
            <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
        </p:sldLayout>
    """.trimIndent()

    private fun officeThemeXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="ScanMate">
            <a:themeElements>
                <a:clrScheme name="ScanMate"><a:dk1><a:srgbClr val="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1F1F1F"/></a:dk2><a:lt2><a:srgbClr val="F5F5F5"/></a:lt2><a:accent1><a:srgbClr val="0B57D0"/></a:accent1><a:accent2><a:srgbClr val="2E7D32"/></a:accent2><a:accent3><a:srgbClr val="EF6C00"/></a:accent3><a:accent4><a:srgbClr val="6A1B9A"/></a:accent4><a:accent5><a:srgbClr val="00838F"/></a:accent5><a:accent6><a:srgbClr val="C62828"/></a:accent6><a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme>
                <a:fontScheme name="ScanMate"><a:majorFont><a:latin typeface="Aptos Display"/></a:majorFont><a:minorFont><a:latin typeface="Aptos"/></a:minorFont></a:fontScheme>
                <a:fmtScheme name="ScanMate"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme>
            </a:themeElements>
        </a:theme>
    """.trimIndent()

    suspend fun saveDocxText(context: Context, text: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = FileUtils.appFolder(context, "OCR") ?: return@withContext null
            val safeName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "ScanMate_Document_${System.currentTimeMillis()}" })
                .removeSuffix(".docx")
                .removeSuffix(".DOCX")
            val file = File(storageDir, "$safeName.docx")
            val cleaned = DocumentIntelligence.cleanOcrText(text).ifBlank { "ScanMate document export" }
            exportDocx(cleaned, file)
            file.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            SafeLogger.e("DocxExporter", "Operation failed", e)
            null
        }
    }

    /**
     * Writes a valid DOCX without Apache POI at runtime.
     *
     * The release APK previously crashed in the previous POI-based DOCX runtime path.
     * A DOCX is an OPC ZIP package,
     * so this exporter writes the small set of Office XML parts ScanMate needs for OCR text export.
     */
    fun exportDocx(text: String, file: File) {
        file.parentFile?.mkdirs()
        val safeText = text.ifBlank { "ScanMate document export" }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            zip.putDocxEntry("[Content_Types].xml", contentTypesXml())
            zip.putDocxEntry("_rels/.rels", rootRelsXml())
            zip.putDocxEntry("docProps/core.xml", officeCoreXml(file.nameWithoutExtension.ifBlank { "ScanMate Document" }))
            zip.putDocxEntry("docProps/app.xml", officeAppXml())
            zip.putDocxEntry("word/document.xml", buildDocumentXml(safeText))
            zip.putDocxEntry("word/_rels/document.xml.rels", wordRelsXml())
            zip.putDocxEntry("word/styles.xml", stylesXml())
            zip.putDocxEntry("word/settings.xml", settingsXml())
        }
    }

    private fun contentTypesXml(): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
    <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
    <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
    <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>
""".trimIndent()

    private fun rootRelsXml(): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
    <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>
""".trimIndent()

    private fun wordRelsXml(): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
""".trimIndent()

    private fun stylesXml(): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:docDefaults>
        <w:rPrDefault>
            <w:rPr>
                <w:rFonts w:ascii="Aptos" w:hAnsi="Aptos" w:cs="Aptos"/>
                <w:sz w:val="22"/>
                <w:szCs w:val="22"/>
            </w:rPr>
        </w:rPrDefault>
        <w:pPrDefault>
            <w:pPr>
                <w:spacing w:after="120" w:line="276" w:lineRule="auto"/>
            </w:pPr>
        </w:pPrDefault>
    </w:docDefaults>
    <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
        <w:name w:val="Normal"/>
        <w:qFormat/>
        <w:pPr>
            <w:spacing w:after="120" w:line="276" w:lineRule="auto"/>
        </w:pPr>
        <w:rPr>
            <w:rFonts w:ascii="Aptos" w:hAnsi="Aptos" w:cs="Aptos"/>
            <w:sz w:val="22"/>
            <w:szCs w:val="22"/>
        </w:rPr>
    </w:style>
</w:styles>
""".trimIndent()

    private fun settingsXml(): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:zoom w:percent="100"/>
    <w:defaultTabStop w:val="720"/>
    <w:compat/>
</w:settings>
""".trimIndent()

    private fun buildDocumentXml(text: String): String {
        val normalised = text.replace("\r\n", "\n").replace('\r', '\n')
        val paragraphs = normalised.split("\n").joinToString("\n") { line ->
            "<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(line)}</w:t></w:r></w:p>"
        }.ifBlank {
            "<w:p><w:r><w:t xml:space=\"preserve\">ScanMate document export</w:t></w:r></w:p>"
        }
        return """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:body>
        $paragraphs
        <w:sectPr>
            <w:pgSz w:w="12240" w:h="15840"/>
            <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
        </w:sectPr>
    </w:body>
</w:document>
""".trimIndent()
    }

    private fun ZipOutputStream.putStoredXmlEntry(path: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val crc32 = CRC32().apply { update(bytes) }
        val entry = ZipEntry(path).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc32.value
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putDocxEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun escapeXml(value: String): String = value
        .filter { char -> char == '\t' || char == '\n' || char == '\r' || char.code >= 0x20 }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
