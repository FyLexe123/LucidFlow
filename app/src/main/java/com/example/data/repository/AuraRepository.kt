package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.OfflineVaultDao
import com.example.data.local.OfflineVaultEntity
import com.example.data.model.AnalyticsEventRecord
import com.example.data.model.ChatMessageRecord
import com.example.data.model.UserProfile
import com.example.data.model.VaultItem
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AuraRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val offlineVaultDao: OfflineVaultDao? = null
) {
    constructor(context: Context, offlineVaultDao: OfflineVaultDao? = null) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        offlineVaultDao = offlineVaultDao
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- USER PROFILE & SETTINGS ---

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        defaultSaltBase64: String,
        defaultFingerprint: String
    ): Result<UserProfile> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val profile = existing.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid, displayName = displayName)
                Result.success(profile)
            } else {
                val cleanName = displayName.ifBlank { "Aura User" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "preferredLanguage" to "tr",
                    "darkMode" to "system",
                    "e2eeEnabled" to true,
                    "e2eeSaltBase64" to defaultSaltBase64,
                    "e2eeKeyFingerprint" to defaultFingerprint,
                    "notificationsEnabled" to true,
                    "notifyDailyDigest" to true,
                    "notifySyncAlerts" to true,
                    "notifyAiInsights" to true,
                    "quietHoursStart" to 23L,
                    "quietHoursEnd" to 7L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid, displayName = cleanName)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(updates: Map<String, Any>): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        val safeUpdates = updates.toMutableMap().apply {
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.update(safeUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- E2EE VAULT ITEMS ---

    fun observeVaultItems(): Flow<List<VaultItem>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("vaultItems")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(
                        VaultItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    // Mirror to local Room cache for offline resilience
                    offlineVaultDao?.let { dao ->
                        val entities = list.map { item ->
                            OfflineVaultEntity(
                                localId = item.id,
                                userId = uid,
                                title = item.title,
                                cipherTextBase64 = item.cipherTextBase64,
                                ivBase64 = item.ivBase64,
                                authTagHash = item.authTagHash,
                                category = item.category,
                                isEncrypted = item.isEncrypted,
                                isPinned = item.isPinned,
                                pendingSync = false,
                                updatedAtEpochMs = item.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        }
                        dao.upsertAll(entities)
                    }
                    list
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeOfflineCachedVaultItems(userId: String): Flow<List<OfflineVaultEntity>>? {
        return offlineVaultDao?.observeLocalItems(userId)
    }

    suspend fun createVaultItem(
        title: String,
        cipherTextBase64: String,
        ivBase64: String,
        authTagHash: String,
        category: String,
        isEncrypted: Boolean,
        isPinned: Boolean = false,
        isOfflineMode: Boolean = false
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val itemId = UUID.randomUUID().toString().replace("-", "_")
        val docRef = db.collection("users").document(uid).collection("vaultItems").document(itemId)

        // Always cache locally first for instant offline-first UX
        offlineVaultDao?.upsertItem(
            OfflineVaultEntity(
                localId = itemId,
                userId = uid,
                title = title.take(120),
                cipherTextBase64 = cipherTextBase64,
                ivBase64 = ivBase64,
                authTagHash = authTagHash,
                category = category,
                isEncrypted = isEncrypted,
                isPinned = isPinned,
                pendingSync = isOfflineMode,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )

        if (isOfflineMode) {
            return Result.success(itemId)
        }

        val payload = mapOf(
            "userId" to uid,
            "title" to title.take(120),
            "cipherTextBase64" to cipherTextBase64,
            "ivBase64" to ivBase64,
            "authTagHash" to authTagHash,
            "category" to category,
            "isEncrypted" to isEncrypted,
            "isPinned" to isPinned,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            offlineVaultDao?.markSynced(itemId)
            Result.success(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncPendingOfflineVaultItems(): Result<Int> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val dao = offlineVaultDao ?: return Result.success(0)
        val pending = dao.getPendingSyncItems(uid)
        var syncedCount = 0
        for (entity in pending) {
            val docRef = db.collection("users").document(uid).collection("vaultItems").document(entity.localId)
            val payload = mapOf(
                "userId" to uid,
                "title" to entity.title.take(120),
                "cipherTextBase64" to entity.cipherTextBase64,
                "ivBase64" to entity.ivBase64,
                "authTagHash" to entity.authTagHash,
                "category" to entity.category,
                "isEncrypted" to entity.isEncrypted,
                "isPinned" to entity.isPinned,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
                dao.markSynced(entity.localId)
                syncedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
                return Result.failure(e)
            }
        }
        return Result.success(syncedCount)
    }

    suspend fun togglePinVaultItem(item: VaultItem): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        require(item.id.isNotBlank()) { "VaultItem id must not be blank" }
        val docRef = db.collection("users").document(uid).collection("vaultItems").document(item.id)
        return try {
            docRef.update(
                mapOf(
                    "isPinned" to !item.isPinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteVaultItem(itemId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("vaultItems").document(itemId)
        return try {
            docRef.delete().await()
            offlineVaultDao?.deleteById(itemId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- MULTI-TURN CHAT HISTORY ---

    fun observeChatMessages(): Flow<List<ChatMessageRecord>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("chatMessages")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessageRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun saveChatMessage(
        role: String,
        content: String,
        modelName: String,
        thoughtSummary: String? = null,
        groundingSources: List<String>? = null,
        latencyMs: Long = 0L
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val msgId = UUID.randomUUID().toString().replace("-", "_")
        val docRef = db.collection("users").document(uid).collection("chatMessages").document(msgId)
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("role", role)
            put("content", content.take(32768).ifBlank { " " })
            put("modelName", modelName.take(80))
            if (!thoughtSummary.isNullOrBlank()) {
                put("thoughtSummary", thoughtSummary.take(16384))
            }
            if (!groundingSources.isNullOrEmpty()) {
                put("groundingSources", groundingSources.take(10).map { it.take(512) })
            }
            put("latencyMs", latencyMs.coerceIn(0L, 300_000L))
            put("createdAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.set(payload).await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(messageIds: List<String>): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val colRef = db.collection("users").document(uid).collection("chatMessages")
            for (id in messageIds) {
                colRef.document(id).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/chatMessages", auth)
            Result.failure(e)
        }
    }

    // --- ANALYTICS EVENTS ---

    fun observeAnalyticsEvents(): Flow<List<AnalyticsEventRecord>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("analyticsEvents")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AnalyticsEventRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun logAnalyticsEvent(
        featureType: String,
        modelUsed: String,
        latencyMs: Long,
        tokenEstimate: Long,
        isOfflineQueued: Boolean,
        isEncrypted: Boolean
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val evtId = UUID.randomUUID().toString().replace("-", "_")
        val docRef = db.collection("users").document(uid).collection("analyticsEvents").document(evtId)
        val payload = mapOf(
            "userId" to uid,
            "featureType" to featureType,
            "modelUsed" to modelUsed.take(80),
            "latencyMs" to latencyMs.coerceIn(0L, 300_000L),
            "tokenEstimate" to tokenEstimate.coerceIn(0L, 1_000_000L),
            "isOfflineQueued" to isOfflineQueued,
            "isEncrypted" to isEncrypted,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(evtId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
