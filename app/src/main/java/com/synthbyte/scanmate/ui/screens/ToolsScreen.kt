package com.synthbyte.scanmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class ToolTone { PRIMARY, ACCENT, DANGER, NEUTRAL }

private data class ToolAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tone: ToolTone,
    val enabled: Boolean = true,
    val stateLabel: String = "",
    val onClick: (() -> Unit)? = null
)

private data class ToolSection(
    val title: String,
    val subtitle: String,
    val actions: List<ToolAction>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPdfTools: () -> Unit,
    onNavigateToQr: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    onNavigateToTranslate: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToZip: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    val sections = listOf(
        ToolSection(
            title = "Capture",
            subtitle = "Start from camera, gallery or saved files.",
            actions = listOf(
                ToolAction("scan", "Scan", "Camera scanner", Icons.Default.CameraAlt, ToolTone.PRIMARY, onClick = onNavigateToCamera),
                ToolAction("import_images", "Import", "Gallery images", Icons.Default.PhotoLibrary, ToolTone.PRIMARY, onClick = onNavigateToFiles),
                ToolAction("import_files", "Files", "Saved exports", Icons.Default.Folder, ToolTone.PRIMARY, onClick = onNavigateToFiles),
                ToolAction("ocr", "OCR", "Extract text", Icons.Default.Translate, ToolTone.ACCENT, onClick = onNavigateToTranslate)
            )
        ),
        ToolSection(
            title = "PDF and export",
            subtitle = "Create, convert, compress and share.",
            actions = listOf(
                ToolAction("images_pdf", "Images to PDF", "Build a PDF", Icons.Default.PictureAsPdf, ToolTone.PRIMARY, onClick = onNavigateToPdfTools),
                ToolAction("merge", "Merge PDF", "Combine files", Icons.Default.PictureAsPdf, ToolTone.PRIMARY, onClick = onNavigateToPdfTools),
                ToolAction("compress", "Compress", "Reduce size", Icons.Default.PictureAsPdf, ToolTone.PRIMARY, onClick = onNavigateToPdfTools),
                ToolAction("pdf_word", "PDF to Word", "DOCX export", Icons.Default.Description, ToolTone.PRIMARY, stateLabel = "DOCX", onClick = onNavigateToPdfTools),
                ToolAction("pdf_excel", "PDF to Excel", "Spreadsheet", Icons.Default.Description, ToolTone.PRIMARY, stateLabel = "XLSX", onClick = onNavigateToPdfTools),
                ToolAction("pdf_ppt", "PDF to PPT", "Slides", Icons.Default.Description, ToolTone.PRIMARY, stateLabel = "PPTX", onClick = onNavigateToPdfTools),
                ToolAction("pdf_images", "PDF to Images", "Page images", Icons.Default.Image, ToolTone.PRIMARY, onClick = onNavigateToPdfTools),
                ToolAction("text_pdf", "Text to PDF", "Clean text file", Icons.Default.PictureAsPdf, ToolTone.PRIMARY, onClick = onNavigateToPdfTools)
            )
        ),
        ToolSection(
            title = "Edit pages",
            subtitle = "Tools that improve scans after capture.",
            actions = listOf(
                ToolAction("auto_crop", "Auto crop", "Find borders", Icons.Default.AutoAwesome, ToolTone.ACCENT, onClick = onNavigateToPdfTools),
                ToolAction("erase_marks", "Clean marks", "Remove dots", Icons.Default.AutoAwesome, ToolTone.ACCENT, onClick = onNavigateToCamera),
                ToolAction("deskew", "Deskew", "Straighten", Icons.Default.AutoAwesome, ToolTone.ACCENT, onClick = onNavigateToCamera),
                ToolAction("watermark", "Watermark", "Add label", Icons.Default.Description, ToolTone.PRIMARY, onClick = onNavigateToFiles),
                ToolAction("sign", "Signature", "Sign pages", Icons.Default.Description, ToolTone.PRIMARY, onClick = onNavigateToFiles),
                ToolAction("reorder", "Reorder", "Arrange pages", Icons.Default.PictureAsPdf, ToolTone.PRIMARY, onClick = onNavigateToFiles),
                ToolAction("rotate", "Rotate", "Fix direction", Icons.Default.PictureAsPdf, ToolTone.PRIMARY, onClick = onNavigateToFiles),
                ToolAction("duplicate", "Duplicate", "Copy page", Icons.Default.ContentCopy, ToolTone.PRIMARY, onClick = onNavigateToFiles)
            )
        ),
        ToolSection(
            title = "Security and utilities",
            subtitle = "Keep private files organized and ready.",
            actions = listOf(
                ToolAction("vault", "Vault", "Private storage", Icons.Default.Lock, ToolTone.DANGER, onClick = onNavigateToVault),
                ToolAction("zip", "ZIP backup", "Archive files", Icons.Default.FolderZip, ToolTone.PRIMARY, onClick = onNavigateToZip),
                ToolAction("scan_qr", "Scan QR", "Read codes", Icons.Default.QrCode2, ToolTone.ACCENT, onClick = onNavigateToQrScanner),
                ToolAction("generate_qr", "Create QR", "Generate code", Icons.Default.QrCode2, ToolTone.ACCENT, onClick = onNavigateToQr),
                ToolAction("share", "Share", "Send files", Icons.Default.Share, ToolTone.PRIMARY, onClick = onNavigateToFiles),
                ToolAction("print", "Print", "Open print flow", Icons.Default.Print, ToolTone.NEUTRAL, onClick = onNavigateToFiles),
                ToolAction("settings", "Settings", "App controls", Icons.Default.Settings, ToolTone.NEUTRAL, onClick = onNavigateToSettings)
            )
        ),
        ToolSection(
            title = "Optional AI",
            subtitle = "Works only when you add your own API key.",
            actions = listOf(
                ToolAction("ai_workspace", "AI workspace", "Summaries", Icons.Default.AutoAwesome, ToolTone.ACCENT, stateLabel = "Optional", onClick = onNavigateToAi),
                ToolAction("ocr_cleanup", "OCR cleanup", "Fix text", Icons.Default.Translate, ToolTone.ACCENT, onClick = onNavigateToTranslate),
                ToolAction("smart_title", "Smart title", "Name files", Icons.Default.AutoAwesome, ToolTone.ACCENT, stateLabel = "Optional", onClick = onNavigateToAi)
            )
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Tools", fontWeight = FontWeight.Bold)
                        Text(
                            "Every document action in one place",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "tools_intro") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fast actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Choose the task first; ScanMate keeps the existing scanner, OCR, PDF, QR, vault and export flows unchanged.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            sections.forEach { section ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header_${section.title}") {
                    SectionHeader(section)
                }
                items(section.actions, key = { it.id }) { tool ->
                    ToolCard(tool)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: ToolSection) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AssistChip(onClick = {}, label = { Text("${section.actions.size}") })
        }
        Text(section.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun colorsForTone(tone: ToolTone) = when (tone) {
    ToolTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    ToolTone.ACCENT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
    ToolTone.DANGER -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    ToolTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ToolCard(tool: ToolAction) {
    val (iconContainer, iconTint) = colorsForTone(tool.tone)
    Card(
        onClick = { tool.onClick?.invoke() },
        enabled = tool.enabled && tool.onClick != null,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tool.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val contentAlpha = if (tool.enabled) 1f else 0.45f
            Box(
                modifier = Modifier.size(40.dp).background(iconContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp).alpha(contentAlpha))
            }
            Text(
                tool.title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(contentAlpha)
            )
            Text(
                tool.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(contentAlpha)
            )
            if (tool.stateLabel.isNotBlank()) {
                Text(
                    tool.stateLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.alpha(contentAlpha)
                )
            }
        }
    }
}
