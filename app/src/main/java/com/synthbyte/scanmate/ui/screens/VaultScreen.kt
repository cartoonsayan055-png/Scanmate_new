package com.synthbyte.scanmate.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.synthbyte.scanmate.security.SecurityAudit
import com.synthbyte.scanmate.ui.components.SecureScreenEffect
import com.synthbyte.scanmate.utils.EncryptedVaultUtils
import com.synthbyte.scanmate.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onNavigateBack: () -> Unit) {
    SecureScreenEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuth = biometricManager.canAuthenticate(authenticators)
    var vaultUnlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    fun requestUnlock() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            authError = "Vault unlock is not available from this screen."
            return
        }
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            authError = "Set up fingerprint or device lock to use Secure Vault."
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vaultUnlocked = true
                    authError = null
                    SecurityAudit.markVaultAccess(context)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authError = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    authError = "Fingerprint not recognized. Try again."
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Secure Vault")
                .setSubtitle("Use fingerprint or device lock to access encrypted files")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    if (!vaultUnlocked) {
        LaunchedEffect(Unit) { requestUnlock() }
        LockedVaultState(
            authError = authError,
            onRetry = { requestUnlock() },
            onBack = onNavigateBack
        )
        return
    }

    var vaultItems by remember { mutableStateOf<List<File>>(emptyList()) }
    var previewName by remember { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        scope.launch {
            vaultItems = withContext(Dispatchers.IO) {
                FileUtils.appFolder(context, "Vault")
                    ?.listFiles { file -> EncryptedVaultUtils.isVaultFile(file) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
            }
        }
    }

    fun decryptAndOpen(file: File, share: Boolean) {
        scope.launch {
            val decrypted = EncryptedVaultUtils.decryptToCacheFile(context, file)
            val metadata = EncryptedVaultUtils.readMetadata(file)
            if (decrypted == null || metadata == null) {
                Toast.makeText(context, "Could not unlock this vault item", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (share) {
                FileUtils.shareFile(context, decrypted, metadata.mimeType)
            } else {
                FileUtils.openFile(context, decrypted, metadata.mimeType)
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Vault") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "vault_header") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.padding(8.dp))
                        Column {
                            Text("Encrypted local storage", fontWeight = FontWeight.Bold)
                            Text("Images, PDFs, exports and OCR text stay encrypted on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (vaultItems.isEmpty()) {
                item(key = "vault_empty") { EmptyVaultState() }
            } else {
                items(vaultItems, key = { it.absolutePath }) { file ->
                    val metadata = remember(file.absolutePath, file.lastModified()) { EncryptedVaultUtils.readMetadata(file) }
                    VaultItemCard(
                        file = file,
                        metadata = metadata,
                        onPreview = {
                            scope.launch {
                                previewName = metadata?.displayName ?: file.nameWithoutExtension
                                previewText = EncryptedVaultUtils.readEncryptedText(file)
                                    ?: "This vault item is a file. Use Open or Share after unlocking."
                            }
                        },
                        onOpen = { decryptAndOpen(file, share = false) },
                        onShare = { decryptAndOpen(file, share = true) },
                        onDelete = { deleteCandidate = file }
                    )
                }
            }
        }
    }

    if (previewText != null) {
        AlertDialog(
            onDismissRequest = { previewText = null },
            title = { Text(previewName ?: "Vault preview") },
            text = { Text(previewText.orEmpty().take(2000)) },
            confirmButton = {
                TextButton(onClick = { FileUtils.shareText(context, previewText.orEmpty(), "Share decrypted text") }) { Text("Share text") }
            },
            dismissButton = { TextButton(onClick = { previewText = null }) { Text("Close") } }
        )
    }

    deleteCandidate?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete vault item?") },
            text = { Text("This removes the encrypted item from local storage. It cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    val deleted = runCatching { file.delete() }.getOrDefault(false)
                    Toast.makeText(context, if (deleted) "Vault item deleted" else "Could not delete item", Toast.LENGTH_SHORT).show()
                    deleteCandidate = null
                    refresh()
                }) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LockedVaultState(authError: String?, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Fingerprint, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Vault Locked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Authenticate to access encrypted files and OCR text.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            authError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Button(onClick = onRetry) { Text("Unlock") }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun EmptyVaultState() {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Vault is empty", fontWeight = FontWeight.Bold)
                Text("Move documents, images or OCR text to Secure Vault from document details.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun VaultItemCard(
    file: File,
    metadata: EncryptedVaultUtils.VaultMetadata?,
    onPreview: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(file.lastModified()) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }
    val icon = when {
        metadata?.mimeType == "text/plain" -> Icons.Default.TextSnippet
        metadata?.mimeType?.startsWith("image/") == true -> Icons.Default.Image
        else -> Icons.Default.Description
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(metadata?.displayName ?: file.nameWithoutExtension, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${file.length() / 1024L} KB · ${metadata?.mimeType ?: "encrypted"} · $date", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPreview) { Icon(Icons.Default.Visibility, contentDescription = "Preview") }
            IconButton(onClick = onOpen) { Icon(Icons.Default.OpenInNew, contentDescription = "Open") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}
