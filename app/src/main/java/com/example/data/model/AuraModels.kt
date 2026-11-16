package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val preferredLanguage: String = "tr",
    val darkMode: String = "system",
    val e2eeEnabled: Boolean = true,
    val e2eeSaltBase64: String? = null,
    val e2eeKeyFingerprint: String? = null,
    val notificationsEnabled: Boolean = true,
    val notifyDailyDigest: Boolean = true,
    val notifySyncAlerts: Boolean = true,
    val notifyAiInsights: Boolean = true,
    val quietHoursStart: Long = 23L,
    val quietHoursEnd: Long = 7L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VaultItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val cipherTextBase64: String = "",
    val ivBase64: String = "",
    val authTagHash: String = "",
    val category: String = "note",
    val isEncrypted: Boolean = true,
    val isPinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val content: String = "",
    val modelName: String = "gemini-4-hydrogen",
    val thoughtSummary: String? = null,
    val groundingSources: List<String>? = null,
    val latencyMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AnalyticsEventRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val featureType: String = "chat",
    val modelUsed: String = "gemini-4-hydrogen",
    val latencyMs: Long = 0L,
    val tokenEstimate: Long = 0L,
    val isOfflineQueued: Boolean = false,
    val isEncrypted: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)
