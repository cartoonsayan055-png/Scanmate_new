package com.synthbyte.scanmate.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.data.Page
import com.synthbyte.scanmate.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.synthbyte.scanmate.ui.viewmodels.DocumentDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(docId: Long, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: DocumentDetailViewModel = hiltViewModel()
    val documentWithPages by viewModel.documentWithPages.collectAsState(initial = null)
    val pages = documentWithPages?.pages.orEmpty()
    val orderedPages = remember(docId, pages) { pages.sortedBy { it.pageOrder } }
    val scope = rememberCoroutineScope()

    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize(1, 1)) }
    var selectedPageId by remember { mutableStateOf<Long?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val signatureColor = MaterialTheme.colorScheme.onBackground.toArgb()

    LaunchedEffect(orderedPages) {
        if (selectedPageId == null) selectedPageId = orderedPages.firstOrNull()?.id
    }

    LaunchedEffect(selectedPageId, version, orderedPages) {
        val page = orderedPages.firstOrNull { it.id == selectedPageId }
        selectedBitmap = withContext(Dispatchers.IO) { page?.imagePath?.let { FileUtils.decodeSampledBitmap(it, 1800, 1800) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("E-Signature") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { strokes.clear(); activeStroke = emptyList() }) { Icon(Icons.Default.Clear, "Clear") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Draw, null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text("Draw signature offline", fontWeight = FontWeight.Bold)
                        Text("Save as PNG or apply to a scanned page before PDF export.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("Signature pad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .onSizeChanged { padSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> activeStroke = listOf(offset) },
                            onDrag = { change, _ -> activeStroke = activeStroke + change.position },
                            onDragEnd = {
                                if (activeStroke.size > 1) strokes.add(activeStroke)
                                activeStroke = emptyList()
                            },
                            onDragCancel = { activeStroke = emptyList() }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val color = Color.Black
                    (strokes + listOf(activeStroke)).forEach { stroke ->
                        stroke.zipWithNext().forEach { (from, to) ->
                            drawLine(color = color, start = from, end = to, strokeWidth = 5f, cap = StrokeCap.Round)
                        }
                    }
                }
                if (strokes.isEmpty() && activeStroke.isEmpty()) {
                    Text("Sign here", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { strokes.clear(); activeStroke = emptyList() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Clear, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
                Button(onClick = {
                    if (strokes.isEmpty()) {
                        Toast.makeText(context, "Draw a signature first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        isProcessing = true
                        val bitmap = renderSignatureBitmap(strokes.toList(), padSize, signatureColor)
                        val file = FileUtils.saveBitmapToFolder(context, bitmap, "Signatures", "Signature_${System.currentTimeMillis()}", Bitmap.CompressFormat.PNG, 100)
                        isProcessing = false
                        Toast.makeText(context, if (file != null) "Signature PNG saved" else "Could not save signature", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save PNG")
                }
            }

            Text("Apply to page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (orderedPages.isEmpty()) {
                AssistChip(onClick = {}, label = { Text("No pages found in this document") })
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(orderedPages, key = { it.id }) { page ->
                        FilterChip(
                            selected = selectedPageId == page.id,
                            onClick = { selectedPageId = page.id },
                            label = { Text("Page ${page.pageOrder + 1}") }
                        )
                    }
                }
                val preview = selectedBitmap
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Selected page",
                            modifier = Modifier.fillMaxWidth().height(340.dp).padding(12.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().height(180.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading page preview...")
                        }
                    }
                }
                Button(
                    onClick = {
                        if (strokes.isEmpty()) {
                            Toast.makeText(context, "Draw a signature first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val targetPage = orderedPages.firstOrNull { it.id == selectedPageId }
                        if (targetPage == null) {
                            Toast.makeText(context, "Select a page first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            isProcessing = true
                            val saved = withContext(Dispatchers.IO) {
                                val pageBitmap = FileUtils.decodeSampledBitmap(targetPage.imagePath, 2600, 2600) ?: return@withContext null
                                val sigBitmap = renderSignatureBitmap(strokes.toList(), padSize, AndroidColor.BLACK)
                                val stamped = FileUtils.drawSignatureOnBitmap(pageBitmap, sigBitmap)
                                FileUtils.saveEditedBitmap(context, stamped, "SIGNED_PAGE_${targetPage.id}")
                            }
                            if (saved != null) {
                                viewModel.updatePageImage(targetPage.id, saved.absolutePath)
                                Toast.makeText(context, "Signature applied to page", Toast.LENGTH_SHORT).show()
                                version++
                            } else {
                                Toast.makeText(context, "Could not apply signature", Toast.LENGTH_SHORT).show()
                            }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Apply signature to selected page") }
            }
        }
    }
}

private fun renderSignatureBitmap(strokes: List<List<Offset>>, size: IntSize, color: Int): Bitmap {
    val width = size.width.coerceAtLeast(600)
    val height = size.height.coerceAtLeast(220)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.TRANSPARENT)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = 7f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        style = AndroidPaint.Style.STROKE
    }
    strokes.forEach { stroke ->
        stroke.zipWithNext().forEach { (from, to) ->
            canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        }
    }
    return bitmap
}
