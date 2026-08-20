package com.synthbyte.scanmate.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.synthbyte.scanmate.core.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptedVaultUtils {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "scanmate_ai_pro_local_vault"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val VERSION_TEXT_V1 = "SCANMATE_VAULT_V1"
    private const val VERSION_V2 = "SCANMATE_VAULT_V2"
    private const val VERSION_BINARY_V3 = "SMVAULT3"
    private const val TAG = "EncryptedVault"

    data class VaultMetadata(
        val displayName: String,
        val mimeType: String,
        val itemType: String,
        val originalExtension: String,
        val createdAt: Long
    )

    suspend fun saveEncryptedText(context: Context, text: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val safeName = FileUtils.sanitizeFileBaseName(filename.ifBlank { "OCR_${System.currentTimeMillis()}" })
                .removeSuffix(".vault")
                .removeSuffix(".txt")
            writeEncryptedPayload(
                context = context,
                displayName = "$safeName.txt",
                itemType = "text",
                mimeType = "text/plain",
                originalExtension = "txt",
                bytes = text.toByteArray(Charsets.UTF_8),
                vaultBaseName = safeName
            )
        } catch (throwable: Throwable) {
            SafeLogger.e(TAG, "Saving encrypted text failed", throwable)
            null
        }
    }

    suspend fun saveEncryptedFile(
        context: Context,
        sourceFile: File,
        displayName: String = sourceFile.name,
        moveOriginal: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext null
            val cleanDisplayName = displayName.ifBlank { sourceFile.name }
            val extension = sourceFile.extension.ifBlank { "bin" }
            val vaultFile = writeEncryptedPayload(
                context = context,
                displayName = cleanDisplayName,
                itemType = "file",
                mimeType = FileUtils.mimeTypeFor(sourceFile),
                originalExtension = extension,
                bytes = sourceFile.readBytes(),
                vaultBaseName = FileUtils.sanitizeFileBaseName(cleanDisplayName.substringBeforeLast('.'))
            ) ?: return@withContext null

            if (moveOriginal && vaultFile.exists() && vaultFile.length() > 0L) {
                runCatching { sourceFile.delete() }
            }
            vaultFile
        } catch (throwable: Throwable) {
            SafeLogger.e(TAG, "Saving encrypted file failed", throwable)
            null
        }
    }

    suspend fun moveFileToVault(
        context: Context,
        sourceFile: File,
        displayName: String = sourceFile.name
    ): File? = saveEncryptedFile(
        context = context,
        sourceFile = sourceFile,
        displayName = displayName,
        moveOriginal = true
    )

    suspend fun saveDocumentBundle(
        context: Context,
        documentTitle: String,
        sourceFiles: List<File>,
        ocrText: String?,
        moveOriginals: Boolean = true
    ): List<File> = withContext(Dispatchers.IO) {
        val saved = mutableListOf<File>()
        val safeTitle = FileUtils.sanitizeFileBaseName(documentTitle.ifBlank { "ScanMate_Document" })
        val validFiles = sourceFiles.filter { it.exists() && it.length() > 0L }

        validFiles.forEachIndexed { index, file ->
            val extension = file.extension.ifBlank { "jpg" }
            val display = "${safeTitle}_page_${index + 1}.$extension"
            val vaultFile = saveEncryptedFile(
                context = context,
                sourceFile = file,
                displayName = display,
                moveOriginal = false
            )
            if (vaultFile != null) saved += vaultFile
        }

        val cleanOcr = ocrText.orEmpty().trim()
        if (cleanOcr.isNotBlank()) {
            saveEncryptedText(
                context = context,
                text = cleanOcr,
                filename = "${safeTitle}_OCR_${System.currentTimeMillis()}"
            )?.let { saved += it }
        }

        val expectedMinimum = validFiles.size + if (cleanOcr.isNotBlank()) 1 else 0
        if (moveOriginals && saved.size >= expectedMinimum) {
            validFiles.forEach { file -> runCatching { file.delete() } }
        }

        saved
    }

    suspend fun readEncryptedText(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val decoded = decryptPayload(file) ?: return@withContext null
            val metadata = readMetadata(file)
            if (metadata?.mimeType == "text/plain" || metadata?.itemType == "text") {
                String(decoded, Charsets.UTF_8)
            } else {
                null
            }
        } catch (throwable: Throwable) {
            SafeLogger.e(TAG, "Reading encrypted text failed", throwable)
            null
        }
    }

    suspend fun decryptToCacheFile(context: Context, vaultFile: File): File? = withContext(Dispatchers.IO) {
        try {
            val metadata = readMetadata(vaultFile) ?: return@withContext null
            val bytes = decryptPayload(vaultFile) ?: return@withContext null
            val cacheDir = File(context.cacheDir, "decrypted-vault").apply { mkdirs() }
            val safeName = FileUtils.sanitizeFileBaseName(metadata.displayName.ifBlank { vaultFile.nameWithoutExtension })
            val output = File(cacheDir, safeName)
            output.writeBytes(bytes)
            output.takeIf { it.exists() && it.length() > 0L }
        } catch (throwable: Throwable) {
            SafeLogger.e(TAG, "Decrypting vault item failed", throwable)
            null
        }
    }

    fun readMetadata(file: File): VaultMetadata? {
        return try {
            if (isBinaryV3(file)) return readBinaryEnvelope(file)?.metadata
            val lines = file.readLines(Charsets.UTF_8)
            when {
                lines.size >= 4 && lines[0] == VERSION_V2 -> {
                    val json = JSONObject(String(Base64.decode(lines[1], Base64.NO_WRAP), Charsets.UTF_8))
                    metadataFromJson(json, file)
                }
                lines.size >= 3 && lines[0] == VERSION_TEXT_V1 -> VaultMetadata(
                    displayName = "${file.nameWithoutExtension}.txt",
                    mimeType = "text/plain",
                    itemType = "text",
                    originalExtension = "txt",
                    createdAt = file.lastModified()
                )
                else -> null
            }
        } catch (throwable: Throwable) {
            SafeLogger.e(TAG, "Reading vault metadata failed", throwable)
            null
        }
    }

    fun isVaultFile(file: File): Boolean = file.isFile && file.extension.equals("vault", ignoreCase = true)

    fun isEncryptionReady(): Boolean = runCatching { getOrCreateKey(); true }.getOrDefault(false)

    /**
     * Re-encrypts vault files in the newest binary envelope. Existing Keystore material remains
     * decrypt-compatible; this is safe to run after OS biometric enrollment changes.
     */
    suspend fun rotateVaultEnvelope(context: Context): Int = withContext(Dispatchers.IO) {
        val vaultDir = FileUtils.appFolder(context, "Vault") ?: return@withContext 0
        val files = vaultDir.listFiles { file -> isVaultFile(file) }.orEmpty()
        var rotated = 0
        files.forEach { file ->
            val metadata = readMetadata(file) ?: return@forEach
            val bytes = decryptPayload(file) ?: return@forEach
            val temp = writeEncryptedPayload(
                context = context,
                displayName = metadata.displayName,
                itemType = metadata.itemType,
                mimeType = metadata.mimeType,
                originalExtension = metadata.originalExtension,
                bytes = bytes,
                vaultBaseName = "${file.nameWithoutExtension}_rotated"
            ) ?: return@forEach
            if (temp.exists() && temp.length() > 0L) {
                runCatching { file.delete() }
                runCatching { temp.renameTo(file) }
                rotated += 1
            }
        }
        rotated
    }

    private fun writeEncryptedPayload(
        context: Context,
        displayName: String,
        itemType: String,
        mimeType: String,
        originalExtension: String,
        bytes: ByteArray,
        vaultBaseName: String
    ): File? {
        val vaultDir = FileUtils.appFolder(context, "Vault") ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(bytes)

        val metadata = JSONObject()
            .put("displayName", displayName)
            .put("itemType", itemType)
            .put("mimeType", mimeType)
            .put("originalExtension", originalExtension)
            .put("createdAt", System.currentTimeMillis())

        val safeBase = FileUtils.sanitizeFileBaseName(vaultBaseName.ifBlank { "Vault_${System.currentTimeMillis()}" })
            .removeSuffix(".vault")
        var file = File(vaultDir, "$safeBase.vault")
        var counter = 1
        while (file.exists()) {
            file = File(vaultDir, "${safeBase}_$counter.vault")
            counter += 1
        }

        DataOutputStream(file.outputStream().buffered()).use { output ->
            output.writeUTF(VERSION_BINARY_V3)
            val metadataBytes = metadata.toString().toByteArray(Charsets.UTF_8)
            output.writeInt(metadataBytes.size)
            output.write(metadataBytes)
            output.writeInt(cipher.iv.size)
            output.write(cipher.iv)
            output.writeInt(encrypted.size)
            output.write(encrypted)
        }
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    private fun decryptPayload(file: File): ByteArray? {
        if (isBinaryV3(file)) {
            val envelope = readBinaryEnvelope(file) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, envelope.iv))
            return cipher.doFinal(envelope.ciphertext)
        }

        val parts = file.readLines(Charsets.UTF_8)
        val iv: ByteArray
        val encrypted: ByteArray

        when {
            parts.size >= 4 && parts[0] == VERSION_V2 -> {
                iv = Base64.decode(parts[2], Base64.NO_WRAP)
                encrypted = Base64.decode(parts[3], Base64.NO_WRAP)
            }
            parts.size >= 3 && parts[0] == VERSION_TEXT_V1 -> {
                iv = Base64.decode(parts[1], Base64.NO_WRAP)
                encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
            }
            else -> return null
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        return generateKey(strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false)
            if (strongBox) builder.setIsStrongBoxBacked(true)
        }

        return try {
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        } catch (throwable: Throwable) {
            if (strongBox) {
                SafeLogger.e(TAG, "StrongBox unavailable; falling back to Android Keystore", throwable)
                generateKey(strongBox = false)
            } else {
                throw throwable
            }
        }
    }

    private data class BinaryEnvelope(
        val metadata: VaultMetadata,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    private fun isBinaryV3(file: File): Boolean = runCatching {
        DataInputStream(file.inputStream().buffered()).use { it.readUTF() == VERSION_BINARY_V3 }
    }.getOrDefault(false)

    private fun readBinaryEnvelope(file: File): BinaryEnvelope? = runCatching {
        DataInputStream(file.inputStream().buffered()).use { input ->
            val version = input.readUTF()
            if (version != VERSION_BINARY_V3) return@runCatching null
            val metadataBytes = ByteArray(input.readInt().coerceIn(0, 64 * 1024))
            input.readFully(metadataBytes)
            val iv = ByteArray(input.readInt().coerceIn(1, 1024))
            input.readFully(iv)
            val ciphertext = ByteArray(input.readInt().coerceAtLeast(0))
            input.readFully(ciphertext)
            BinaryEnvelope(
                metadata = metadataFromJson(JSONObject(String(metadataBytes, Charsets.UTF_8)), file),
                iv = iv,
                ciphertext = ciphertext
            )
        }
    }.getOrNull()

    private fun metadataFromJson(json: JSONObject, file: File): VaultMetadata = VaultMetadata(
        displayName = json.optString("displayName", file.nameWithoutExtension),
        mimeType = json.optString("mimeType", "application/octet-stream"),
        itemType = json.optString("itemType", "file"),
        originalExtension = json.optString("originalExtension", "bin"),
        createdAt = json.optLong("createdAt", file.lastModified())
    )
}
