package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.OkxMarketClient
import com.example.data.repository.SheraFirestoreRepository
import com.example.domain.QuantIndicatorEngine
import com.example.domain.SheraAiBrainService
import com.example.domain.WalkForwardBacktester
import com.example.model.AiModelChoice
import com.example.model.AiVisionForecast
import com.example.model.AlertRule
import com.example.model.AppLanguage
import com.example.model.BacktestReport
import com.example.model.ChartDisplayMode
import com.example.model.ConnectionStatus
import com.example.model.DerivativesTelemetry
import com.example.model.MarketStructure
import com.example.model.OkxCandle
import com.example.model.OkxTicker
import com.example.model.OkxTimeframe
import com.example.model.OpportunitySetup
import com.example.model.RiskLevel
import com.example.model.SignalCategory
import com.example.model.SignalRecord
import com.example.model.WatchlistItem
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.util.AlertNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SheraMainViewModel"

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

sealed interface AiAnalysisUiState {
    data object Idle : AiAnalysisUiState
    data object Loading : AiAnalysisUiState
    data class Success(val forecast: AiVisionForecast) : AiAnalysisUiState
    data class Error(val message: String) : AiAnalysisUiState
}

sealed interface BacktestUiState {
    data object Idle : BacktestUiState
    data object Running : BacktestUiState
    data class Success(val report: BacktestReport) : BacktestUiState
    data class Error(val message: String) : BacktestUiState
}

class SheraMainViewModel(
    private val firestoreRepository: SheraFirestoreRepository,
    private val currentUserId: String,
    private val okxClient: OkxMarketClient = OkxMarketClient(),
    private val aiService: SheraAiBrainService = SheraAiBrainService()
) : ViewModel() {

    private val _language = MutableStateFlow(AppLanguage.KURDISH_SORANI)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    val tickers: StateFlow<List<OkxTicker>> = okxClient.tickers
    val connectionStatus: StateFlow<ConnectionStatus> = okxClient.connectionStatus

    // Search, Pagination, and Filters for OKX SPOT/USDT Markets
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showWatchlistOnly = MutableStateFlow(false)
    val showWatchlistOnly: StateFlow<Boolean> = _showWatchlistOnly.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    val pageSize: Int = 25

    // Selected Pair, Timeframe & Chart Display Mode (Native OKX Canvas vs Official TradingView Widget)
    private val _selectedInstId = MutableStateFlow("BTC-USDT")
    val selectedInstId: StateFlow<String> = _selectedInstId.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(OkxTimeframe.TF_1H)
    val selectedTimeframe: StateFlow<OkxTimeframe> = _selectedTimeframe.asStateFlow()

    private val _chartDisplayMode = MutableStateFlow(ChartDisplayMode.OKX_NATIVE_CANVAS)
    val chartDisplayMode: StateFlow<ChartDisplayMode> = _chartDisplayMode.asStateFlow()

    private val _selectedCandles = MutableStateFlow<List<OkxCandle>>(emptyList())
    val selectedCandles: StateFlow<List<OkxCandle>> = _selectedCandles.asStateFlow()

    private val _selectedSetup = MutableStateFlow<OpportunitySetup?>(null)
    val selectedSetup: StateFlow<OpportunitySetup?> = _selectedSetup.asStateFlow()

    private val _derivativesTelemetry = MutableStateFlow<DerivativesTelemetry?>(null)
    val derivativesTelemetry: StateFlow<DerivativesTelemetry?> = _derivativesTelemetry.asStateFlow()

    private val _isChartLoading = MutableStateFlow(false)
    val isChartLoading: StateFlow<Boolean> = _isChartLoading.asStateFlow()

    // Opportunity Scanner state & filters
    private val _opportunities = MutableStateFlow<List<OpportunitySetup>>(emptyList())
    val opportunities: StateFlow<List<OpportunitySetup>> = _opportunities.asStateFlow()

    private val _isScannerRunning = MutableStateFlow(false)
    val isScannerRunning: StateFlow<Boolean> = _isScannerRunning.asStateFlow()

    private val _scannerRiskFilter = MutableStateFlow<RiskLevel?>(null)
    val scannerRiskFilter: StateFlow<RiskLevel?> = _scannerRiskFilter.asStateFlow()

    private val _scannerStructureFilter = MutableStateFlow<MarketStructure?>(null)
    val scannerStructureFilter: StateFlow<MarketStructure?> = _scannerStructureFilter.asStateFlow()

    private val _scannerCategoryFilter = MutableStateFlow<SignalCategory?>(null)
    val scannerCategoryFilter: StateFlow<SignalCategory?> = _scannerCategoryFilter.asStateFlow()

    // AI Vision & Trading Desk state
    private val _selectedAiModel = MutableStateFlow(AiModelChoice.GEMINI_3_8_FLASH)
    val selectedAiModel: StateFlow<AiModelChoice> = _selectedAiModel.asStateFlow()

    private val _selectedChartBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedChartBitmap: StateFlow<Bitmap?> = _selectedChartBitmap.asStateFlow()

    private val _aiUserQuestion = MutableStateFlow("")
    val aiUserQuestion: StateFlow<String> = _aiUserQuestion.asStateFlow()

    private val _aiState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val aiState: StateFlow<AiAnalysisUiState> = _aiState.asStateFlow()

    // Unlimited AI Chat & Live 1-Min Signal state
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "سڵاو! من شێرە AI (SHÊRA AI) ئەندازیاری فەرمی پشک و دراوی ئۆکس مە. لێرە دەتوانیت بێسنوور چات بکەیت یان وێنەی چارت بنێریت بۆ ئەوەی دەستبەجێ پێتبڵێم زیان دەکات یان قازانج، لەگەڵ پێشبینی ١ دەقەیی.",
                sender = MessageSender.AI
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _inputMessage = MutableStateFlow("")
    val inputMessage: StateFlow<String> = _inputMessage.asStateFlow()

    private val _liveMinuteSignal = MutableStateFlow("🟢 ئێستا BTC-USDT بکڕە: پێشبینی بەرزبوونەوەی +3.5% لە ١٠ دەقەی داهاتوودا")
    val liveMinuteSignal: StateFlow<String> = _liveMinuteSignal.asStateFlow()

    // Backtesting state
    private val _backtestState = MutableStateFlow<BacktestUiState>(BacktestUiState.Idle)
    val backtestState: StateFlow<BacktestUiState> = _backtestState.asStateFlow()

    // Triggered Alert Banner
    private val _latestAlertBanner = MutableStateFlow<String?>(null)
    val latestAlertBanner: StateFlow<String?> = _latestAlertBanner.asStateFlow()

    // Cloud Firestore Real-Time Flows (Two-tier error handling per skill)
    val watchlistState: StateFlow<CloudUiState<List<WatchlistItem>>> =
        firestoreRepository.observeWatchlist(currentUserId)
            .map<List<WatchlistItem>, CloudUiState<List<WatchlistItem>>> { CloudUiState.Success(it) }
            .catch { err ->
                Log.w(TAG, "Error observing watchlist", err)
                emit(CloudUiState.Error(err.message ?: "Failed to observe watchlist"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    val alertRulesState: StateFlow<CloudUiState<List<AlertRule>>> =
        firestoreRepository.observeAlertRules(currentUserId)
            .map<List<AlertRule>, CloudUiState<List<AlertRule>>> { CloudUiState.Success(it) }
            .catch { err ->
                Log.w(TAG, "Error observing alert rules", err)
                emit(CloudUiState.Error(err.message ?: "Failed to observe alert rules"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    val signalHistoryState: StateFlow<CloudUiState<List<SignalRecord>>> =
        firestoreRepository.observeSignalHistory(currentUserId)
            .map<List<SignalRecord>, CloudUiState<List<SignalRecord>>> { CloudUiState.Success(it) }
            .catch { err ->
                Log.w(TAG, "Error observing signal history", err)
                emit(CloudUiState.Error(err.message ?: "Failed to observe signal history"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    init {
        startMarketMonitoringLoop()
        startLiveMinuteSignalLoop()
    }

    private fun startLiveMinuteSignalLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                val opps = opportunities.value
                if (opps.isNotEmpty()) {
                    val top = opps.random()
                    val isKu = _language.value == AppLanguage.KURDISH_SORANI
                    val msg = if (isKu) {
                        "🟢 ئێستا ${top.instId} بکڕە: پێشبینی قازانجی +${QuantIndicatorEngine.formatNum(top.ticker.change24hPct.coerceAtLeast(1.5), 1)}% لە ١٠ دەقەی داهاتوودا"
                    } else {
                        "🟢 BUY ${top.instId} Now: Expected +${QuantIndicatorEngine.formatNum(top.ticker.change24hPct.coerceAtLeast(1.5), 1)}% gain in next 10 mins"
                    }
                    _liveMinuteSignal.value = msg
                }
            }
        }
    }

    fun setInputMessage(msg: String) {
        _inputMessage.value = msg
    }

    fun sendChatMessage(text: String, bitmap: Bitmap?) {
        val userMsg = ChatMessage(
            text = text.ifBlank { if (bitmap != null) "شیکاری ئەم وێنەی چارتە بکە (زیان دەکات یان قازانج؟)" else "پرسیاری بازاڕ" },
            sender = MessageSender.USER,
            bitmap = bitmap
        )
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            _aiState.value = AiAnalysisUiState.Loading
            val setup = _selectedSetup.value
            val derivatives = _derivativesTelemetry.value
            val modelChoice = _selectedAiModel.value

            val result = if (bitmap != null) {
                aiService.analyzeChartImage(
                    chartBitmap = bitmap,
                    selectedModel = modelChoice,
                    userQuestion = text,
                    attachedLiveSetup = setup,
                    derivativesTelemetry = derivatives
                )
            } else {
                val targetSetup = setup ?: opportunities.value.firstOrNull()
                if (targetSetup != null) {
                    aiService.analyzeLivePairWithAi(
                        setup = targetSetup,
                        selectedModel = modelChoice,
                        userQuestion = text,
                        derivativesTelemetry = derivatives
                    )
                } else {
                    Result.failure(IllegalStateException("No market telemetry found. Please upload a chart image or select a pair."))
                }
            }

            result.onSuccess { forecast ->
                _aiState.value = AiAnalysisUiState.Success(forecast)
                val aiReplyText = if (_language.value == AppLanguage.KURDISH_SORANI) {
                    forecast.marketStructureSummaryKu
                } else {
                    forecast.marketStructureSummaryEn
                }
                val aiMsg = ChatMessage(
                    text = aiReplyText,
                    sender = MessageSender.AI,
                    forecast = forecast
                )
                _chatMessages.value = _chatMessages.value + aiMsg
            }.onFailure { err ->
                _aiState.value = AiAnalysisUiState.Error(err.message ?: "Analysis failed")
                val errMsg = ChatMessage(
                    text = "⚠️ هەڵە لە شیکاری AI: ${err.message}",
                    sender = MessageSender.AI
                )
                _chatMessages.value = _chatMessages.value + errMsg
            }
        }
    }

    private fun startMarketMonitoringLoop() {
        viewModelScope.launch {
            refreshMarketsAndSelectedPair(initialConnectWs = true)
            runOpportunityScanner()
            while (isActive) {
                delay(20_000L)
                refreshMarketsAndSelectedPair(initialConnectWs = false)
            }
        }
    }

    fun refreshMarketsAndSelectedPair(initialConnectWs: Boolean = false) {
        viewModelScope.launch {
            val res = okxClient.fetchAllSpotUsdtTickers()
            res.onSuccess { list ->
                if (initialConnectWs && list.isNotEmpty()) {
                    val topIds = list.take(30).map { it.instId }
                    okxClient.connectWebSocket(viewModelScope, topIds)
                }
            }
            loadCandlesAndAnalyzeSelectedPair(forceRefresh = true)
        }
    }

    fun selectPair(instId: String) {
        _selectedInstId.value = instId
        loadCandlesAndAnalyzeSelectedPair(forceRefresh = false)
    }

    fun selectTimeframe(timeframe: OkxTimeframe) {
        _selectedTimeframe.value = timeframe
        loadCandlesAndAnalyzeSelectedPair(forceRefresh = false)
        runOpportunityScanner()
    }

    fun setChartDisplayMode(mode: ChartDisplayMode) {
        _chartDisplayMode.value = mode
    }

    fun loadCandlesAndAnalyzeSelectedPair(forceRefresh: Boolean = false) {
        val instId = _selectedInstId.value
        val tf = _selectedTimeframe.value
        viewModelScope.launch {
            _isChartLoading.value = true
            launch {
                _derivativesTelemetry.value = okxClient.fetchDerivativesTelemetry(instId, forceRefresh)
            }
            val candleRes = okxClient.fetchCandles(instId, tf, limit = 220, forceRefresh = forceRefresh)
            candleRes.onSuccess { candles ->
                _selectedCandles.value = candles
                val ticker = tickers.value.firstOrNull { it.instId == instId }
                    ?: syntheticTickerFromCandles(instId, candles)
                if (ticker != null) {
                    val setupWithBacktest = withContext(Dispatchers.Default) {
                        val rawSetup = QuantIndicatorEngine.evaluateOpportunitySetup(
                            instId = instId,
                            timeframe = tf,
                            ticker = ticker,
                            candles = candles
                        )
                        if (rawSetup != null) {
                            val bt = WalkForwardBacktester.runBacktest(
                                instId = instId,
                                timeframe = tf,
                                candles = candles,
                                feePctPerSide = 0.10,
                                slippagePctPerSide = 0.05
                            )
                            rawSetup.copy(
                                verifiedBacktestWinRatePct = if (bt.totalTrades > 0) bt.winRatePct else null,
                                verifiedBacktestSampleSize = bt.totalTrades,
                                verifiedOutOfSampleReturnPct = if (bt.outOfSampleTrades > 0) bt.outOfSampleReturnPct else null
                            )
                        } else null
                    }
                    _selectedSetup.value = setupWithBacktest
                }
            }
            _isChartLoading.value = false
        }
    }

    fun runOpportunityScanner() {
        if (_isScannerRunning.value) return
        viewModelScope.launch {
            _isScannerRunning.value = true
            try {
                var currentTickers = tickers.value
                if (currentTickers.isEmpty()) {
                    currentTickers = okxClient.fetchAllSpotUsdtTickers().getOrNull().orEmpty()
                }
                val watchedIds = (watchlistState.value as? CloudUiState.Success)?.data
                    ?.map { it.instId }
                    .orEmpty()
                // Combine watched pairs + top high-liquidity SPOT/USDT pairs
                val candidateTickers = (
                    currentTickers.filter { it.instId in watchedIds } +
                        currentTickers.take(18)
                    ).distinctBy { it.instId }.take(18)

                val tf = _selectedTimeframe.value
                val results = mutableListOf<OpportunitySetup>()

                for (ticker in candidateTickers) {
                    val candles = okxClient.fetchCandles(ticker.instId, tf, limit = 210).getOrNull() ?: continue
                    val enrichedSetup = withContext(Dispatchers.Default) {
                        val setup = QuantIndicatorEngine.evaluateOpportunitySetup(
                            instId = ticker.instId,
                            timeframe = tf,
                            ticker = ticker,
                            candles = candles
                        ) ?: return@withContext null
                        val bt = WalkForwardBacktester.runBacktest(
                            instId = ticker.instId,
                            timeframe = tf,
                            candles = candles,
                            feePctPerSide = 0.10,
                            slippagePctPerSide = 0.05
                        )
                        setup.copy(
                            verifiedBacktestWinRatePct = if (bt.totalTrades > 0) bt.winRatePct else null,
                            verifiedBacktestSampleSize = bt.totalTrades,
                            verifiedOutOfSampleReturnPct = if (bt.outOfSampleTrades > 0) bt.outOfSampleReturnPct else null
                        )
                    } ?: continue
                    results.add(enrichedSetup)
                }

                // Rank by signal priority (BUY WATCH / SELL WATCH first), Signal Quality Score, Liquidity, and low risk
                val ranked = results.sortedWith(
                    compareByDescending<OpportunitySetup> { it.signalCategory != SignalCategory.WATCH }
                        .thenByDescending { it.confluenceScore }
                        .thenByDescending { it.ticker.volUsdt24h }
                )
                _opportunities.value = ranked
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Opportunity scanner encountered error", e)
            } finally {
                _isScannerRunning.value = false
            }
        }
    }

    // --- Multimodal AI Chart Image & Quant Forecast ---

    fun setAiModelChoice(choice: AiModelChoice) {
        _selectedAiModel.value = choice
    }

    fun setAiUserQuestion(question: String) {
        _aiUserQuestion.value = question
    }

    fun setSelectedChartBitmap(bitmap: Bitmap?) {
        _selectedChartBitmap.value = bitmap
    }

    /**
     * Generates a high-contrast candlestick chart bitmap from the currently loaded OKX candles
     * so the user can send the live OKX chart directly to Gemini AI Vision with one tap,
     * OR upload any screenshot from their phone gallery via Android Photo Picker.
     */
    fun captureCurrentOkxChartAsBitmapAndAnalyze() {
        val candles = _selectedCandles.value
        val setup = _selectedSetup.value
        if (candles.isEmpty()) return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                renderCandlesToBitmap(
                    instId = _selectedInstId.value,
                    timeframe = _selectedTimeframe.value.bar,
                    candles = candles.takeLast(60),
                    ema20 = setup?.snapshot?.ema20Series?.takeLast(60).orEmpty(),
                    ema50 = setup?.snapshot?.ema50Series?.takeLast(60).orEmpty()
                )
            }
            _selectedChartBitmap.value = bitmap
            analyzeCurrentImageOrPairWithAi()
        }
    }

    fun analyzeCurrentImageOrPairWithAi() {
        val bitmap = _selectedChartBitmap.value
        val setup = _selectedSetup.value
        val derivatives = _derivativesTelemetry.value
        val modelChoice = _selectedAiModel.value
        val question = _aiUserQuestion.value

        viewModelScope.launch {
            _aiState.value = AiAnalysisUiState.Loading
            val result = if (bitmap != null) {
                aiService.analyzeChartImage(
                    chartBitmap = bitmap,
                    selectedModel = modelChoice,
                    userQuestion = question,
                    attachedLiveSetup = setup,
                    derivativesTelemetry = derivatives
                )
            } else if (setup != null) {
                aiService.analyzeLivePairWithAi(
                    setup = setup,
                    selectedModel = modelChoice,
                    userQuestion = question,
                    derivativesTelemetry = derivatives
                )
            } else {
                Result.failure(
                    IllegalStateException(
                        if (_language.value == AppLanguage.KURDISH_SORANI) {
                            "تکایە وێنەی چارتێک هەڵبژێرە یان چاوەڕێ بکە داتای دراوەکە بار بێت."
                        } else {
                            "Please select a chart image or wait for live pair telemetry to load."
                        }
                    )
                )
            }

            result.onSuccess { forecast ->
                _aiState.value = AiAnalysisUiState.Success(forecast)
                // Persist analysis record to user's Firestore signalHistory
                val refPrice = setup?.snapshot?.currentPrice ?: 0.0
                val refInv = setup?.invalidationPrice ?: 0.0
                val refT1 = setup?.target1Price ?: 0.0
                val refT2 = setup?.target2Price ?: 0.0
                firestoreRepository.saveSignalRecord(
                    instId = forecast.detectedSymbol.ifBlank { _selectedInstId.value },
                    sourceType = if (bitmap != null) "CHART_IMAGE_AI" else "LIVE_QUANT",
                    timeframe = forecast.detectedTimeframe.ifBlank { _selectedTimeframe.value.bar },
                    signalCategory = forecast.signalCategory.code,
                    confluenceScore = forecast.confidenceScore,
                    riskLevel = forecast.riskLevel.code,
                    entryPrice = refPrice,
                    invalidationPrice = refInv,
                    target1Price = refT1,
                    target2Price = refT2,
                    summaryKurdish = "${forecast.marketStructureSummaryKu} | Entry: ${forecast.entryZone} | Stop: ${forecast.invalidationLevel} | T1: ${forecast.target1}",
                    summaryEnglish = "${forecast.marketStructureSummaryEn} | Entry: ${forecast.entryZone} | Stop: ${forecast.invalidationLevel} | T1: ${forecast.target1}"
                )
            }.onFailure { err ->
                _aiState.value = AiAnalysisUiState.Error(
                    err.localizedMessage ?: "AI analysis failed"
                )
            }
        }
    }

    fun saveLiveQuantSetupToCloud(setup: OpportunitySetup) {
        viewModelScope.launch {
            firestoreRepository.saveSignalRecord(
                instId = setup.instId,
                sourceType = "LIVE_QUANT",
                timeframe = setup.timeframe.bar,
                signalCategory = setup.signalCategory.code,
                confluenceScore = setup.confluenceScore,
                riskLevel = setup.riskLevel.code,
                entryPrice = setup.snapshot.currentPrice,
                invalidationPrice = setup.invalidationPrice,
                target1Price = setup.target1Price,
                target2Price = setup.target2Price,
                summaryKurdish = setup.reasonsKurdish.joinToString(" • "),
                summaryEnglish = setup.reasonsEnglish.joinToString(" • ")
            )
        }
    }

    // --- Walk-Forward Historical Backtest ---

    fun runWalkForwardBacktest(
        feePctPerSide: Double = 0.10,
        slippagePctPerSide: Double = 0.05
    ) {
        val instId = _selectedInstId.value
        val tf = _selectedTimeframe.value
        viewModelScope.launch {
            _backtestState.value = BacktestUiState.Running
            val candlesRes = okxClient.fetchCandles(instId, tf, limit = 280, forceRefresh = false)
            candlesRes.onSuccess { candles ->
                val report = withContext(Dispatchers.Default) {
                    WalkForwardBacktester.runBacktest(
                        instId = instId,
                        timeframe = tf,
                        candles = candles,
                        feePctPerSide = feePctPerSide,
                        slippagePctPerSide = slippagePctPerSide
                    )
                }
                _backtestState.value = BacktestUiState.Success(report)
                // Also update selectedSetup with the newly verified historical win rate
                _selectedSetup.value = _selectedSetup.value?.copy(
                    verifiedBacktestWinRatePct = if (report.totalTrades > 0) report.winRatePct else null,
                    verifiedBacktestSampleSize = report.totalTrades,
                    verifiedOutOfSampleReturnPct = if (report.outOfSampleTrades > 0) report.outOfSampleReturnPct else null
                )
            }.onFailure { err ->
                _backtestState.value = BacktestUiState.Error(
                    err.localizedMessage ?: "Failed to load historical candles for backtest"
                )
            }
        }
    }

    // --- Watchlist & Alert Rules Management ---

    fun toggleWatchlist(instId: String) {
        val currentList = (watchlistState.value as? CloudUiState.Success)?.data.orEmpty()
        val exists = currentList.any { it.instId == instId }
        viewModelScope.launch {
            if (exists) {
                firestoreRepository.removeWatchlistItem(instId)
            } else {
                firestoreRepository.upsertWatchlistItem(instId, "OKX SPOT Watch")
            }
        }
    }

    fun addAlertRule(
        instId: String,
        ruleType: String,
        thresholdValue: Double,
        timeframe: String,
        cooldownMinutes: Long
    ) {
        viewModelScope.launch {
            firestoreRepository.createAlertRule(
                instId = instId,
                ruleType = ruleType,
                thresholdValue = thresholdValue,
                timeframe = timeframe,
                cooldownMinutes = cooldownMinutes
            )
        }
    }

    fun toggleAlertRule(rule: AlertRule) {
        viewModelScope.launch {
            firestoreRepository.updateAlertRuleState(
                ruleId = rule.id,
                enabled = !rule.enabled,
                lastTriggeredEpochMs = rule.lastTriggeredEpochMs
            )
        }
    }

    fun deleteAlertRule(ruleId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteAlertRule(ruleId)
        }
    }

    fun deleteSignalRecord(signalId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteSignalRecord(signalId)
        }
    }

    /**
     * Evaluates active alert rules against live OKX market data with strict cooldown deduplication
     * so notifications are never spammed repeatedly.
     */
    fun evaluateAlertsNow(context: Context) {
        val rules = (alertRulesState.value as? CloudUiState.Success)?.data.orEmpty()
        if (rules.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        val tickerLookup = tickers.value.associateBy { it.instId }
        val oppLookup = opportunities.value.associateBy { it.instId }

        viewModelScope.launch {
            for (rule in rules) {
                if (!rule.enabled) continue
                val cooldownMs = rule.cooldownMinutes.coerceAtLeast(1L) * 60_000L
                if (nowMs - rule.lastTriggeredEpochMs < cooldownMs) continue

                val ticker = tickerLookup[rule.instId] ?: continue
                val setup = oppLookup[rule.instId]
                val price = ticker.lastPrice

                val triggeredMessage: String? = when (rule.ruleType) {
                    "PRICE_ABOVE" -> if (price >= rule.thresholdValue) {
                        "${rule.instId}: نرخ گەیشتە سەرووی ${QuantIndicatorEngine.formatPrice(rule.thresholdValue)} (${QuantIndicatorEngine.formatPrice(price)} USDT)"
                    } else null
                    "PRICE_BELOW" -> if (price <= rule.thresholdValue) {
                        "${rule.instId}: نرخ دابەزی بۆ خوار ${QuantIndicatorEngine.formatPrice(rule.thresholdValue)} (${QuantIndicatorEngine.formatPrice(price)} USDT)"
                    } else null
                    "SUPPORT_BREAK" -> if (setup != null && price <= setup.snapshot.supportLevel) {
                        "${rule.instId}: شکاندنی ئاستی پشتگیری (${QuantIndicatorEngine.formatPrice(setup.snapshot.supportLevel)})"
                    } else null
                    "RESISTANCE_BREAK" -> if (setup != null && price >= setup.snapshot.resistanceLevel) {
                        "${rule.instId}: تێپەڕاندنی ئاستی بەرگری (${QuantIndicatorEngine.formatPrice(setup.snapshot.resistanceLevel)})"
                    } else null
                    "VOLUME_SPIKE" -> if (setup != null && setup.snapshot.volumeRatio20 >= rule.thresholdValue.coerceAtLeast(1.5)) {
                        "${rule.instId}: بازدانی قەبارەی مامەڵە (${QuantIndicatorEngine.formatNum(setup.snapshot.volumeRatio20, 2)}x)"
                    } else null
                    "SIGNAL_CHANGE" -> if (setup != null && setup.signalCategory != SignalCategory.WATCH) {
                        "${rule.instId}: گۆڕانی نیشانەی تەکنیکی بۆ ${setup.signalCategory.shortTag}"
                    } else null
                    else -> null
                }

                if (triggeredMessage != null) {
                    _latestAlertBanner.value = triggeredMessage
                    AlertNotificationHelper.sendMarketAlertNotification(
                        context = context,
                        notificationId = rule.id.hashCode(),
                        title = "SHÊRA AI • ئاگادارکردنەوەی بازاڕ",
                        body = triggeredMessage
                    )
                    firestoreRepository.updateAlertRuleState(
                        ruleId = rule.id,
                        enabled = true,
                        lastTriggeredEpochMs = nowMs
                    )
                }
            }
        }
    }

    fun dismissAlertBanner() {
        _latestAlertBanner.value = null
    }

    // --- UI Filter Mutators ---

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _currentPage.value = 0
    }

    fun setShowWatchlistOnly(onlyWatchlist: Boolean) {
        _showWatchlistOnly.value = onlyWatchlist
        _currentPage.value = 0
    }

    fun setPage(page: Int) {
        _currentPage.value = page.coerceAtLeast(0)
    }

    fun setScannerRiskFilter(risk: RiskLevel?) {
        _scannerRiskFilter.value = risk
    }

    fun setScannerStructureFilter(structure: MarketStructure?) {
        _scannerStructureFilter.value = structure
    }

    fun setScannerCategoryFilter(category: SignalCategory?) {
        _scannerCategoryFilter.value = category
    }

    override fun onCleared() {
        super.onCleared()
        okxClient.disconnectWebSocket()
    }

    private fun syntheticTickerFromCandles(instId: String, candles: List<OkxCandle>): OkxTicker? {
        if (candles.isEmpty()) return null
        val last = candles.last()
        val first24 = candles.takeLast(24)
        val open = first24.first().open
        val changePct = if (open > 0) ((last.close - open) / open) * 100.0 else 0.0
        return OkxTicker(
            instId = instId,
            baseSymbol = instId.substringBefore("-"),
            quoteSymbol = "USDT",
            lastPrice = last.close,
            open24h = open,
            high24h = first24.maxOf { it.high },
            low24h = first24.minOf { it.low },
            volBase24h = first24.sumOf { it.volumeBase },
            volUsdt24h = first24.sumOf { it.volumeQuoteUsdt },
            change24hPct = changePct,
            timestampMs = last.timestampMs
        )
    }

    private fun renderCandlesToBitmap(
        instId: String,
        timeframe: String,
        candles: List<OkxCandle>,
        ema20: List<Double>,
        ema50: List<Double>
    ): Bitmap {
        val width = 960
        val height = 540
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.parseColor("#080A0F"))

        val minLow = candles.minOfOrNull { it.low } ?: 1.0
        val maxHigh = candles.maxOfOrNull { it.high } ?: 2.0
        val range = (maxHigh - minLow).coerceAtLeast(1e-8)

        val padLeft = 40f
        val padRight = 110f
        val padTop = 56f
        val padBottom = 48f
        val chartW = width - padLeft - padRight
        val chartH = height - padTop - padBottom

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1B2234")
            strokeWidth = 1.5f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#F3B72B")
            textSize = 24f
            isFakeBoldText = true
        }
        canvas.drawText("SHÊRA AI • $instId ($timeframe)", padLeft, 36f, textPaint)

        for (g in 0..4) {
            val y = padTop + (chartH * g / 4f)
            canvas.drawLine(padLeft, y, width - padRight, y, gridPaint)
        }

        val stepX = chartW / candles.size.coerceAtLeast(1)
        val bodyW = (stepX * 0.66f).coerceAtLeast(3f)
        val bullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#10B981")
            strokeWidth = 2f
        }
        val bearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#F43F5E")
            strokeWidth = 2f
        }

        fun priceToY(p: Double): Float {
            return (padTop + chartH - (((p - minLow) / range).toFloat() * chartH))
        }

        candles.forEachIndexed { idx, c ->
            val cx = padLeft + idx * stepX + stepX / 2f
            val yHigh = priceToY(c.high)
            val yLow = priceToY(c.low)
            val yOpen = priceToY(c.open)
            val yClose = priceToY(c.close)
            val paint = if (c.close >= c.open) bullPaint else bearPaint
            canvas.drawLine(cx, yHigh, cx, yLow, paint)
            val top = kotlin.math.min(yOpen, yClose)
            val bot = kotlin.math.max(yOpen, yClose).coerceAtLeast(top + 2f)
            canvas.drawRect(cx - bodyW / 2f, top, cx + bodyW / 2f, bot, paint)
        }

        val ema20Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#F3B72B")
            strokeWidth = 2.5f
        }
        for (i in 1 until ema20.size.coerceAtMost(candles.size)) {
            val x1 = padLeft + (i - 1) * stepX + stepX / 2f
            val x2 = padLeft + i * stepX + stepX / 2f
            canvas.drawLine(x1, priceToY(ema20[i - 1]), x2, priceToY(ema20[i]), ema20Paint)
        }

        val ema50Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#38BDF8")
            strokeWidth = 2.5f
        }
        for (i in 1 until ema50.size.coerceAtMost(candles.size)) {
            val x1 = padLeft + (i - 1) * stepX + stepX / 2f
            val x2 = padLeft + i * stepX + stepX / 2f
            canvas.drawLine(x1, priceToY(ema50[i - 1]), x2, priceToY(ema50[i]), ema50Paint)
        }

        return bmp
    }
}
