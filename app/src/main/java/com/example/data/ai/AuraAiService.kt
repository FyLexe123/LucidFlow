package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AnalyticsEventRecord
import com.example.data.model.ChatMessageRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.RetrievalConfig
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.ThinkingConfig
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "AuraAiService"
private const val AI_TIMEOUT_MS = 60_000L
private const val MAX_INLINE_BYTES = 18 * 1024 * 1024

enum class ChatModelTier(
    val modelId: String,
    val displayLabel: String,
    val isHighThinking: Boolean = false
) {
    HYDROGEN("gemini-4-hydrogen", "Gemini 4 Hydrogen (Genel)"),
    ARGON("gemini-4-argon", "Gemini 4 Argon (Karmaşık)"),
    CARBON_THINKING("gemini-4-carbon", "Gemini 4 Carbon (Derin Düşünme)", isHighThinking = true),
    FLASH_LITE("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite (Hızlı)")
}

enum class ImageAspectOption(val label: String, val ratio: AspectRatio) {
    RATIO_1_1("1:1", AspectRatio.SQUARE_1x1),
    RATIO_2_3("2:3", AspectRatio.PORTRAIT_2x3),
    RATIO_3_2("3:2", AspectRatio.LANDSCAPE_3x2),
    RATIO_3_4("3:4", AspectRatio.PORTRAIT_3x4),
    RATIO_4_3("4:3", AspectRatio.LANDSCAPE_4x3),
    RATIO_9_16("9:16", AspectRatio.PORTRAIT_9x16),
    RATIO_16_9("16:9", AspectRatio.LANDSCAPE_16x9),
    RATIO_21_9("21:9", AspectRatio.LANDSCAPE_21x9)
}

enum class ImageResolutionOption(val label: String, val size: ImageSize) {
    RES_1K("1K", ImageSize.SIZE_1K),
    RES_2K("2K", ImageSize.SIZE_2K),
    RES_4K("4K", ImageSize.SIZE_4K)
}

data class GroundedSource(
    val title: String,
    val uri: String,
    val placeId: String? = null
)

data class StreamChatChunk(
    val accumulatedText: String,
    val accumulatedThoughts: String,
    val sources: List<GroundedSource>,
    val isComplete: Boolean
)

data class MediaGenerationOutput(
    val bytes: ByteArray,
    val mimeType: String,
    val textNote: String? = null
)

@OptIn(PublicPreviewAPI::class)
class AuraAiService {

    private val aiBackend by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private fun buildChatModel(
        tier: ChatModelTier,
        useSearchGrounding: Boolean,
        useMapsGrounding: Boolean,
        lat: Double,
        lng: Double,
        languageBcp47: String
    ) = run {
        val tools = buildList {
            if (useSearchGrounding) add(Tool.googleSearch())
            if (useMapsGrounding) add(Tool.googleMaps())
        }

        val toolConfig = if (useMapsGrounding) {
            ToolConfig(
                functionCallingConfig = null,
                retrievalConfig = RetrievalConfig.builder()
                    .setLatLng(LatLng(latitude = lat, longitude = lng))
                    .setLanguageCode(languageBcp47)
                    .build()
            )
        } else null

        val genConfig = if (tier.isHighThinking) {
            // Do NOT set maxOutputTokens when ThinkingLevel.HIGH is configured.
            generationConfig {
                thinkingConfig = ThinkingConfig.Builder()
                    .setThinkingLevel(ThinkingLevel.HIGH)
                    .setIncludeThoughts(true)
                    .build()
            }
        } else {
            generationConfig {
                temperature = 0.7f
            }
        }

        val effectiveModelName = if (useSearchGrounding || useMapsGrounding) {
            ChatModelTier.HYDROGEN.modelId
        } else {
            tier.modelId
        }

        aiBackend.generativeModel(
            modelName = effectiveModelName,
            generationConfig = genConfig,
            tools = tools.ifEmpty { null },
            toolConfig = toolConfig,
            systemInstruction = content {
                text(
                    "You are AuraGlass Intelligence, a concise, privacy-first, multilingual AI workspace assistant. " +
                        "Respond naturally in the user's language (locale: $languageBcp47) with structured clarity."
                )
            }
        )
    }

    fun streamChatMessage(
        prompt: String,
        historyRecords: List<ChatMessageRecord>,
        tier: ChatModelTier,
        useSearchGrounding: Boolean,
        useMapsGrounding: Boolean,
        lat: Double,
        lng: Double,
        languageBcp47: String
    ): Flow<StreamChatChunk> = flow {
        val model = buildChatModel(
            tier = tier,
            useSearchGrounding = useSearchGrounding,
            useMapsGrounding = useMapsGrounding,
            lat = lat,
            lng = lng,
            languageBcp47 = languageBcp47
        )

        val history: List<Content> = historyRecords.takeLast(16).map { record ->
            content(role = if (record.role == "model") "model" else "user") {
                text(record.content)
            }
        }

        val chat = model.startChat(history = history)
        val textBuilder = StringBuilder()
        val thoughtsBuilder = StringBuilder()
        val collectedSources = linkedMapOf<String, GroundedSource>()

        withTimeout(AI_TIMEOUT_MS) {
            chat.sendMessageStream(prompt).collect { chunk ->
                chunk.thoughtSummary?.let { thought ->
                    if (thought.isNotEmpty()) {
                        thoughtsBuilder.append(thought)
                    }
                }
                chunk.text?.let { partText ->
                    textBuilder.append(partText)
                }
                chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                    gChunk.web?.let { web ->
                        val uri = web.uri ?: return@let
                        val title = web.title ?: web.domain ?: uri
                        collectedSources[uri] = GroundedSource(title = title, uri = uri)
                    }
                    gChunk.maps?.let { maps ->
                        val uri = maps.uri ?: return@let
                        val title = maps.title ?: "Google Maps Place"
                        collectedSources[uri] = GroundedSource(
                            title = title,
                            uri = uri,
                            placeId = maps.placeId
                        )
                    }
                }
                emit(
                    StreamChatChunk(
                        accumulatedText = textBuilder.toString(),
                        accumulatedThoughts = thoughtsBuilder.toString(),
                        sources = collectedSources.values.toList(),
                        isComplete = false
                    )
                )
            }
        }

        emit(
            StreamChatChunk(
                accumulatedText = textBuilder.toString(),
                accumulatedThoughts = thoughtsBuilder.toString(),
                sources = collectedSources.values.toList(),
                isComplete = true
            )
        )
    }

    suspend fun searchMapsPlaces(
        query: String,
        lat: Double,
        lng: Double,
        languageBcp47: String
    ): Pair<String, List<GroundedSource>> {
        val model = aiBackend.generativeModel(
            modelName = ChatModelTier.HYDROGEN.modelId,
            tools = listOf(Tool.googleMaps()),
            toolConfig = ToolConfig(
                functionCallingConfig = null,
                retrievalConfig = RetrievalConfig.builder()
                    .setLatLng(LatLng(latitude = lat, longitude = lng))
                    .setLanguageCode(languageBcp47)
                    .build()
            )
        )
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(query)
        }
        val sources = mutableListOf<GroundedSource>()
        response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { chunk ->
            chunk.maps?.let { maps ->
                val uri = maps.uri ?: "https://maps.google.com"
                sources += GroundedSource(
                    title = maps.title ?: "Place",
                    uri = uri,
                    placeId = maps.placeId
                )
            }
        }
        val text = response.text.orEmpty().ifBlank { "No Maps summary returned." }
        return text to sources
    }

    suspend fun generateOrEditImage(
        prompt: String,
        highQualityPro: Boolean,
        aspectOption: ImageAspectOption,
        resolutionOption: ImageResolutionOption,
        sourceBitmap: Bitmap?
    ): Bitmap {
        val modelName = if (highQualityPro) "gemini-3-pro-image-preview" else "gemini-nano-banana-2.1"
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE, ResponseModality.TEXT)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectOption.ratio)
                .apply {
                    if (highQualityPro) {
                        setImageSize(resolutionOption.size)
                    }
                }
                .build()
        }
        val model = aiBackend.generativeModel(
            modelName = modelName,
            generationConfig = config
        )

        val response = withTimeout(AI_TIMEOUT_MS) {
            if (sourceBitmap != null) {
                model.generateContent(
                    content {
                        image(sourceBitmap)
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("Model ($modelName) did not return an image.")
    }

    suspend fun generateSpeechTts(
        text: String,
        languageBcp47: String,
        voiceName: String = "Kore"
    ): MediaGenerationOutput {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
            speechConfig = SpeechConfig(
                voice = Voice(voiceName),
                languageCode = languageBcp47
            )
        }
        val model = aiBackend.generativeModel(
            modelName = "gemini-3.8-flash-tts",
            generationConfig = config
        )
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(text)
        }
        val part = response.inlineDataParts.firstOrNull()
            ?: response.candidates.firstOrNull()?.content?.parts?.filterIsInstance<InlineDataPart>()?.firstOrNull()
            ?: throw IllegalStateException("TTS model returned no audio data.")

        return MediaGenerationOutput(
            bytes = part.inlineData,
            mimeType = part.mimeType
        )
    }

    suspend fun generateMusic(
        prompt: String,
        fullLengthPro: Boolean
    ): MediaGenerationOutput {
        val modelName = if (fullLengthPro) "lyria-3-pro-preview" else "lyria-3-clip-preview"
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
        }
        val model = aiBackend.generativeModel(
            modelName = modelName,
            generationConfig = config
        )
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(prompt)
        }
        val part = response.inlineDataParts.firstOrNull()
            ?: response.candidates.firstOrNull()?.content?.parts?.filterIsInstance<InlineDataPart>()?.firstOrNull()
            ?: throw IllegalStateException("Music model ($modelName) returned no audio payload.")

        return MediaGenerationOutput(
            bytes = part.inlineData,
            mimeType = part.mimeType,
            textNote = response.text
        )
    }

    suspend fun generateVideo(
        prompt: String,
        landscape16x9: Boolean,
        sourceBitmap: Bitmap?
    ): MediaGenerationOutput {
        val ratioLabel = if (landscape16x9) "16:9" else "9:16"
        val model = aiBackend.generativeModel(
            modelName = "gemini-omni-1.2-flash",
            generationConfig = generationConfig {
                temperature = 0.7f
            }
        )
        val fullPrompt = "Generate a short video in $ratioLabel aspect ratio: $prompt"
        val response = withTimeout(AI_TIMEOUT_MS) {
            if (sourceBitmap != null) {
                model.generateContent(
                    content {
                        image(sourceBitmap)
                        text(fullPrompt)
                    }
                )
            } else {
                model.generateContent(fullPrompt)
            }
        }
        val videoPart = response.inlineDataParts.firstOrNull()
            ?: response.candidates.firstOrNull()?.content?.parts?.filterIsInstance<InlineDataPart>()?.firstOrNull()
            ?: throw IllegalStateException(
                response.text?.takeIf { it.isNotBlank() }
                    ?: "Video generation model (gemini-omni-1.2-flash) returned no video bytes."
            )

        return MediaGenerationOutput(
            bytes = videoPart.inlineData,
            mimeType = videoPart.mimeType,
            textNote = response.text
        )
    }

    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
        val model = aiBackend.generativeModel("gemini-4-argon")
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
        }
        return response.text ?: throw IllegalStateException("Empty image analysis response.")
    }

    suspend fun analyzeVideo(videoBytes: ByteArray, mimeType: String, prompt: String): String {
        require(videoBytes.size <= MAX_INLINE_BYTES) {
            "Video exceeds 18MB inline size limit."
        }
        val model = aiBackend.generativeModel("gemini-4-argon")
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(
                content {
                    inlineData(videoBytes, mimeType)
                    text(prompt)
                }
            )
        }
        return response.text ?: throw IllegalStateException("Empty video analysis response.")
    }

    suspend fun transcribeAudio(wavBytes: ByteArray, languageBcp47: String): String {
        require(wavBytes.size <= MAX_INLINE_BYTES) {
            "Audio recording exceeds 18MB inline limit."
        }
        val model = aiBackend.generativeModel("gemini-3.5-transcribe")
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(
                content {
                    inlineData(wavBytes, "audio/wav")
                    text("Transcribe this audio accurately in language $languageBcp47.")
                }
            )
        }
        return response.text ?: throw IllegalStateException("Empty transcription response.")
    }

    suspend fun startLiveVoiceSession(languageBcp47: String): LiveSession {
        val liveModel: LiveGenerativeModel = aiBackend.liveModel(
            modelName = "gemini-3.8-live",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(
                    voice = Voice("Kore"),
                    languageCode = languageBcp47
                )
            },
            systemInstruction = content {
                text("You are AuraGlass Live Voice Assistant. Respond concisely in $languageBcp47.")
            }
        )
        val session = withTimeout(AI_TIMEOUT_MS) {
            liveModel.connect()
        }
        session.startAudioConversation()
        return session
    }

    suspend fun generateAnalyticsReport(
        events: List<AnalyticsEventRecord>,
        vaultCount: Int,
        encryptedCount: Int,
        pendingOfflineCount: Int,
        languageBcp47: String
    ): String {
        val model = aiBackend.generativeModel("gemini-4-argon")
        val avgLatency = if (events.isEmpty()) 0L else events.map { it.latencyMs }.average().toLong()
        val byFeature = events.groupingBy { it.featureType }.eachCount()
        val summaryPrompt = """
            Generate a concise, executive data analytics report in language $languageBcp47 based on these real user telemetry metrics:
            - Total recorded AI & Vault events: ${events.size}
            - Average response latency: ${avgLatency} ms
            - Vault items stored: $vaultCount (E2EE AES-256-GCM encrypted: $encryptedCount)
            - Pending offline sync queue: $pendingOfflineCount
            - Feature distribution: $byFeature
            Provide 3 key insights, security posture status, and 2 actionable optimization recommendations.
        """.trimIndent()

        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(summaryPrompt)
        }
        return response.text ?: throw IllegalStateException("Analytics report generation returned empty text.")
    }
}
