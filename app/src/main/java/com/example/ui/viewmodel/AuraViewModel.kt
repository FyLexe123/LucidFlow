package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.AuraAiService
import com.example.data.ai.ChatModelTier
import com.example.data.ai.GroundedSource
import com.example.data.ai.ImageAspectOption
import com.example.data.ai.ImageResolutionOption
import com.example.data.local.AuraLocalDatabase
import com.example.data.local.LocalAppPreferences
import com.example.data.local.OfflineVaultEntity
import com.example.data.local.UserPreferencesStore
import com.example.data.model.AnalyticsEventRecord
import com.example.data.model.ChatMessageRecord
import com.example.data.model.UserProfile
import com.example.data.model.VaultItem
import com.example.data.repository.AuraRepository
import com.example.security.E2eeCryptoManager
import com.example.ui.i18n.AppLanguage
import com.example.util.AudioPcmHelper
import com.example.util.AuraNotificationManager
import com.example.util.ConnectivityMonitor
import com.example.util.NotificationCategory
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.SecretKey

private const val TAG = "AuraViewModel"

sealed interface StreamUiState {
    data object Idle : StreamUiState
    data object Loading : StreamUiState
    data class Streaming(val text: String, val thoughts: String, val sources: List<GroundedSource>) : StreamUiState
    data class Error(val message: String) : StreamUiState
}

sealed interface StudioResultState {
    data object Idle : StudioResultState
    data object Loading : StudioResultState
    data class ImageReady(val bitmap: Bitmap, val modelName: String) : StudioResultState
    data class TextReady(val text: String, val modelName: String) : StudioResultState
    data class AudioReady(val mimeType: String, val byteCount: Int, val modelName: String) : StudioResultState
    data class VideoReady(val filePath: String, val mimeType: String, val note: String?, val modelName: String) : StudioResultState
    data class Error(val message: String) : StudioResultState
}

data class MapsUiState(
    val isLoading: Boolean = false,
    val summary: String = "",
    val places: List<GroundedSource> = emptyList(),
    val error: String? = null,
    val userLat: Double = 41.0082,
    val userLng: Double = 28.9784
)

@OptIn(PublicPreviewAPI::class)
class AuraViewModel(
    private val appContext: Context,
    private val repository: AuraRepository,
    private val prefsStore: UserPreferencesStore,
    private val connectivityMonitor: ConnectivityMonitor,
    private val aiService: AuraAiService,
    val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    val localPrefs: StateFlow<LocalAppPreferences> = prefsStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalAppPreferences())

    private val networkOnline: StateFlow<Boolean> = connectivityMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), connectivityMonitor.isCurrentlyOnline())

    val isEffectiveOffline: StateFlow<Boolean> = combine(networkOnline, localPrefs) { online, prefs ->
        !online || prefs.offlineModeManual
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile()
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Profile observation error", fsErr ?: error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val vaultItems: StateFlow<List<VaultItem>> = repository.observeVaultItems()
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Vault observation error", fsErr ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val offlineCachedVault: StateFlow<List<OfflineVaultEntity>> =
        (repository.observeOfflineCachedVaultItems(currentUserId)
            ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val chatMessages: StateFlow<List<ChatMessageRecord>> = repository.observeChatMessages()
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Chat observation error", fsErr ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val analyticsEvents: StateFlow<List<AnalyticsEventRecord>> = repository.observeAnalyticsEvents()
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Analytics observation error", fsErr ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // --- E2EE Key Management ---
    private var activeSaltBase64: String = E2eeCryptoManager.generateSaltBase64()
    private val _activeKey = MutableStateFlow<SecretKey?>(null)
    private val _keyFingerprint = MutableStateFlow("")
    val keyFingerprint: StateFlow<String> = _keyFingerprint.asStateFlow()

    private val _decryptedMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val decryptedMap: StateFlow<Map<String, String>> = _decryptedMap.asStateFlow()

    // --- Chat State ---
    private val _chatStreamState = MutableStateFlow<StreamUiState>(StreamUiState.Idle)
    val chatStreamState: StateFlow<StreamUiState> = _chatStreamState.asStateFlow()

    private val _selectedModel = MutableStateFlow(ChatModelTier.HYDROGEN)
    val selectedModel: StateFlow<ChatModelTier> = _selectedModel.asStateFlow()

    private val _searchGrounding = MutableStateFlow(false)
    val searchGrounding: StateFlow<Boolean> = _searchGrounding.asStateFlow()

    private val _mapsGrounding = MutableStateFlow(false)
    val mapsGrounding: StateFlow<Boolean> = _mapsGrounding.asStateFlow()

    // --- Studio State ---
    private val _studioState = MutableStateFlow<StudioResultState>(StudioResultState.Idle)
    val studioState: StateFlow<StudioResultState> = _studioState.asStateFlow()

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()
    private var recordJob: Job? = null

    private val _isLiveCallActive = MutableStateFlow(false)
    val isLiveCallActive: StateFlow<Boolean> = _isLiveCallActive.asStateFlow()
    private var liveSession: LiveSession? = null

    // --- Maps State ---
    private val _mapsState = MutableStateFlow(MapsUiState())
    val mapsState: StateFlow<MapsUiState> = _mapsState.asStateFlow()

    // --- Analytics AI Report State ---
    private val _analyticsReportState = MutableStateFlow<StudioResultState>(StudioResultState.Idle)
    val analyticsReportState: StateFlow<StudioResultState> = _analyticsReportState.asStateFlow()

    // --- Transient Snack/Banner Message ---
    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultKey = E2eeCryptoManager.deriveDefaultSessionKey(currentUserId, activeSaltBase64)
            _activeKey.value = defaultKey
            _keyFingerprint.value = E2eeCryptoManager.fingerprint(defaultKey)

            val result = repository.ensureUserProfile(
                displayName = initialDisplayName,
                defaultSaltBase64 = activeSaltBase64,
                defaultFingerprint = _keyFingerprint.value
            )
            result.getOrNull()?.let { profile ->
                profile.e2eeSaltBase64?.takeIf { it.isNotBlank() }?.let { cloudSalt ->
                    activeSaltBase64 = cloudSalt
                    val syncedKey = E2eeCryptoManager.deriveDefaultSessionKey(currentUserId, cloudSalt)
                    _activeKey.value = syncedKey
                    _keyFingerprint.value = E2eeCryptoManager.fingerprint(syncedKey)
                }
                prefsStore.setLanguage(profile.preferredLanguage)
                prefsStore.setDarkMode(profile.darkMode)
            }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun applyCustomPassphrase(passphrase: String) {
        if (passphrase.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            val newKey = E2eeCryptoManager.deriveKey(passphrase, activeSaltBase64)
            val fp = E2eeCryptoManager.fingerprint(newKey)
            _activeKey.value = newKey
            _keyFingerprint.value = fp
            _decryptedMap.value = emptyMap()
            repository.updateUserSettings(mapOf("e2eeKeyFingerprint" to fp))
            _bannerMessage.value = "E2EE Key updated ($fp)"
        }
    }

    fun createEncryptedVaultItem(title: String, plaintext: String, category: String) {
        val key = _activeKey.value ?: return
        viewModelScope.launch {
            val encrypted = withContext(Dispatchers.Default) {
                E2eeCryptoManager.encrypt(plaintext, key)
            }
            val offline = isEffectiveOffline.value
            val res = repository.createVaultItem(
                title = title.trim().ifBlank { "Untitled" },
                cipherTextBase64 = encrypted.cipherTextBase64,
                ivBase64 = encrypted.ivBase64,
                authTagHash = encrypted.authTagHash,
                category = category,
                isEncrypted = true,
                isPinned = false,
                isOfflineMode = offline
            )
            if (res.isSuccess) {
                val createdId = res.getOrThrow()
                _decryptedMap.value = _decryptedMap.value + (createdId to plaintext)
                if (!offline) {
                    repository.logAnalyticsEvent(
                        featureType = "vault_e2ee",
                        modelUsed = "AES-256-GCM",
                        latencyMs = 12L,
                        tokenEstimate = plaintext.length.toLong(),
                        isOfflineQueued = false,
                        isEncrypted = true
                    )
                }
                maybeSendNotification(
                    NotificationCategory.SYNC_SECURITY,
                    "E2EE Vault",
                    if (offline) "Saved locally in offline encrypted queue." else "Encrypted and synced to cloud."
                )
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save vault item"
            }
        }
    }

    fun toggleDecryptVaultItem(itemId: String, cipherTextBase64: String, ivBase64: String) {
        val current = _decryptedMap.value
        if (current.containsKey(itemId)) {
            _decryptedMap.value = current - itemId
            return
        }
        val key = _activeKey.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val result = E2eeCryptoManager.decrypt(cipherTextBase64, ivBase64, key)
            if (result.isSuccess) {
                _decryptedMap.value = _decryptedMap.value + (itemId to result.getOrThrow())
            } else {
                _bannerMessage.value = "Decryption failed: check your E2EE passphrase."
            }
        }
    }

    fun togglePinItem(item: VaultItem) {
        viewModelScope.launch {
            repository.togglePinVaultItem(item)
        }
    }

    fun deleteVaultItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteVaultItem(itemId)
        }
    }

    fun syncPendingOfflineItems() {
        viewModelScope.launch {
            val res = repository.syncPendingOfflineVaultItems()
            if (res.isSuccess) {
                val count = res.getOrThrow()
                _bannerMessage.value = "Synced $count offline record(s) to cloud."
                maybeSendNotification(
                    NotificationCategory.SYNC_SECURITY,
                    "Cloud Sync",
                    "$count encrypted item(s) synced."
                )
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Sync failed."
            }
        }
    }

    // --- Settings & Preferences ---

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            prefsStore.setLanguage(lang.code)
            if (!isEffectiveOffline.value) {
                repository.updateUserSettings(mapOf("preferredLanguage" to lang.code))
            }
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch {
            prefsStore.setDarkMode(mode)
            if (!isEffectiveOffline.value) {
                repository.updateUserSettings(mapOf("darkMode" to mode))
            }
        }
    }

    fun setOfflineModeManual(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setOfflineManual(enabled)
            if (!enabled && networkOnline.value) {
                syncPendingOfflineItems()
            }
        }
    }

    fun updateNotifications(
        master: Boolean,
        digest: Boolean,
        sync: Boolean,
        ai: Boolean,
        quietStart: Int,
        quietEnd: Int
    ) {
        viewModelScope.launch {
            prefsStore.updateNotificationPrefs(master, digest, sync, ai, quietStart, quietEnd)
            if (!isEffectiveOffline.value) {
                repository.updateUserSettings(
                    mapOf(
                        "notificationsEnabled" to master,
                        "notifyDailyDigest" to digest,
                        "notifySyncAlerts" to sync,
                        "notifyAiInsights" to ai,
                        "quietHoursStart" to quietStart.toLong(),
                        "quietHoursEnd" to quietEnd.toLong()
                    )
                )
            }
        }
    }

    fun sendTestNotification() {
        val prefs = localPrefs.value
        val sent = AuraNotificationManager.sendNotification(
            context = appContext,
            category = NotificationCategory.AI_INSIGHT,
            title = "AuraGlass",
            body = "Notifications active • E2EE AES-256-GCM",
            respectQuietHours = false,
            quietStart = prefs.quietStartHour,
            quietEnd = prefs.quietEndHour
        )
        _bannerMessage.value = if (sent) "Notification dispatched." else "Notification permission required."
    }

    private fun maybeSendNotification(category: NotificationCategory, title: String, body: String) {
        val prefs = localPrefs.value
        if (!prefs.notificationsEnabled) return
        val allowed = when (category) {
            NotificationCategory.DIGEST -> prefs.notifyDigest
            NotificationCategory.SYNC_SECURITY -> prefs.notifySync
            NotificationCategory.AI_INSIGHT -> prefs.notifyAi
        }
        if (!allowed) return
        AuraNotificationManager.sendNotification(
            context = appContext,
            category = category,
            title = title,
            body = body,
            respectQuietHours = true,
            quietStart = prefs.quietStartHour,
            quietEnd = prefs.quietEndHour
        )
    }

    // --- Chat Actions ---

    fun selectModel(tier: ChatModelTier) {
        _selectedModel.value = tier
    }

    fun toggleSearchGrounding(enabled: Boolean) {
        _searchGrounding.value = enabled
    }

    fun toggleMapsGrounding(enabled: Boolean) {
        _mapsGrounding.value = enabled
    }

    fun sendChatMessage(prompt: String) {
        if (prompt.isBlank()) return
        if (isEffectiveOffline.value) {
            _chatStreamState.value = StreamUiState.Error("Offline mode is active. Disable offline mode for cloud Gemini inference.")
            return
        }
        val lang = AppLanguage.fromCode(localPrefs.value.languageCode)
        val tier = _selectedModel.value
        val useSearch = _searchGrounding.value
        val useMaps = _mapsGrounding.value
        val coords = _mapsState.value
        val historySnapshot = chatMessages.value

        viewModelScope.launch {
            _chatStreamState.value = StreamUiState.Loading
            val startTime = System.currentTimeMillis()
            repository.saveChatMessage(
                role = "user",
                content = prompt,
                modelName = tier.modelId,
                latencyMs = 0L
            )

            try {
                var finalText = ""
                var finalThoughts = ""
                var finalSources = emptyList<GroundedSource>()

                aiService.streamChatMessage(
                    prompt = prompt,
                    historyRecords = historySnapshot,
                    tier = tier,
                    useSearchGrounding = useSearch,
                    useMapsGrounding = useMaps,
                    lat = coords.userLat,
                    lng = coords.userLng,
                    languageBcp47 = lang.bcp47
                ).collect { chunk ->
                    finalText = chunk.accumulatedText
                    finalThoughts = chunk.accumulatedThoughts
                    finalSources = chunk.sources
                    if (!chunk.isComplete) {
                        _chatStreamState.value = StreamUiState.Streaming(
                            text = finalText,
                            thoughts = finalThoughts,
                            sources = finalSources
                        )
                    }
                }

                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                if (finalText.isBlank()) {
                    _chatStreamState.value = StreamUiState.Error("Model returned an empty response.")
                } else {
                    val effectiveModel = if (useSearch || useMaps) ChatModelTier.HYDROGEN.modelId else tier.modelId
                    repository.saveChatMessage(
                        role = "model",
                        content = finalText,
                        modelName = effectiveModel,
                        thoughtSummary = finalThoughts.ifBlank { null },
                        groundingSources = finalSources.map { "${it.title}|${it.uri}" }.ifEmpty { null },
                        latencyMs = elapsed
                    )
                    val feat = when {
                        useMaps -> "maps"
                        useSearch -> "search"
                        else -> "chat"
                    }
                    repository.logAnalyticsEvent(
                        featureType = feat,
                        modelUsed = effectiveModel,
                        latencyMs = elapsed,
                        tokenEstimate = (prompt.length + finalText.length).toLong() / 4L,
                        isOfflineQueued = false,
                        isEncrypted = false
                    )
                    _chatStreamState.value = StreamUiState.Idle
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat generation timed out", e)
                _chatStreamState.value = StreamUiState.Error("Request timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                _chatStreamState.value = StreamUiState.Error(e.localizedMessage ?: "Chat generation failed.")
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val ids = chatMessages.value.map { it.id }.filter { it.isNotBlank() }
            if (ids.isNotEmpty()) {
                repository.clearChatHistory(ids)
            }
        }
    }

    // --- Studio Actions ---

    fun generateOrEditImage(
        prompt: String,
        highQualityPro: Boolean,
        aspectOption: ImageAspectOption,
        resolutionOption: ImageResolutionOption,
        sourceBitmap: Bitmap?
    ) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _studioState.value = StudioResultState.Loading
            val start = System.currentTimeMillis()
            val modelName = if (highQualityPro) "gemini-3-pro-image-preview" else "gemini-nano-banana-2.1"
            try {
                val bmp = aiService.generateOrEditImage(
                    prompt = prompt,
                    highQualityPro = highQualityPro,
                    aspectOption = aspectOption,
                    resolutionOption = resolutionOption,
                    sourceBitmap = sourceBitmap
                )
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _studioState.value = StudioResultState.ImageReady(bmp, modelName)
                repository.logAnalyticsEvent("image", modelName, elapsed, 256L, false, false)
                maybeSendNotification(NotificationCategory.AI_INSIGHT, "Studio", "Image ready ($modelName)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _studioState.value = StudioResultState.Error(e.localizedMessage ?: "Image generation failed.")
            }
        }
    }

    fun synthesizeSpeech(text: String) {
        if (text.isBlank()) return
        val lang = AppLanguage.fromCode(localPrefs.value.languageCode)
        viewModelScope.launch {
            _studioState.value = StudioResultState.Loading
            val start = System.currentTimeMillis()
            try {
                val out = aiService.generateSpeechTts(text, lang.bcp47)
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _studioState.value = StudioResultState.AudioReady(out.mimeType, out.bytes.size, "gemini-3.8-flash-tts")
                repository.logAnalyticsEvent("tts", "gemini-3.8-flash-tts", elapsed, text.length.toLong(), false, false)
                AudioPcmHelper.playPcm24kMono(out.bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed", e)
                _studioState.value = StudioResultState.Error(e.localizedMessage ?: "TTS failed.")
            }
        }
    }

    fun generateMusic(prompt: String, fullLengthPro: Boolean) {
        if (prompt.isBlank()) return
        val modelName = if (fullLengthPro) "lyria-3-pro-preview" else "lyria-3-clip-preview"
        viewModelScope.launch {
            _studioState.value = StudioResultState.Loading
            val start = System.currentTimeMillis()
            try {
                val out = aiService.generateMusic(prompt, fullLengthPro)
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _studioState.value = StudioResultState.AudioReady(out.mimeType, out.bytes.size, modelName)
                repository.logAnalyticsEvent("music", modelName, elapsed, 512L, false, false)
                AudioPcmHelper.playPcm24kMono(out.bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Music generation failed", e)
                _studioState.value = StudioResultState.Error(e.localizedMessage ?: "Music generation failed.")
            }
        }
    }

    fun generateVideo(prompt: String, landscape16x9: Boolean, sourceBitmap: Bitmap?) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _studioState.value = StudioResultState.Loading
            val start = System.currentTimeMillis()
            try {
                val out = aiService.generateVideo(prompt, landscape16x9, sourceBitmap)
                val file = withContext(Dispatchers.IO) {
                    val outFile = File(appContext.cacheDir, "aura_gen_${System.currentTimeMillis()}.mp4")
                    outFile.writeBytes(out.bytes)
                    outFile
                }
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _studioState.value = StudioResultState.VideoReady(file.absolutePath, out.mimeType, out.textNote, "gemini-omni-1.2-flash")
                repository.logAnalyticsEvent("video", "gemini-omni-1.2-flash", elapsed, 1024L, false, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video generation failed", e)
                _studioState.value = StudioResultState.Error(e.localizedMessage ?: "Video generation failed.")
            }
        }
    }

    fun analyzeSelectedImage(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _studioState.value = StudioResultState.Loading
            val start = System.currentTimeMillis()
            try {
                val text = aiService.analyzeImage(bitmap, prompt.ifBlank { "Describe and extract key details from this image." })
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _studioState.value = StudioResultState.TextReady(text, "gemini-4-argon")
                repository.logAnalyticsEvent("image", "gemini-4-argon", elapsed, text.length.toLong() / 4L, false, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image analysis failed", e)
                _studioState.value = StudioResultState.Error(e.localizedMessage ?: "Image analysis failed.")
            }
        }
    }

    fun analyzeSelectedVideo(uri: Uri, prompt: String) {
        viewModelScope.launch {
            _studioState.value = StudioResultState.Loading
            val start = System.currentTimeMillis()
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Unable to open selected video.")
                }
                val mime = appContext.contentResolver.getType(uri) ?: "video/mp4"
                val text = aiService.analyzeVideo(bytes, mime, prompt.ifBlank { "Summarize key events and spoken information in this video." })
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _studioState.value = StudioResultState.TextReady(text, "gemini-4-argon")
                repository.logAnalyticsEvent("video", "gemini-4-argon", elapsed, text.length.toLong() / 4L, false, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video analysis failed", e)
                _studioState.value = StudioResultState.Error(e.localizedMessage ?: "Video analysis failed.")
            }
        }
    }

    fun toggleAudioRecordingForTranscription() {
        if (_isRecordingAudio.value) {
            _isRecordingAudio.value = false
            return
        }
        val lang = AppLanguage.fromCode(localPrefs.value.languageCode)
        _isRecordingAudio.value = true
        recordJob = viewModelScope.launch {
            try {
                val wavBytes = AudioPcmHelper.recordWavAudio(isRecordingProvider = { _isRecordingAudio.value })
                _studioState.value = StudioResultState.Loading
                val start = System.currentTimeMillis()
                val transcript = aiService.transcribeAudio(wavBytes, lang.bcp47)
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _studioState.value = StudioResultState.TextReady(transcript, "gemini-3.5-transcribe")
                repository.logAnalyticsEvent("transcribe", "gemini-3.5-transcribe", elapsed, transcript.length.toLong() / 4L, false, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio transcription failed", e)
                _studioState.value = StudioResultState.Error(e.localizedMessage ?: "Audio transcription failed.")
            } finally {
                _isRecordingAudio.value = false
            }
        }
    }

    fun toggleLiveVoiceCall() {
        if (_isLiveCallActive.value) {
            viewModelScope.launch {
                runCatching {
                    liveSession?.stopAudioConversation()
                    liveSession?.close()
                }
                liveSession = null
                _isLiveCallActive.value = false
            }
            return
        }
        val lang = AppLanguage.fromCode(localPrefs.value.languageCode)
        viewModelScope.launch {
            _studioState.value = StudioResultState.Loading
            try {
                val session = aiService.startLiveVoiceSession(lang.bcp47)
                liveSession = session
                _isLiveCallActive.value = true
                _studioState.value = StudioResultState.TextReady("Live audio conversation active (gemini-3.8-live).", "gemini-3.8-live")
                repository.logAnalyticsEvent("live", "gemini-3.8-live", 180L, 128L, false, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live API connection failed", e)
                _isLiveCallActive.value = false
                _studioState.value = StudioResultState.Error(e.localizedMessage ?: "Live API connection failed.")
            }
        }
    }

    // --- Maps Actions ---

    fun updateUserCoordinates(lat: Double, lng: Double) {
        _mapsState.value = _mapsState.value.copy(userLat = lat, userLng = lng)
    }

    fun searchMapsWithGrounding(query: String) {
        if (query.isBlank()) return
        val lang = AppLanguage.fromCode(localPrefs.value.languageCode)
        val cur = _mapsState.value
        viewModelScope.launch {
            _mapsState.value = cur.copy(isLoading = true, error = null)
            val start = System.currentTimeMillis()
            try {
                val (summary, places) = aiService.searchMapsPlaces(
                    query = query,
                    lat = cur.userLat,
                    lng = cur.userLng,
                    languageBcp47 = lang.bcp47
                )
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _mapsState.value = _mapsState.value.copy(
                    isLoading = false,
                    summary = summary,
                    places = places,
                    error = null
                )
                repository.logAnalyticsEvent("maps", ChatModelTier.HYDROGEN.modelId, elapsed, summary.length.toLong() / 4L, false, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Maps grounding failed", e)
                _mapsState.value = _mapsState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Maps query failed."
                )
            }
        }
    }

    // --- Analytics Report ---

    fun generateAiAnalyticsReport() {
        val lang = AppLanguage.fromCode(localPrefs.value.languageCode)
        viewModelScope.launch {
            _analyticsReportState.value = StudioResultState.Loading
            val start = System.currentTimeMillis()
            try {
                val allVault = vaultItems.value
                val encryptedCount = allVault.count { it.isEncrypted }
                val pendingCount = offlineCachedVault.value.count { it.pendingSync }
                val report = aiService.generateAnalyticsReport(
                    events = analyticsEvents.value,
                    vaultCount = allVault.size,
                    encryptedCount = encryptedCount,
                    pendingOfflineCount = pendingCount,
                    languageBcp47 = lang.bcp47
                )
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                _analyticsReportState.value = StudioResultState.TextReady(report, "gemini-4-argon")
                repository.logAnalyticsEvent("chat", "gemini-4-argon", elapsed, report.length.toLong() / 4L, false, false)
                maybeSendNotification(NotificationCategory.DIGEST, "Analytics Report Ready", report.take(120))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Analytics report failed", e)
                _analyticsReportState.value = StudioResultState.Error(e.localizedMessage ?: "Failed to generate report.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordJob?.cancel()
        runCatching {
            liveSession?.stopAudioConversation()
        }
    }

    companion object {
        fun provideFactory(
            currentUserId: String,
            initialDisplayName: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val localDb = AuraLocalDatabase.getInstance(app)
                val repository = AuraRepository(app, localDb.offlineVaultDao())
                AuraViewModel(
                    appContext = app.applicationContext,
                    repository = repository,
                    prefsStore = UserPreferencesStore(app),
                    connectivityMonitor = ConnectivityMonitor(app),
                    aiService = AuraAiService(),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName
                )
            }
        }
    }
}
