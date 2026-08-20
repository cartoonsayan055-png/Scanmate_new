package com.synthbyte.scanmate.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.ui.viewmodels.PageEditorViewModel
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.FilterType
import com.synthbyte.scanmate.utils.OcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(docId: Long, pageId: Long, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: PageEditorViewModel = hiltViewModel()
    val page by viewModel.page.collectAsState(initial = null)
    val workingBitmap by viewModel.workingBitmap.collectAsState()
    val pageEditorError by viewModel.errorState.collectAsState()
    val viewModelProcessing by viewModel.isProcessing.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var sourcePath by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterType.ORIGINAL) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showPerspectiveDialog by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("ScanMate") }
    var noteText by remember { mutableStateOf("Reviewed") }
    var isProcessing by remember { mutableStateOf(false) }
    val isEditorProcessing = isProcessing || viewModelProcessing
    var changeVersion by remember { mutableIntStateOf(0) }
    val undoStack = remember { mutableStateListOf<Bitmap>() }
    val redoStack = remember { mutableStateListOf<Bitmap>() }

    fun pushUndoSnapshot() {
        workingBitmap?.let { bmp ->
            val maxSide = 900
            val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
            val snapshot = if (scale >= 1f) {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }
            if (undoStack.size >= 5) {
                undoStack.removeAt(0).also { runCatching { it.recycle() } }
            }
            undoStack.add(snapshot)
            redoStack.forEach { runCatching { it.recycle() } }
            redoStack.clear()
        }
    }

    fun clearUndoRedoStacks() {
        undoStack.forEach { runCatching { it.recycle() } }
        undoStack.clear()
        redoStack.forEach { runCatching { it.recycle() } }
        redoStack.clear()
    }

    DisposableEffect(Unit) {
        onDispose { clearUndoRedoStacks() }
    }

    LaunchedEffect(pageEditorError) {
        pageEditorError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val replaceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                val currentPage = page
                val file = if (currentPage != null) viewModel.replacePageImage(currentPage.id, uri) else null
                if (file != null && currentPage != null) {
                    sourcePath = null
                    viewModel.pushBitmap(null)
                    clearUndoRedoStacks()
                    Toast.makeText(context, "Page replaced", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not replace this page", Toast.LENGTH_SHORT).show()
                }
                isProcessing = false
            }
        }
    }

    LaunchedEffect(page?.imagePath, changeVersion) {
        val path = page?.imagePath
        if (path != null && path != sourcePath) {
            sourcePath = path
            selectedFilter = FilterType.ORIGINAL
            clearUndoRedoStacks()
            viewModel.loadPageBitmap(path)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit page", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(
                        enabled = page?.imagePath != null && !isEditorProcessing,
                        onClick = {
                            val path = page?.imagePath ?: return@IconButton
                            scope.launch {
                                isProcessing = true
                                viewModel.loadPageBitmap(path)
                                selectedFilter = FilterType.ORIGINAL
                                clearUndoRedoStacks()
                                isProcessing = false
                            }
                        }
                    ) { Icon(Icons.Default.Restore, "Reset") }
                    IconButton(
                        enabled = workingBitmap != null && page != null && !isEditorProcessing,
                        onClick = {
                            val bitmap = workingBitmap ?: return@IconButton
                            val currentPage = page ?: return@IconButton
                            scope.launch {
                                isProcessing = true
                                val file = viewModel.saveEditedPage(currentPage.id, bitmap)
                                if (file != null) {
                                    Toast.makeText(context, "Edited page saved", Toast.LENGTH_SHORT).show()
                                    sourcePath = null
                                    changeVersion++
                                } else {
                                    Toast.makeText(context, "Could not save edited page", Toast.LENGTH_SHORT).show()
                                }
                                isProcessing = false
                            }
                        }
                    ) { Icon(Icons.Default.Save, "Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Make this page cleaner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Crop, rotate, enhance, OCR and save without overwriting the original until you confirm.", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (isEditorProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val bitmap = workingBitmap
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Edited page preview",
                        modifier = Modifier.fillMaxWidth().height(460.dp).padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(260.dp).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (page == null) CircularProgressIndicator() else Icon(Icons.Default.ImageNotSupported, null, modifier = Modifier.size(42.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        val path = page?.imagePath
                        Text(
                            when {
                                page == null -> "Loading page..."
                                path.isNullOrBlank() -> "This page has no image path"
                                !File(path).exists() -> "This page image is missing"
                                else -> "This page image could not be loaded"
                            }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val previous = if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.lastIndex) else null
                        if (previous != null) {
                            workingBitmap?.let { redoStack.add(it.copy(Bitmap.Config.ARGB_8888, false)) }
                            viewModel.pushBitmap(previous)
                            selectedFilter = FilterType.ORIGINAL
                        }
                    },
                    enabled = undoStack.isNotEmpty() && !isEditorProcessing,
                    modifier = Modifier.weight(1f)
                ) { Text("Undo") }
                OutlinedButton(
                    onClick = {
                        val next = if (redoStack.isNotEmpty()) redoStack.removeAt(redoStack.lastIndex) else null
                        if (next != null) {
                            workingBitmap?.let { undoStack.add(it.copy(Bitmap.Config.ARGB_8888, false)) }
                            viewModel.pushBitmap(next)
                            selectedFilter = FilterType.ORIGINAL
                        }
                    },
                    enabled = redoStack.isNotEmpty() && !isEditorProcessing,
                    modifier = Modifier.weight(1f)
                ) { Text("Redo") }
            }

            ToolSectionTitle("Layout and crop")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item(key = "save") {
                    AssistChip(
                        enabled = workingBitmap != null && page != null && !isEditorProcessing,
                        onClick = {
                            val bitmapToSave = workingBitmap ?: return@AssistChip
                            val currentPage = page ?: return@AssistChip
                            scope.launch {
                                isProcessing = true
                                val file = viewModel.saveEditedPage(currentPage.id, bitmapToSave)
                                Toast.makeText(context, if (file != null) "Saved" else "Save failed", Toast.LENGTH_SHORT).show()
                                if (file != null) {
                                    sourcePath = null
                                    changeVersion++
                                }
                                isProcessing = false
                            }
                        },
                        label = { Text("Save") },
                        leadingIcon = { Icon(Icons.Default.Save, null, Modifier.size(16.dp)) }
                    )
                }
                item(key = "reset") {
                    AssistChip(
                        enabled = page?.imagePath != null && !isEditorProcessing,
                        onClick = {
                            val path = page?.imagePath ?: return@AssistChip
                            scope.launch {
                                isProcessing = true
                                viewModel.loadPageBitmap(path)
                                selectedFilter = FilterType.ORIGINAL
                                clearUndoRedoStacks()
                                isProcessing = false
                            }
                        },
                        label = { Text("Reset") },
                        leadingIcon = { Icon(Icons.Default.Restore, null, Modifier.size(16.dp)) }
                    )
                }
                item(key = "crop") {
                    AssistChip(
                        enabled = workingBitmap != null && !isEditorProcessing,
                        onClick = { showCropDialog = true },
                        label = { Text("Crop") },
                        leadingIcon = { Icon(Icons.Default.Crop, null, Modifier.size(16.dp)) }
                    )
                }
                item(key = "corners") {
                    AssistChip(
                        enabled = workingBitmap != null && !isEditorProcessing,
                        onClick = { showPerspectiveDialog = true },
                        label = { Text("Corners") },
                        leadingIcon = { Icon(Icons.Default.Crop, null, Modifier.size(16.dp)) }
                    )
                }
                item(key = "rotate_left") {
                    AssistChip(
                        enabled = workingBitmap != null && !isEditorProcessing,
                        onClick = {
                            pushUndoSnapshot()
                            workingBitmap?.let { viewModel.pushBitmap(FileUtils.rotateBitmap(it, -90f)) }
                        },
                        label = { Text("Rotate left") },
                        leadingIcon = { Icon(Icons.Default.RotateLeft, null, Modifier.size(16.dp)) }
                    )
                }
                item(key = "rotate_right") {
                    AssistChip(
                        enabled = workingBitmap != null && !isEditorProcessing,
                        onClick = {
                            pushUndoSnapshot()
                            workingBitmap?.let { viewModel.pushBitmap(FileUtils.rotateBitmap(it, 90f)) }
                        },
                        label = { Text("Rotate right") },
                        leadingIcon = { Icon(Icons.Default.RotateRight, null, Modifier.size(16.dp)) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCropDialog = true }, enabled = workingBitmap != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Crop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Manual crop")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val current = workingBitmap ?: return@launch
                        pushUndoSnapshot()
                        isProcessing = true
                        viewModel.pushBitmap(withContext(Dispatchers.Default) { FileUtils.autoCropDocument(current) })
                        selectedFilter = FilterType.ORIGINAL
                        isProcessing = false
                    }
                }, enabled = workingBitmap != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FilterAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Auto crop")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPerspectiveDialog = true }, enabled = workingBitmap != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Crop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Adjust corners")
                }
                OutlinedButton(onClick = {
                    val current = workingBitmap ?: return@OutlinedButton
                    scope.launch {
                        pushUndoSnapshot()
                        isProcessing = true
                        viewModel.pushBitmap(withContext(Dispatchers.Default) { FileUtils.autoCropDocument(current) })
                        viewModel.applyFilter(FilterType.ENHANCED_COLOR.name)
                        selectedFilter = FilterType.ENHANCED_COLOR
                        isProcessing = false
                    }
                }, enabled = workingBitmap != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Text("Enhance")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showWatermarkDialog = true }, enabled = workingBitmap != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Text("Watermark")
                }
                OutlinedButton(onClick = { showNoteDialog = true }, enabled = workingBitmap != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Text("Text note")
                }
            }

            ToolSectionTitle("Filters and cleanup")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(FilterType.entries, key = { it.name }) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        enabled = workingBitmap != null && !isEditorProcessing,
                        onClick = {
                            workingBitmap ?: return@FilterChip
                            scope.launch {
                                pushUndoSnapshot()
                                isProcessing = true
                                viewModel.applyFilter(filter.name)
                                selectedFilter = filter
                                isProcessing = false
                            }
                        },
                        label = { Text(filter.label) }
                    )
                }
                item(key = "erase_marks_chip") {
                    FilterChip(
                        selected = false,
                        enabled = workingBitmap != null && !isEditorProcessing,
                        onClick = {
                            pushUndoSnapshot()
                            viewModel.removeMarks()
                        },
                        label = { Text("Clean marks") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) }
                    )
                }
                item(key = "remove_shadow_chip") {
                    FilterChip(
                        selected = false,
                        enabled = workingBitmap != null && !isEditorProcessing,
                        onClick = {
                            pushUndoSnapshot()
                            viewModel.removeShadow()
                        },
                        label = { Text("Reduce shadow") },
                        leadingIcon = { Icon(Icons.Default.WbSunny, null, Modifier.size(16.dp)) }
                    )
                }
                item(key = "deskew_chip") {
                    FilterChip(
                        selected = false,
                        enabled = workingBitmap != null && !isEditorProcessing,
                        onClick = {
                            pushUndoSnapshot()
                            viewModel.deskew()
                        },
                        label = { Text("Deskew") },
                        leadingIcon = { Icon(Icons.Default.RotateRight, null, Modifier.size(16.dp)) }
                    )
                }
                item(key = "editor_tools_processing") {
                    if (isEditorProcessing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            ToolSectionTitle("Page actions")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    replaceLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, enabled = !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.SwapHoriz, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Replace")
                }
                OutlinedButton(onClick = {
                    val currentPage = page ?: return@OutlinedButton
                    scope.launch {
                        viewModel.duplicatePage(currentPage)
                        Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show()
                    }
                }, enabled = page != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Duplicate")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val currentPage = page ?: return@OutlinedButton
                    scope.launch {
                        viewModel.movePage(currentPage, -1)
                        Toast.makeText(context, "Page moved", Toast.LENGTH_SHORT).show()
                    }
                }, enabled = page != null && !isEditorProcessing, modifier = Modifier.weight(1f)) { Text("Move up") }
                OutlinedButton(onClick = {
                    val currentPage = page ?: return@OutlinedButton
                    scope.launch {
                        viewModel.movePage(currentPage, 1)
                        Toast.makeText(context, "Page moved", Toast.LENGTH_SHORT).show()
                    }
                }, enabled = page != null && !isEditorProcessing, modifier = Modifier.weight(1f)) { Text("Move down") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val currentPage = page ?: return@OutlinedButton
                    scope.launch {
                        val imageFile = File(currentPage.imagePath)
                        if (!imageFile.exists() || imageFile.length() == 0L) {
                            Toast.makeText(context, "Page image is missing", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        isProcessing = true
                        val text = withContext(Dispatchers.IO) { OcrHelper.extractTextFromFile(context, imageFile) }
                        isProcessing = false
                        if (text.isBlank() || text.startsWith("OCR failed", ignoreCase = true)) {
                            Toast.makeText(context, "No readable text found on this page", Toast.LENGTH_SHORT).show()
                        } else {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Page OCR", text))
                            viewModel.savePageOcr(currentPage, text)
                            Toast.makeText(context, "Page OCR copied and saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, enabled = page != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.TextSnippet, null)
                    Spacer(Modifier.width(6.dp))
                    Text("OCR page")
                }
                OutlinedButton(onClick = { showDeleteDialog = true }, enabled = page != null && !isEditorProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
            AssistChip(onClick = {}, label = { Text("Save confirms edits; Reset reloads the original saved page image.") })
        }
    }

    if (showCropDialog) {
        ManualCropDialog(
            bitmap = workingBitmap,
            onDismiss = { showCropDialog = false },
            onApply = { left, top, right, bottom ->
                pushUndoSnapshot()
                workingBitmap?.let { viewModel.pushBitmap(FileUtils.cropBitmapNormalized(it, left, top, right, bottom)) }
                selectedFilter = FilterType.ORIGINAL
                showCropDialog = false
            }
        )
    }

    if (showWatermarkDialog) {
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text("Add watermark") },
            text = {
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    pushUndoSnapshot()
                    workingBitmap?.let { viewModel.pushBitmap(FileUtils.drawWatermarkOnBitmap(it, watermarkText)) }
                    showWatermarkDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showWatermarkDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Add text note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                Button(onClick = {
                    pushUndoSnapshot()
                    workingBitmap?.let { viewModel.pushBitmap(FileUtils.drawNoteStampOnBitmap(it, noteText)) }
                    showNoteDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPerspectiveDialog) {
        PerspectiveDialog(
            bitmap = workingBitmap,
            onDismiss = { showPerspectiveDialog = false },
            onApply = { corners ->
                pushUndoSnapshot()
                workingBitmap?.let { bitmap ->
                    viewModel.pushBitmap(FileUtils.perspectiveCorrectBitmapFromCorners(bitmap, corners))
                }
                selectedFilter = FilterType.ORIGINAL
                showPerspectiveDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete page?") },
            text = { Text("This removes this page from the document. Other pages will be re-numbered safely.") },
            confirmButton = {
                Button(onClick = {
                    val currentPage = page ?: return@Button
                    scope.launch {
                        viewModel.deleteCurrentPage(currentPage.id)
                        Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ToolSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ManualCropDialog(
    bitmap: Bitmap?,
    onDismiss: () -> Unit,
    onApply: (Float, Float, Float, Float) -> Unit
) {
    var crop by remember(bitmap?.width, bitmap?.height) { mutableStateOf(CropRectPercent.safeDefault()) }
    var selectedPreset by remember { mutableStateOf("balanced") }
    val cropIsValid = remember(crop) { crop.isValid() }
    val presets = listOf("original" to "Original", "fit" to "Fit", "a4" to "A4", "letter" to "Letter")

    fun applyPreset(id: String) {
        selectedPreset = id
        crop = when (id) {
            "original" -> CropRectPercent(0f, 0f, 1f, 1f)
            "fit" -> CropRectPercent(0.02f, 0.02f, 0.98f, 0.98f)
            "a4" -> CropRectPercent.centerAspect(bitmap, if (bitmap != null && bitmap.width >= bitmap.height) 1.4142f else 1f / 1.4142f)
            "letter" -> CropRectPercent.centerAspect(bitmap, if (bitmap != null && bitmap.width >= bitmap.height) 11f / 8.5f else 8.5f / 11f)
            else -> CropRectPercent.safeDefault()
        }.coerced()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(12.dp)
                .widthIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Crop page", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "Drag the large corner handles or use sliders. Presets are safe and keep the original image untouched until Save.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    item {
                        DraggableCropPreviewBox(
                            bitmap = bitmap,
                            crop = crop,
                            onCropChange = {
                                crop = it.coerced()
                                selectedPreset = "custom"
                            },
                            modifier = Modifier.fillMaxWidth().height(360.dp)
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            items(presets, key = { it.first }) { preset ->
                                FilterChip(
                                    selected = selectedPreset == preset.first,
                                    onClick = { applyPreset(preset.first) },
                                    label = { Text(preset.second, softWrap = false, maxLines = 1) }
                                )
                            }
                            item(key = "balanced") {
                                FilterChip(
                                    selected = selectedPreset == "balanced",
                                    onClick = { selectedPreset = "balanced"; crop = CropRectPercent.safeDefault() },
                                    label = { Text("Reset", softWrap = false, maxLines = 1) }
                                )
                            }
                        }
                    }
                    item { CropEdgeSlider("Left edge", crop.left, 0f..(crop.right - CropRectPercent.MinSize).coerceAtLeast(0f)) { crop = crop.copy(left = it).coerced(); selectedPreset = "custom" } }
                    item { CropEdgeSlider("Top edge", crop.top, 0f..(crop.bottom - CropRectPercent.MinSize).coerceAtLeast(0f)) { crop = crop.copy(top = it).coerced(); selectedPreset = "custom" } }
                    item { CropEdgeSlider("Right edge", 1f - crop.right, 0f..(1f - crop.left - CropRectPercent.MinSize).coerceAtLeast(0f)) { crop = crop.copy(right = 1f - it).coerced(); selectedPreset = "custom" } }
                    item { CropEdgeSlider("Bottom edge", 1f - crop.bottom, 0f..(1f - crop.top - CropRectPercent.MinSize).coerceAtLeast(0f)) { crop = crop.copy(bottom = 1f - it).coerced(); selectedPreset = "custom" } }
                    if (!cropIsValid) {
                        item {
                            Text(
                                "Crop is too tight. Keep at least 18% of page width and height visible.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { applyPreset("original") }) { Text("Original") }
                        TextButton(onClick = { selectedPreset = "balanced"; crop = CropRectPercent.safeDefault() }) { Text("Reset") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            enabled = cropIsValid && bitmap != null,
                            onClick = { onApply(crop.left, crop.top, 1f - crop.right, 1f - crop.bottom) }
                        ) { Text("Apply crop") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableCropPreviewBox(
    bitmap: Bitmap?,
    crop: CropRectPercent,
    onCropChange: (CropRectPercent) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        val boxWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val bitmapAspect = bitmap?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: 0.72f
        val boxAspect = boxWidth / boxHeight
        val imageWidth = if (bitmapAspect > boxAspect) boxWidth else boxHeight * bitmapAspect
        val imageHeight = if (bitmapAspect > boxAspect) boxWidth / bitmapAspect else boxHeight
        val imageLeft = (boxWidth - imageWidth) / 2f
        val imageTop = (boxHeight - imageHeight) / 2f

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Crop preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        val guideColor = MaterialTheme.colorScheme.primary
        val maskColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
        val handleFill = MaterialTheme.colorScheme.primary
        val handleStroke = MaterialTheme.colorScheme.surface
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cropLeft = imageLeft + imageWidth * crop.left
            val cropTop = imageTop + imageHeight * crop.top
            val cropRight = imageLeft + imageWidth * crop.right
            val cropBottom = imageTop + imageHeight * crop.bottom
            drawRect(maskColor, topLeft = Offset(imageLeft, imageTop), size = Size(imageWidth, (cropTop - imageTop).coerceAtLeast(0f)))
            drawRect(maskColor, topLeft = Offset(imageLeft, cropBottom), size = Size(imageWidth, (imageTop + imageHeight - cropBottom).coerceAtLeast(0f)))
            drawRect(maskColor, topLeft = Offset(imageLeft, cropTop), size = Size((cropLeft - imageLeft).coerceAtLeast(0f), (cropBottom - cropTop).coerceAtLeast(0f)))
            drawRect(maskColor, topLeft = Offset(cropRight, cropTop), size = Size((imageLeft + imageWidth - cropRight).coerceAtLeast(0f), (cropBottom - cropTop).coerceAtLeast(0f)))
            drawRect(guideColor.copy(alpha = 0.13f), topLeft = Offset(cropLeft, cropTop), size = Size((cropRight - cropLeft).coerceAtLeast(1f), (cropBottom - cropTop).coerceAtLeast(1f)))
            drawLine(guideColor, Offset(cropLeft, cropTop), Offset(cropRight, cropTop), strokeWidth = 5f)
            drawLine(guideColor, Offset(cropRight, cropTop), Offset(cropRight, cropBottom), strokeWidth = 5f)
            drawLine(guideColor, Offset(cropRight, cropBottom), Offset(cropLeft, cropBottom), strokeWidth = 5f)
            drawLine(guideColor, Offset(cropLeft, cropBottom), Offset(cropLeft, cropTop), strokeWidth = 5f)
            listOf(
                Offset(cropLeft, cropTop),
                Offset(cropRight, cropTop),
                Offset(cropRight, cropBottom),
                Offset(cropLeft, cropBottom)
            ).forEach { point ->
                drawCircle(handleStroke, radius = 22f, center = point)
                drawCircle(handleFill, radius = 16f, center = point)
                drawCircle(handleStroke, radius = 16f, center = point, style = Stroke(width = 3f))
            }
        }
        CropHandle.entries.forEach { handle ->
            val point = crop.handlePoint(handle)
            DragHandleBox(
                position = point,
                imageLeft = imageLeft,
                imageTop = imageTop,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                contentDescription = handle.label,
                onDelta = { delta -> onCropChange(crop.drag(handle, delta)) }
            )
        }
    }
}

@Composable
private fun DragHandleBox(
    position: Offset,
    imageLeft: Float,
    imageTop: Float,
    imageWidth: Float,
    imageHeight: Float,
    contentDescription: String,
    onDelta: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (imageLeft + position.x * imageWidth).roundToInt() - 36,
                    (imageTop + position.y * imageHeight).roundToInt() - 36
                )
            }
            .size(72.dp)
            .pointerInput(imageWidth, imageHeight, contentDescription) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDelta(
                        Offset(
                            dragAmount.x / imageWidth.coerceAtLeast(1f),
                            dragAmount.y / imageHeight.coerceAtLeast(1f)
                        )
                    )
                }
            }
    )
}

@Composable
private fun CropEdgeSlider(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val safeEnd = valueRange.endInclusive.coerceAtLeast(valueRange.start + 0.001f)
    val safeRange = valueRange.start..safeEnd
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("${(value * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(safeRange.start, safeRange.endInclusive), onValueChange = onChange, valueRange = safeRange)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Keep more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Trim more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class CropHandle(val label: String) {
    TopLeft("Top left"),
    TopRight("Top right"),
    BottomRight("Bottom right"),
    BottomLeft("Bottom left")
}

private data class CropRectPercent(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun isValid(): Boolean =
        left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f &&
            right - left >= MinSize && bottom - top >= MinSize

    fun coerced(): CropRectPercent {
        val safeLeft = left.coerceIn(0f, 1f - MinSize)
        val safeTop = top.coerceIn(0f, 1f - MinSize)
        val safeRight = right.coerceIn(safeLeft + MinSize, 1f)
        val safeBottom = bottom.coerceIn(safeTop + MinSize, 1f)
        return CropRectPercent(safeLeft, safeTop, safeRight, safeBottom)
    }

    fun handlePoint(handle: CropHandle): Offset = when (handle) {
        CropHandle.TopLeft -> Offset(left, top)
        CropHandle.TopRight -> Offset(right, top)
        CropHandle.BottomRight -> Offset(right, bottom)
        CropHandle.BottomLeft -> Offset(left, bottom)
    }

    fun drag(handle: CropHandle, delta: Offset): CropRectPercent = when (handle) {
        CropHandle.TopLeft -> copy(left = left + delta.x, top = top + delta.y)
        CropHandle.TopRight -> copy(right = right + delta.x, top = top + delta.y)
        CropHandle.BottomRight -> copy(right = right + delta.x, bottom = bottom + delta.y)
        CropHandle.BottomLeft -> copy(left = left + delta.x, bottom = bottom + delta.y)
    }.coerced()

    companion object {
        const val MinSize = 0.18f

        fun safeDefault(): CropRectPercent = CropRectPercent(0.04f, 0.04f, 0.96f, 0.96f)

        fun centerAspect(bitmap: Bitmap?, targetAspect: Float): CropRectPercent {
            val aspect = targetAspect.coerceIn(0.25f, 4f)
            val imageAspect = bitmap?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: aspect
            val width: Float
            val height: Float
            if (imageAspect > aspect) {
                height = 0.96f
                width = (height * aspect / imageAspect).coerceIn(MinSize, 0.96f)
            } else {
                width = 0.96f
                height = (width * imageAspect / aspect).coerceIn(MinSize, 0.96f)
            }
            val left = (1f - width) / 2f
            val top = (1f - height) / 2f
            return CropRectPercent(left, top, left + width, top + height).coerced()
        }
    }
}

@Composable
private fun PerspectiveDialog(
    bitmap: Bitmap?,
    onDismiss: () -> Unit,
    onApply: (List<Offset>) -> Unit
) {
    val defaultCorners = listOf(
        Offset(0.06f, 0.06f),
        Offset(0.94f, 0.06f),
        Offset(0.94f, 0.94f),
        Offset(0.06f, 0.94f)
    )
    val fitCorners = listOf(
        Offset(0.02f, 0.02f),
        Offset(0.98f, 0.02f),
        Offset(0.98f, 0.98f),
        Offset(0.02f, 0.98f)
    )
    var corners by remember(bitmap?.width, bitmap?.height) { mutableStateOf(defaultCorners) }
    var activeCorner by remember { mutableStateOf<Int?>(null) }
    val isValid = remember(corners) { cornersAreValid(corners) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(10.dp)
                .widthIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Drag page corners", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Place each large handle on the real document corner. This is manual and will not be overwritten by auto crop.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 320.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                        .padding(10.dp)
                ) {
                    val boxWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val boxHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val bitmapAspect = bitmap?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: 0.72f
                    val boxAspect = boxWidth / boxHeight
                    val imageWidth = if (bitmapAspect > boxAspect) boxWidth else boxHeight * bitmapAspect
                    val imageHeight = if (bitmapAspect > boxAspect) boxWidth / bitmapAspect else boxHeight
                    val imageLeft = (boxWidth - imageWidth) / 2f
                    val imageTop = (boxHeight - imageHeight) / 2f

                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Perspective preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    val handleColor = MaterialTheme.colorScheme.primary
                    val handleBorder = MaterialTheme.colorScheme.surface
                    val guideColor = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val points = corners.map { point ->
                            Offset(imageLeft + point.x * imageWidth, imageTop + point.y * imageHeight)
                        }
                        drawLine(guideColor, points[0], points[1], strokeWidth = 5f)
                        drawLine(guideColor, points[1], points[2], strokeWidth = 5f)
                        drawLine(guideColor, points[2], points[3], strokeWidth = 5f)
                        drawLine(guideColor, points[3], points[0], strokeWidth = 5f)
                        points.forEachIndexed { index, point ->
                            val radius = if (activeCorner == index) 30f else 23f
                            drawCircle(handleBorder, radius = radius + 7f, center = point)
                            drawCircle(handleColor, radius = radius, center = point)
                            drawCircle(handleBorder, radius = radius, center = point, style = Stroke(width = 4f))
                        }
                    }
                    corners.forEachIndexed { index, point ->
                        PerspectiveCornerHandle(
                            position = point,
                            imageLeft = imageLeft,
                            imageTop = imageTop,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            onDragStateChange = { dragging -> activeCorner = if (dragging) index else null },
                            onDelta = { delta -> corners = corners.updateCornerByDelta(index, delta) }
                        )
                    }
                }
                Text(
                    text = if (isValid) "Tip: drag handles directly. Apply uses these exact selected corners." else "Move the handles apart so the page outline does not cross or become too small.",
                    color = if (isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { corners = defaultCorners }) { Text("Reset") }
                        TextButton(onClick = { corners = fitCorners }) { Text("Fit page") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            enabled = isValid && bitmap != null,
                            onClick = { onApply(corners) }
                        ) { Text("Apply") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerspectiveCornerHandle(
    position: Offset,
    imageLeft: Float,
    imageTop: Float,
    imageWidth: Float,
    imageHeight: Float,
    onDragStateChange: (Boolean) -> Unit = {},
    onDelta: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (imageLeft + position.x * imageWidth).roundToInt() - 38,
                    (imageTop + position.y * imageHeight).roundToInt() - 38
                )
            }
            .size(76.dp)
            .pointerInput(imageWidth, imageHeight) {
                detectDragGestures(
                    onDragStart = { onDragStateChange(true) },
                    onDragCancel = { onDragStateChange(false) },
                    onDragEnd = { onDragStateChange(false) }
                ) { change, dragAmount ->
                    change.consume()
                    onDelta(
                        Offset(
                            dragAmount.x / imageWidth.coerceAtLeast(1f),
                            dragAmount.y / imageHeight.coerceAtLeast(1f)
                        )
                    )
                }
            }
    )
}

private fun List<Offset>.updateCornerByDelta(index: Int, delta: Offset): List<Offset> {
    if (size != 4) return this
    return updateCorner(index, this[index] + delta)
}

private fun List<Offset>.updateCorner(index: Int, next: Offset): List<Offset> {
    if (size != 4) return this
    val minGap = 0.06f
    fun Float.inRange(min: Float, max: Float): Float {
        val low = min.coerceIn(0.02f, 0.98f)
        val high = max.coerceIn(0.02f, 0.98f)
        return if (low <= high) coerceIn(low, high) else ((low + high) / 2f).coerceIn(0.02f, 0.98f)
    }
    val tl = this[0]
    val tr = this[1]
    val br = this[2]
    val bl = this[3]
    val clamped = when (index) {
        0 -> Offset(next.x.inRange(0.02f, minOf(tr.x, br.x) - minGap), next.y.inRange(0.02f, minOf(bl.y, br.y) - minGap))
        1 -> Offset(next.x.inRange(maxOf(tl.x, bl.x) + minGap, 0.98f), next.y.inRange(0.02f, minOf(bl.y, br.y) - minGap))
        2 -> Offset(next.x.inRange(maxOf(tl.x, bl.x) + minGap, 0.98f), next.y.inRange(maxOf(tl.y, tr.y) + minGap, 0.98f))
        3 -> Offset(next.x.inRange(0.02f, minOf(tr.x, br.x) - minGap), next.y.inRange(maxOf(tl.y, tr.y) + minGap, 0.98f))
        else -> next
    }
    return toMutableList().also { it[index] = clamped }
}

private fun cornersAreValid(corners: List<Offset>): Boolean {
    if (corners.size != 4) return false
    if (corners.any { it.x !in 0f..1f || it.y !in 0f..1f }) return false
    if (segmentsIntersect(corners[0], corners[1], corners[2], corners[3])) return false
    if (segmentsIntersect(corners[1], corners[2], corners[3], corners[0])) return false
    val area = abs(
        corners.indices.sumOf { i ->
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            (a.x * b.y - b.x * a.y).toDouble()
        }.toFloat()
    ) / 2f
    val topWidth = distance(corners[0], corners[1])
    val bottomWidth = distance(corners[3], corners[2])
    val leftHeight = distance(corners[0], corners[3])
    val rightHeight = distance(corners[1], corners[2])
    val aspect = max(topWidth, bottomWidth) / max(max(leftHeight, rightHeight), 0.001f)
    return area >= 0.10f &&
        aspect in 0.20f..5.0f &&
        topWidth >= 0.12f && bottomWidth >= 0.12f && leftHeight >= 0.12f && rightHeight >= 0.12f &&
        corners[0].x < corners[1].x &&
        corners[3].x < corners[2].x &&
        corners[0].y < corners[3].y &&
        corners[1].y < corners[2].y
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun segmentsIntersect(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
    fun orientation(p: Offset, q: Offset, r: Offset): Float {
        return (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
    }
    val o1 = orientation(a, b, c)
    val o2 = orientation(a, b, d)
    val o3 = orientation(c, d, a)
    val o4 = orientation(c, d, b)
    return o1 * o2 < 0f && o3 * o4 < 0f
}
