package com.synthbyte.scanmate.ui.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun HomeLoadingSkeleton(isEmptyState: Boolean = false, onScan: () -> Unit = {}) {
    val transition = rememberInfiniteTransition(label = "home-shimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Restart),
        label = "shimmer-sweep"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        start = Offset(sweep - 450f, 0f),
        end = Offset(sweep, 0f)
    )
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isEmptyState) {
                Text("Your document desk is empty", style = MaterialTheme.typography.titleMedium)
                Text("Start with one scan or import a gallery image.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onScan) { Text("Scan first page") }
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(18.dp).background(brush, RoundedCornerShape(10.dp)))
                Spacer(modifier = Modifier.fillMaxWidth().height(56.dp).background(brush, RoundedCornerShape(14.dp)))
                Spacer(modifier = Modifier.fillMaxWidth().height(56.dp).background(brush, RoundedCornerShape(14.dp)))
            }
        }
    }
}
