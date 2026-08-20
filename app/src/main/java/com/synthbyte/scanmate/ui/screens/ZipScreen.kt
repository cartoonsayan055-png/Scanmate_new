package com.synthbyte.scanmate.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCompressing by remember { mutableStateOf(false) }
    var fileCount by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        fileCount = withContext(Dispatchers.IO) { FileUtils.listManagedFiles(context).size }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & ZIP") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(86.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Create Local Backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Compress scans, PDFs, QR exports, OCR text and backups into a shareable ZIP. This works offline and does not require a backend.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Text("$fileCount managed files found", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

            if (isCompressing) {
                CircularProgressIndicator()
                Text("Creating backup...", modifier = Modifier.padding(top = 16.dp))
            } else {
                Button(onClick = {
                    coroutineScope.launch {
                        isCompressing = true
                        val files = withContext(Dispatchers.IO) { FileUtils.listManagedFiles(context) }
                        if (files.isEmpty()) {
                            Toast.makeText(context, "No files to backup", Toast.LENGTH_SHORT).show()
                        } else {
                            val zip = ZipUtils.createZip(context, files, "ScanMate_Backup_${System.currentTimeMillis()}")
                            if (zip != null) {
                                Toast.makeText(context, "Backup created: ${zip.name}", Toast.LENGTH_LONG).show()
                                FileUtils.shareFile(context, zip, "application/zip")
                            } else {
                                Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        fileCount = withContext(Dispatchers.IO) { FileUtils.listManagedFiles(context).size }
                        isCompressing = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Backup app files to ZIP") }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))

                OutlinedButton(onClick = {
                    val latest = FileUtils.listManagedFiles(context).firstOrNull { it.extension.equals("zip", ignoreCase = true) }
                    if (latest != null) FileUtils.shareFile(context, latest, "application/zip") else Toast.makeText(context, "No ZIP backup found", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Share latest ZIP backup") }
            }
        }
    }
}
