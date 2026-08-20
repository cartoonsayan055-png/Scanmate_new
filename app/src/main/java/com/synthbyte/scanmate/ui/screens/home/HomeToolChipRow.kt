package com.synthbyte.scanmate.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeToolChipRow(
    onPdf: () -> Unit,
    onOcr: () -> Unit,
    onQr: () -> Unit,
    onZip: () -> Unit,
    onTranslate: () -> Unit,
    onVault: () -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ToolPill("PDF Tools", Icons.Default.PictureAsPdf, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, onPdf)
        ToolPill("OCR", Icons.Default.Description, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary, onOcr)
        ToolPill("QR Tools", Icons.Default.QrCodeScanner, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary, onQr)
        ToolPill("Translate", Icons.Default.Translate, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, onTranslate)
        ToolPill("Vault", Icons.Default.Lock, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, onVault)
        ToolPill("ZIP", Icons.Default.FolderZip, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, onZip)
    }
}

@Composable
private fun ToolPill(
    label: String,
    icon: ImageVector,
    iconBackground: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp).background(iconBackground, RoundedCornerShape(8.dp)).padding(4.dp)
            )
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
