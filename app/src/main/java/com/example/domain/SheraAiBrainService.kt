package com.example.domain

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiModelChoice
import com.example.model.AiVisionForecast
import com.example.model.DerivativesTelemetry
import com.example.model.OpportunitySetup
import com.example.model.RiskLevel
import com.example.model.SignalCategory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "SheraAiBrainService"

class SheraAiBrainService {

    private val forecastSchema = Schema.obj(
        properties = mapOf(
            "detectedSymbol" to Schema.string(description = "Detected crypto pair symbol e.g. BTC-USDT or CHART"),
            "detectedTimeframe" to Schema.string(description = "Detected chart timeframe e.g. 15m, 1H, 4H, 1D"),
            "signalCategory" to Schema.enumeration(
                values = listOf("BUY_SETUP", "SELL_SETUP", "WATCH"),
                description = "Technical setup classification: BUY_SETUP (BUY WATCH), SELL_SETUP (SELL WATCH), or WATCH (NEUTRAL)"
            ),
            "confidenceScore" to Schema.integer(description = "Signal Quality Score out of 100 (NOT profit probability)"),
            "riskLevel" to Schema.enumeration(
                values = listOf("LOW", "MEDIUM", "HIGH"),
                description = "Technical risk level"
            ),
            "marketStructureSummaryKu" to Schema.string(
                description = "Detailed market structure, candlestick & technical evidence explanation in Central Kurdish (Sorani)"
            ),
            "marketStructureSummaryEn" to Schema.string(
                description = "Detailed market structure, candlestick & technical evidence explanation in English"
            ),
            "detectedPatterns" to Schema.array(
                items = Schema.string(),
                description = "Detected candlestick or chart patterns and indicator evidence"
            ),
            "supportZone" to Schema.string(description = "Key support price zone"),
            "resistanceZone" to Schema.string(description = "Key resistance price zone"),
            "entryZone" to Schema.string(description = "Suggested technical entry price zone"),
            "invalidationLevel" to Schema.string(description = "Strict stop-loss / invalidation price level"),
            "target1" to Schema.string(description = "Conservative first target price T1"),
            "target2" to Schema.string(description = "Secondary target price T2"),
            "target3" to Schema.string(description = "Extended target price T3"),
            "riskRewardEstimate" to Schema.string(description = "Estimated Risk to Reward ratio e.g. 1:2.2"),
            "bullishScenarioKu" to Schema.string(
                description = "Bullish continuation/reversal scenario and confirmation conditions in Sorani Kurdish"
            ),
            "bullishScenarioEn" to Schema.string(
                description = "Bullish continuation/reversal scenario and confirmation conditions in English"
            ),
            "bearishScenarioKu" to Schema.string(
                description = "Bearish breakdown / invalidation risk scenario in Sorani Kurdish"
            ),
            "bearishScenarioEn" to Schema.string(
                description = "Bearish breakdown / invalidation risk scenario in English"
            )
        )
    )

    private val structuredConfig by lazy {
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = forecastSchema
        }
    }

    private val flashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelChoice.GEMINI_3_8_FLASH.modelId,
            generationConfig = structuredConfig
        )
    }

    private val argonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelChoice.GEMINI_4_ARGON.modelId,
            generationConfig = structuredConfig
        )
    }

    /**
     * Multimodal Chart Image + Optional Live OKX Quant & Derivatives Context Analysis.
     */
    suspend fun analyzeChartImage(
        chartBitmap: Bitmap,
        selectedModel: AiModelChoice,
        userQuestion: String,
        attachedLiveSetup: OpportunitySetup?,
        derivativesTelemetry: DerivativesTelemetry? = null
    ): Result<AiVisionForecast> {
        val scaledBitmap = scaleBitmapSafely(chartBitmap, maxDimension = 1280)
        val derivativesBlock = formatDerivativesBlock(derivativesTelemetry)
        val backtestBlock = formatBacktestBlock(attachedLiveSetup)

        val liveContextBlock = if (attachedLiveSetup != null) {
            val s = attachedLiveSetup.snapshot
            """
            Live OKX Market Grounding Data for ${attachedLiveSetup.instId} (${attachedLiveSetup.timeframe.bar}):
            - Last Price: ${QuantIndicatorEngine.formatPrice(s.currentPrice)} USDT (24h Change: ${QuantIndicatorEngine.formatNum(attachedLiveSetup.ticker.change24hPct, 2)}%)
            - 24h Volume: ${QuantIndicatorEngine.formatNum(attachedLiveSetup.ticker.volUsdt24h, 0)} USDT
            - RSI(14): ${QuantIndicatorEngine.formatNum(s.rsi14, 2)}
            - EMA(20): ${QuantIndicatorEngine.formatPrice(s.ema20)}, EMA(50): ${QuantIndicatorEngine.formatPrice(s.ema50)}, EMA(200): ${QuantIndicatorEngine.formatPrice(s.ema200)}
            - MACD(12,26,9): Line=${QuantIndicatorEngine.formatNum(s.macdLine, 4)}, Signal=${QuantIndicatorEngine.formatNum(s.macdSignal, 4)}, Hist=${QuantIndicatorEngine.formatNum(s.macdHistogram, 4)}
            - ATR(14): ${QuantIndicatorEngine.formatPrice(s.atr14)} (${QuantIndicatorEngine.formatNum(s.atrPct, 2)}%)
            - Calculated Support: ${QuantIndicatorEngine.formatPrice(s.supportLevel)}, Resistance: ${QuantIndicatorEngine.formatPrice(s.resistanceLevel)}
            $derivativesBlock
            $backtestBlock
            """.trimIndent()
        } else {
            "No live OKX pair explicitly linked; extract symbol, timeframe, and price levels directly from the chart image.\n$derivativesBlock"
        }

        val prompt = """
            You are SHÊRA AI (شێرە AI), an institutional cryptocurrency quantitative market analyst for OKX markets.
            Analyze the provided trading chart image with strict quantitative discipline:
            1. Identify the visible candlestick structure, trendlines, support/resistance zones, liquidity sweeps, order blocks, and any visible indicators (RSI, MACD, EMA, Volume).
            2. Combine visual chart reading with the live OKX data below (if provided):
            $liveContextBlock
            3. User's specific note/question: "${userQuestion.ifBlank { "Full technical chart analysis, entry, invalidation, and scenario forecast" }}"
            4. CRITICAL RULES:
               - Never guarantee profit. Every setup is probabilistic.
               - `confidenceScore` (0-100) represents Signal Quality & Technical Confluence Score ONLY, never profit probability.
               - Only mention historical win probability if verified backtest data is present above.
               - A price drop alone or a single candle spike is NOT a confirmed BUY WATCH setup; require multi-factor confluence.
               - Provide clear Entry Zone, Invalidation Level (Stop-Loss), and 3 realistic Targets (T1, T2, T3).
               - Write all Kurdish fields in clear, natural Central Kurdish (Sorani - کوردیی سۆرانی) and English fields in professional English.
        """.trimIndent()

        return executeStructuredInference(
            selectedModel = selectedModel,
            bitmap = scaledBitmap,
            prompt = prompt,
            attachedLiveSetup = attachedLiveSetup
        )
    }

    /**
     * Deep AI Market Analyst on a Live OKX Pair using real indicator + derivatives + backtest telemetry.
     */
    suspend fun analyzeLivePairWithAi(
        setup: OpportunitySetup,
        selectedModel: AiModelChoice,
        userQuestion: String,
        derivativesTelemetry: DerivativesTelemetry? = null
    ): Result<AiVisionForecast> {
        val s = setup.snapshot
        val derivativesBlock = formatDerivativesBlock(derivativesTelemetry)
        val backtestBlock = formatBacktestBlock(setup)

        val prompt = """
            You are SHÊRA AI (شێرە AI), an institutional cryptocurrency quantitative analyst for OKX SPOT/USDT markets.
            Perform a deep technical and quantitative forecast based on this REAL-TIME OKX market telemetry:
            - Instrument: ${setup.instId} | Timeframe: ${setup.timeframe.bar}
            - Current Price: ${QuantIndicatorEngine.formatPrice(s.currentPrice)} USDT
            - 24h High: ${QuantIndicatorEngine.formatPrice(setup.ticker.high24h)} | 24h Low: ${QuantIndicatorEngine.formatPrice(setup.ticker.low24h)}
            - 24h Volume: ${QuantIndicatorEngine.formatNum(setup.ticker.volUsdt24h, 0)} USDT | 24h Change: ${QuantIndicatorEngine.formatNum(setup.ticker.change24hPct, 2)}%
            - RSI(14): ${QuantIndicatorEngine.formatNum(s.rsi14, 2)}
            - EMA(20): ${QuantIndicatorEngine.formatPrice(s.ema20)} | EMA(50): ${QuantIndicatorEngine.formatPrice(s.ema50)} | EMA(200): ${QuantIndicatorEngine.formatPrice(s.ema200)}
            - MACD(12,26,9): Line=${QuantIndicatorEngine.formatNum(s.macdLine, 5)}, Signal=${QuantIndicatorEngine.formatNum(s.macdSignal, 5)}, Histogram=${QuantIndicatorEngine.formatNum(s.macdHistogram, 5)}
            - ATR(14): ${QuantIndicatorEngine.formatPrice(s.atr14)} (${QuantIndicatorEngine.formatNum(s.atrPct, 2)}%)
            - 20-Bar Volume Ratio: ${QuantIndicatorEngine.formatNum(s.volumeRatio20, 2)}x | 3-Bar Move: ${QuantIndicatorEngine.formatNum(s.rapidMovePct3Bars, 2)}%
            - Structural Support: ${QuantIndicatorEngine.formatPrice(s.supportLevel)} | Structural Resistance: ${QuantIndicatorEngine.formatPrice(s.resistanceLevel)}
            - Algorithmic Classification: ${setup.signalCategory.shortTag} (Signal Quality Score: ${setup.confluenceScore}/100, Risk: ${setup.riskLevel.code})
            $derivativesBlock
            $backtestBlock
            - User Note: "${userQuestion.ifBlank { "Provide institutional technical breakdown, entry, invalidation, and targets" }}"

            CRITICAL RULES:
            - Never claim guaranteed profit.
            - `confidenceScore` (0-100) is strictly a Signal Quality Score, NOT a probability of profit.
            - Only mention historical win probability if verified by Walk-Forward Backtest above.
            - A price drop alone is NEVER a buy signal; require multi-indicator confluence.
            - Explain both bullish and bearish invalidation scenarios in Sorani Kurdish (کوردیی سۆرانی) and English.
        """.trimIndent()

        return executeStructuredInference(
            selectedModel = selectedModel,
            bitmap = null,
            prompt = prompt,
            attachedLiveSetup = setup
        )
    }

    private fun formatDerivativesBlock(telemetry: DerivativesTelemetry?): String {
        if (telemetry == null || !telemetry.isAvailable) {
            return "- Derivatives (Funding/OI/Liquidations): Not available for this pair (Zero fake data)."
        }
        val fr = telemetry.fundingRatePct?.let { "${QuantIndicatorEngine.formatNum(it, 4)}%" } ?: "N/A"
        val oi = telemetry.openInterestUsdt?.let { "$${QuantIndicatorEngine.formatNum(it, 0)} USDT" } ?: "N/A"
        val liq = "Long Liq=${QuantIndicatorEngine.formatNum(telemetry.recentLongLiqBase ?: 0.0, 2)}, Short Liq=${QuantIndicatorEngine.formatNum(telemetry.recentShortLiqBase ?: 0.0, 2)}"
        return "- Public Derivatives (${telemetry.swapInstId}): Funding Rate=$fr | Open Interest=$oi | Recent Liquidations ($liq)"
    }

    private fun formatBacktestBlock(setup: OpportunitySetup?): String {
        val wr = setup?.verifiedBacktestWinRatePct
        val count = setup?.verifiedBacktestSampleSize ?: 0
        return if (wr != null && count > 0) {
            "- Verified Walk-Forward Backtest on Historical Candles: Win Rate=${QuantIndicatorEngine.formatNum(wr, 1)}% across $count trades (Out-of-Sample Return: ${QuantIndicatorEngine.formatNum(setup.verifiedOutOfSampleReturnPct ?: 0.0, 2)}%)"
        } else {
            "- Historical Backtest Win Probability: Unverified (Not yet backtested on historical candles; do NOT state a historical win rate percentage)."
        }
    }

    private suspend fun executeStructuredInference(
        selectedModel: AiModelChoice,
        bitmap: Bitmap?,
        prompt: String,
        attachedLiveSetup: OpportunitySetup?
    ): Result<AiVisionForecast> {
        val primaryModel = when (selectedModel) {
            AiModelChoice.GEMINI_3_8_FLASH -> flashModel
            AiModelChoice.GEMINI_4_ARGON -> argonModel
        }

        return try {
            val (jsonText, actualModelLabel) = try {
                val resp = withTimeout(60_000L) {
                    if (bitmap != null) {
                        primaryModel.generateContent(
                            content {
                                image(bitmap)
                                text(prompt)
                            }
                        )
                    } else {
                        primaryModel.generateContent(prompt)
                    }
                }
                (resp.text ?: throw IllegalStateException("Empty AI response")) to selectedModel.modelId
            } catch (e: CancellationException) {
                throw e
            } catch (primaryErr: Exception) {
                if (selectedModel == AiModelChoice.GEMINI_4_ARGON) {
                    Log.w(TAG, "Gemini 4 Argon unavailable (${primaryErr.message}); falling back to gemini-3.8-flash")
                    val fallbackResp = withTimeout(60_000L) {
                        if (bitmap != null) {
                            flashModel.generateContent(
                                content {
                                    image(bitmap)
                                    text(prompt)
                                }
                            )
                        } else {
                            flashModel.generateContent(prompt)
                        }
                    }
                    (fallbackResp.text ?: throw IllegalStateException("Empty AI response")) to
                        "${AiModelChoice.GEMINI_3_8_FLASH.modelId} (Auto-Fallback)"
                } else {
                    throw primaryErr
                }
            }

            val parsed = parseForecastJson(jsonText, actualModelLabel, attachedLiveSetup)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI analysis timed out after 60s", e)
            Result.failure(IllegalStateException("شیکاری زیرەکی دەستکرد کاتی تەواو بوو (60s Timeout). تکایە دووبارە هەوڵ بدەرەوە."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic inference failed", e)
            Result.failure(e)
        }
    }

    private fun parseForecastJson(
        jsonString: String,
        modelUsed: String,
        attachedLiveSetup: OpportunitySetup?
    ): AiVisionForecast {
        val json = JSONObject(jsonString)
        val catStr = json.optString("signalCategory", "WATCH")
        val signalCategory = SignalCategory.entries.firstOrNull { it.code == catStr } ?: SignalCategory.WATCH
        val riskStr = json.optString("riskLevel", "MEDIUM")
        val riskLevel = RiskLevel.entries.firstOrNull { it.code == riskStr } ?: RiskLevel.MEDIUM

        val patternsArr = json.optJSONArray("detectedPatterns")
        val patterns = if (patternsArr != null) {
            (0 until patternsArr.length()).mapNotNull { idx ->
                patternsArr.optString(idx).takeIf { it.isNotBlank() }
            }
        } else {
            emptyList()
        }

        return AiVisionForecast(
            detectedSymbol = json.optString("detectedSymbol", "OKX-SPOT"),
            detectedTimeframe = json.optString("detectedTimeframe", "1H"),
            modelUsed = modelUsed,
            signalCategory = signalCategory,
            confidenceScore = json.optInt("confidenceScore", 60).coerceIn(0, 100),
            riskLevel = riskLevel,
            marketStructureSummaryKu = json.optString("marketStructureSummaryKu", ""),
            marketStructureSummaryEn = json.optString("marketStructureSummaryEn", ""),
            detectedPatterns = patterns,
            supportZone = json.optString("supportZone", "-"),
            resistanceZone = json.optString("resistanceZone", "-"),
            entryZone = json.optString("entryZone", "-"),
            invalidationLevel = json.optString("invalidationLevel", "-"),
            target1 = json.optString("target1", "-"),
            target2 = json.optString("target2", "-"),
            target3 = json.optString("target3", "-"),
            riskRewardEstimate = json.optString("riskRewardEstimate", "1:1.8"),
            bullishScenarioKu = json.optString("bullishScenarioKu", ""),
            bullishScenarioEn = json.optString("bullishScenarioEn", ""),
            bearishScenarioKu = json.optString("bearishScenarioKu", ""),
            bearishScenarioEn = json.optString("bearishScenarioEn", ""),
            verifiedBacktestWinRatePct = attachedLiveSetup?.verifiedBacktestWinRatePct,
            verifiedBacktestTrades = attachedLiveSetup?.verifiedBacktestSampleSize ?: 0
        )
    }

    private fun scaleBitmapSafely(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = width.toFloat() / height.toFloat()
        val (targetW, targetH) = if (width > height) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
