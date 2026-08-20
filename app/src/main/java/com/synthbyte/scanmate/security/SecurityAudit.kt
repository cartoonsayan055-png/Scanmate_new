package com.synthbyte.scanmate.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.synthbyte.scanmate.utils.EncryptedVaultUtils
import com.synthbyte.scanmate.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SecurityAudit {
    data class Snapshot(
        val biometricStatus: String,
        val encryptionStatus: String,
        val strongBoxStatus: String,
        val vaultItemCount: Int,
        val lastVaultAccess: String
    )

    fun snapshot(context: Context): Snapshot {
        val biometricStatus = when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Biometric / device credential ready"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometric enrolled — device credential fallback available if configured"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware detected"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware temporarily unavailable"
            else -> "Biometric status unknown"
        }
        val vaultDir = FileUtils.appFolder(context, "Vault")
        val vaultFiles = vaultDir?.listFiles { file -> EncryptedVaultUtils.isVaultFile(file) }.orEmpty()
        val lastAccess = context.getSharedPreferences("scanmate_security_audit", Context.MODE_PRIVATE)
            .getLong("last_vault_access", 0L)
        return Snapshot(
            biometricStatus = biometricStatus,
            encryptionStatus = if (EncryptedVaultUtils.isEncryptionReady()) "AES-256-GCM Android Keystore ready" else "Encryption key not available yet",
            strongBoxStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) "StrongBox requested when hardware supports it" else "StrongBox not supported on this Android version",
            vaultItemCount = vaultFiles.size,
            lastVaultAccess = if (lastAccess > 0L) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastAccess))
            } else {
                "Never unlocked on this install"
            }
        )
    }

    fun markVaultAccess(context: Context) {
        context.getSharedPreferences("scanmate_security_audit", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_vault_access", System.currentTimeMillis())
            .apply()
    }
}
