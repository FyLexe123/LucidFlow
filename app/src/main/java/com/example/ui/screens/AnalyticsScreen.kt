package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.OfflineVaultEntity
import com.example.data.model.AnalyticsEventRecord
import com.example.data.model.VaultItem
import com.example.ui.components.EncryptionCoverageGauge
import com.example.ui.components.FeatureBreakdownBar
import com.example.ui.components.GlassIconBadge
import com.example.ui.components.LatencyTrendChart
import com.example.ui.components.LiquidGlassCard
import com.example.ui.i18n.AuraStrings
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.viewmodel.StudioResultState

@Composable
fun AnalyticsScreen(
    strings: AuraStrings,
    events: List<AnalyticsEventRecord>,
    vaultItems: List<VaultItem>,
    offlineCached: List<OfflineVaultEntity>,
    reportState: StudioResultState,
    onGenerateAiReport: () -> Unit,
    onSaveReportToVault: (String, String) -> Unit
) {
    val glass = LocalLiquidGlass.current

    val avgLatency = remember(events) {
        if (events.isEmpty()) 0L else events.map { it.latencyMs }.average().toLong()
    }
    val encryptionRatio = remember(vaultItems) {
        if (vaultItems.isEmpty()) 1f else vaultItems.count { it.isEncrypted }.toFloat() / vaultItems.size
    }
    val pendingQueueCount = remember(offlineCached) {
        offlineCached.count { it.pendingSync }
    }
    val featureCounts = remember(events) {
        val map = events.groupingBy { it.featureType }.eachCount()
        map.entries.sortedByDescending { it.value }
    }
    val maxFeatureCount = remember(featureCounts) {
        featureCounts.maxOfOrNull { it.value } ?: 1
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.analyticsTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            // 2x2 KPI Row
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiGlassTile(
                        title = strings.totalEvents,
                        value = events.size.toString(),
                        icon = Icons.Rounded.QueryStats,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiGlassTile(
                        title = strings.avgLatency,
                        value = "${avgLatency}ms",
                        icon = Icons.Rounded.Speed,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiGlassTile(
                        title = strings.e2eeCoverage,
                        value = "${(encryptionRatio * 100).toInt()}%",
                        icon = Icons.Rounded.Lock,
                        tint = glass.accentSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    KpiGlassTile(
                        title = strings.offlineQueueCount,
                        value = pendingQueueCount.toString(),
                        icon = Icons.Rounded.CloudQueue,
                        tint = glass.accentWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${strings.avgLatency} (ms)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Telemetry timeline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    EncryptionCoverageGauge(
                        ratio = encryptionRatio,
                        label = "E2EE"
                    )
                }
                Spacer(Modifier.height(10.dp))
                LatencyTrendChart(latenciesMs = events.map { it.latencyMs })
            }
        }

        item {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Telemetry Distribution",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                if (featureCounts.isEmpty()) {
                    Text(
                        text = "No usage events logged yet. Run chat, studio, maps, or vault actions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        featureCounts.forEachIndexed { index, entry ->
                            val accent = when (index % 3) {
                                0 -> MaterialTheme.colorScheme.primary
                                1 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            FeatureBreakdownBar(
                                label = entry.key.uppercase(),
                                count = entry.value,
                                maxCount = maxFeatureCount,
                                accent = accent
                            )
                        }
                    }
                }
            }
        }

        item {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onGenerateAiReport,
                    enabled = reportState !is StudioResultState.Loading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("generate_analytics_report_button")
                ) {
                    if (reportState is StudioResultState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = strings.generateAiReport)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.generateAiReport)
                    }
                }

                when (reportState) {
                    is StudioResultState.TextReady -> {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = reportState.modelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = reportState.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onSaveReportToVault("Analytics Executive Report", reportState.text) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("save_analytics_report_vault_button")
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = strings.encryptAndSave, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.encryptAndSave)
                        }
                    }
                    is StudioResultState.Error -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = reportState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun KpiGlassTile(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconBadge(icon = icon, contentDescription = title, tint = tint, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
