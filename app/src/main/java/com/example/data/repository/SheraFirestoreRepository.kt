package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.model.AlertRule
import com.example.model.SignalRecord
import com.example.model.WatchlistItem
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SheraFirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeDocId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100).ifBlank { "item_1" }
    }

    // --- Watchlist ---

    fun observeWatchlist(userId: String = requireUserId()): Flow<List<WatchlistItem>> = flow {
        val path = "users/$userId/watchlist"
        emitAll(
            db.collection("users").document(userId).collection("watchlist")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchlistItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun upsertWatchlistItem(instId: String, note: String = ""): Result<String> {
        val uid = requireUserId()
        val safeInstId = sanitizeDocId(instId)
        val docRef = db.collection("users").document(uid).collection("watchlist").document(safeInstId)
        return try {
            val payload = mapOf(
                "instId" to safeInstId,
                "userId" to uid,
                "note" to note.take(200),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(safeInstId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeWatchlistItem(instId: String): Result<Unit> {
        val uid = requireUserId()
        val safeInstId = sanitizeDocId(instId)
        val docRef = db.collection("users").document(uid).collection("watchlist").document(safeInstId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getWatchlistItemById(targetUserId: String, instId: String): Result<WatchlistItem?> {
        val safeInstId = sanitizeDocId(instId)
        val docRef = db.collection("users").document(targetUserId).collection("watchlist").document(safeInstId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    WatchlistItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    // --- Alert Rules ---

    fun observeAlertRules(userId: String = requireUserId()): Flow<List<AlertRule>> = flow {
        val path = "users/$userId/alertRules"
        emitAll(
            db.collection("users").document(userId).collection("alertRules")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AlertRule::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createAlertRule(
        instId: String,
        ruleType: String,
        thresholdValue: Double,
        timeframe: String,
        cooldownMinutes: Long = 30L
    ): Result<String> {
        val uid = requireUserId()
        val ruleId = "rule_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("alertRules").document(ruleId)
        return try {
            val payload = mapOf(
                "instId" to instId.take(30),
                "userId" to uid,
                "ruleType" to ruleType,
                "thresholdValue" to thresholdValue.coerceAtLeast(0.0),
                "timeframe" to timeframe,
                "enabled" to true,
                "cooldownMinutes" to cooldownMinutes.coerceIn(1L, 1440L),
                "lastTriggeredEpochMs" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(ruleId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateAlertRuleState(
        ruleId: String,
        enabled: Boolean,
        lastTriggeredEpochMs: Long
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("alertRules").document(ruleId)
        return try {
            val updates = mapOf(
                "enabled" to enabled,
                "lastTriggeredEpochMs" to lastTriggeredEpochMs.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteAlertRule(ruleId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("alertRules").document(ruleId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Signal History ---

    fun observeSignalHistory(userId: String = requireUserId()): Flow<List<SignalRecord>> = flow {
        val path = "users/$userId/signalHistory"
        emitAll(
            db.collection("users").document(userId).collection("signalHistory")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SignalRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: com.google.firebase.Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveSignalRecord(
        instId: String,
        sourceType: String,
        timeframe: String,
        signalCategory: String,
        confluenceScore: Int,
        riskLevel: String,
        entryPrice: Double,
        invalidationPrice: Double,
        target1Price: Double,
        target2Price: Double,
        summaryKurdish: String,
        summaryEnglish: String
    ): Result<String> {
        val uid = requireUserId()
        val signalId = "sig_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("signalHistory").document(signalId)
        return try {
            val payload = mapOf(
                "instId" to instId.take(30).ifBlank { "UNKNOWN" },
                "userId" to uid,
                "sourceType" to sourceType,
                "timeframe" to timeframe.take(10).ifBlank { "1H" },
                "signalCategory" to signalCategory,
                "confluenceScore" to confluenceScore.coerceIn(0, 100).toLong(),
                "riskLevel" to riskLevel,
                "entryPrice" to entryPrice.coerceAtLeast(0.0),
                "invalidationPrice" to invalidationPrice.coerceAtLeast(0.0),
                "target1Price" to target1Price.coerceAtLeast(0.0),
                "target2Price" to target2Price.coerceAtLeast(0.0),
                "summaryKurdish" to summaryKurdish.take(1950),
                "summaryEnglish" to summaryEnglish.take(1950),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(signalId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSignalRecord(signalId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("signalHistory").document(signalId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
