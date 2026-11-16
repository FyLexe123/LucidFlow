package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.OfflineVaultEntity
import com.example.data.model.VaultItem
import com.example.ui.components.GlassIconBadge
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.i18n.AuraStrings
import com.example.ui.theme.LocalLiquidGlass

private data class DisplayVaultRecord(
    val id: String,
    val title: String,
    val cipherTextBase64: String,
    val ivBase64: String,
    val authTagHash: String,
    val category: String,
    val isEncrypted: Boolean,
    val isPinned: Boolean,
    val isPendingSync: Boolean,
    val rawCloudItem: VaultItem?
)

@Composable
fun VaultScreen(
    strings: AuraStrings,
    vaultItems: List<VaultItem>,
    offlineCached: List<OfflineVaultEntity>,
    decryptedMap: Map<String, String>,
    isOffline: Boolean,
    keyFingerprint: String,
    onToggleOffline: (Boolean) -> Unit,
    onSyncPending: () -> Unit,
    onCreateVaultItem: (title: String, plaintext: String, category: String) -> Unit,
    onToggleDecrypt: (id: String, cipherText: String, iv: String) -> Unit,
    onTogglePin: (VaultItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onApplyPassphrase: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showPassphraseDialog by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("all") }

    val pendingOfflineItems = remember(offlineCached) {
        offlineCached.filter { it.pendingSync }
    }

    val mergedItems = remember(vaultItems, offlineCached, selectedCategory) {
        val cloudIds = vaultItems.map { it.id }.toSet()
        val offlineOnly = offlineCached
            .filter { it.localId !in cloudIds }
            .map {
                DisplayVaultRecord(
                    id = it.localId,
                    title = it.title,
                    cipherTextBase64 = it.cipherTextBase64,
                    ivBase64 = it.ivBase64,
                    authTagHash = it.authTagHash,
                    category = it.category,
                    isEncrypted = it.isEncrypted,
                    isPinned = it.isPinned,
                    isPendingSync = it.pendingSync,
                    rawCloudItem = null
                )
            }
        val cloudRecords = vaultItems.map { item ->
            DisplayVaultRecord(
                id = item.id,
                title = item.title,
                cipherTextBase64 = item.cipherTextBase64,
                ivBase64 = item.ivBase64,
                authTagHash = item.authTagHash,
                category = item.category,
                isEncrypted = item.isEncrypted,
                isPinned = item.isPinned,
                isPendingSync = false,
                rawCloudItem = item
            )
        }
        (offlineOnly + cloudRecords).filter {
            selectedCategory == "all" || it.category == selectedCategory
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = strings.encryptedVaultTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(glass.glassFillTop)
                            .border(1.dp, glass.specularHighlight, RoundedCornerShape(14.dp))
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = strings.settingsTitle,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                // Status Pills Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassStatusPill(
                        text = if (isOffline) strings.statusOffline else strings.statusOnline,
                        icon = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                        tint = if (isOffline) glass.accentWarning else glass.accentSuccess,
                        onClick = { onToggleOffline(!isOffline) },
                        modifier = Modifier.testTag("offline_status_pill")
                    )

                    GlassStatusPill(
                        text = "${strings.keyFingerprintLabel}: ${keyFingerprint.ifBlank { "—" }}",
                        icon = Icons.Rounded.Key,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { showPassphraseDialog = true },
                        modifier = Modifier.testTag("passphrase_button")
                    )

                    if (pendingOfflineItems.isNotEmpty()) {
                        GlassStatusPill(
                            text = "${strings.syncNow} (${pendingOfflineItems.size})",
                            icon = Icons.Rounded.Sync,
                            tint = MaterialTheme.colorScheme.secondary,
                            onClick = onSyncPending,
                            modifier = Modifier.testTag("sync_now_button")
                        )
                    }
                }
            }

            item {
                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("all", "note", "key", "place", "report").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = cat.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.testTag("vault_filter_$cat")
                        )
                    }
                }
            }

            if (mergedItems.isEmpty()) {
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_vault_shield),
                                contentDescription = strings.e2eeBadge,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(86.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.e2eeBadge,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = strings.vaultEmptyHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("add_vault_item_empty_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = strings.addVaultItem)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.addVaultItem)
                        }
                    }
                }
            } else {
                items(mergedItems, key = { it.id }) { item ->
                    val decryptedText = decryptedMap[item.id]
                    VaultRecordCard(
                        strings = strings,
                        record = item,
                        decryptedText = decryptedText,
                        onToggleDecrypt = {
                            onToggleDecrypt(item.id, item.cipherTextBase64, item.ivBase64)
                        },
                        onTogglePin = {
                            item.rawCloudItem?.let(onTogglePin)
                        },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 18.dp)
                .testTag("add_vault_item_button")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = strings.addVaultItem)
        }
    }

    if (showCreateDialog) {
        CreateVaultDialog(
            strings = strings,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, content, category ->
                onCreateVaultItem(title, content, category)
                showCreateDialog = false
            }
        )
    }

    if (showPassphraseDialog) {
        PassphraseDialog(
            strings = strings,
            currentFingerprint = keyFingerprint,
            onDismiss = { showPassphraseDialog = false },
            onApply = { passphrase ->
                onApplyPassphrase(passphrase)
                showPassphraseDialog = false
            }
        )
    }
}

@Composable
private fun VaultRecordCard(
    strings: AuraStrings,
    record: DisplayVaultRecord,
    decryptedText: String?,
    onToggleDecrypt: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        highlightAccent = if (record.isPinned) MaterialTheme.colorScheme.primary else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconBadge(
                    icon = if (decryptedText != null) Icons.Rounded.LockOpen else Icons.Rounded.EnhancedEncryption,
                    contentDescription = record.category,
                    tint = if (decryptedText != null) glass.accentSuccess else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = record.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (record.isPendingSync) {
                            Text(
                                text = "• ${strings.pendingSyncLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.accentWarning
                            )
                        } else {
                            Text(
                                text = "• SHA:${record.authTagHash.take(8)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.rawCloudItem != null) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("pin_vault_item_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin",
                            tint = if (record.isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_vault_item_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = strings.delete,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        AnimatedVisibility(visible = decryptedText != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(glass.accentSuccess.copy(alpha = 0.12f))
                    .border(1.dp, glass.accentSuccess.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = decryptedText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (decryptedText == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = strings.e2eeBadge,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AES-GCM: ${record.cipherTextBase64.take(28)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onToggleDecrypt,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("decrypt_vault_item_button")
            ) {
                Icon(
                    imageVector = if (decryptedText == null) Icons.Rounded.LockOpen else Icons.Rounded.VisibilityOff,
                    contentDescription = if (decryptedText == null) strings.decryptView else strings.hidePlaintext,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (decryptedText == null) strings.decryptView else strings.hidePlaintext)
            }
        }
    }
}

@Composable
private fun CreateVaultDialog(
    strings: AuraStrings,
    onDismiss: () -> Unit,
    onConfirm: (title: String, plaintext: String, category: String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("note") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addVaultItem) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.titleLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_title_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(strings.contentLabel) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_content_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("note", "key", "place", "report").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.uppercase()) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, content, category) },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("confirm_save_vault_button")
            ) {
                Icon(Icons.Rounded.CloudSync, contentDescription = strings.encryptAndSave, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.encryptAndSave)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
private fun PassphraseDialog(
    strings: AuraStrings,
    currentFingerprint: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.passphraseTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${strings.keyFingerprintLabel}: $currentFingerprint",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("PBKDF2-SHA256 Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("passphrase_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(passphrase) },
                enabled = passphrase.length >= 4,
                modifier = Modifier.testTag("apply_passphrase_button")
            ) {
                Text(strings.applyKey)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
