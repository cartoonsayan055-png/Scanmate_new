package com.synthbyte.scanmate.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Draws the scanner guide/edge overlay.
 *
 * A default frame is always rendered when live edge detection has no result, so the signed APK never
 * shows a blank camera surface. When [corners] are provided, the same renderer animates the detected
 * document quadrilateral and confidence state.
 */
@Composable
fun DocumentOverlay(corners: List<Offset>?, confidence: Float = 0f) {
    val defaultFrame = rememberDefaultFrame()
    val activeCorners = if (corners != null && corners.size == 4) corners else defaultFrame
    val hasDetection = corners != null && corners.size == 4

    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val cornerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.13f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corner_scale"
    )
    val isLocked = hasDetection && confidence >= 0.75f
    val isCandidate = hasDetection && confidence >= 0.45f
    val animAlpha by animateFloatAsState(
        targetValue = when {
            isLocked -> 1f
            isCandidate -> pulseAlpha.coerceAtLeast(0.70f)
            else -> 0.62f
        },
        animationSpec = tween(260),
        label = "overlay_alpha"
    )

    val strokeEffect = when {
        isLocked -> null
        isCandidate -> PathEffect.dashPathEffect(floatArrayOf(16f, 8f), phase = 0f)
        else -> PathEffect.dashPathEffect(floatArrayOf(20f, 12f), phase = 0f)
    }
    val overlayColor = when {
        isLocked -> MaterialTheme.colorScheme.tertiary
        isCandidate -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.94f)
    }
    val fillColor = when {
        isLocked -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
        isCandidate -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    val tl by animateOffsetAsState(activeCorners[0].coercedUnit(), animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "doc_tl")
    val tr by animateOffsetAsState(activeCorners[1].coercedUnit(), animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "doc_tr")
    val br by animateOffsetAsState(activeCorners[2].coercedUnit(), animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "doc_br")
    val bl by animateOffsetAsState(activeCorners[3].coercedUnit(), animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "doc_bl")

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            fun Offset.toCanvasOffset(): Offset = Offset(x * size.width, y * size.height)
            val path = Path().apply {
                moveTo(tl.toCanvasOffset().x, tl.toCanvasOffset().y)
                lineTo(tr.toCanvasOffset().x, tr.toCanvasOffset().y)
                lineTo(br.toCanvasOffset().x, br.toCanvasOffset().y)
                lineTo(bl.toCanvasOffset().x, bl.toCanvasOffset().y)
                close()
            }

            if (fillColor.alpha > 0f) {
                drawPath(path, color = fillColor.copy(alpha = fillColor.alpha * animAlpha))
            }

            drawPath(
                path = path,
                color = overlayColor.copy(alpha = animAlpha),
                style = Stroke(
                    width = (if (isLocked) 3.6f else 2.7f).dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = strokeEffect
                )
            )

            listOf(tl, tr, br, bl).forEach { c ->
                val pt = c.toCanvasOffset()
                val r = (if (isLocked) 7f else 6f * cornerScale).dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.92f), radius = r + 2.dp.toPx(), center = pt, style = Stroke(2.dp.toPx()))
                drawCircle(overlayColor.copy(alpha = animAlpha), radius = r, center = pt)
            }
        }
    }
}

private fun rememberDefaultFrame(): List<Offset> = listOf(
    Offset(0.10f, 0.18f),
    Offset(0.90f, 0.18f),
    Offset(0.90f, 0.78f),
    Offset(0.10f, 0.78f)
)

private fun Offset.coercedUnit(): Offset = Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
