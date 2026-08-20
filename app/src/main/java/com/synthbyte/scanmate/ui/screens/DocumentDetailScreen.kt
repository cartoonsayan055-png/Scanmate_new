package com.synthbyte.scanmate.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.synthbyte.scanmate.data.DocumentWithPages
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.utils.DocumentAnalyticsEngine
import com.synthbyte.scanmate.utils.EncryptedVaultUtils
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.OcrHelper
import com.synthbyte.scanmate.utils.PdfExportQuality
import com.synthbyte.scanmate.utils.PdfPageSize
import com.synthbyte.scanmate.ui.viewmodels.ExportState
import androidx.hilt.navigation.compose.hiltViewModel
import com.synthbyte.scanmate.ui.viewmodels.DocumentDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    docId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToPageEditor: (Long, Long) -> Unit,
    onNavigateToSignature: (Long) -> Unit
) {
    val context = LocalContext.current
    val viewModel: DocumentDetailViewModel = hiltViewModel()
    val documentWithPages by viewModel.documentWithPages.collectAsState(initial = null)
    val exportState by viewModel.exportState.collectAsState()
    var isProcessing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedPdf by remember { mutableStateOf<File?>(null) }
    var exportedDocx by remember { mutableStateOf<File?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var exportName by remember { mutableStateOf("") }
    var selectedPdfQuality by remember { mutableStateOf(PdfExportQuality.BALANCED) }
    var exportProgress by remember { mutableStateOf<String?>(null) }
    var selectedPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var passwordProtect by remember { mutableStateOf(false) }
    var pdfPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var allowCopy by remember { mutableStateOf(true) }
    var allowPrinting by remember { mutableStateOf(true) }
    val passwordValid = pdfPassword.length >= 6
    val passwordsMatch = confirmPassword == pdfPassword
    var topBarMenuExpanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("General") }
    var tags by remember { mutableStateOf("") }
    var workspace by remember { mutableStateOf("Inbox") }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    LaunchedEffect(documentWithPages?.document?.title, documentWithPages?.document?.category, documentWithPages?.document?.tags, documentWithPages?.document?.workspace) {
        val title = documentWithPages?.document?.title.orEmpty()
        renameTitle = title
        if (documentWithPages?.document != null && (exportName.isBlank() || exportName == "ScanMate_Export")) {
            exportName = FileUtils.sanitizeFileBaseName(title)
                .ifBlank { "ScanMate_Export" }
        }
        category = documentWithPages?.document?.category ?: "General"
        tags = documentWithPages?.document?.tags.orEmpty()
        workspace = documentWithPages?.document?.workspace ?: "Inbox"
    }


    fun onConfirmExport() {
        if (passwordProtect && (!passwordValid || !passwordsMatch)) {
            Toast.makeText(context, "Enter matching password of at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }
        showExportDialog = false
        val safeExportName = FileUtils.sanitizeFileBaseName(exportName)
            .ifBlank { "ScanMate_Export" }
        if (passwordProtect && passwordValid && passwordsMatch) {
            viewModel.exportProtectedPdf(documentWithPages, pdfPassword, safeExportName, allowPrinting, allowCopy)
        } else {
            viewModel.exportPdf(documentWithPages, selectedPdfQuality, safeExportName, selectedPageSize)
        }
    }

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            ExportState.Idle -> {
                isProcessing = false
                exportProgress = null
            }
            is ExportState.Loading -> {
                isProcessing = true
                exportProgress = state.message
            }
            is ExportState.PdfSuccess -> {
                isProcessing = false
                exportProgress = null
                exportedPdf = state.file
                Toast.makeText(context, "PDF exported", Toast.LENGTH_SHORT).show()
                viewModel.clearExportState()
            }
            is ExportState.DocxSuccess -> {
                isProcessing = false
                exportProgress = null
                exportedDocx = state.file
                Toast.makeText(context, "DOCX exported", Toast.LENGTH_SHORT).show()
                viewModel.clearExportState()
            }
            is ExportState.OcrSuccess -> {
                isProcessing = false
                exportProgress = null
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Extracted Text", state.text))
                Toast.makeText(context, "OCR complete · ${state.qualityLabel}", Toast.LENGTH_SHORT).show()
                viewModel.clearExportState()
            }
            is ExportState.VaultSuccess -> {
                isProcessing = false
                exportProgress = null
                Toast.makeText(context, "Moved ${state.itemCount} item(s) to Secure Vault", Toast.LENGTH_SHORT).show()
                viewModel.clearExportState()
            }
            is ExportState.QualitySuccess -> {
                isProcessing = false
                exportProgress = null
            }
            is ExportState.Error -> {
                isProcessing = false
                exportProgress = null
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.clearExportState()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(documentWithPages?.document?.title ?: "Document", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    documentWithPages?.let { dwp ->
                        IconButton(onClick = {
                            viewModel.setFavorite(!dwp.document.isFavorite)
                        }) {
                            Icon(if (dwp.document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
                        }
                    }
                    IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.PictureAsPdf, "Export PDF") }
                    Box {
                        IconButton(onClick = { topBarMenuExpanded = true }) { Icon(Icons.Default.MoreVert, "More options") }
                        DropdownMenu(expanded = topBarMenuExpanded, onDismissRequest = { topBarMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                            onClick = {
                                topBarMenuExpanded = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Run OCR") },
                            leadingIcon = { Icon(Icons.Default.TextSnippet, null) },
                            onClick = {
                                topBarMenuExpanded = false
                                viewModel.extractOcr(documentWithPages)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Check quality") },
                            leadingIcon = { Icon(Icons.Default.Analytics, null) },
                            onClick = {
                                viewModel.scoreQuality(documentWithPages)
                                topBarMenuExpanded = false
                            }
                        )
                        documentWithPages?.let { dwp ->
                            DropdownMenuItem(
                                text = { Text(if (dwp.document.isPinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Default.PushPin, null) },
                                onClick = {
                                    topBarMenuExpanded = false
                                    viewModel.setPinned(!dwp.document.isPinned)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Move to Secure Vault") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick = {
                                topBarMenuExpanded = false
                                viewModel.moveDocumentToVault(documentWithPages) {
                                    Toast.makeText(context, "Document moved to Secure Vault", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                topBarMenuExpanded = false
                                showDeleteDialog = true
                            }
                        )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                exportProgress?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            val dwp = documentWithPages
            if (dwp == null) {
                LoadingDocumentState()
            } else {
                val pages = dwp.pages.sortedBy { it.pageOrder }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(dwp.document.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${pages.size} page${if (pages.size == 1) "" else "s"} · ${dwp.document.workspace.ifBlank { "Inbox" }} · ${dwp.document.category.ifBlank { "General" }}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                DocumentPreview(pages)
                QuickActionRow(
                    dwp = dwp,
                    onShareFirstImage = {
                        val firstFile = pages.firstOrNull()?.imagePath?.let { File(it) }
                        if (firstFile != null && firstFile.exists()) {
                            FileUtils.shareFile(context, firstFile, FileUtils.mimeTypeFor(firstFile))
                        } else {
                            Toast.makeText(context, "No image file found to share", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExport = { showExportDialog = true },
                    onExportDocx = { viewModel.exportDocx(documentWithPages) },
                    onSignature = { onNavigateToSignature(docId) },
                    onMeta = { showMetaDialog = true },
                    onMoveVault = {
                        viewModel.moveDocumentToVault(dwp) {
                            Toast.makeText(context, "Document moved to Secure Vault", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    }
                )
                DocumentMetaChips(dwp)
                DocumentInsightsCard(dwp)
                OcrCard(dwp = dwp, clipboardManager = clipboardManager, context = context) { file -> exportedDocx = file }
                PageThumbnails(pages, onEdit = { page -> onNavigateToPageEditor(docId, page.id) })
                PageManagementList(
                    pages = pages,
                    onEdit = { page -> onNavigateToPageEditor(docId, page.id) },
                    onMove = { page, direction ->
                        viewModel.movePage(page, direction) {
                            Toast.makeText(context, "Page order updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReorder = { reorderedPages ->
                        viewModel.reorderPages(reorderedPages) {
                            Toast.makeText(context, "Page order saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDuplicate = { page ->
                        viewModel.duplicatePage(page) {
                            Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { page ->
                        viewModel.deletePage(page.id) {
                            Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    singleLine = true,
                    label = { Text("Document name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.rename(renameTitle)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showMetaDialog) {
        AlertDialog(
            onDismissRequest = { showMetaDialog = false },
            title = { Text("Category & tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = workspace, onValueChange = { workspace = it }, label = { Text("Workspace / folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags, comma separated") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateMeta(category, tags, workspace)
                    showMetaDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showMetaDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this document?") },
            text = { Text("This removes the document record from ScanMate. Export or share anything important first.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            modifier = Modifier.fillMaxWidth(0.92f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Export PDF") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = exportName,
                        onValueChange = { exportName = it },
                        label = { Text("PDF name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Choose page size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfPageSize.entries.forEach { size ->
                            FilterChip(
                                selected = selectedPageSize == size,
                                onClick = { selectedPageSize = size },
                                label = { Text(size.label, modifier = Modifier.padding(horizontal = 16.dp)) },
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = passwordProtect, onCheckedChange = { passwordProtect = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Password protect PDF", style = MaterialTheme.typography.bodyMedium)
                    }
                    AnimatedVisibility(visible = passwordProtect) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pdfPassword,
                                onValueChange = { pdfPassword = it },
                                label = { Text("PDF Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = pdfPassword.isNotEmpty() && !passwordValid,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (pdfPassword.isNotEmpty() && !passwordValid) {
                                Text("Minimum 6 characters required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                            if (pdfPassword.length >= 6) {
                                val passwordStrength = when {
                                    pdfPassword.length >= 12 && pdfPassword.any { it.isDigit() } && pdfPassword.any { !it.isLetterOrDigit() } -> Triple("Strong", 1f, MaterialTheme.colorScheme.primary)
                                    pdfPassword.length >= 8 && (pdfPassword.any { it.isDigit() } || pdfPassword.any { !it.isLetterOrDigit() }) -> Triple("Fair", 0.6f, MaterialTheme.colorScheme.tertiary)
                                    else -> Triple("Weak", 0.3f, MaterialTheme.colorScheme.error)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LinearProgressIndicator(progress = { passwordStrength.second }, modifier = Modifier.weight(1f).height(4.dp), color = passwordStrength.third, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                    Text(passwordStrength.first, style = MaterialTheme.typography.labelSmall, color = passwordStrength.third, fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                                supportingText = { if (confirmPassword.isNotEmpty() && !passwordsMatch) Text("Passwords do not match") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = allowPrinting, onCheckedChange = { allowPrinting = it })
                                    Text("Allow printing", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = allowCopy, onCheckedChange = { allowCopy = it })
                                    Text("Allow copy", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text("Choose quality", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PdfExportQuality.entries.forEach { quality ->
                        OutlinedButton(
                            onClick = { selectedPdfQuality = quality },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    if (selectedPdfQuality == quality) "${quality.label} ✓" else quality.label,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(quality.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showExportDialog = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = { onConfirmExport() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text("Export PDF") }
                }
            },
            dismissButton = {}
        )
    }


    if (exportState is ExportState.QualitySuccess) {
        val report = (exportState as ExportState.QualitySuccess).report
        ModalBottomSheet(onDismissRequest = { viewModel.clearExportState() }) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Document Quality", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { report.score / 100f },
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 8.dp,
                        color = when {
                            report.score >= 85 -> MaterialTheme.colorScheme.primary
                            report.score >= 65 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text("${report.score}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(report.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (report.issues.isNotEmpty()) {
                    Text("Issues", fontWeight = FontWeight.Bold)
                    report.issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (report.tips.isNotEmpty()) {
                    Text("Tips", fontWeight = FontWeight.Bold)
                    report.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    exportedPdf?.let { pdfFile ->
        AlertDialog(
            onDismissRequest = { exportedPdf = null },
            title = { Text("PDF ready") },
            text = { Text("${pdfFile.name} was exported successfully and can be opened or shared.") },
            confirmButton = {
                Button(onClick = { FileUtils.openFile(context, pdfFile, "application/pdf") }) { Text("Open PDF") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { FileUtils.shareFile(context, pdfFile, "application/pdf") }) { Text("Share") }
                    TextButton(onClick = { exportedPdf = null }) { Text("Close") }
                }
            }
        )
    }

    exportedDocx?.let { docxFile ->
        AlertDialog(
            onDismissRequest = { exportedDocx = null },
            title = { Text("Office export ready") },
            text = { Text("${docxFile.name} was exported from OCR text and can be opened or shared.") },
            confirmButton = {
                Button(onClick = { FileUtils.openFile(context, docxFile, FileUtils.mimeTypeFor(docxFile)) }) { Text("Open") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { FileUtils.shareFile(context, docxFile, FileUtils.mimeTypeFor(docxFile)) }) { Text("Share") }
                    TextButton(onClick = { exportedDocx = null }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun LoadingDocumentState() {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading document...")
    }
}

@Composable
private fun DocumentPreview(pages: List<Page>) {
    val firstPath = pages.firstOrNull()?.imagePath
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(null, firstPath) {
        value = withContext(Dispatchers.IO) {
            firstPath?.let { FileUtils.decodeSampledBitmap(it, 1400, 1400) }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        when {
            bitmap != null -> bitmap?.let { safeBitmap ->
                Image(
                    bitmap = safeBitmap.asImageBitmap(),
                    contentDescription = "Document Page",
                    modifier = Modifier.fillMaxWidth().height(420.dp).padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
            firstPath != null -> Box(
                modifier = Modifier.fillMaxWidth().height(420.dp).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            else -> Column(modifier = Modifier.fillMaxWidth().height(220.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.ImageNotSupported, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No preview available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    dwp: DocumentWithPages,
    onShareFirstImage: () -> Unit,
    onExport: () -> Unit,
    onExportDocx: () -> Unit,
    onSignature: () -> Unit,
    onMeta: () -> Unit,
    onMoveVault: () -> Unit
) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "quick_pages") { AssistChip(onClick = {}, label = { Text("${dwp.pages.size} page${if (dwp.pages.size == 1) "" else "s"}") }) }
        item(key = "quick_share") { AssistChip(onClick = onShareFirstImage, leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp)) }, label = { Text("Share image") }) }
        item(key = "quick_export_pdf") { AssistChip(onClick = onExport, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp)) }, label = { Text("Export PDF") }) }
        item(key = "quick_export_docx") { AssistChip(onClick = onExportDocx, leadingIcon = { Icon(Icons.Default.TextSnippet, null, modifier = Modifier.size(16.dp)) }, label = { Text("Export DOCX") }) }
        item(key = "quick_signature") { AssistChip(onClick = onSignature, leadingIcon = { Icon(Icons.Default.Style, null, modifier = Modifier.size(16.dp)) }, label = { Text("Signature") }) }
        item(key = "quick_vault") { AssistChip(onClick = onMoveVault, leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp)) }, label = { Text("Move to Vault") }) }
        item(key = "quick_tags") { AssistChip(onClick = onMeta, leadingIcon = { Icon(Icons.Default.Tag, null, modifier = Modifier.size(16.dp)) }, label = { Text("Tags") }) }
    }
}

@Composable
private fun DocumentMetaChips(dwp: DocumentWithPages) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "meta_workspace") { FilterChip(selected = true, onClick = {}, label = { Text(dwp.document.workspace.ifBlank { "Inbox" }) }) }
        item(key = "meta_category") { FilterChip(selected = true, onClick = {}, label = { Text(dwp.document.category.ifBlank { "General" }) }) }
        if (dwp.document.isPinned) item(key = "meta_pinned") { FilterChip(selected = true, onClick = {}, label = { Text("Pinned") }) }
        val tagList = dwp.document.tags.split(',').map { it.trim() }.filter { it.isNotBlank() }
        tagList.forEach { tag ->
            item(key = "tag_$tag") { FilterChip(selected = false, onClick = {}, label = { Text(tag) }) }
        }
    }
}

@Composable
private fun DocumentInsightsCard(dwp: DocumentWithPages) {
    val insights = remember(dwp.document.id, dwp.document.ocrText, dwp.pages.size, dwp.document.tags) {
        DocumentAnalyticsEngine.analyze(dwp)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Document insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightPill("Pages", insights.pageCount.toString(), Modifier.weight(1f))
                InsightPill("Words", insights.wordCount.toString(), Modifier.weight(1f))
                InsightPill("Read", "${insights.estimatedReadingMinutes}m", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightPill("Size", "${insights.storageKb}KB", Modifier.weight(1f))
                InsightPill("Status", insights.qualityLabel, Modifier.weight(1f))
            }
            Text("Keywords: ${insights.keywordPreview}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InsightPill(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OcrCard(dwp: DocumentWithPages, clipboardManager: ClipboardManager, context: Context, onDocxReady: (File) -> Unit) {
    val text = dwp.document.ocrText
    val coroutineScope = rememberCoroutineScope()
    if (!text.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                val stats = remember(text) { OcrHelper.buildStats(text) }
                Text("Extracted Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${stats.qualityLabel} · ${stats.confidencePercent}% · ${stats.wordCount} words", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(stats.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "ocr_copy") {
                        TextButton(onClick = {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                    }
                    item(key = "ocr_share") { TextButton(onClick = { FileUtils.shareText(context, text) }) { Text("Share") } }
                    item(key = "ocr_save_txt") {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                val file = FileUtils.saveTextFile(context, text, "OCR_${dwp.document.id}_${System.currentTimeMillis()}")
                                if (file != null) FileUtils.shareFile(context, file, "text/plain") else Toast.makeText(context, "TXT export failed", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Save TXT") }
                    }
                    item(key = "ocr_save_docx") {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                val file = FileUtils.saveDocxText(context, text, "OCR_${dwp.document.id}_${System.currentTimeMillis()}")
                                if (file != null) onDocxReady(file) else Toast.makeText(context, "DOCX export failed", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Save DOCX") }
                    }
                    item(key = "ocr_save_xlsx") {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                val file = FileUtils.saveXlsxFromText(context, text, "OCR_${dwp.document.id}_${System.currentTimeMillis()}")
                                if (file != null) onDocxReady(file) else Toast.makeText(context, "XLSX export failed", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Save XLSX") }
                    }
                    item(key = "ocr_vault") {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                val file = EncryptedVaultUtils.saveEncryptedText(context, text, "OCR_${dwp.document.id}_${System.currentTimeMillis()}")
                                if (file != null) Toast.makeText(context, "Saved to encrypted vault", Toast.LENGTH_SHORT).show() else Toast.makeText(context, "Vault save failed", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Save text to vault") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnails(pages: List<Page>, onEdit: (Page) -> Unit) {
    if (pages.isNotEmpty()) {
        Text("Pages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyRow(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pages, key = { it.id }) { page ->
                val bitmap by androidx.compose.runtime.produceState<Bitmap?>(null, page.imagePath) {
                    value = withContext(Dispatchers.IO) {
                        val file = File(page.imagePath)
                        if (file.exists() && file.length() > 0L) FileUtils.decodeSampledBitmap(page.imagePath, 360, 360) else null
                    }
                }
                Card(onClick = { onEdit(page) }, shape = RoundedCornerShape(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bitmap != null) {
                            bitmap?.let { safeBitmap ->
                                Image(bitmap = safeBitmap.asImageBitmap(), contentDescription = "Thumbnail", modifier = Modifier.size(112.dp), contentScale = ContentScale.Crop)
                            }
                        } else {
                            Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ImageNotSupported, contentDescription = "Image missing")
                            }
                        }
                        Text("Page ${page.pageOrder + 1}", modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageManagementList(
    pages: List<Page>,
    onEdit: (Page) -> Unit,
    onMove: (Page, Int) -> Unit,
    onReorder: (List<Page>) -> Unit,
    onDuplicate: (Page) -> Unit,
    onDelete: (Page) -> Unit
) {
    val orderedPages = remember { mutableStateListOf<Page>() }
    var activePageId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var reorderDirty by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val itemStepPx = with(LocalDensity.current) { 72.dp.toPx() }

    LaunchedEffect(pages.map { it.id to it.pageOrder }) {
        if (activePageId == null) {
            orderedPages.clear()
            orderedPages.addAll(pages.sortedBy { it.pageOrder })
            reorderDirty = false
            dragOffsetY = 0f
        }
    }

    fun moveDragged(pageId: Long, direction: Int) {
        val currentIndex = orderedPages.indexOfFirst { it.id == pageId }
        if (currentIndex < 0) return
        val newIndex = (currentIndex + direction).coerceIn(0, orderedPages.lastIndex)
        if (currentIndex == newIndex) return
        val page = orderedPages.removeAt(currentIndex)
        orderedPages.add(newIndex, page)
        reorderDirty = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun finishDrag() {
        val changed = reorderDirty
        activePageId = null
        dragOffsetY = 0f
        reorderDirty = false
        if (changed) {
            onReorder(orderedPages.mapIndexed { index, page -> page.copy(pageOrder = index) })
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Page management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Drag the handle on the left to reorder. Arrow buttons remain as a reliable fallback.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        orderedPages.forEachIndexed { index, page ->
            key(page.id) {
                val isActive = activePageId == page.id
                val offsetY = if (isActive) dragOffsetY.roundToInt() else 0
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, offsetY) }
                        .zIndex(if (isActive) 1f else 0f)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                                .pointerInput(orderedPages.size, page.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            activePageId = page.id
                                            dragOffsetY = 0f
                                            reorderDirty = false
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragCancel = {
                                            activePageId = null
                                            dragOffsetY = 0f
                                            reorderDirty = false
                                        },
                                        onDragEnd = { finishDrag() }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        if (activePageId == page.id) {
                                            dragOffsetY += dragAmount.y
                                            while (dragOffsetY > itemStepPx * 0.55f) {
                                                val before = orderedPages.indexOfFirst { it.id == page.id }
                                                moveDragged(page.id, 1)
                                                val after = orderedPages.indexOfFirst { it.id == page.id }
                                                if (before == after) break
                                                dragOffsetY -= itemStepPx
                                            }
                                            while (dragOffsetY < -itemStepPx * 0.55f) {
                                                val before = orderedPages.indexOfFirst { it.id == page.id }
                                                moveDragged(page.id, -1)
                                                val after = orderedPages.indexOfFirst { it.id == page.id }
                                                if (before == after) break
                                                dragOffsetY += itemStepPx
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("☰", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Page ${index + 1}", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (File(page.imagePath).exists()) "Ready" else "Image missing",
                                color = if (File(page.imagePath).exists()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { onMove(page, -1) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { onMove(page, 1) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { onDuplicate(page) }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
                        IconButton(onClick = { onEdit(page) }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { onDelete(page) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
    }
}
