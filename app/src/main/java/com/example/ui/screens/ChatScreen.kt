package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.ChatModelTier
import com.example.data.ai.GroundedSource
import com.example.data.model.ChatMessageRecord
import com.example.ui.components.LiquidGlassCard
import com.example.ui.i18n.AuraStrings
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.StreamUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    strings: AuraStrings,
    messages: List<ChatMessageRecord>,
    streamState: StreamUiState,
    selectedTier: ChatModelTier,
    searchGrounding: Boolean,
    mapsGrounding: Boolean,
    onSelectTier: (ChatModelTier) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onToggleMaps: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Model & Grounding Controls
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = strings.modelSelectorTitle,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.modelSelectorTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = strings.clearChat,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChatModelTier.entries.forEach { tier ->
                    FilterChip(
                        selected = selectedTier == tier,
                        onClick = { onSelectTier(tier) },
                        label = { Text(tier.displayLabel, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("model_chip_${tier.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = searchGrounding,
                    onClick = { onToggleSearch(!searchGrounding) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = strings.searchGrounding, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(strings.searchGrounding, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("toggle_search_grounding")
                )
                FilterChip(
                    selected = mapsGrounding,
                    onClick = { onToggleMaps(!mapsGrounding) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Map, contentDescription = strings.mapsGrounding, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(strings.mapsGrounding, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("toggle_maps_grounding")
                )
            }
        }

        // Conversation List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id.ifBlank { "${it.role}_${it.content.hashCode()}" } }) { msg ->
                val sources = msg.groundingSources.orEmpty().mapNotNull { encoded ->
                    val parts = encoded.split("|", limit = 2)
                    if (parts.size == 2) GroundedSource(title = parts[0], uri = parts[1]) else null
                }
                ChatBubbleCard(
                    strings = strings,
                    isUser = msg.role == "user",
                    content = msg.content,
                    modelName = msg.modelName,
                    thoughts = msg.thoughtSummary.orEmpty(),
                    sources = sources,
                    latencyMs = msg.latencyMs
                )
            }

            when (streamState) {
                StreamUiState.Loading -> {
                    item {
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "${selectedTier.displayLabel}...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is StreamUiState.Streaming -> {
                    item {
                        ChatBubbleCard(
                            strings = strings,
                            isUser = false,
                            content = streamState.text.ifBlank { "..." },
                            modelName = selectedTier.modelId,
                            thoughts = streamState.thoughts,
                            sources = streamState.sources,
                            latencyMs = 0L
                        )
                    }
                }
                is StreamUiState.Error -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = streamState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                StreamUiState.Idle -> Unit
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(glass.glassFillBottom)
                .border(1.dp, glass.specularHighlight)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text(strings.chatPlaceholder, maxLines = 1) },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = {
                    val prompt = inputText.trim()
                    if (prompt.isNotEmpty()) {
                        inputText = ""
                        onSendMessage(prompt)
                    }
                },
                enabled = inputText.isNotBlank() && streamState !is StreamUiState.Loading,
                modifier = Modifier
                    .size(50.dp)
                    .testTag("chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = strings.send)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubbleCard(
    strings: AuraStrings,
    isUser: Boolean,
    content: String,
    modelName: String,
    thoughts: String,
    sources: List<GroundedSource>,
    latencyMs: Long
) {
    val uriHandler = LocalUriHandler.current
    var showThoughts by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(0.92f),
            highlightAccent = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "You" else modelName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                if (latencyMs > 0L) {
                    Text(
                        text = "${latencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isUser && thoughts.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                        .clickable { showThoughts = !showThoughts }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = strings.thinkingSummaryTitle,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.thinkingSummaryTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Icon(
                        imageVector = if (showThoughts) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                AnimatedVisibility(visible = showThoughts) {
                    Text(
                        text = thoughts,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sources.forEach { src ->
                        AssistChip(
                            onClick = {
                                runCatching { uriHandler.openUri(src.uri) }
                            },
                            label = {
                                Text(
                                    text = src.title.take(28),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = src.title,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
