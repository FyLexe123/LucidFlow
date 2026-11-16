package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.ai.ImageAspectOption
import com.example.data.ai.ImageResolutionOption
import com.example.ui.components.GlassIconBadge
import com.example.ui.components.LiquidGlassCard
import com.example.ui.i18n.AuraStrings
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.StudioResultState

private enum class StudioMode {
    IMAGE, TTS, MUSIC, VIDEO, ANALYZE, TRANSCRIBE, LIVE_VOICE
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    strings: AuraStrings,
    studioState: StudioResultState,
    isRecordingAudio: Boolean,
    isLiveCallActive: Boolean,
    onGenerateOrEditImage: (String, Boolean, ImageAspectOption, ImageResolutionOption, Bitmap?) -> Unit,
    onSynthesizeSpeech: (String) -> Unit,
    onGenerateMusic: (String, Boolean) -> Unit,
    onGenerateVideo: (String, Boolean, Bitmap?) -> Unit,
    onAnalyzeImage: (Bitmap, String) -> Unit,
    onAnalyzeVideo: (Uri, String) -> Unit,
    onToggleRecordAudio: () -> Unit,
    onToggleLiveCall: () -> Unit,
    onSaveTextToVault: (String, String) -> Unit
) {
    val context = LocalContext.current
    val glass = LocalLiquidGlass.current

    var activeMode by rememberSaveable { mutableStateOf(StudioMode.IMAGE) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var highQualityPro by rememberSaveable { mutableStateOf(false) }
    var selectedAspect by rememberSaveable { mutableStateOf(ImageAspectOption.RATIO_1_1) }
    var selectedResolution by rememberSaveable { mutableStateOf(ImageResolutionOption.RES_1K) }
    var musicFullPro by rememberSaveable { mutableStateOf(false) }
    var videoLandscape16x9 by rememberSaveable { mutableStateOf(true) }

    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pickedVideoUri by remember { mutableStateOf<Uri?>(null) }

    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAudioAction?.invoke()
        }
        pendingAudioAction = null
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    pickedBitmap = BitmapFactory.decodeStream(stream)
                    pickedVideoUri = null
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedVideoUri = uri
            pickedBitmap = null
        }
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingAudioAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Studio Mode Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                StudioMode.IMAGE to Pair(strings.studioImageGen, Icons.Rounded.Image),
                StudioMode.TTS to Pair(strings.studioTts, Icons.Rounded.RecordVoiceOver),
                StudioMode.MUSIC to Pair(strings.studioMusic, Icons.Rounded.MusicNote),
                StudioMode.VIDEO to Pair(strings.studioVideo, Icons.Rounded.Videocam),
                StudioMode.ANALYZE to Pair(strings.studioAnalyze, Icons.Rounded.VideoLibrary),
                StudioMode.TRANSCRIBE to Pair(strings.studioTranscribe, Icons.Rounded.Mic),
                StudioMode.LIVE_VOICE to Pair(strings.studioLiveVoice, Icons.Rounded.GraphicEq)
            ).forEach { (mode, meta) ->
                FilterChip(
                    selected = activeMode == mode,
                    onClick = { activeMode = mode },
                    leadingIcon = { Icon(meta.second, contentDescription = meta.first, modifier = Modifier.size(16.dp)) },
                    label = { Text(meta.first, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("studio_tab_${mode.name.lowercase()}")
                )
            }
        }

        // Control Card per Mode
        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
            when (activeMode) {
                StudioMode.IMAGE -> {
                    Text(
                        text = strings.studioImageGen,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Prompt") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_image_prompt")
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (highQualityPro) "Pro (gemini-3-pro-image-preview)" else "Fast (gemini-nano-banana-2.1)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Switch(
                            checked = highQualityPro,
                            onCheckedChange = { highQualityPro = it },
                            modifier = Modifier.testTag("studio_image_pro_switch")
                        )
                    }
                    if (highQualityPro) {
                        Text(strings.resolutionLabel, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImageResolutionOption.entries.forEach { res ->
                                FilterChip(
                                    selected = selectedResolution == res,
                                    onClick = { selectedResolution = res },
                                    label = { Text(res.label) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(strings.aspectRatioLabel, style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ImageAspectOption.entries.forEach { aspect ->
                            FilterChip(
                                selected = selectedAspect == aspect,
                                onClick = { selectedAspect = aspect },
                                label = { Text(aspect.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_pick_edit_photo_button")
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = strings.pickPhoto, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (pickedBitmap == null) strings.pickPhoto else "Edit Photo ✓")
                        }
                        Button(
                            onClick = {
                                onGenerateOrEditImage(prompt, highQualityPro, selectedAspect, selectedResolution, pickedBitmap)
                            },
                            enabled = prompt.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_generate_image_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = strings.generateAction, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.generateAction)
                        }
                    }
                }

                StudioMode.TTS -> {
                    Text(
                        text = "${strings.studioTts} • gemini-3.8-flash-tts",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Speech Text") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_tts_input")
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onSynthesizeSpeech(prompt) },
                        enabled = prompt.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("studio_tts_button")
                    ) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = strings.studioTts)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.generateAction)
                    }
                }

                StudioMode.MUSIC -> {
                    Text(
                        text = "${strings.studioMusic} • Lyria 3",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Mood / Genre / Instruments") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_music_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (musicFullPro) "lyria-3-pro-preview (Full)" else "lyria-3-clip-preview (30s Clip)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Switch(checked = musicFullPro, onCheckedChange = { musicFullPro = it })
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onGenerateMusic(prompt, musicFullPro) },
                        enabled = prompt.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("studio_music_button")
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = strings.studioMusic)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.generateAction)
                    }
                }

                StudioMode.VIDEO -> {
                    Text(
                        text = "${strings.studioVideo} • gemini-omni-1.2-flash",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Scene Prompt") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_video_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = videoLandscape16x9,
                            onClick = { videoLandscape16x9 = true },
                            label = { Text("16:9 Landscape") }
                        )
                        FilterChip(
                            selected = !videoLandscape16x9,
                            onClick = { videoLandscape16x9 = false },
                            label = { Text("9:16 Portrait") }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(if (pickedBitmap == null) strings.pickPhoto else "Image Ref ✓")
                        }
                        Button(
                            onClick = { onGenerateVideo(prompt, videoLandscape16x9, pickedBitmap) },
                            enabled = prompt.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_video_button")
                        ) {
                            Text(strings.generateAction)
                        }
                    }
                }

                StudioMode.ANALYZE -> {
                    Text(
                        text = "${strings.studioAnalyze} • gemini-4-argon",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Question / Prompt (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_analyze_pick_photo")
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = strings.pickPhoto, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.pickPhoto)
                        }
                        Button(
                            onClick = {
                                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("studio_analyze_pick_video")
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = strings.pickVideo, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.pickVideo)
                        }
                    }
                    if (pickedBitmap != null || pickedVideoUri != null) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                pickedBitmap?.let { onAnalyzeImage(it, prompt) }
                                    ?: pickedVideoUri?.let { onAnalyzeVideo(it, prompt) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("studio_run_analysis_button")
                        ) {
                            Text("Analyze with Gemini 4 Argon")
                        }
                    }
                }

                StudioMode.TRANSCRIBE -> {
                    Text(
                        text = "${strings.studioTranscribe} • gemini-3.5-transcribe",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (isRecordingAudio) {
                                onToggleRecordAudio()
                            } else {
                                runWithMicPermission { onToggleRecordAudio() }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("studio_transcribe_record_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingAudio) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (isRecordingAudio) strings.stopRecording else strings.recordAudio
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isRecordingAudio) strings.stopRecording else strings.recordAudio)
                    }
                }

                StudioMode.LIVE_VOICE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassIconBadge(
                            icon = Icons.Rounded.GraphicEq,
                            contentDescription = strings.studioLiveVoice,
                            tint = if (isLiveCallActive) glass.accentSuccess else MaterialTheme.colorScheme.primary,
                            size = 48.dp
                        )
                        Column {
                            Text(
                                text = strings.studioLiveVoice,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "gemini-3.8-live • Low-Latency Audio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (isLiveCallActive) {
                                onToggleLiveCall()
                            } else {
                                runWithMicPermission { onToggleLiveCall() }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLiveCallActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("studio_live_voice_button")
                    ) {
                        Icon(
                            imageVector = if (isLiveCallActive) Icons.Rounded.CallEnd else Icons.Rounded.Call,
                            contentDescription = if (isLiveCallActive) strings.endLiveCall else strings.startLiveCall
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isLiveCallActive) strings.endLiveCall else strings.startLiveCall)
                    }
                }
            }
        }

        // Result Display Card
        when (studioState) {
            StudioResultState.Idle -> Unit
            StudioResultState.Loading -> {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.4.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Processing multimodal request...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is StudioResultState.ImageReady -> {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(studioState.modelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = studioState.bitmap.asImageBitmap(),
                        contentDescription = studioState.modelName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            is StudioResultState.AudioReady -> {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconBadge(Icons.Rounded.GraphicEq, null, glass.accentSuccess)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(studioState.modelName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${studioState.mimeType} • ${studioState.byteCount / 1024} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            is StudioResultState.VideoReady -> {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(studioState.modelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoPath(studioState.filePath)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            is StudioResultState.TextReady -> {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(studioState.modelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(studioState.text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onSaveTextToVault("Studio: ${studioState.modelName}", studioState.text) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("studio_save_result_vault_button")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = strings.encryptAndSave, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.encryptAndSave)
                    }
                }
            }
            is StudioResultState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(studioState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
