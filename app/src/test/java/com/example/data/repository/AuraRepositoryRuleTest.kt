package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class AuraRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_and_createVaultItem_succeedsForAuthenticatedOwner() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = AuraRepository(db = firestore, auth = auth)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile("Alice", "c2FsdA==", "AA:BB:CC:DD")
        }
        assertTrue("Expected profile creation to succeed: ${profileResult.exceptionOrNull()}", profileResult.isSuccess)

        val itemResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createVaultItem(
                title = "Encrypted Note ${UUID.randomUUID().toString().take(6)}",
                cipherTextBase64 = "ZW5jcnlwdGVkUGF5bG9hZA==",
                ivBase64 = "MTIzNDU2Nzg5MDEy",
                authTagHash = "abcdef123456",
                category = "note",
                isEncrypted = true,
                isPinned = false
            )
        }
        assertTrue("Expected vault item creation to succeed: ${itemResult.exceptionOrNull()}", itemResult.isSuccess)
        val createdId = itemResult.getOrThrow()

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeVaultItems().first { list -> list.any { it.id == createdId } }
        }
        assertTrue(items.any { it.id == createdId })
    }

    @Test
    fun saveChatMessage_and_logAnalyticsEvent_succeedsForAuthenticatedOwner() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = AuraRepository(db = firestore, auth = auth)

        val msgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveChatMessage(
                role = "user",
                content = "Test prompt",
                modelName = "gemini-4-hydrogen",
                latencyMs = 240L
            )
        }
        assertTrue("Expected chat message save to succeed: ${msgRes.exceptionOrNull()}", msgRes.isSuccess)

        val evtRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logAnalyticsEvent(
                featureType = "chat",
                modelUsed = "gemini-4-hydrogen",
                latencyMs = 240L,
                tokenEstimate = 85L,
                isOfflineQueued = false,
                isEncrypted = true
            )
        }
        assertTrue("Expected analytics event log to succeed: ${evtRes.exceptionOrNull()}", evtRes.isSuccess)
    }

    @Test
    fun crossUserRead_vaultItem_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = AuraRepository(db = firestore, auth = auth)
        val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createVaultItem(
                title = "Private Secret",
                cipherTextBase64 = "c2VjcmV0",
                ivBase64 = "aXYxMjM0",
                authTagHash = "hash",
                category = "note",
                isEncrypted = true
            ).getOrThrow()
        }

        // Bob tries to read Alice's vault item directly via Firestore
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("vaultItems").document(itemId)
                    .get().let { task ->
                        com.google.android.gms.tasks.Tasks.await(task)
                    }
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's vaultItem")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun observeVaultItems_unauthenticatedUser_throwsIllegalStateOrPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = AuraRepository(db = firestore, auth = auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeVaultItems().first()
            }
            fail("Expected unauthenticated observeVaultItems to fail")
        } catch (e: Exception) {
            // Repository fail-fast IllegalStateException or Firestore PERMISSION_DENIED both block unauth access
            assertTrue(e is IllegalStateException || e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_aura@test.com"
        const val BOB_EMAIL = "bob_aura@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
