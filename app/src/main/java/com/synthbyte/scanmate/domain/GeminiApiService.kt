package com.synthbyte.scanmate.domain

import com.synthbyte.scanmate.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

// --- Common Data Classes ---
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseModalities: List<String>? = null
)

data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null
)

data class GeminiError(
    val code: Int?,
    val message: String?,
    val status: String?
)

data class Candidate(
    val content: Content
)

data class GeminiTextResult(
    val text: String,
    val isSuccess: Boolean,
    val statusCode: Int? = null
)

// --- Retrofit Setup ---
interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateContentRequest
    ): Response<GenerateContentResponse>
}

private object GeminiCertificatePins {
    private const val HOST = "generativelanguage.googleapis.com"

    fun create(): CertificatePinner {
        val configuredPins = BuildConfig.GEMINI_CERT_PINS
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.startsWith("sha256/") }

        if (configuredPins.isEmpty()) return CertificatePinner.DEFAULT

        return CertificatePinner.Builder().apply {
            configuredPins.forEach { pin -> add(HOST, pin) }
        }.build()
    }
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .certificatePinner(GeminiCertificatePins.create())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) addDebugLoggingInterceptor()
        }
        .build()

    private fun OkHttpClient.Builder.addDebugLoggingInterceptor() {
        runCatching {
            val interceptorClass = Class.forName("okhttp3.logging.HttpLoggingInterceptor")
            val levelClass = Class.forName("okhttp3.logging.HttpLoggingInterceptor\$Level")
            val interceptor = interceptorClass.getDeclaredConstructor().newInstance()
            val basicLevel = levelClass.enumConstants.firstOrNull { (it as? Enum<*>)?.name == "BASIC" } ?: return@runCatching
            interceptorClass.getMethod("setLevel", levelClass).invoke(interceptor, basicLevel)
            addInterceptor(interceptor as okhttp3.Interceptor)
        }
    }

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

class GeminiHelper(private val apiKey: String) {
    suspend fun generateContent(
        prompt: String,
        modelId: String = GeminiModels.DEFAULT_MODEL_ID
    ): GeminiTextResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanPrompt = prompt.trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiTextResult("Add Gemini API key in Settings to use AI features.", false)
        }
        if (cleanPrompt.isBlank()) {
            return@withContext GeminiTextResult("Paste OCR text or type a prompt first.", false)
        }

        val safeModelId = GeminiModels.modelIdOrDefault(modelId)
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = cleanPrompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.35f,
                topP = 0.9f
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(safeModelId, cleanKey, request)
            if (!response.isSuccessful) {
                val error = parseError(response)
                return@withContext GeminiTextResult(
                    text = friendlyGeminiError(response.code(), error?.message),
                    isSuccess = false,
                    statusCode = response.code()
                )
            }

            val body = response.body()
            val bodyError = body?.error
            if (bodyError != null) {
                val code = bodyError.code ?: response.code()
                return@withContext GeminiTextResult(
                    text = friendlyGeminiError(code, bodyError.message),
                    isSuccess = false,
                    statusCode = code
                )
            }

            val output = body?.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("\n")
                ?.trim()

            GeminiTextResult(
                text = output.takeUnless { it.isNullOrBlank() } ?: "Gemini returned an empty response. Try shorter text or another model.",
                isSuccess = !output.isNullOrBlank(),
                statusCode = response.code()
            )
        } catch (_: SocketTimeoutException) {
            GeminiTextResult("AI request timed out. Try again in a moment.", false)
        } catch (_: IOException) {
            GeminiTextResult("AI needs internet. Offline tools still work.", false)
        } catch (e: Exception) {
            GeminiTextResult("AI request failed. Check your internet connection and API key, then try again. Details: ${e.localizedMessage ?: e::class.java.simpleName}", false)
        }
    }

    suspend fun testConnection(modelId: String): GeminiTextResult = generateContent(
        prompt = "Reply with exactly: ScanMate AI test OK",
        modelId = modelId
    )

    private fun parseError(response: Response<GenerateContentResponse>): GeminiError? {
        return runCatching {
            val json = response.errorBody()?.string().orEmpty()
            RetrofitClient.moshi.adapter(GenerateContentResponse::class.java).fromJson(json)?.error
        }.getOrNull()
    }

    private fun friendlyGeminiError(statusCode: Int, rawMessage: String?): String {
        return when (statusCode) {
            400 -> "The AI request was invalid. Try shorter text or check the selected Gemini model in Settings."
            401 -> "Gemini API key is invalid. Check the key in Settings."
            403 -> "Gemini API access is blocked for this key or project. Check API permissions, billing, or regional availability."
            404 -> "This Gemini model is not available for your API key. Select another model in Settings."
            429 -> "Gemini rate limit reached. Try again later or select another available model in Settings."
            500, 502, 503, 504 -> "Gemini service is temporarily unavailable. Try again later."
            else -> "AI request failed with HTTP $statusCode.${rawMessage?.let { " Details: $it" } ?: ""}"
        }
    }
}
