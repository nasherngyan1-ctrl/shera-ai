package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SheraFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun watchlist_createAndObserve_authenticatedOwnerSucceeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = SheraFirestoreRepository(firestore)
        val pairId = "BTC-USDT-${UUID.randomUUID().toString().take(6)}"

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertWatchlistItem(pairId, "Core Bitcoin market")
        }
        assertTrue(createRes.isSuccess)

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeWatchlist(uid).first { list -> list.any { it.instId == pairId } }
        }
        assertTrue(items.any { it.instId == pairId })
    }

    @Test
    fun watchlist_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SheraFirestoreRepository(firestore)
        val pairId = "ETH-USDT-${UUID.randomUUID().toString().take(6)}"
        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertWatchlistItem(pairId, "Alice ETH")
        }
        assertTrue(createRes.isSuccess)

        signInTestUser(BOB_EMAIL)
        val bobRepo = SheraFirestoreRepository(firestore)
        val crossRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getWatchlistItemById(aliceUid, pairId)
        }
        assertTrue(crossRead.isFailure)
        val ex = crossRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun alertRule_and_signalHistory_createAndObserve_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = SheraFirestoreRepository(firestore)

        val ruleRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createAlertRule(
                instId = "SOL-USDT",
                ruleType = "PRICE_ABOVE",
                thresholdValue = 200.0,
                timeframe = "1H",
                cooldownMinutes = 15L
            )
        }
        assertTrue(ruleRes.isSuccess)

        val sigRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveSignalRecord(
                instId = "SOL-USDT",
                sourceType = "CHART_IMAGE_AI",
                timeframe = "4H",
                signalCategory = "BUY_SETUP",
                confluenceScore = 82,
                riskLevel = "MEDIUM",
                entryPrice = 185.0,
                invalidationPrice = 176.0,
                target1Price = 198.0,
                target2Price = 212.0,
                summaryKurdish = "شیکاری تەکنیکی لەسەر بنەمای وێنەی چارت",
                summaryEnglish = "Technical chart image setup analysis"
            )
        }
        assertTrue(sigRes.isSuccess)

        val signals = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSignalHistory(uid).first { it.isNotEmpty() }
        }
        assertTrue(signals.any { it.id == sigRes.getOrNull() })
    }

    @Test
    fun observeWatchlist_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SheraFirestoreRepository(firestore)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeWatchlist("unauth_uid_123").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@sheraai.test"
        const val BOB_EMAIL = "bob@sheraai.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
