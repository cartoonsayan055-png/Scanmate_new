package com.synthbyte.scanmate.ui.screens

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synthbyte.scanmate.core.SafeLogger
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.ui.components.DocumentOverlay
import androidx.hilt.navigation.compose.hiltViewModel
import com.synthbyte.scanmate.ui.viewmodels.CameraViewModel
import com.synthbyte.scanmate.util.EdgeAnalyzer
import com.synthbyte.scanmate.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.graphics.Bitmap
import com.synthbyte.scanmate.utils.FilterType

private enum class ScanQuality(val label: String, val jpegQuality: Int, val captureMode: Int) {
    STANDARD("Standard", 84, ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY),
    HIGH("High", 94, ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY),
    MAX("Max", 100, ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
}

private enum class ScanAspect(val label: String, val cameraValue: Int) {
    RATIO_4_3("4:3", AspectRatio.RATIO_4_3),
    RATIO_16_9("16:9", AspectRatio.RATIO_16_9)
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onNavigateBack: () -> Unit,
    onScanFinished: (Long) -> Unit,
    settingsRepository: SettingsRepository
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    if (!cameraPermissionState.status.isGranted) {
        CameraPermissionState(onNavigateBack = onNavigateBack) {
            cameraPermissionState.launchPermissionRequest()
        }
        return
    }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val viewModel: CameraViewModel = hiltViewModel()
    val defaultWorkspace by settingsRepository.defaultWorkspaceFlow.collectAsState(initial = "Inbox")
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val capturedImages = remember { mutableStateListOf<File>() }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(ScanQuality.HIGH) }
    var aspect by remember { mutableStateOf(ScanAspect.RATIO_4_3) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var autoDetectEnabled by remember { mutableStateOf(false) }
    var stableFrameCount by remember { mutableIntStateOf(0) }
    var analysisFrameCount by remember { mutableIntStateOf(0) }
    var lastAutoCaptureAt by remember { mutableStateOf(0L) }
    var detectedCorners by remember { mutableStateOf<List<Offset>?>(null) }
    var currentConfidence by remember { mutableStateOf(0f) }
    val isLocked = detectedCorners != null && currentConfidence >= 0.82f
    val hasLiveDetection = detectedCorners != null
    var selectedFilter by remember { mutableStateOf(FilterType.ORIGINAL) }
    val scannerHint = when {
        !autoDetectEnabled -> "Manual mode · tap Auto edges when needed"
        analysisFrameCount == 0 -> "Starting edge detection…"
        !hasLiveDetection -> "Place the page inside the guide"
        currentConfidence >= 0.86f -> "Edges locked · hold steady"
        currentConfidence >= 0.62f -> "Almost ready · hold steady"
        else -> "Move closer or improve lighting"
    }

    LaunchedEffect(autoDetectEnabled) {
        EdgeAnalyzer.reset()
        stableFrameCount = 0
        if (!autoDetectEnabled) {
            detectedCorners = null
            currentConfidence = 0f
        } else {
            analysisFrameCount = 0
        }
    }

    fun finishDocument() {
        if (capturedImages.isEmpty()) {
            Toast.makeText(context, "No pages captured", Toast.LENGTH_SHORT).show()
            return
        }
        if (isFinishing) return
        isFinishing = true
        coroutineScope.launch {
            try {
                val documentId = viewModel.saveScannedDocument(capturedImages.toList(), defaultWorkspace)
                onScanFinished(documentId)
            } catch (throwable: Throwable) {
                SafeLogger.e("CameraScreen", "Saving scanned document failed", throwable)
                Toast.makeText(context, "Save failed: ${throwable.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                isFinishing = false
            }
        }
    }

    fun takePicture() {
        if (isSaving || isFinishing) return
        val capture = imageCapture ?: return
        val photoFile = File.createTempFile("scanmate_", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        isSaving = true
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    coroutineScope.launch {
                        val qualityFile = if (quality == ScanQuality.MAX) {
                            viewModel.sharpenCapturedImageFile(photoFile)
                        } else {
                            photoFile
                        }
                        val finalFile = withContext(Dispatchers.IO) {
                            val filteredFile = if (selectedFilter != FilterType.ORIGINAL) {
                                val bitmap = FileUtils.decodeSampledBitmap(qualityFile.absolutePath, 2600, 2600)
                                if (bitmap == null) {
                                    qualityFile
                                } else {
                                    val upright = FileUtils.normalizeBitmapOrientation(bitmap, qualityFile)
                                    val filtered = FileUtils.applyFilter(upright, selectedFilter)
                                    val saved = FileUtils.saveBitmapToFolder(
                                        context = context,
                                        bitmap = filtered,
                                        folderName = "Scans",
                                        filename = "FILTERED_${qualityFile.nameWithoutExtension}",
                                        format = Bitmap.CompressFormat.JPEG,
                                        quality = quality.jpegQuality
                                    ) ?: qualityFile
                                    if (!bitmap.isRecycled) runCatching { bitmap.recycle() }
                                    if (upright !== bitmap && !upright.isRecycled) runCatching { upright.recycle() }
                                    if (!filtered.isRecycled) runCatching { filtered.recycle() }
                                    saved
                                }
                            } else {
                                qualityFile
                            }

                            val corners = detectedCorners
                            val processedFile = if (corners != null && corners.size == 4 && currentConfidence >= 0.55f) {
                                FileUtils.applyPerspectiveCorrection(
                                    file = filteredFile,
                                    corners = corners,
                                    previewWidth = previewView.width.takeIf { it > 0 } ?: 1080,
                                    previewHeight = previewView.height.takeIf { it > 0 } ?: 1920
                                ) ?: filteredFile
                            } else {
                                filteredFile
                            }
                            val managedFile = FileUtils.moveFileToFolder(
                                context = context,
                                source = processedFile,
                                folderName = "Scans",
                                preferredName = "SCAN_${System.currentTimeMillis()}"
                            ) ?: processedFile
                            listOf(photoFile, qualityFile, filteredFile, processedFile)
                                .distinctBy { it.absolutePath }
                                .filter { it.absolutePath != managedFile.absolutePath && it.exists() && it.parentFile?.absolutePath == context.cacheDir.absolutePath }
                                .forEach { runCatching { it.delete() } }
                            managedFile
                        }
                        capturedImages.add(finalFile)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        isSaving = false
                        Toast.makeText(context, "Captured", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isSaving = false
                    Toast.makeText(context, "Capture failed: ${exception.localizedMessage ?: "Camera error"}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isSaving = true
                val imported = viewModel.importUris(uris)
                capturedImages.addAll(imported)
                isSaving = false
                Toast.makeText(
                    context,
                    if (imported.isNotEmpty()) "Imported ${imported.size} image(s)" else "No images could be imported",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(isLocked) {
        if (isLocked) {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
        }
    }

    LaunchedEffect(lensFacing, quality, aspect, autoDetectEnabled) {
        cameraError = null
        runCatching {
            val cameraProvider = awaitCameraProvider(context)
            val requestedSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val selector = if (cameraProvider.hasCamera(requestedSelector)) {
                requestedSelector
            } else {
                val fallbackSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
                if (cameraProvider.hasCamera(fallbackSelector)) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    cameraError = "Selected camera is not available. Using rear camera instead."
                    fallbackSelector
                } else {
                    cameraError = "No compatible camera was found on this device."
                    null
                }
            }

            if (selector != null) {
                val preview = Preview.Builder()
                    .setTargetAspectRatio(aspect.cameraValue)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(aspect.cameraValue)
                    .setCaptureMode(quality.captureMode)
                    .setJpegQuality(quality.jpegQuality)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(aspect.cameraValue)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        var lastAnalyzedAt = 0L
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy: ImageProxy ->
                            try {
                                if (!autoDetectEnabled) {
                                    mainHandler.post {
                                        detectedCorners = null
                                        currentConfidence = 0f
                                        stableFrameCount = 0
                                    }
                                    return@setAnalyzer
                                }
                                val now = System.currentTimeMillis()
                                if (now - lastAnalyzedAt < 140L) return@setAnalyzer
                                lastAnalyzedAt = now
                                val result = EdgeAnalyzer.detect(imageProxy)
                                mainHandler.post {
                                    analysisFrameCount += 1
                                    if (result == null) {
                                        detectedCorners = null
                                        currentConfidence = 0f
                                        stableFrameCount = 0
                                    } else {
                                        detectedCorners = result.corners
                                        currentConfidence = result.confidence
                                        val nowMain = System.currentTimeMillis()
                                        val autoCaptureReady = result.isStable &&
                                            result.confidence >= 0.78f &&
                                            autoDetectEnabled &&
                                            !isSaving &&
                                            !isFinishing &&
                                            nowMain - lastAutoCaptureAt > 2600L
                                        if (autoCaptureReady) {
                                            stableFrameCount += 1
                                            if (stableFrameCount >= 2) {
                                                stableFrameCount = 0
                                                lastAutoCaptureAt = nowMain
                                                takePicture()
                                            }
                                        } else if (!result.isStable || result.confidence < 0.62f || isSaving || isFinishing) {
                                            stableFrameCount = 0
                                        }
                                    }
                                }
                            } catch (throwable: Throwable) {
                                SafeLogger.e("CameraScreen", "Edge analysis failed", throwable)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                imageCapture = capture
                analysisFrameCount = 0
                stableFrameCount = 0
                torchEnabled = false
            }
        }.onFailure { throwable ->
            SafeLogger.e("CameraScreen", "Camera bind failed", throwable)
            cameraError = throwable.localizedMessage ?: "Camera failed to start."
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        DocumentOverlay(corners = detectedCorners, confidence = currentConfidence)

        AnimatedVisibility(
            visible = autoDetectEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 154.dp)
        ) {
            Text(
                text = scannerHint,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        if (isLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                        else Color.Black.copy(alpha = 0.58f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${capturedImages.size} page${if (capturedImages.size == 1) "" else "s"} ready") }
                )
            }

            cameraError?.let { error ->
                Text(
                    text = error,
                    color = Color.White,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.67f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                )
            }
        }

        if (showSettingsSheet) {
            ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Camera setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Choose the frame and filter before capture.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Aspect ratio", style = MaterialTheme.typography.titleMedium)
                    ScanAspect.entries.forEach { option ->
                        ListItem(
                            headlineContent = { Text(option.label) },
                            modifier = Modifier.clickable {
                                aspect = option
                                showSettingsSheet = false
                            },
                            trailingContent = {
                                if (aspect == option) Icon(Icons.Default.Check, contentDescription = null)
                            }
                        )
                    }
                    Text("Quality: ${quality.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Capture filter", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedFilter == FilterType.ORIGINAL,
                            onClick = { selectedFilter = FilterType.ORIGINAL },
                            label = { Text("Original") }
                        )
                        FilterChip(
                            selected = selectedFilter == FilterType.WHITEBOARD,
                            onClick = { selectedFilter = FilterType.WHITEBOARD },
                            label = { Text("Whiteboard") }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip", tint = Color.White)
                    }
                    IconButton(onClick = {
                        torchEnabled = !torchEnabled
                        camera?.cameraControl?.enableTorch(torchEnabled)
                    }) {
                        Icon(
                            if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Torch",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                    }
                    AssistChip(
                        onClick = {
                            autoDetectEnabled = !autoDetectEnabled
                            EdgeAnalyzer.reset()
                            stableFrameCount = 0
                            if (autoDetectEnabled) {
                                detectedCorners = null
                                currentConfidence = 0f
                            }
                        },
                        label = { Text(if (autoDetectEnabled) "Auto edges" else "Manual") },
                        shape = RoundedCornerShape(50),
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = if (autoDetectEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = if (autoDetectEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScanQuality.entries.forEach { option ->
                        FilterChip(
                            selected = quality == option,
                            onClick = { quality = option },
                            label = { Text(option.label.first().toString()) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }

                IconButton(onClick = { showSettingsSheet = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }

            CapturedThumbnailStrip(capturedImages = capturedImages)

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (hasLiveDetection) "Edge match ${((currentConfidence * 100).toInt()).coerceIn(0, 100)}%" else "Guide frame") },
                    shape = RoundedCornerShape(50),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        labelColor = Color.White
                    )
                )
                FloatingActionButton(
                    onClick = { takePicture() },
                    shape = CircleShape,
                    containerColor = Color.White,
                    modifier = Modifier
                        .size(74.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isLocked) 1f else 0.55f), CircleShape),
                    elevation = FloatingActionButtonDefaults.elevation(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isSaving) 0.28f else 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                FloatingActionButton(
                    onClick = { finishDocument() },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    if (isFinishing) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Done, contentDescription = "Done", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapturedThumbnailStrip(capturedImages: List<File>) {
    val ctx = LocalContext.current
    AnimatedVisibility(visible = capturedImages.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    "${capturedImages.size} pages",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(capturedImages, key = { index, file -> "${file.absolutePath}_$index" }) { index, file ->
                    Box {
                        AsyncImage(
                            model = remember(file.absolutePath) {
                                ImageRequest.Builder(ctx)
                                    .data(file)
                                    .diskCacheKey(file.absolutePath)
                                    .memoryCacheKey(file.absolutePath)
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier.size(52.dp, 68.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionState(onNavigateBack: () -> Unit, onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera permission is required to scan documents.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("ScanMate keeps scanning offline and only uses the camera after you grant permission.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onRequestPermission) { Text("Grant Permission") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateBack) { Text("Back") }
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener(
        {
            try {
                if (continuation.isActive) continuation.resume(future.get())
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        },
        ContextCompat.getMainExecutor(context)
    )
}
