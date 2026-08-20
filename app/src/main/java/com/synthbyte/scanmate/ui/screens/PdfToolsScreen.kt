package com.synthbyte.scanmate.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.OcrHelper
import com.synthbyte.scanmate.utils.PdfExportQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedPdfs = remember { mutableStateListOf<Uri>() }
    val selectedImages = remember { mutableStateListOf<Uri>() }
    var isProcessing by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var outputName by remember { mutableStateOf("scanmate-export") }
    var splitStart by remember { mutableStateOf("1") }
    var splitEnd by remember { mutableStateOf("1") }
    var quality by remember { mutableStateOf(PdfExportQuality.BALANCED) }
    var textToPdf by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            selectedPdfs.clear()
            selectedPdfs.addAll(uris)
            resultMessage = "${uris.size} PDF file${if (uris.size == 1) "" else "s"} selected."
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 40)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.clear()
            selectedImages.addAll(uris)
            resultMessage = "${uris.size} image file${if (uris.size == 1) "" else "s"} selected."
        }
    }

    fun showResult(file: File?, successMessage: String, failureMessage: String) {
        exportedFile = file
        resultMessage = if (file != null) successMessage else failureMessage
        Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()
    }

    fun requireSelectedPdf(): Boolean {
        if (selectedPdfs.isEmpty()) {
            resultMessage = "Select one or more PDFs first."
            Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF & Office Export") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Offline export workspace", fontWeight = FontWeight.Bold)
                            Text("Render PDFs safely, export real Office files, create images and merge page ranges without cloud services.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.UploadFile, null)
                            Spacer(Modifier.width(6.dp))
                            Text("PDFs")
                        }
                        OutlinedButton(
                            onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Images")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Export filename") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PdfExportQuality.entries.forEach { item ->
                    FilterChip(
                        selected = quality == item,
                        onClick = { quality = item },
                        label = { Text(item.label.substringBefore(" /")) }
                    )
                }
            }

            if (selectedPdfs.isEmpty()) {
                EmptyPdfToolState()
            } else {
                Text("Selected PDFs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                selectedPdfs.forEachIndexed { index, uri ->
                    PdfUriRow(index = index, uri = uri, onRemove = { selectedPdfs.remove(uri) })
                }
            }

            if (selectedImages.isNotEmpty()) {
                Text("Selected Images", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                selectedImages.forEachIndexed { index, uri ->
                    PdfUriRow(index = index, uri = uri, onRemove = { selectedImages.remove(uri) })
                }
            }

            ToolGroupTitle("PDF output")
            ConversionButton(
                icon = Icons.Default.PictureAsPdf,
                title = "Images to PDF",
                subtitle = "Build a readable PDF from selected gallery images.",
                enabled = selectedImages.isNotEmpty() && !isProcessing
            ) {
                scope.launch {
                    isProcessing = true
                    val output = withContext(Dispatchers.IO) {
                        val bitmaps = renderImageUris(context, selectedImages)
                        try {
                            FileUtils.generatePdfFromBitmaps(context, bitmaps, outputName, quality)
                        } finally {
                            recycleBitmaps(bitmaps)
                        }
                    }
                    isProcessing = false
                    showResult(output, "Images to PDF exported", "Images to PDF failed")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (!requireSelectedPdf()) return@Button
                        scope.launch {
                            isProcessing = true
                            val output = withContext(Dispatchers.IO) {
                                val pages = renderSelectedPdfPages(context, selectedPdfs, quality)
                                try {
                                    FileUtils.generatePdfFromBitmaps(context, pages, outputName, quality)
                                } finally {
                                    recycleBitmaps(pages)
                                }
                            }
                            isProcessing = false
                            showResult(output, "Merged PDF exported", "PDF merge failed")
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MergeType, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Merge")
                }
                OutlinedButton(
                    onClick = {
                        if (!requireSelectedPdf()) return@OutlinedButton
                        val start = splitStart.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val end = splitEnd.toIntOrNull()?.coerceAtLeast(start) ?: start
                        scope.launch {
                            isProcessing = true
                            val output = withContext(Dispatchers.IO) {
                                val pages = FileUtils.renderPdfUriToBitmaps(context, selectedPdfs.first(), maxWidth = widthFor(quality), pageRange = start..end)
                                try {
                                    FileUtils.generatePdfFromBitmaps(context, pages, "${outputName}_pages_${start}_$end", quality)
                                } finally {
                                    recycleBitmaps(pages)
                                }
                            }
                            isProcessing = false
                            showResult(output, "Page range exported", "Page extraction failed")
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) { Text("Extract pages") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = splitStart,
                    onValueChange = { splitStart = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("From page") }
                )
                OutlinedTextField(
                    value = splitEnd,
                    onValueChange = { splitEnd = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("To page") }
                )
            }

            ToolGroupTitle("Convert selected PDF")
            ConversionButton(
                icon = Icons.Default.Description,
                title = "PDF to Word",
                subtitle = "OCR pages and export a real DOCX file.",
                enabled = selectedPdfs.isNotEmpty() && !isProcessing
            ) {
                scope.launch {
                    isProcessing = true
                    val output = withContext(Dispatchers.IO) {
                        val pages = renderSelectedPdfPages(context, selectedPdfs, quality)
                        try {
                            val text = ocrPagesToText(pages)
                            FileUtils.saveDocxText(context, text, outputName)
                        } finally {
                            recycleBitmaps(pages)
                        }
                    }
                    isProcessing = false
                    showResult(output, "DOCX exported from PDF OCR", "PDF to Word failed")
                }
            }
            ConversionButton(
                icon = Icons.Default.Description,
                title = "PDF to Excel",
                subtitle = "OCR rows into a valid XLSX workbook.",
                enabled = selectedPdfs.isNotEmpty() && !isProcessing
            ) {
                scope.launch {
                    isProcessing = true
                    val output = withContext(Dispatchers.IO) {
                        val pages = renderSelectedPdfPages(context, selectedPdfs, quality)
                        try {
                            val text = ocrPagesToText(pages)
                            FileUtils.saveXlsxFromText(context, text, outputName)
                        } finally {
                            recycleBitmaps(pages)
                        }
                    }
                    isProcessing = false
                    showResult(output, "XLSX exported from PDF OCR", "PDF to Excel failed")
                }
            }
            ConversionButton(
                icon = Icons.Default.PictureAsPdf,
                title = "PDF to PPT",
                subtitle = "One rendered PDF page per valid PPTX slide.",
                enabled = selectedPdfs.isNotEmpty() && !isProcessing
            ) {
                scope.launch {
                    isProcessing = true
                    val output = withContext(Dispatchers.IO) {
                        val pages = renderSelectedPdfPages(context, selectedPdfs, quality)
                        try {
                            FileUtils.savePptxFromBitmaps(context, pages, outputName)
                        } finally {
                            recycleBitmaps(pages)
                        }
                    }
                    isProcessing = false
                    showResult(output, "PPTX exported from PDF pages", "PDF to PPT failed")
                }
            }
            ConversionButton(
                icon = Icons.Default.Image,
                title = "PDF to Images",
                subtitle = "Render every selected page as PNG images.",
                enabled = selectedPdfs.isNotEmpty() && !isProcessing
            ) {
                scope.launch {
                    isProcessing = true
                    val files = withContext(Dispatchers.IO) {
                        val pages = renderSelectedPdfPages(context, selectedPdfs, quality)
                        try {
                            FileUtils.saveRenderedPdfPagesAsImages(context, pages, outputName)
                        } finally {
                            recycleBitmaps(pages)
                        }
                    }
                    exportedFile = files.firstOrNull()
                    resultMessage = if (files.isNotEmpty()) "${files.size} image file${if (files.size == 1) "" else "s"} exported" else "PDF to images failed"
                    isProcessing = false
                    Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()
                }
            }
            ConversionButton(
                icon = Icons.Default.Image,
                title = "PDF to Long Image",
                subtitle = "Stitch rendered pages into one PNG where memory allows.",
                enabled = selectedPdfs.isNotEmpty() && !isProcessing
            ) {
                scope.launch {
                    isProcessing = true
                    val output = withContext(Dispatchers.IO) {
                        val pages = renderSelectedPdfPages(context, selectedPdfs, quality)
                        try {
                            FileUtils.saveLongImageFromBitmaps(context, pages, outputName)
                        } finally {
                            recycleBitmaps(pages)
                        }
                    }
                    isProcessing = false
                    showResult(output, "Long image exported", "Long image failed or document was too tall")
                }
            }

            ToolGroupTitle("Text to PDF")
            OutlinedTextField(
                value = textToPdf,
                onValueChange = { textToPdf = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("Paste or type text") }
            )
            Button(
                onClick = {
                    scope.launch {
                        isProcessing = true
                        val output = FileUtils.generatePdfFromText(context, textToPdf, outputName)
                        isProcessing = false
                        showResult(output, "Text PDF exported", "Add text before exporting")
                    }
                },
                enabled = textToPdf.isNotBlank() && !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PictureAsPdf, null)
                Spacer(Modifier.width(6.dp))
                Text("Export Text to PDF")
            }

            if (resultMessage.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(resultMessage, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            exportedFile?.let { file ->
                ExportedFileActions(file = file, onDismiss = { exportedFile = null })
            }
        }
    }
}

@Composable
private fun EmptyPdfToolState() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No PDFs selected", fontWeight = FontWeight.Bold)
            Text("Choose PDFs to merge, extract pages, convert to Office files, export images, or make a long image.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PdfUriRow(index: Int, uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val name = remember(uri) { FileUtils.getDisplayName(context, uri) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text("${index + 1}") })
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Ready for offline rendering and export", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
        }
    }
}

@Composable
private fun ToolGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ConversionButton(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExportedFileActions(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mimeType = remember(file) { FileUtils.mimeTypeFor(file) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export ready") },
        text = { Text("${file.name} was created successfully.") },
        confirmButton = {
            Button(onClick = { FileUtils.openFile(context, file, mimeType) }) {
                Icon(Icons.Default.OpenInNew, null)
                Spacer(Modifier.width(6.dp))
                Text("Open")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { FileUtils.shareFile(context, file, mimeType) }) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

private const val PDF_IMPORT_MAX_SIDE = 2600

private fun renderImageUris(context: android.content.Context, selectedImages: List<Uri>): List<android.graphics.Bitmap> {
    // Decoding several full-resolution gallery photos into memory at once (unsampled)
    // was a real OOM risk here. Bounds-check first, then decode downsampled.
    return selectedImages.mapNotNull { uri ->
        runCatching {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return@runCatching null

            var sampleSize = 1
            while (options.outHeight / sampleSize >= PDF_IMPORT_MAX_SIDE * 2 ||
                options.outWidth / sampleSize >= PDF_IMPORT_MAX_SIDE * 2
            ) {
                sampleSize *= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }.getOrNull()
    }
}

private fun renderSelectedPdfPages(context: android.content.Context, selectedPdfs: List<Uri>, quality: PdfExportQuality): List<android.graphics.Bitmap> {
    return selectedPdfs.flatMap { uri -> FileUtils.renderPdfUriToBitmaps(context, uri, maxWidth = widthFor(quality)) }
}

private suspend fun ocrPagesToText(pages: List<android.graphics.Bitmap>): String {
    return pages.mapIndexed { index, bitmap ->
        val stats = OcrHelper.extractStatsFromBitmap(bitmap)
        "Page ${index + 1}\n${stats.text}"
    }.joinToString("\n\u000C\n")
}

private fun recycleBitmaps(pages: List<android.graphics.Bitmap>) {
    pages.forEach { bitmap -> if (!bitmap.isRecycled) runCatching { bitmap.recycle() } }
}

private fun widthFor(quality: PdfExportQuality): Int = when (quality) {
    PdfExportQuality.SMALL -> 1000
    PdfExportQuality.BALANCED -> 1500
    PdfExportQuality.HIGH -> 2200
}
