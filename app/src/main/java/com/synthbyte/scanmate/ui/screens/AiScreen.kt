package com.synthbyte.scanmate.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.domain.GeminiHelper
import com.synthbyte.scanmate.domain.GeminiModels
import com.synthbyte.scanmate.utils.AiWorkflow
import com.synthbyte.scanmate.utils.DocumentIntelligence
import com.synthbyte.scanmate.utils.FileUtils
import com.synthbyte.scanmate.utils.NetworkUtils
import androidx.hilt.navigation.compose.hiltViewModel
import com.synthbyte.scanmate.ui.viewmodels.DocumentViewModel
import kotlinx.coroutines.launch
import com.synthbyte.scanmate.utils.FileCore

private const val BUSINESS_CARD = "BUSINESS_CARD"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiScreen(onNavigateBack: () -> Unit, settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val repository = settingsRepository
    val apiKey by repository.geminiApiKeyFlow.collectAsState(initial = "")
    val selectedModelId by repository.geminiModelIdFlow.collectAsState(initial = GeminiModels.DEFAULT_MODEL_ID)
    val selectedModel = GeminiModels.optionFor(selectedModelId)
    val isOnline = NetworkUtils.isOnline(context)
    val canUseOnlineAi = isOnline && !apiKey.isNullOrBlank()

    var prompt by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var responseTitle by remember { mutableStateOf("AI Response") }
    var responseIsError by remember { mutableStateOf(false) }
    var isOfflineFallback by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedWorkflow by remember { mutableStateOf<AiWorkflow?>(null) }
    var selectedWorkflowId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val documentViewModel: DocumentViewModel = hiltViewModel()
    val recentDocuments by documentViewModel.allDocuments.collectAsState(initial = emptyList())
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val cardResult = remember(prompt, selectedWorkflowId) {
        if (selectedWorkflowId == BUSINESS_CARD && prompt.isNotBlank()) DocumentIntelligence.extractBusinessCard(prompt) else null
    }

    fun buildVCard(card: DocumentIntelligence.BusinessCardResult): String = buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        card.name?.takeIf { it.isNotBlank() }?.let { name ->
            appendLine("FN:$name")
            appendLine("N:$name;;;;")
        }
        card.email?.takeIf { it.isNotBlank() }?.let { appendLine("EMAIL:$it") }
        card.phone?.takeIf { it.isNotBlank() }?.let { appendLine("TEL:$it") }
        card.website?.takeIf { it.isNotBlank() }?.let { appendLine("URL:$it") }
        appendLine("END:VCARD")
    }

    fun showLocalError(message: String) {
        responseTitle = "Needs attention"
        response = message
        responseIsError = true
        isOfflineFallback = false
    }

    fun publishOffline(workflow: AiWorkflow, sourceText: String) {
        responseTitle = "Offline ${workflow.label}"
        response = DocumentIntelligence.buildOfflineResponse(workflow, sourceText)
        responseIsError = response.startsWith("Paste OCR", ignoreCase = true)
        isOfflineFallback = !responseIsError
    }

    fun runSmartWorkflow(workflow: AiWorkflow, sourceText: String) {
        selectedWorkflow = workflow
        selectedWorkflowId = workflow.name
        val finalText = sourceText.trim()
        if (finalText.isBlank()) {
            showLocalError("Paste OCR text or type a prompt first.")
            return
        }
        if (!canUseOnlineAi) {
            publishOffline(workflow, finalText)
            return
        }
        coroutineScope.launch {
            isLoading = true
            response = ""
            responseTitle = workflow.label
            isOfflineFallback = false
            val result = GeminiHelper(apiKey.orEmpty()).generateContent(DocumentIntelligence.buildPrompt(workflow, finalText), selectedModelId)
            response = result.text
            responseIsError = !result.isSuccess
            isLoading = false
        }
    }

    fun runFreePrompt() {
        val finalPrompt = prompt.trim()
        if (finalPrompt.isBlank()) {
            showLocalError("Paste OCR text or type a prompt first.")
            return
        }
        if (!canUseOnlineAi) {
            publishOffline(AiWorkflow.SUMMARY, finalPrompt)
            return
        }
        coroutineScope.launch {
            isLoading = true
            response = ""
            responseTitle = "AI Response"
            isOfflineFallback = false
            val result = GeminiHelper(apiKey.orEmpty()).generateContent(finalPrompt, selectedModelId)
            response = result.text
            responseIsError = !result.isSuccess
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Workspace") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "ai_hero") {
                AiHeroCard(
                    canUseOnlineAi = canUseOnlineAi,
                    isOnline = isOnline,
                    selectedModel = selectedModel.displayName
                )
            }

            item(key = "ai_workflow_grid") {
                WorkflowChipGrid(
                    selectedWorkflow = selectedWorkflow,
                    selectedBusinessCard = selectedWorkflowId == BUSINESS_CARD,
                    enabled = !isLoading,
                    onWorkflowSelected = { workflow ->
                        if (prompt.isBlank()) {
                            selectedWorkflow = workflow
                            selectedWorkflowId = workflow.name
                            prompt = workflow.promptPrefix
                        } else {
                            runSmartWorkflow(workflow, prompt)
                        }
                    },
                    onBusinessCardSelected = {
                        selectedWorkflow = null
                        selectedWorkflowId = BUSINESS_CARD
                        if (prompt.isBlank()) showLocalError("Paste business card OCR text first.")
                    }
                )
            }

            item(key = "ai_load_last_scan") {
                TextButton(
                    onClick = {
                        val latestText = recentDocuments.firstOrNull { !it.ocrText.isNullOrBlank() }?.ocrText.orEmpty()
                        if (latestText.isBlank()) {
                            Toast.makeText(context, "No recent scan with OCR text found", Toast.LENGTH_SHORT).show()
                        } else {
                            prompt = latestText
                            Toast.makeText(context, "Loaded text from last scan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load text from last scan")
                }
            }

            if (!canUseOnlineAi) {
                item(key = "ai_offline_banner") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "AI tools are optional. Add your API key in Settings for online Gemini, or keep using offline tools.",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item(key = "ai_prompt") {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Paste OCR text, receipt, invoice, homework question, or document notes") },
                    supportingText = {
                        if (canUseOnlineAi) {
                            Text("Online AI uses ${selectedModel.displayName}. Offline fallback still protects the workflow.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    maxLines = 8,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(22.dp)
                )
            }

            item(key = "ai_actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { runFreePrompt() }, modifier = Modifier.weight(1f), enabled = !isLoading) {
                        Text(if (isLoading) "Working..." else "Generate")
                    }
                    OutlinedButton(onClick = {
                        prompt = DocumentIntelligence.cleanOcrText(prompt)
                        Toast.makeText(context, "OCR text cleaned locally", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f), enabled = !isLoading) {
                        Text("Clean Text")
                    }
                }
            }

            if (isLoading) {
                item(key = "ai_progress") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            cardResult?.let { card ->
                item(key = "ai_business_card_result") {
                    BusinessCardResultCard(
                        card = card,
                        onCopy = { label, value ->
                            clipboardManager.setPrimaryClip(ClipData.newPlainText(label, value))
                            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
                        },
                        onSaveVCard = {
                            coroutineScope.launch {
                                val saved = FileCore.saveTextFile(context, buildVCard(card), "contact_${System.currentTimeMillis()}.vcf")
                                if (saved != null) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", saved)
                                    context.startActivity(android.content.Intent.createChooser(
                                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/x-vcard"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        },
                                        "Save contact to…"
                                    ))
                                } else {
                                    Toast.makeText(context, "Failed to save contact", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            item(key = "ai_response") {
                AnimatedVisibility(response.isNotBlank()) {
                    ResponseCard(
                        title = responseTitle,
                        response = response,
                        isError = responseIsError,
                        isOfflineFallback = isOfflineFallback,
                        onCopy = {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText(responseTitle, response))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        onShare = { FileUtils.shareText(context, response) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkflowChipGrid(
    selectedWorkflow: AiWorkflow?,
    selectedBusinessCard: Boolean,
    enabled: Boolean,
    onWorkflowSelected: (AiWorkflow) -> Unit,
    onBusinessCardSelected: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Smart Workflows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiWorkflow.entries.forEach { workflow ->
                FilterChip(
                    selected = selectedWorkflow == workflow,
                    onClick = { onWorkflowSelected(workflow) },
                    enabled = enabled,
                    label = { Text(workflow.label) }
                )
            }
            FilterChip(
                selected = selectedBusinessCard,
                onClick = onBusinessCardSelected,
                enabled = enabled,
                label = { Text("Business Card") }
            )
        }
    }
}

@Composable
private fun AiHeroCard(canUseOnlineAi: Boolean, isOnline: Boolean, selectedModel: String) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(58.dp).background(Color.White.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (canUseOnlineAi) Icons.Default.AutoAwesome else Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("AI-first document workspace", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (canUseOnlineAi) "Online AI ready · $selectedModel"
                    else if (isOnline) "Offline fallback active · add key for Gemini"
                    else "Offline fallback active · scanner remains usable",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun BusinessCardResultCard(
    card: DocumentIntelligence.BusinessCardResult,
    onCopy: (String, String) -> Unit,
    onSaveVCard: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Business Card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BusinessCardRow("Name", card.name, onCopy)
            BusinessCardRow("Email", card.email, onCopy)
            BusinessCardRow("Phone", card.phone, onCopy)
            BusinessCardRow("Website", card.website, onCopy)
            TextButton(onClick = onSaveVCard) { Text("Save as vCard") }
        }
    }
}

@Composable
private fun BusinessCardRow(label: String, value: String?, onCopy: (String, String) -> Unit) {
    val safeValue = value?.takeIf { it.isNotBlank() } ?: "—"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(safeValue, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(
            enabled = value?.isNotBlank() == true,
            onClick = { onCopy(label, safeValue) }
        ) {
            Icon(Icons.Default.ContentCopy, null)
        }
    }
}

@Composable
private fun ResponseCard(
    title: String,
    response: String,
    isError: Boolean,
    isOfflineFallback: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.28f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isOfflineFallback) Text("Generated locally without sending data online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(if (isOfflineFallback) Icons.Default.CloudOff else Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(response, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null)
                    Text("Copy")
                }
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, null)
                    Text("Share")
                }
            }
        }
    }
}
