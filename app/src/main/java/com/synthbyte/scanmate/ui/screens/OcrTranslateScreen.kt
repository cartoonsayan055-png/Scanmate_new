package com.synthbyte.scanmate.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.domain.GeminiHelper
import com.synthbyte.scanmate.domain.GeminiModels
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.NetworkUtils
import com.synthbyte.scanmate.utils.OcrHelper
import com.synthbyte.scanmate.utils.OcrTranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTranslateScreen(onNavigateBack: () -> Unit, settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiKey by settingsRepository.geminiApiKeyFlow.collectAsState(initial = "")
    val modelId by settingsRepository.geminiModelIdFlow.collectAsState(initial = GeminiModels.DEFAULT_MODEL_ID)
    val isOnline = NetworkUtils.isOnline(context)
    val canUseOnlineAi = isOnline && !apiKey.isNullOrBlank()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var selectedLanguage by remember { mutableStateOf(OcrTranslationEngine.supportedLanguages.first()) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Choose an image, extract OCR, then translate.") }
    var isProcessing by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedImageUri = uri
        translatedText = ""
        if (uri != null) {
            scope.launch {
                isProcessing = true
                status = "Extracting OCR from selected image..."
                val copied = withContext(Dispatchers.IO) { FileUtils.copyUriToImageFile(context, uri) }
                val result = withContext(Dispatchers.IO) {
                    copied?.let { OcrHelper.extractTextWithStatsFromFile(context, it) }
                }
                isProcessing = false
                if (result == null || result.text.isBlank()) {
                    status = "No readable OCR text found. Try a sharper image."
                    Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                } else {
                    ocrText = result.text
                    status = "OCR ready · ${result.qualityLabel} · ${result.confidencePercent}% · ${result.wordCount} words"
                }
            }
        }
    }

    fun runTranslation() {
        val source = ocrText.trim()
        if (source.isBlank()) {
            Toast.makeText(context, "Extract or paste OCR text first", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isProcessing = true
            status = if (canUseOnlineAi) "Translating with optional online AI..." else "Offline-safe fallback active..."
            var usedFallback = !canUseOnlineAi
            val output = if (canUseOnlineAi) {
                val prompt = OcrTranslationEngine.onlinePrompt(source, selectedLanguage.code)
                val result = GeminiHelper(apiKey.orEmpty()).generateContent(prompt, modelId)
                if (result.isSuccess) {
                    result.text
                } else {
                    usedFallback = true
                    OcrTranslationEngine.offlineFallback(source, selectedLanguage.code) + "\n\nOnline AI note: ${result.text}"
                }
            } else {
                OcrTranslationEngine.offlineFallback(source, selectedLanguage.code)
            }
            translatedText = output
            status = if (usedFallback) "Offline fallback completed safely" else "Translation completed"
            isProcessing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR Translate") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "translate_hero") {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Real-time OCR translation workflow", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Works online with your API key. Without internet/key, it safely keeps OCR cleanup, keywords, and translation preparation offline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AssistChip(onClick = {}, label = { Text(if (canUseOnlineAi) "Online AI ready" else "Offline fallback active") })
                    }
                }
            }

            if (isProcessing) item(key = "translate_progress") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            item(key = "translate_language") {
                Text("Target language", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(OcrTranslationEngine.supportedLanguages, key = { it.code }) { language ->
                        FilterChip(
                            selected = selectedLanguage.code == language.code,
                            onClick = { selectedLanguage = language },
                            label = { Text(language.label) }
                        )
                    }
                }
            }

            item(key = "translate_actions") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), enabled = !isProcessing) {
                        Text("Pick image")
                    }
                    OutlinedButton(onClick = { runTranslation() }, modifier = Modifier.weight(1f), enabled = !isProcessing) {
                        Text("Translate")
                    }
                }
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }

            item(key = "translate_text_input") {
                OutlinedTextField(
                    value = ocrText,
                    onValueChange = { ocrText = it },
                    label = { Text("OCR text") },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    minLines = 5,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            if (translatedText.isNotBlank()) {
                item(key = "translate_preview") {
                    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Translated preview", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(translatedText)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item(key = "translate_copy") {
                                    TextButton(onClick = {
                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("ScanMate translation", translatedText))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, null)
                                        Text("Copy")
                                    }
                                }
                                item(key = "translate_share") {
                                    TextButton(onClick = { FileUtils.shareText(context, translatedText) }) {
                                        Icon(Icons.Default.Share, null)
                                        Text("Share")
                                    }
                                }
                                item(key = "translate_save_txt") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val file = FileUtils.saveTextFile(context, translatedText, "Translation_${System.currentTimeMillis()}")
                                            if (file != null) FileUtils.shareFile(context, file, "text/plain") else Toast.makeText(context, "TXT export failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text("TXT") }
                                }
                                item(key = "translate_save_docx") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val file: File? = FileUtils.saveDocxText(context, translatedText, "Translation_${System.currentTimeMillis()}")
                                            if (file != null) FileUtils.shareFile(context, file, FileUtils.mimeTypeFor(file)) else Toast.makeText(context, "DOCX export failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text("DOCX") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
