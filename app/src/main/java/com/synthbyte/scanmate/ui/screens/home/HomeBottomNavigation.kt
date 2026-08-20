package com.synthbyte.scanmate.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

class HomeNavItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun HomeBottomNavigation(selected: String, onScan: () -> Unit, items: List<HomeNavItem>) {
    Box(modifier = Modifier.fillMaxWidth()) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            items.take(2).forEach { item -> NavItem(selected, item) }
            Spacer(modifier = Modifier.width(68.dp))
            items.drop(2).take(2).forEach { item -> NavItem(selected, item) }
        }

        var pressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.94f else 1f,
            label = "scan-fab-scale",
            animationSpec = spring()
        )
        FloatingActionButton(
            onClick = {
                pressed = true
                onScan()
                pressed = false
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(56.dp)
                .scale(scale)
                .shadow(10.dp, RoundedCornerShape(18.dp)),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Scan")
        }
    }
}

@Composable
private fun RowScope.NavItem(selected: String, item: HomeNavItem) {
    NavigationBarItem(
        selected = selected == item.label,
        onClick = item.onClick,
        icon = { Icon(item.icon, contentDescription = item.label) },
        label = { Text(item.label) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary
        )
    )
}
