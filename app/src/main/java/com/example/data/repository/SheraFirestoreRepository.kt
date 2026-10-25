package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.model.AlertRule
import com.example.model.SignalRecord
import com.example.model.WatchlistItem
import com.example.util.OperationType
import com.example.util.ensureFirebaseInitialized
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SheraFirestoreRepository(private val db: FirebaseFirestore?) {

    constructor(context: Context) : this(createFirestoreOrNull(context))

    private val localWatchlist = MutableStateFlow<List<WatchlistItem>>(
        listOf(
            WatchlistItem(id = "BTC-USDT", instId = "BTC-USDT", userId = "local", note = "OKX SPOT Watch"),
            WatchlistItem(id = "ETH-USDT", instId = "ETH-USDT", userId = "local", note = "OKX SPOT Watch"),
            WatchlistItem(id = "SOL-USDT", instId = "SOL-USDT", userId = "local", note = "OKX SPOT Watch")
        )
    )
    private val localAlertRules = MutableStateFlow<List<AlertRule>>(emptyList())
    private val localSignalHistory = MutableStateFlow<List<SignalRecord>>(emptyList())

    private fun currentAuthenticatedUid(): String? {
        return runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
    }

    private fun isCloudSession(userId: String): Boolean {
        return db != null && userId != "local_terminal_user"
    }

    private fun sanitizeDocId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100).ifBlank { "item_1" }
    }

    // --- Watchlist ---

    fun observeWatchlist(userId: String = currentAuthenticatedUid() ?: "local_terminal_user"): Flow<List<WatchlistItem>> = flow {
        val firestore = db
        if (!isCloudSession(userId) || firestore == null) {
            emitAll(localWatchlist)
            return@flow
        }
        val path = "users/$userId/watchlist"
        emitAll(
            firestore.collection("users").document(userId).collection("watchlist")
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
        val safeInstId = sanitizeDocId(instId)
        val uid = currentAuthenticatedUid()
        val nowTs = Timestamp.now()
        val localItem = WatchlistItem(
            id = safeInstId,
            instId = safeInstId,
            userId = uid ?: "local_terminal_user",
            note = note.take(200),
            createdAt = nowTs,
            updatedAt = nowTs
        )
        localWatchlist.value = (localWatchlist.value.filterNot { it.instId == safeInstId } + localItem)

        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            return Result.success(safeInstId)
        }

        val docRef = firestore.collection("users").document(uid).collection("watchlist").document(safeInstId)
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
            Result.success(safeInstId)
        }
    }

    suspend fun removeWatchlistItem(instId: String): Result<Unit> {
        val safeInstId = sanitizeDocId(instId)
        localWatchlist.value = localWatchlist.value.filterNot { it.instId == safeInstId }

        val uid = currentAuthenticatedUid()
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            return Result.success(Unit)
        }

        val docRef = firestore.collection("users").document(uid).collection("watchlist").document(safeInstId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.success(Unit)
        }
    }

    suspend fun getWatchlistItemById(targetUserId: String, instId: String): Result<WatchlistItem?> {
        val safeInstId = sanitizeDocId(instId)
        val firestore = db
        if (firestore == null) {
            return Result.success(localWatchlist.value.firstOrNull { it.instId == safeInstId })
        }
        val docRef = firestore.collection("users").document(targetUserId).collection("watchlist").document(safeInstId)
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

    fun observeAlertRules(userId: String = currentAuthenticatedUid() ?: "local_terminal_user"): Flow<List<AlertRule>> = flow {
        val firestore = db
        if (!isCloudSession(userId) || firestore == null) {
            emitAll(localAlertRules)
            return@flow
        }
        val path = "users/$userId/alertRules"
        emitAll(
            firestore.collection("users").document(userId).collection("alertRules")
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
                    emitAll(localAlertRules)
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
        val ruleId = "rule_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val uid = currentAuthenticatedUid()
        val nowTs = Timestamp.now()
        val localRule = AlertRule(
            id = ruleId,
            instId = instId.take(30),
            userId = uid ?: "local_terminal_user",
            ruleType = ruleType,
            thresholdValue = thresholdValue.coerceAtLeast(0.0),
            timeframe = timeframe,
            enabled = true,
            cooldownMinutes = cooldownMinutes.coerceIn(1L, 1440L),
            lastTriggeredEpochMs = 0L,
            createdAt = nowTs,
            updatedAt = nowTs
        )
        localAlertRules.value = listOf(localRule) + localAlertRules.value

        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            return Result.success(ruleId)
        }

        val docRef = firestore.collection("users").document(uid).collection("alertRules").document(ruleId)
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
            Result.success(ruleId)
        }
    }

    suspend fun updateAlertRuleState(
        ruleId: String,
        enabled: Boolean,
        lastTriggeredEpochMs: Long
    ): Result<Unit> {
        localAlertRules.value = localAlertRules.value.map { r ->
            if (r.id == ruleId) {
                r.copy(enabled = enabled, lastTriggeredEpochMs = lastTriggeredEpochMs.coerceAtLeast(0L))
            } else {
                r
            }
        }

        val uid = currentAuthenticatedUid()
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            return Result.success(Unit)
        }

        val docRef = firestore.collection("users").document(uid).collection("alertRules").document(ruleId)
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
            Result.success(Unit)
        }
    }

    suspend fun deleteAlertRule(ruleId: String): Result<Unit> {
        localAlertRules.value = localAlertRules.value.filterNot { it.id == ruleId }

        val uid = currentAuthenticatedUid()
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            return Result.success(Unit)
        }

        val docRef = firestore.collection("users").document(uid).collection("alertRules").document(ruleId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.success(Unit)
        }
    }

    // --- Signal History ---

    fun observeSignalHistory(userId: String = currentAuthenticatedUid() ?: "local_terminal_user"): Flow<List<SignalRecord>> = flow {
        val firestore = db
        if (!isCloudSession(userId) || firestore == null) {
            emitAll(localSignalHistory)
            return@flow
        }
        val path = "users/$userId/signalHistory"
        emitAll(
            firestore.collection("users").document(userId).collection("signalHistory")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SignalRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    emitAll(localSignalHistory)
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
        val signalId = "sig_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val uid = currentAuthenticatedUid()
        val nowTs = Timestamp.now()
        val localRecord = SignalRecord(
            id = signalId,
            instId = instId.take(30).ifBlank { "UNKNOWN" },
            userId = uid ?: "local_terminal_user",
            sourceType = sourceType,
            timeframe = timeframe.take(10).ifBlank { "1H" },
            signalCategory = signalCategory,
            confluenceScore = confluenceScore.coerceIn(0, 100).toLong(),
            riskLevel = riskLevel,
            entryPrice = entryPrice.coerceAtLeast(0.0),
            invalidationPrice = invalidationPrice.coerceAtLeast(0.0),
            target1Price = target1Price.coerceAtLeast(0.0),
            target2Price = target2Price.coerceAtLeast(0.0),
            summaryKurdish = summaryKurdish.take(1950),
            summaryEnglish = summaryEnglish.take(1950),
            createdAt = nowTs,
            updatedAt = nowTs
        )
        localSignalHistory.value = listOf(localRecord) + localSignalHistory.value

        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            return Result.success(signalId)
        }

        val docRef = firestore.collection("users").document(uid).collection("signalHistory").document(signalId)
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
            Result.success(signalId)
        }
    }

    suspend fun deleteSignalRecord(signalId: String): Result<Unit> {
        localSignalHistory.value = localSignalHistory.value.filterNot { it.id == signalId }

        val uid = currentAuthenticatedUid()
        val firestore = db
        if (uid.isNullOrBlank() || firestore == null) {
            return Result.success(Unit)
        }

        val docRef = firestore.collection("users").document(uid).collection("signalHistory").document(signalId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.success(Unit)
        }
    }

    companion object {
        private fun createFirestoreOrNull(context: Context): FirebaseFirestore? {
            return try {
                ensureFirebaseInitialized(context)
                val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                FirebaseFirestore.getInstance(dbId)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
