package com.synthbyte.scanmate.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synthbyte.scanmate.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FileSortMode.DATE) }
    var viewMode by remember { mutableStateOf(FileViewMode.LIST) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(refreshKey) {
        files = withContext(Dispatchers.IO) { FileUtils.listManagedFiles(context) }
    }

    val visibleFiles = remember(files, query, sortMode) {
        files.filter { file ->
            query.isBlank() || file.name.contains(query, ignoreCase = true) || (file.parentFile?.name ?: "").contains(query, ignoreCase = true)
        }.let { filtered ->
            when (sortMode) {
                FileSortMode.DATE -> filtered.sortedByDescending { it.lastModified() }
                FileSortMode.NAME -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
                FileSortMode.SIZE -> filtered.sortedByDescending { it.length() }
            }
        }
    }
    val groupedFiles = remember(visibleFiles) {
        visibleFiles.groupBy { it.parentFile?.name ?: "App" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewMode = FileViewMode.LIST }) {
                        Icon(
                            Icons.Default.ViewList,
                            contentDescription = "List view",
                            tint = if (viewMode == FileViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewMode = FileViewMode.GRID }) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = "Grid view",
                            tint = if (viewMode == FileViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text("Managed files", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Browse scans, PDFs, QR codes, OCR text and exports stored by ScanMate.", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Search files and folders") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sortMode == FileSortMode.DATE, onClick = { sortMode = FileSortMode.DATE }, label = { Text("Recent") })
                FilterChip(selected = sortMode == FileSortMode.NAME, onClick = { sortMode = FileSortMode.NAME }, label = { Text("Name") })
                FilterChip(selected = sortMode == FileSortMode.SIZE, onClick = { sortMode = FileSortMode.SIZE }, label = { Text("Largest") })
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (visibleFiles.isEmpty()) {
                EmptyFilesCard(hasFiles = files.isNotEmpty())
            } else if (viewMode == FileViewMode.LIST) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    groupedFiles.forEach { (folderName, sectionFiles) ->
                        stickyHeader(key = "header_$folderName") {
                            FileSectionHeader(folderName)
                        }
                        items(sectionFiles, key = { it.absolutePath }) { file ->
                            ManagedFileRow(
                                file = file,
                                onOpen = { FileUtils.openFile(context, file, FileUtils.mimeTypeFor(file)) },
                                onShare = { FileUtils.shareFile(context, file, FileUtils.mimeTypeFor(file)) },
                                onDelete = { fileToDelete = file }
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedFiles.forEach { (folderName, sectionFiles) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "grid_header_$folderName") {
                            FileSectionHeader(folderName)
                        }
                        gridItems(sectionFiles, key = { it.absolutePath }) { file ->
                            ManagedFileGridCell(
                                file = file,
                                onOpen = { FileUtils.openFile(context, file, FileUtils.mimeTypeFor(file)) },
                                onShare = { FileUtils.shareFile(context, file, FileUtils.mimeTypeFor(file)) },
                                onDelete = { fileToDelete = file }
                            )
                        }
                    }
                }
            }
        }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete file?") },
            text = { Text("${file.name} will be removed from ScanMate's app folder.") },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = file.delete()
                    Toast.makeText(context, if (deleted) "File deleted" else "Could not delete file", Toast.LENGTH_SHORT).show()
                    fileToDelete = null
                    refreshKey++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FileSectionHeader(folderName: String) {
    Text(
        text = folderName,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EmptyFilesCard(hasFiles: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (hasFiles) "No matching files" else "No exported files yet", fontWeight = FontWeight.Bold)
            Text(
                if (hasFiles) "Try a different search term or sort option." else "Create a scan, export a PDF, save OCR text, generate QR codes, or make a ZIP backup to see files here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FileThumb(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ext = file.extension.lowercase()
    when {
        ext in listOf("jpg", "jpeg", "png", "webp") -> AsyncImage(
            model = remember(file.absolutePath) {
                ImageRequest.Builder(context)
                    .data(file)
                    .diskCacheKey(file.absolutePath)
                    .crossfade(true)
                    .build()
            },
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        ext == "pdf" -> Box(
            modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "PDF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        else -> Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ManagedFileRow(file: File, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val date = remember(file.lastModified()) { formatShortDate(file.lastModified()) }
    val size = remember(file.length()) { formatFileSize(file.length()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(file = file, modifier = Modifier.size(48.dp, 60.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${file.parentFile?.name ?: "App"} · $size · $date", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOpen) { Icon(Icons.Default.OpenInNew, "Open") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
private fun ManagedFileGridCell(file: File, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val date = remember(file.lastModified()) { formatShortDate(file.lastModified()) }
    val size = remember(file.length()) { formatFileSize(file.length()) }
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FileThumb(file = file, modifier = Modifier.size(48.dp, 60.dp))
            Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(size, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Row(horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb >= 1024.0) {
        String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
    } else {
        "${maxOf(1, kb.toInt())} KB"
    }
}

private fun formatShortDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))

private enum class FileSortMode { DATE, NAME, SIZE }
private enum class FileViewMode { LIST, GRID }
