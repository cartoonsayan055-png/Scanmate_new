package com.synthbyte.scanmate.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synthbyte.scanmate.data.QrHistory
import com.synthbyte.scanmate.ui.viewmodels.DocumentViewModel
import com.synthbyte.scanmate.utils.BarcodeScannerHelper
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.QRUtils
import com.synthbyte.scanmate.utils.QrPayloadParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class QrPalette(
    val label: String,
    val foreground: Int,
    val background: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(onNavigateBack: () -> Unit, onOpenCameraScanner: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var selectedPaletteIndex by remember { mutableIntStateOf(0) }
    var addCenterBadge by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val documentViewModel: DocumentViewModel = hiltViewModel()
    val qrHistory by documentViewModel.qrHistory.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryArgb = MaterialTheme.colorScheme.onPrimary.toArgb()
    val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
    val qrPalettes = remember(primaryArgb, onPrimaryArgb, onSurfaceArgb, surfaceArgb) {
        listOf(
            QrPalette("Classic", onSurfaceArgb, surfaceArgb),
            QrPalette("Brand", primaryArgb, onPrimaryArgb),
            QrPalette("Graphite", onSurfaceArgb, surfaceArgb)
        )
    }
    val selectedPalette = qrPalettes[selectedPaletteIndex.coerceIn(qrPalettes.indices)]

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isProcessing = true
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream) }
                    }
                    if (bitmap == null) {
                        Toast.makeText(context, "Could not read image", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val result = BarcodeScannerHelper.scanBarcode(bitmap)
                    if (result != null) {
                        scanResult = result
                        documentViewModel.insertQrHistory(result, "SCANNED")
                        Toast.makeText(context, "QR/barcode scanned", Toast.LENGTH_LONG).show()
                    } else {
                        scanResult = "No QR or barcode found."
                        Toast.makeText(context, "No QR/barcode found", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Scan failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR & Barcode Tools") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { documentViewModel.clearQrHistory() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear history")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "qr_create") {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Create QR Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(1500) },
                            label = { Text("Enter text, URL, phone, email, or note") },
                            supportingText = { Text("${text.length}/1500 characters") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(16.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            qrPalettes.forEachIndexed { index, palette ->
                                FilterChip(
                                    selected = selectedPaletteIndex == index,
                                    onClick = { selectedPaletteIndex = index },
                                    label = { Text(palette.label) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Safe center badge", fontWeight = FontWeight.SemiBold)
                                Text("Small center badge; keeps QR readable.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = addCenterBadge, onCheckedChange = { addCenterBadge = it })
                        }
                        Button(onClick = {
                            val value = text.trim()
                            if (value.isBlank()) {
                                Toast.makeText(context, "Enter QR content first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProcessing = true
                            val bitmap = QRUtils.generateQRCode(
                                text = value,
                                foregroundColor = selectedPalette.foreground,
                                backgroundColor = selectedPalette.background,
                                addCenterBadge = addCenterBadge
                            )
                            qrBitmap = bitmap
                            isProcessing = false
                            if (bitmap != null) {
                                documentViewModel.insertQrHistory(value, "GENERATED")
                            } else {
                                Toast.makeText(context, "QR generation failed", Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.QrCode2, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isProcessing) "Processing..." else "Generate QR Code")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onOpenCameraScanner, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CameraAlt, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Camera Scan")
                            }
                            OutlinedButton(
                                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery")
                            }
                        }
                    }
                }
            }

            qrBitmap?.let { bitmap ->
                item(key = "qr_preview") {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)))
                                    .padding(18.dp)
                            ) {
                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(248.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Premium QR preview", fontWeight = FontWeight.Bold)
                            Text("Style is intentionally restrained so scanners can still read it.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    coroutineScope.launch {
                                        val file = FileUtils.saveBitmapAsPng(context, bitmap, "QR_${System.currentTimeMillis()}")
                                        Toast.makeText(context, if (file != null) "Saved: ${file.name}" else "Failed to save", Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text("Save PNG") }
                                OutlinedButton(onClick = {
                                    coroutineScope.launch {
                                        val file = FileUtils.saveBitmapAsPng(context, bitmap, "QR_Share_${System.currentTimeMillis()}")
                                        if (file != null) FileUtils.shareFile(context, file, "image/png")
                                    }
                                }) { Text("Share") }
                            }
                        }
                    }
                }
            }

            scanResult?.let { result ->
                item(key = "qr_scan_result") {
                    ResultCard(title = "Scan Result", value = result, clipboardManager = clipboardManager, context = context)
                }
            }

            if (qrHistory.isNotEmpty()) {
                item(key = "qr_history_header") {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(qrHistory, key = { it.id }) { entry ->
                    HistoryRow(item = entry, clipboardManager = clipboardManager, context = context)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(title: String, value: String, clipboardManager: ClipboardManager, context: Context) {
    val info = remember(value) { QrPayloadParser.parse(value) }
    val contactIntent = remember(value) { QrPayloadParser.contactInsertIntent(value) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            AssistChip(onClick = {}, label = { Text(info.typeLabel) })
            Text(info.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(title, value))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                TextButton(onClick = { FileUtils.shareText(context, value) }) { Text("Share") }
                info.actionUri?.let { uri ->
                    TextButton(onClick = { openSafeLink(context, uri.toString()) }) {
                        Icon(Icons.Default.Link, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(info.actionLabel ?: "Open")
                    }
                }
                if (contactIntent != null) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(contactIntent) }
                            .onFailure { Toast.makeText(context, "No contacts app found", Toast.LENGTH_SHORT).show() }
                    }) { Text("Save contact") }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: QrHistory, clipboardManager: ClipboardManager, context: Context) {
    val date = remember(item.timestamp) {
        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    ListItem(
        headlineContent = { Text(item.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${item.type} · $date") },
        trailingContent = {
            IconButton(onClick = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("QR History", item.value))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, "Copy")
            }
        }
    )
    HorizontalDivider()
}

private fun openSafeLink(context: Context, value: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value.trim()))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
    }
}
