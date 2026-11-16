package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.LocalAppPreferences
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AuraStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    strings: AuraStrings,
    prefs: LocalAppPreferences,
    userDisplayName: String,
    userEmail: String,
    keyFingerprint: String,
    onDismiss: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectDarkMode: (String) -> Unit,
    onToggleOfflineManual: (Boolean) -> Unit,
    onUpdateNotifications: (
        master: Boolean,
        digest: Boolean,
        sync: Boolean,
        ai: Boolean,
        quietStart: Int,
        quietEnd: Int
    ) -> Unit,
    onSendTestNotification: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onUpdateNotifications(
                true,
                prefs.notifyDigest,
                prefs.notifySync,
                prefs.notifyAi,
                prefs.quietStartHour,
                prefs.quietEndHour
            )
            onSendTestNotification()
        }
    }

    fun requestNotifPermissionIfNeeded(actionAfterGrant: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                actionAfterGrant()
            } else {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            actionAfterGrant()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "$userDisplayName • $userEmail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Appearance / Dark Mode
            Text(strings.themeMode, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to strings.themeSystem,
                    "dark" to strings.themeDark,
                    "light" to strings.themeLight
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = prefs.darkMode == mode,
                        onClick = { onSelectDarkMode(mode) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("theme_${mode}_option")
                    )
                }
            }

            // Multilingual Support
            Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = prefs.languageCode == lang.code,
                        onClick = { onSelectLanguage(lang) },
                        label = { Text(lang.nativeLabel) },
                        modifier = Modifier.testTag("settings_lang_${lang.code}")
                    )
                }
            }

            // Offline Working Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.offlineModeToggle, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = prefs.offlineModeManual,
                    onCheckedChange = onToggleOfflineManual,
                    modifier = Modifier.testTag("offline_mode_switch")
                )
            }

            HorizontalDivider()

            // Customizable Notifications
            Text(strings.notificationsTitle, style = MaterialTheme.typography.titleSmall)

            SettingSwitchRow(
                label = strings.notifyMaster,
                checked = prefs.notificationsEnabled,
                tag = "notify_master_switch",
                onCheckedChange = { enabled ->
                    if (enabled) {
                        requestNotifPermissionIfNeeded {
                            onUpdateNotifications(
                                true,
                                prefs.notifyDigest,
                                prefs.notifySync,
                                prefs.notifyAi,
                                prefs.quietStartHour,
                                prefs.quietEndHour
                            )
                        }
                    } else {
                        onUpdateNotifications(
                            false,
                            prefs.notifyDigest,
                            prefs.notifySync,
                            prefs.notifyAi,
                            prefs.quietStartHour,
                            prefs.quietEndHour
                        )
                    }
                }
            )

            SettingSwitchRow(
                label = strings.notifyDigest,
                checked = prefs.notifyDigest,
                tag = "notify_digest_switch",
                onCheckedChange = {
                    onUpdateNotifications(
                        prefs.notificationsEnabled,
                        it,
                        prefs.notifySync,
                        prefs.notifyAi,
                        prefs.quietStartHour,
                        prefs.quietEndHour
                    )
                }
            )

            SettingSwitchRow(
                label = strings.notifySync,
                checked = prefs.notifySync,
                tag = "notify_sync_switch",
                onCheckedChange = {
                    onUpdateNotifications(
                        prefs.notificationsEnabled,
                        prefs.notifyDigest,
                        it,
                        prefs.notifyAi,
                        prefs.quietStartHour,
                        prefs.quietEndHour
                    )
                }
            )

            SettingSwitchRow(
                label = strings.notifyAi,
                checked = prefs.notifyAi,
                tag = "notify_ai_switch",
                onCheckedChange = {
                    onUpdateNotifications(
                        prefs.notificationsEnabled,
                        prefs.notifyDigest,
                        prefs.notifySync,
                        it,
                        prefs.quietStartHour,
                        prefs.quietEndHour
                    )
                }
            )

            // Quiet Hours Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.quietHoursLabel}: ${prefs.quietStartHour}:00 - ${prefs.quietEndHour}:00",
                    style = MaterialTheme.typography.bodySmall
                )
                Row {
                    TextButton(
                        onClick = {
                            val nextStart = (prefs.quietStartHour + 1) % 24
                            onUpdateNotifications(
                                prefs.notificationsEnabled,
                                prefs.notifyDigest,
                                prefs.notifySync,
                                prefs.notifyAi,
                                nextStart,
                                prefs.quietEndHour
                            )
                        }
                    ) { Text("+Start") }
                    TextButton(
                        onClick = {
                            val nextEnd = (prefs.quietEndHour + 1) % 24
                            onUpdateNotifications(
                                prefs.notificationsEnabled,
                                prefs.notifyDigest,
                                prefs.notifySync,
                                prefs.notifyAi,
                                prefs.quietStartHour,
                                nextEnd
                            )
                        }
                    ) { Text("+End") }
                }
            }

            OutlinedButton(
                onClick = { requestNotifPermissionIfNeeded { onSendTestNotification() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_notification_button")
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = strings.testNotification, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.testNotification)
            }

            HorizontalDivider()

            Text(
                text = "${strings.keyFingerprintLabel}: $keyFingerprint",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = strings.signOut)
                Spacer(Modifier.width(8.dp))
                Text(strings.signOut)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
