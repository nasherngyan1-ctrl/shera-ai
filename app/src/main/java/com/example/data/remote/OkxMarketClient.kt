package com.example.data.remote

import android.util.Log
import com.example.model.ConnectionStateType
import com.example.model.ConnectionStatus
import com.example.model.DerivativesTelemetry
import com.example.model.OkxCandle
import com.example.model.OkxTicker
import com.example.model.OkxTimeframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min

private const val TAG = "OkxMarketClient"
private const val OKX_REST_BASE = "https://www.okx.com"
private const val OKX_WS_PUBLIC = "wss://ws.okx.com:8443/ws/v5/public"

class OkxMarketClient(
    private val httpClient: OkHttpClient = defaultOkHttpClient()
) {
    private val _tickers = MutableStateFlow<List<OkxTicker>>(emptyList())
    val tickers: StateFlow<List<OkxTicker>> = _tickers.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val tickerMap = ConcurrentHashMap<String, OkxTicker>()
    private val candleCache = ConcurrentHashMap<String, Pair<Long, List<OkxCandle>>>()
    private val derivativesCache = ConcurrentHashMap<String, Pair<Long, DerivativesTelemetry>>()
    private val rateLimitMutex = Mutex()
    private var lastRestRequestMs = 0L

    private var webSocket: WebSocket? = null
    private var wsSubscribedInstIds: Set<String> = emptySet()
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectAttempts = 0

    suspend fun fetchAllSpotUsdtTickers(): Result<List<OkxTicker>> = withContext(Dispatchers.IO) {
        try {
            throttleRestCall(minIntervalMs = 180L)
            val request = Request.Builder()
                .url("$OKX_REST_BASE/api/v5/market/tickers?instType=SPOT")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "OKX HTTP ${response.code}"
                    updateStatus(ConnectionStateType.ERROR, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = parseTickersJson(bodyStr)
                if (parsed.isNotEmpty()) {
                    parsed.forEach { tickerMap[it.instId] = it }
                    val sorted = tickerMap.values.sortedByDescending { it.volUsdt24h }
                    _tickers.value = sorted
                    val currentState = if (webSocket != null) {
                        ConnectionStateType.LIVE_WS
                    } else {
                        ConnectionStateType.REST_POLLING
                    }
                    _connectionStatus.value = ConnectionStatus(
                        state = currentState,
                        lastUpdatedEpochMs = System.currentTimeMillis(),
                        activeSpotPairsCount = sorted.size,
                        reconnectAttempt = reconnectAttempts,
                        cachedPairsCount = candleCache.size,
                        errorMessage = null
                    )
                    Result.success(sorted)
                } else {
                    val msg = "Empty SPOT/USDT ticker response from OKX"
                    updateStatus(ConnectionStateType.ERROR, msg)
                    Result.failure(IllegalStateException(msg))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch OKX SPOT tickers", e)
            updateStatus(ConnectionStateType.ERROR, e.localizedMessage ?: "Network error connecting to OKX")
            Result.failure(e)
        }
    }

    suspend fun fetchCandles(
        instId: String,
        timeframe: OkxTimeframe,
        limit: Int = 220,
        forceRefresh: Boolean = false
    ): Result<List<OkxCandle>> = withContext(Dispatchers.IO) {
        val cacheKey = "$instId:${timeframe.bar}:$limit"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            candleCache[cacheKey]?.let { (cachedAt, candles) ->
                if (now - cachedAt < 12_000L && candles.isNotEmpty()) {
                    return@withContext Result.success(candles)
                }
            }
        }

        try {
            throttleRestCall(minIntervalMs = 160L)
            val safeLimit = limit.coerceIn(30, 300)
            val url = "$OKX_REST_BASE/api/v5/market/candles?instId=$instId&bar=${timeframe.bar}&limit=$safeLimit"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("OKX Candles HTTP ${response.code} for $instId")
                    )
                }
                val bodyStr = response.body?.string().orEmpty()
                val candles = parseCandlesJson(bodyStr)
                if (candles.isNotEmpty()) {
                    candleCache[cacheKey] = System.currentTimeMillis() to candles
                    _connectionStatus.value = _connectionStatus.value.copy(cachedPairsCount = candleCache.size)
                    Result.success(candles)
                } else {
                    Result.failure(IllegalStateException("No candles returned for $instId (${timeframe.bar})"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch candles for $instId ${timeframe.bar}", e)
            candleCache[cacheKey]?.second?.let { cached ->
                if (cached.isNotEmpty()) return@withContext Result.success(cached)
            }
            Result.failure(e)
        }
    }

    /**
     * Fetches optional Funding Rate, Open Interest, and Liquidations for the corresponding
     * perpetual contract (`{BASE}-USDT-SWAP`) using official public unauthenticated endpoints.
     * Explicitly reports CoinGlass commercial API status and NEVER fabricates or mocks data.
     */
    suspend fun fetchDerivativesTelemetry(
        spotInstId: String,
        forceRefresh: Boolean = false
    ): DerivativesTelemetry = withContext(Dispatchers.IO) {
        val baseSymbol = spotInstId.substringBefore("-").uppercase()
        val uly = "$baseSymbol-USDT"
        val swapInstId = "$baseSymbol-USDT-SWAP"
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            derivativesCache[spotInstId]?.let { (cachedAt, telemetry) ->
                if (now - cachedAt < 25_000L) {
                    return@withContext telemetry
                }
            }
        }

        try {
            // 1. Funding Rate
            throttleRestCall(minIntervalMs = 150L)
            val fundingUrl = "$OKX_REST_BASE/api/v5/public/funding-rate?instId=$swapInstId"
            val fundingPair = executeGetString(fundingUrl)?.let { parseFundingRateJson(it) }

            // 2. Open Interest
            throttleRestCall(minIntervalMs = 150L)
            val oiUrl = "$OKX_REST_BASE/api/v5/public/open-interest?instType=SWAP&instId=$swapInstId"
            val spotPrice = tickerMap[spotInstId]?.lastPrice ?: 0.0
            val oiPair = executeGetString(oiUrl)?.let { parseOpenInterestJson(it, spotPrice) }

            // 3. Liquidations
            throttleRestCall(minIntervalMs = 150L)
            val liqUrl = "$OKX_REST_BASE/api/v5/public/liquidation-orders?instType=SWAP&uly=$uly&state=filled&limit=1"
            val liqTriple = executeGetString(liqUrl)?.let { parseLiquidationsJson(it) }

            val hasData = fundingPair != null || oiPair != null
            val result = if (hasData) {
                DerivativesTelemetry(
                    instId = spotInstId,
                    swapInstId = swapInstId,
                    isAvailable = true,
                    fundingRatePct = fundingPair?.first,
                    nextFundingRatePct = fundingPair?.second,
                    openInterestUsdt = oiPair?.first,
                    openInterestContracts = oiPair?.second,
                    recentLongLiqBase = liqTriple?.first,
                    recentShortLiqBase = liqTriple?.second,
                    recentLiqCount = liqTriple?.third ?: 0,
                    fetchedAtMs = System.currentTimeMillis()
                )
            } else {
                DerivativesTelemetry(
                    instId = spotInstId,
                    swapInstId = swapInstId,
                    isAvailable = false,
                    unavailableReasonKu = "گرێبەستی دێریڤەیتیڤ ($swapInstId) یان کلیلی CoinGlass بۆ ئەم دراوە بەردەست نییە — هیچ داتایەکی ساختە پیشان نادرێت.",
                    unavailableReasonEn = "No public $swapInstId contract or licensed CoinGlass feed available for this asset — zero fake data shown.",
                    fetchedAtMs = System.currentTimeMillis()
                )
            }

            derivativesCache[spotInstId] = System.currentTimeMillis() to result
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Derivatives telemetry unavailable for $spotInstId: ${e.message}")
            DerivativesTelemetry(
                instId = spotInstId,
                swapInstId = swapInstId,
                isAvailable = false,
                unavailableReasonKu = "داتای Funding Rate و Open Interest لەم کاتەدا بەردەست نییە (${e.localizedMessage ?: "Network"}).",
                unavailableReasonEn = "Funding Rate & Open Interest currently unavailable (${e.localizedMessage ?: "Network"}).",
                fetchedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun executeGetString(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    fun connectWebSocket(scope: CoroutineScope, topInstIds: List<String>) {
        val targetSet = topInstIds.take(35).toSet()
        if (targetSet.isEmpty()) return
        if (webSocket != null && wsSubscribedInstIds == targetSet) return

        disconnectWebSocket()
        wsSubscribedInstIds = targetSet

        val request = Request.Builder().url(OKX_WS_PUBLIC).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                Log.d(TAG, "OKX WebSocket connected, subscribing to ${targetSet.size} pairs")
                val argsArray = JSONArray()
                targetSet.forEach { id ->
                    argsArray.put(
                        JSONObject().apply {
                            put("channel", "tickers")
                            put("instId", id)
                        }
                    )
                }
                val subMsg = JSONObject().apply {
                    put("op", "subscribe")
                    put("args", argsArray)
                }
                webSocket.send(subMsg.toString())
                _connectionStatus.value = _connectionStatus.value.copy(
                    state = ConnectionStateType.LIVE_WS,
                    lastUpdatedEpochMs = System.currentTimeMillis(),
                    reconnectAttempt = 0,
                    cachedPairsCount = candleCache.size,
                    errorMessage = null
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "pong") return
                try {
                    val root = JSONObject(text)
                    val dataArr = root.optJSONArray("data") ?: return
                    var updated = false
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.optJSONObject(i) ?: continue
                        val parsed = parseSingleTickerObject(item) ?: continue
                        tickerMap[parsed.instId] = parsed
                        updated = true
                    }
                    if (updated) {
                        val sorted = tickerMap.values.sortedByDescending { it.volUsdt24h }
                        _tickers.value = sorted
                        _connectionStatus.value = ConnectionStatus(
                            state = ConnectionStateType.LIVE_WS,
                            lastUpdatedEpochMs = System.currentTimeMillis(),
                            activeSpotPairsCount = sorted.size,
                            reconnectAttempt = reconnectAttempts,
                            cachedPairsCount = candleCache.size,
                            errorMessage = null
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse WS ticker message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "OKX WebSocket failure: ${t.message}")
                this@OkxMarketClient.webSocket = null
                reconnectAttempts++
                _connectionStatus.value = _connectionStatus.value.copy(
                    state = ConnectionStateType.RECONNECTING,
                    reconnectAttempt = reconnectAttempts,
                    errorMessage = t.localizedMessage
                )
                scheduleReconnect(scope, targetSet.toList())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@OkxMarketClient.webSocket = null
            }
        })

        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(22_000L)
                webSocket?.send("ping")
            }
        }
    }

    private fun scheduleReconnect(scope: CoroutineScope, instIds: List<String>) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            val backoffMs = min(30_000L, 3_500L * reconnectAttempts.coerceAtLeast(1))
            delay(backoffMs)
            if (isActive) {
                connectWebSocket(scope, instIds)
            }
        }
    }

    fun disconnectWebSocket() {
        pingJob?.cancel()
        reconnectJob?.cancel()
        try {
            webSocket?.close(1000, "Client closing")
        } catch (_: Exception) {
        }
        webSocket = null
    }

    private suspend fun throttleRestCall(minIntervalMs: Long) {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRestRequestMs
            if (elapsed < minIntervalMs) {
                delay(minIntervalMs - elapsed)
            }
            lastRestRequestMs = System.currentTimeMillis()
        }
    }

    private fun updateStatus(state: ConnectionStateType, error: String?) {
        _connectionStatus.value = _connectionStatus.value.copy(
            state = state,
            cachedPairsCount = candleCache.size,
            errorMessage = error
        )
    }

    companion object {
        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun parseTickersJson(jsonString: String): List<OkxTicker> {
            val root = JSONObject(jsonString)
            if (root.optString("code") != "0") return emptyList()
            val data = root.optJSONArray("data") ?: return emptyList()
            val result = ArrayList<OkxTicker>(data.length())
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val ticker = parseSingleTickerObject(obj) ?: continue
                result.add(ticker)
            }
            return result.sortedByDescending { it.volUsdt24h }
        }

        fun parseSingleTickerObject(obj: JSONObject): OkxTicker? {
            val instId = obj.optString("instId", "")
            if (!instId.endsWith("-USDT")) return null
            val parts = instId.split("-")
            if (parts.size != 2) return null

            val last = obj.optString("last").toDoubleOrNull() ?: return null
            if (last <= 0.0) return null
            val open24h = obj.optString("open24h").toDoubleOrNull() ?: last
            val high24h = obj.optString("high24h").toDoubleOrNull() ?: last
            val low24h = obj.optString("low24h").toDoubleOrNull() ?: last
            val vol24h = obj.optString("vol24h").toDoubleOrNull() ?: 0.0
            val volCcy24h = obj.optString("volCcy24h").toDoubleOrNull() ?: (vol24h * last)
            val ts = obj.optString("ts").toLongOrNull() ?: System.currentTimeMillis()
            val changePct = if (open24h > 0.0) ((last - open24h) / open24h) * 100.0 else 0.0

            return OkxTicker(
                instId = instId,
                baseSymbol = parts[0],
                quoteSymbol = parts[1],
                lastPrice = last,
                open24h = open24h,
                high24h = high24h,
                low24h = low24h,
                volBase24h = vol24h,
                volUsdt24h = volCcy24h,
                change24hPct = changePct,
                timestampMs = ts
            )
        }

        fun parseCandlesJson(jsonString: String): List<OkxCandle> {
            val root = JSONObject(jsonString)
            if (root.optString("code") != "0") return emptyList()
            val data = root.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<OkxCandle>(data.length())
            for (i in 0 until data.length()) {
                val arr = data.optJSONArray(i) ?: continue
                if (arr.length() < 6) continue
                val ts = arr.optString(0).toLongOrNull() ?: continue
                val open = arr.optString(1).toDoubleOrNull() ?: continue
                val high = arr.optString(2).toDoubleOrNull() ?: continue
                val low = arr.optString(3).toDoubleOrNull() ?: continue
                val close = arr.optString(4).toDoubleOrNull() ?: continue
                val volBase = arr.optString(5).toDoubleOrNull() ?: 0.0
                val volQuote = if (arr.length() > 7) {
                    arr.optString(7).toDoubleOrNull() ?: (volBase * close)
                } else {
                    volBase * close
                }
                val confirmed = if (arr.length() > 8) arr.optString(8) == "1" else true
                list.add(
                    OkxCandle(
                        timestampMs = ts,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volumeBase = volBase,
                        volumeQuoteUsdt = volQuote,
                        isConfirmed = confirmed
                    )
                )
            }
            return list.sortedBy { it.timestampMs }
        }

        /**
         * Parses OKX `/api/v5/public/funding-rate` response.
         * Returns Pair(currentFundingRatePct, nextFundingRatePct?)
         */
        fun parseFundingRateJson(jsonString: String): Pair<Double, Double?>? {
            val root = JSONObject(jsonString)
            if (root.optString("code") != "0") return null
            val data = root.optJSONArray("data") ?: return null
            if (data.length() == 0) return null
            val item = data.optJSONObject(0) ?: return null
            val rawFunding = item.optString("fundingRate").toDoubleOrNull() ?: return null
            val rawNext = item.optString("nextFundingRate").toDoubleOrNull()
            return (rawFunding * 100.0) to rawNext?.let { it * 100.0 }
        }

        /**
         * Parses OKX `/api/v5/public/open-interest` response.
         * Returns Pair(openInterestUsdt, openInterestContracts)
         */
        fun parseOpenInterestJson(jsonString: String, fallbackSpotPrice: Double): Pair<Double, Double>? {
            val root = JSONObject(jsonString)
            if (root.optString("code") != "0") return null
            val data = root.optJSONArray("data") ?: return null
            if (data.length() == 0) return null
            val item = data.optJSONObject(0) ?: return null
            val oiContracts = item.optString("oi").toDoubleOrNull() ?: return null
            val oiCcy = item.optString("oiCcy").toDoubleOrNull() ?: 0.0
            val oiUsdDirect = item.optString("oiUsd").toDoubleOrNull()
            val oiUsdt = when {
                oiUsdDirect != null && oiUsdDirect > 0.0 -> oiUsdDirect
                oiCcy > 0.0 && fallbackSpotPrice > 0.0 -> oiCcy * fallbackSpotPrice
                else -> oiCcy
            }
            return oiUsdt to oiContracts
        }

        /**
         * Parses OKX `/api/v5/public/liquidation-orders` response.
         * Returns Triple(longLiquidatedSz, shortLiquidatedSz, totalCount)
         */
        fun parseLiquidationsJson(jsonString: String): Triple<Double, Double, Int>? {
            val root = JSONObject(jsonString)
            if (root.optString("code") != "0") return null
            val data = root.optJSONArray("data") ?: return null
            var longLiq = 0.0
            var shortLiq = 0.0
            var count = 0
            for (i in 0 until data.length()) {
                val entry = data.optJSONObject(i) ?: continue
                val details = entry.optJSONArray("details") ?: continue
                for (j in 0 until details.length()) {
                    val d = details.optJSONObject(j) ?: continue
                    val sz = d.optString("sz").toDoubleOrNull() ?: 0.0
                    val posSide = d.optString("posSide", "").lowercase()
                    val side = d.optString("side", "").lowercase()
                    if (posSide == "long" || side == "sell") {
                        longLiq += sz
                    } else if (posSide == "short" || side == "buy") {
                        shortLiq += sz
                    }
                    count++
                }
            }
            return Triple(longLiq, shortLiq, count)
        }
    }
}
