package com.synthbyte.scanmate.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.synthbyte.scanmate.domain.GeminiModels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val storageValue: String, val label: String, val description: String) {
    SYSTEM("system", "System / Device", "Follow the phone theme automatically"),
    LIGHT("light", "Light", "Always use the light theme"),
    DARK("dark", "Dark", "Always use the dark theme");

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

@Singleton
@Suppress("DEPRECATION")
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val fallbackPrefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences("scanmate_safe_settings_fallback", Context.MODE_PRIVATE)
    }

    private val securePrefs: SharedPreferences? by lazy {
        runCatching {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "scanmate_secure_prefs",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL_ID = stringPreferencesKey("gemini_model_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEFAULT_WORKSPACE = stringPreferencesKey("default_workspace")
    }

    val geminiApiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            readApiKey()
                ?: preferences[GEMINI_API_KEY]?.also { legacy ->
                    writeApiKey(legacy)
                    repositoryScope.launch { context.dataStore.edit { it.remove(GEMINI_API_KEY) } }
                }
        }
        .catch { emit(readApiKey().orEmpty()) }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .map { preferences -> ThemeMode.fromStorage(preferences[THEME_MODE]) }
        .catch { emit(ThemeMode.SYSTEM) }

    val geminiModelIdFlow: Flow<String> = context.dataStore.data
        .map { preferences -> GeminiModels.modelIdOrDefault(preferences[GEMINI_MODEL_ID]) }
        .catch { emit(GeminiModels.DEFAULT_MODEL_ID) }

    val onboardingCompleteFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ONBOARDING_COMPLETE] ?: false }
        .catch { emit(false) }

    val defaultWorkspaceFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[DEFAULT_WORKSPACE]?.takeIf { it.isNotBlank() } ?: "Inbox" }
        .catch { emit("Inbox") }

    suspend fun saveApiKey(apiKey: String) {
        writeApiKey(apiKey.trim())
        runCatching { context.dataStore.edit { it.remove(GEMINI_API_KEY) } }
    }

    suspend fun clearApiKey() {
        runCatching { securePrefs?.edit()?.remove("gemini_api_key_secure")?.apply() }
        fallbackPrefs.edit().remove("gemini_api_key_secure").apply()
        runCatching { context.dataStore.edit { it.remove(GEMINI_API_KEY) } }
    }

    suspend fun saveGeminiModel(modelId: String) {
        runCatching { context.dataStore.edit { preferences ->
            preferences[GEMINI_MODEL_ID] = GeminiModels.modelIdOrDefault(modelId)
        } }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        runCatching { context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.storageValue
        } }
    }

    suspend fun setOnboardingComplete(complete: Boolean = true) {
        runCatching { context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = complete
        } }
    }

    suspend fun saveDefaultWorkspace(workspace: String) {
        runCatching { context.dataStore.edit { preferences ->
            preferences[DEFAULT_WORKSPACE] = workspace.trim().ifBlank { "Inbox" }
        } }
    }

    private fun readApiKey(): String? =
        runCatching { securePrefs?.getString("gemini_api_key_secure", null) }.getOrNull()
            ?: fallbackPrefs.getString("gemini_api_key_secure", null)

    private fun writeApiKey(apiKey: String) {
        val savedSecurely = runCatching {
            securePrefs?.edit()?.putString("gemini_api_key_secure", apiKey)?.apply()
            securePrefs != null
        }.getOrDefault(false)
        if (!savedSecurely) {
            fallbackPrefs.edit().putString("gemini_api_key_secure", apiKey).apply()
        }
    }
}
