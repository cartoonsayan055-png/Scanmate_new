package com.synthbyte.scanmate.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val bullets: List<String>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            title = "Scan documents fast",
            subtitle = "Capture clean pages, import from gallery, edit pages, and build multi-page files.",
            icon = Icons.Default.CameraAlt,
            bullets = listOf("CameraX scanner", "Batch scans", "Crop, rotate and enhance")
        ),
        OnboardingPage(
            title = "English OCR built in",
            subtitle = "Extract, clean, copy, share, and export English text while keeping basic OCR offline-ready.",
            icon = Icons.Default.TextSnippet,
            bullets = listOf("English OCR", "Text cleanup", "TXT and DOCX export")
        ),
        OnboardingPage(
            title = "AI when you choose",
            subtitle = "Use optional AI tools for summaries and document help, with local fallback when offline or no key is saved.",
            icon = Icons.Default.AutoAwesome,
            bullets = listOf("No hardcoded keys", "Offline fallback", "Summaries and study helpers")
        ),
        OnboardingPage(
            title = "Secure local vault",
            subtitle = "Store sensitive OCR snippets in an encrypted local vault powered by Android Keystore.",
            icon = Icons.Default.Lock,
            bullets = listOf("Local encryption", "No account. No cloud. No subscription. Ever.", "Private by default", "Export only when you decide")
        ),
        OnboardingPage(
            title = "PDF, QR and exports",
            subtitle = "Generate PDFs, QR codes, backups, and share-ready files from one beginner-friendly workspace.",
            icon = Icons.Default.PictureAsPdf,
            bullets = listOf("PDF export", "QR tools", "ZIP backups")
        )
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ScanMate", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                OutlinedButton(onClick = onFinish) { Text("Skip") }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                OnboardingPageCard(page = pages[pageIndex])
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (index == pagerState.currentPage) 22.dp else 8.dp, 8.dp)
                                .background(
                                    if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                    }
                }
                Button(
                    onClick = {
                        if (isLast) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (isLast) "Get started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageCard(page: OnboardingPage) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .background(
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)),
                            RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(page.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
                }
                Text(page.title, fontWeight = FontWeight.Bold, fontSize = 25.sp, textAlign = TextAlign.Center)
                Text(
                    page.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                page.bullets.forEach { bullet ->
                    Text("• $bullet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
