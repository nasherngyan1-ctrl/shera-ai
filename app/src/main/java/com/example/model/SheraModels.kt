package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppLanguage(val code: String, val isRtl: Boolean, val label: String) {
    KURDISH_SORANI("ckb", true, "کوردی"),
    ENGLISH("en", false, "EN")
}

enum class OkxTimeframe(val bar: String, val tvInterval: String, val labelKu: String, val labelEn: String) {
    TF_1M("1m", "1", "١ خولەک", "1m"),
    TF_5M("5m", "5", "٥ خولەک", "5m"),
    TF_15M("15m", "15", "١٥ خولەک", "15m"),
    TF_1H("1H", "60", "١ کاتژمێر", "1H"),
    TF_4H("4H", "240", "٤ کاتژمێر", "4H"),
    TF_1D("1D", "D", "١ ڕۆژ", "1D");

    companion object {
        fun fromBar(bar: String): OkxTimeframe = entries.firstOrNull { it.bar.equals(bar, ignoreCase = true) } ?: TF_1H
    }
}

enum class ChartDisplayMode(val labelKu: String, val labelEn: String) {
    OKX_NATIVE_CANVAS("گرافی ڕاستەوخۆی OKX", "OKX Native Chart"),
    TRADINGVIEW_OFFICIAL("گرافی فەرمی TradingView", "TradingView Official")
}

/**
 * Preserves Firestore enum codes (BUY_SETUP, SELL_SETUP, WATCH) while surfacing
 * explicit BUY WATCH, SELL WATCH, and NEUTRAL classifications in UI and AI analyst reports.
 */
enum class SignalCategory(val code: String, val shortTag: String, val labelKu: String, val labelEn: String) {
    BUY_SETUP(
        code = "BUY_SETUP",
        shortTag = "BUY WATCH",
        labelKu = "BUY WATCH • چاودێری کڕین (بێ دڵنیایی)",
        labelEn = "BUY WATCH (Probabilistic Setup)"
    ),
    SELL_SETUP(
        code = "SELL_SETUP",
        shortTag = "SELL WATCH",
        labelKu = "SELL WATCH • چاودێری فرۆشتن / قازانج",
        labelEn = "SELL WATCH (Resistance / Exit)"
    ),
    WATCH(
        code = "WATCH",
        shortTag = "NEUTRAL",
        labelKu = "NEUTRAL • بێلایەن و چاودێری",
        labelEn = "NEUTRAL (Wait for Confluence)"
    )
}

enum class RiskLevel(val code: String, val labelKu: String, val labelEn: String) {
    LOW("LOW", "مەترسی نزم", "Low Risk"),
    MEDIUM("MEDIUM", "مەترسی مامناوەند", "Medium Risk"),
    HIGH("HIGH", "مەترسی بەرز", "High Risk")
}

enum class MarketStructure(val labelKu: String, val labelEn: String) {
    UPTREND("ئاراستەی بەرزبوونەوە (Uptrend)", "Bullish Structure (Uptrend)"),
    DOWNTREND("ئاراستەی دابەزین (Downtrend)", "Bearish Structure (Downtrend)"),
    RANGING("بازاڕی ئاسۆیی (Ranging)", "Sideways / Ranging")
}

enum class AiModelChoice(
    val modelId: String,
    val titleKu: String,
    val titleEn: String,
    val badgeKu: String,
    val badgeEn: String
) {
    GEMINI_3_8_FLASH(
        modelId = "gemini-3.8-flash",
        titleKu = "Gemini 3.8 Flash (پێشنیارکراو بۆ خێرایی و وێنە)",
        titleEn = "Gemini 3.8 Flash (Fast Multimodal Quant)",
        badgeKu = "خێرا و ورد • بێ پلانی پارە",
        badgeEn = "Fast Vision + Quant"
    ),
    GEMINI_4_ARGON(
        modelId = "gemini-4-argon",
        titleKu = "Gemini 4 Argon (قووڵترین مێشکی ستراتیژی و بیرکاری)",
        titleEn = "Gemini 4 Argon (Deep Quant Reasoning)",
        badgeKu = "شیکاری قووڵ + گەڕانەوەی خۆکار",
        badgeEn = "Deep Quant + Auto Fallback"
    )
}

enum class ConnectionStateType {
    LIVE_WS,
    REST_POLLING,
    RECONNECTING,
    STALE_WARNING,
    ERROR
}

data class ConnectionStatus(
    val state: ConnectionStateType = ConnectionStateType.REST_POLLING,
    val lastUpdatedEpochMs: Long = 0L,
    val activeSpotPairsCount: Int = 0,
    val reconnectAttempt: Int = 0,
    val cachedPairsCount: Int = 0,
    val errorMessage: String? = null
) {
    fun isStale(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return lastUpdatedEpochMs > 0L && (nowEpochMs - lastUpdatedEpochMs) > 45_000L
    }
}

data class OkxTicker(
    val instId: String,
    val baseSymbol: String,
    val quoteSymbol: String,
    val lastPrice: Double,
    val open24h: Double,
    val high24h: Double,
    val low24h: Double,
    val volBase24h: Double,
    val volUsdt24h: Double,
    val change24hPct: Double,
    val timestampMs: Long
)

data class OkxCandle(
    val timestampMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volumeBase: Double,
    val volumeQuoteUsdt: Double,
    val isConfirmed: Boolean
)

/**
 * Optional CoinGlass & Public Derivatives Telemetry (Funding Rate, Open Interest, Liquidations).
 * Never fabricates data: if a pair has no perpetual swap or an endpoint is unavailable,
 * fields remain null and the UI clearly states unavailability.
 */
data class DerivativesTelemetry(
    val instId: String,
    val swapInstId: String,
    val isAvailable: Boolean,
    val fundingRatePct: Double? = null,
    val nextFundingRatePct: Double? = null,
    val openInterestUsdt: Double? = null,
    val openInterestContracts: Double? = null,
    val recentLongLiqBase: Double? = null,
    val recentShortLiqBase: Double? = null,
    val recentLiqCount: Int = 0,
    val sourceStatusKu: String = "داتای گشتیی فەرمی OKX Derivatives (بێ کلیلی پارەدراوی CoinGlass لە فرۆنت-ئێند)",
    val sourceStatusEn: String = "Official OKX Public Derivatives Telemetry (No Paid CoinGlass Key in Frontend)",
    val unavailableReasonKu: String? = null,
    val unavailableReasonEn: String? = null,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

data class TechnicalSnapshot(
    val instId: String,
    val timeframe: OkxTimeframe,
    val currentPrice: Double,
    val rsi14: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val macdLine: Double,
    val macdSignal: Double,
    val macdHistogram: Double,
    val prevMacdHistogram: Double,
    val atr14: Double,
    val atrPct: Double,
    val volumeRatio20: Double,
    val isVolumeAnomaly: Boolean,
    val rapidMovePct3Bars: Double,
    val isRapidPriceMove: Boolean,
    val supportLevel: Double,
    val resistanceLevel: Double,
    val marketStructure: MarketStructure,
    val ema20Series: List<Double>,
    val ema50Series: List<Double>,
    val ema200Series: List<Double>,
    val analyzedAtMs: Long = System.currentTimeMillis()
)

data class OpportunitySetup(
    val instId: String,
    val timeframe: OkxTimeframe,
    val ticker: OkxTicker,
    val snapshot: TechnicalSnapshot,
    val signalCategory: SignalCategory,
    val confluenceScore: Int, // Signal Quality Score out of 100 (NOT profit probability)
    val riskLevel: RiskLevel,
    val entryZoneLow: Double,
    val entryZoneHigh: Double,
    val invalidationPrice: Double,
    val target1Price: Double,
    val target2Price: Double,
    val riskRewardRatio: Double,
    val reasonsKurdish: List<String>,
    val reasonsEnglish: List<String>,
    val singleCandleWarning: Boolean,
    val verifiedBacktestWinRatePct: Double? = null,
    val verifiedBacktestSampleSize: Int = 0,
    val verifiedOutOfSampleReturnPct: Double? = null,
    val analyzedAtMs: Long = System.currentTimeMillis()
)

data class AiVisionForecast(
    val detectedSymbol: String,
    val detectedTimeframe: String,
    val modelUsed: String,
    val signalCategory: SignalCategory,
    val confidenceScore: Int, // Signal Quality Score out of 100 (NOT profit probability)
    val riskLevel: RiskLevel,
    val marketStructureSummaryKu: String,
    val marketStructureSummaryEn: String,
    val detectedPatterns: List<String>,
    val supportZone: String,
    val resistanceZone: String,
    val entryZone: String,
    val invalidationLevel: String,
    val target1: String,
    val target2: String,
    val target3: String,
    val riskRewardEstimate: String,
    val bullishScenarioKu: String,
    val bullishScenarioEn: String,
    val bearishScenarioKu: String,
    val bearishScenarioEn: String,
    val verifiedBacktestWinRatePct: Double? = null,
    val verifiedBacktestTrades: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
)

data class BacktestTrade(
    val entryTimeMs: Long,
    val exitTimeMs: Long,
    val entryPrice: Double,
    val exitPrice: Double,
    val grossReturnPct: Double,
    val netReturnPct: Double,
    val exitReasonKu: String,
    val exitReasonEn: String,
    val isOutOfSample: Boolean = false
)

data class BacktestReport(
    val instId: String,
    val timeframe: OkxTimeframe,
    val candleCount: Int,
    val feePctPerSide: Double,
    val slippagePctPerSide: Double,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePct: Double,
    val netCumulativeReturnPct: Double,
    val maxDrawdownPct: Double,
    val profitFactor: Double,
    val trades: List<BacktestTrade>,
    val inSampleTrades: Int = 0,
    val inSampleReturnPct: Double = 0.0,
    val outOfSampleTrades: Int = 0,
    val outOfSampleWinRatePct: Double = 0.0,
    val outOfSampleReturnPct: Double = 0.0,
    val totalFeesAndSlippagePct: Double = 0.0,
    val executedAtMs: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class WatchlistItem(
    @DocumentId val id: String = "",
    val instId: String = "",
    val userId: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AlertRule(
    @DocumentId val id: String = "",
    val instId: String = "",
    val userId: String = "",
    val ruleType: String = "PRICE_ABOVE",
    val thresholdValue: Double = 0.0,
    val timeframe: String = "1H",
    val enabled: Boolean = true,
    val cooldownMinutes: Long = 30L,
    val lastTriggeredEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SignalRecord(
    @DocumentId val id: String = "",
    val instId: String = "",
    val userId: String = "",
    val sourceType: String = "LIVE_QUANT",
    val timeframe: String = "1H",
    val signalCategory: String = "WATCH",
    val confluenceScore: Long = 50L,
    val riskLevel: String = "MEDIUM",
    val entryPrice: Double = 0.0,
    val invalidationPrice: Double = 0.0,
    val target1Price: Double = 0.0,
    val target2Price: Double = 0.0,
    val summaryKurdish: String = "",
    val summaryEnglish: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
