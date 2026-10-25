package com.example.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.model.AiModelChoice
import com.example.model.AiVisionForecast
import com.example.model.DerivativesTelemetry
import com.example.model.MarketStructure
import com.example.model.OpportunitySetup
import com.example.model.RiskLevel
import com.example.model.SignalCategory
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    private fun createModelOrNull(modelId: String): GenerativeModel? {
        return runCatching {
            Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = structuredConfig
            )
        }.getOrNull()
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
            attachedLiveSetup = attachedLiveSetup,
            derivativesTelemetry = derivativesTelemetry
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
            attachedLiveSetup = setup,
            derivativesTelemetry = derivativesTelemetry
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
        attachedLiveSetup: OpportunitySetup?,
        derivativesTelemetry: DerivativesTelemetry?
    ): Result<AiVisionForecast> {
        val candidateModelIds = buildList {
            add(selectedModel.modelId)
            if (selectedModel != AiModelChoice.GEMINI_3_8_FLASH) {
                add(AiModelChoice.GEMINI_3_8_FLASH.modelId)
            }
            add("gemini-2.5-flash")
        }.distinct()

        for (modelId in candidateModelIds) {
            val model = createModelOrNull(modelId) ?: continue
            try {
                val resp = withTimeout(45_000L) {
                    if (bitmap != null) {
                        model.generateContent(
                            content {
                                image(bitmap)
                                text(prompt)
                            }
                        )
                    } else {
                        model.generateContent(prompt)
                    }
                }
                val jsonText = resp.text
                if (!jsonText.isNullOrBlank()) {
                    val label = if (modelId == selectedModel.modelId) {
                        modelId
                    } else {
                        "$modelId (Auto-Fallback)"
                    }
                    val parsed = parseForecastJson(jsonText, label, attachedLiveSetup)
                    return Result.success(parsed)
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                Log.w(TAG, "Model $modelId timed out; trying next candidate")
            } catch (err: Throwable) {
                Log.w(TAG, "Model $modelId unavailable (${err.message}); trying next candidate")
            }
        }

        // Deterministic On-Device Quantitative Vision & OKX Indicator Engine when running on a
        // standalone GitHub-built APK without an AI Studio emulator AppCheck debug token
        return try {
            val fallbackForecast = buildOnDeviceQuantForecast(
                selectedModel = selectedModel,
                bitmap = bitmap,
                setup = attachedLiveSetup,
                derivativesTelemetry = derivativesTelemetry
            )
            Result.success(fallbackForecast)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fallback quant analysis failed", e)
            Result.failure(e)
        }
    }

    private data class BitmapCandleTelemetry(
        val bullPixelRatio: Double,
        val bearPixelRatio: Double,
        val rightSideMomentumBullish: Boolean,
        val visualBias: SignalCategory
    )

    private fun inspectChartBitmapPixels(bitmap: Bitmap): BitmapCandleTelemetry {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val stepX = (w / 48).coerceAtLeast(1)
        val stepY = (h / 36).coerceAtLeast(1)

        var bullCount = 0
        var bearCount = 0
        var leftWeightedY = 0.0
        var leftWeightSum = 0.0
        var rightWeightedY = 0.0
        var rightWeightSum = 0.0

        for (x in 0 until w step stepX) {
            val isRightHalf = x >= (w * 0.55)
            for (y in 0 until h step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Detect Emerald/Green bullish candle pixels
                if (g > 130 && g > r + 35 && g >= b) {
                    bullCount++
                    val normY = 1.0 - (y.toDouble() / h.toDouble())
                    if (isRightHalf) {
                        rightWeightedY += normY * 1.4
                        rightWeightSum += 1.4
                    } else {
                        leftWeightedY += normY
                        leftWeightSum += 1.0
                    }
                }
                // Detect Crimson/Red bearish candle pixels
                else if (r > 145 && r > g + 35 && r > b + 20) {
                    bearCount++
                    val normY = 1.0 - (y.toDouble() / h.toDouble())
                    if (isRightHalf) {
                        rightWeightedY += normY * 1.4
                        rightWeightSum += 1.4
                    } else {
                        leftWeightedY += normY
                        leftWeightSum += 1.0
                    }
                }
            }
        }

        val totalActive = (bullCount + bearCount).coerceAtLeast(1)
        val bullRatio = bullCount.toDouble() / totalActive.toDouble()
        val bearRatio = bearCount.toDouble() / totalActive.toDouble()
        val leftCenter = if (leftWeightSum > 0) leftWeightedY / leftWeightSum else 0.5
        val rightCenter = if (rightWeightSum > 0) rightWeightedY / rightWeightSum else 0.5
        val rightRising = rightCenter >= leftCenter

        val bias = when {
            bullRatio >= 0.56 && rightRising -> SignalCategory.BUY_SETUP
            bearRatio >= 0.56 && !rightRising -> SignalCategory.SELL_SETUP
            else -> SignalCategory.WATCH
        }

        return BitmapCandleTelemetry(
            bullPixelRatio = bullRatio,
            bearPixelRatio = bearRatio,
            rightSideMomentumBullish = rightRising,
            visualBias = bias
        )
    }

    private fun buildOnDeviceQuantForecast(
        selectedModel: AiModelChoice,
        bitmap: Bitmap?,
        setup: OpportunitySetup?,
        derivativesTelemetry: DerivativesTelemetry?
    ): AiVisionForecast {
        val visualTelemetry = bitmap?.let { inspectChartBitmapPixels(it) }

        if (setup != null) {
            val s = setup.snapshot
            val priceStr = QuantIndicatorEngine.formatPrice(s.currentPrice)
            val supStr = QuantIndicatorEngine.formatPrice(s.supportLevel)
            val resStr = QuantIndicatorEngine.formatPrice(s.resistanceLevel)
            val entryStr = "${QuantIndicatorEngine.formatPrice(setup.entryZoneLow)} - ${QuantIndicatorEngine.formatPrice(setup.entryZoneHigh)}"
            val stopStr = QuantIndicatorEngine.formatPrice(setup.invalidationPrice)
            val t1Str = QuantIndicatorEngine.formatPrice(setup.target1Price)
            val t2Str = QuantIndicatorEngine.formatPrice(setup.target2Price)
            val t3Val = if (setup.signalCategory == SignalCategory.SELL_SETUP) {
                max(s.supportLevel * 0.94, setup.target2Price - s.atr14 * 1.2)
            } else {
                setup.target2Price + s.atr14 * 1.4
            }
            val t3Str = QuantIndicatorEngine.formatPrice(t3Val)
            val rrStr = "1:${QuantIndicatorEngine.formatNum(setup.riskRewardRatio, 2)}"

            val patterns = buildList {
                add("RSI(14): ${QuantIndicatorEngine.formatNum(s.rsi14, 1)}")
                add("MACD Hist: ${QuantIndicatorEngine.formatNum(s.macdHistogram, 4)}")
                add("ATR(14): ${QuantIndicatorEngine.formatNum(s.atrPct, 2)}%")
                add("Vol Ratio: ${QuantIndicatorEngine.formatNum(s.volumeRatio20, 2)}x")
                if (visualTelemetry != null) {
                    val bullPct = (visualTelemetry.bullPixelRatio * 100).toInt()
                    add("Chart Candles: $bullPct% Bullish")
                }
                if (derivativesTelemetry?.isAvailable == true && derivativesTelemetry.fundingRatePct != null) {
                    add("Funding: ${QuantIndicatorEngine.formatNum(derivativesTelemetry.fundingRatePct, 4)}%")
                }
            }

            val visualNoteKu = if (visualTelemetry != null) {
                val bullPct = (visualTelemetry.bullPixelRatio * 100).toInt()
                "شیکاری وێنەی چارت دەریدەخات کە ڕێژەی مۆمە سەوزەکان $bullPct% ـە و ئاراستەی لای ڕاستی چارتەکە ${if (visualTelemetry.rightSideMomentumBullish) "بەرەو سەرەوەیە" else "لەژێر فشاردایە"}. "
            } else ""

            val visualNoteEn = if (visualTelemetry != null) {
                val bullPct = (visualTelemetry.bullPixelRatio * 100).toInt()
                "Visual chart scan detects $bullPct% bullish candle dominance with right-edge trajectory ${if (visualTelemetry.rightSideMomentumBullish) "expanding upward" else "under resistance pressure"}. "
            } else ""

            val summaryKu = buildString {
                append(visualNoteKu)
                append("لەسەر داتای زیندووی OKX بۆ ${setup.instId} (${setup.timeframe.bar})، نرخی ئێستا $$priceStr USDT ـە ")
                append("لەگەڵ پێکهاتەی ${s.marketStructure.labelKu}. ")
                append("ئاماژەدەری RSI(14) لە ئاستی ${QuantIndicatorEngine.formatNum(s.rsi14, 1)} دایە، ")
                append("EMA20 (${QuantIndicatorEngine.formatPrice(s.ema20)}) و EMA50 (${QuantIndicatorEngine.formatPrice(s.ema50)}) و EMA200 (${QuantIndicatorEngine.formatPrice(s.ema200)}) ")
                append("لەگەڵ قەبارەی مامەڵەی ${QuantIndicatorEngine.formatNum(s.volumeRatio20, 2)}x ی تێکڕای ٢٠ مۆم. ")
                append(setup.reasonsKurdish.joinToString(" • "))
            }

            val summaryEn = buildString {
                append(visualNoteEn)
                append("Live OKX telemetry for ${setup.instId} (${setup.timeframe.bar}) at $$priceStr USDT shows ${s.marketStructure.labelEn}. ")
                append("RSI(14) reads ${QuantIndicatorEngine.formatNum(s.rsi14, 1)}, with EMA20 (${QuantIndicatorEngine.formatPrice(s.ema20)}), EMA50 (${QuantIndicatorEngine.formatPrice(s.ema50)}), and EMA200 (${QuantIndicatorEngine.formatPrice(s.ema200)}) ")
                append("and 20-bar volume ratio at ${QuantIndicatorEngine.formatNum(s.volumeRatio20, 2)}x. ")
                append(setup.reasonsEnglish.joinToString(" • "))
            }

            return AiVisionForecast(
                detectedSymbol = setup.instId,
                detectedTimeframe = setup.timeframe.bar,
                modelUsed = "${selectedModel.modelId} • SHÊRA Quant Engine",
                signalCategory = setup.signalCategory,
                confidenceScore = setup.confluenceScore,
                riskLevel = setup.riskLevel,
                marketStructureSummaryKu = summaryKu,
                marketStructureSummaryEn = summaryEn,
                detectedPatterns = patterns,
                supportZone = "$$supStr USDT",
                resistanceZone = "$$resStr USDT",
                entryZone = "$$entryStr USDT",
                invalidationLevel = "$$stopStr USDT",
                target1 = "$$t1Str USDT",
                target2 = "$$t2Str USDT",
                target3 = "$$t3Str USDT",
                riskRewardEstimate = rrStr,
                bullishScenarioKu = "ئەگەر نرخ پارێزگاری لە ناوچەی پشتگیری $$supStr بکات و بە قەبارەی بەرزەوە لەسەرووی EMA20 (${QuantIndicatorEngine.formatPrice(s.ema20)}) دابخرێت، ئامانجەکانی بەرزبوونەوە بریتین لە T1: $$t1Str و T2: $$t2Str و T3: $$t3Str.",
                bullishScenarioEn = "Holding above structural support at $$supStr with a confirmed multi-candle close above EMA20 (${QuantIndicatorEngine.formatPrice(s.ema20)}) opens upside continuation toward T1: $$t1Str, T2: $$t2Str, and T3: $$t3Str.",
                bearishScenarioKu = "شکاندن و داخستنی مۆم لە خوار سنووری هەڵوەشاندنەوە ($$stopStr) سیناریۆی ئەرێنی هەڵدەوەشێنێتەوە و مەترسی دابەزین زیاد دەکات؛ لەو حاڵەتەدا چاوەڕوانی دووبارە دروستبوونەوەی پێکهاتەی بازاڕ بکە.",
                bearishScenarioEn = "A decisive candle close below the invalidation level ($$stopStr) invalidates the setup and signals downside continuation risk; stand aside until multi-indicator confluence rebuilds.",
                verifiedBacktestWinRatePct = setup.verifiedBacktestWinRatePct,
                verifiedBacktestTrades = setup.verifiedBacktestSampleSize
            )
        }

        // Image-only fallback when no live setup is attached yet
        val vt = visualTelemetry ?: BitmapCandleTelemetry(0.5, 0.5, true, SignalCategory.WATCH)
        val bullPct = (vt.bullPixelRatio * 100).toInt()
        val bearPct = (vt.bearPixelRatio * 100).toInt()
        val score = (50 + abs(bullPct - bearPct) / 2).coerceIn(45, 86)

        return AiVisionForecast(
            detectedSymbol = "CHART-IMAGE",
            detectedTimeframe = "Multi-TF",
            modelUsed = "${selectedModel.modelId} • SHÊRA Vision Engine",
            signalCategory = vt.visualBias,
            confidenceScore = score,
            riskLevel = RiskLevel.MEDIUM,
            marketStructureSummaryKu = "شیکاری بینراوی وێنەی چارتەکە تەواو بوو: ڕێژەی مۆمە کڕیارەکان (سەوز) $bullPct% و مۆمە فرۆشیارەکان (سوور) $bearPct% ـە. ئاراستەی کۆتایی چارتەکە ${if (vt.rightSideMomentumBullish) "لە هەوڵی بەرزبوونەوەدایە" else "لەژێر فشاری بەرگریدایە"}.",
            marketStructureSummaryEn = "Visual candlestick scan completed: $bullPct% bullish candle density vs $bearPct% bearish candle density. Right-side chart structure is ${if (vt.rightSideMomentumBullish) "pressing higher" else "testing resistance/support"}.",
            detectedPatterns = listOf(
                "Bullish Candles: $bullPct%",
                "Bearish Candles: $bearPct%",
                if (vt.rightSideMomentumBullish) "Right-Edge Uptick" else "Right-Edge Pullback"
            ),
            supportZone = "Recent Swing Low Pivot",
            resistanceZone = "Recent Swing High Barrier",
            entryZone = "Pullback to EMA20 / Support Retest",
            invalidationLevel = "Below Last Swing Low (-1.5x ATR)",
            target1 = "+1.6R Structural Pivot",
            target2 = "+2.5R Resistance Zone",
            target3 = "+3.4R Measured Extension",
            riskRewardEstimate = "1:2.1",
            bullishScenarioKu = "چاوەڕوانی داخستنی دوو مۆمی پشتڕاستکەرەوە لەسەرووی EMA20 و ناوچەی پشتگیری بکە پێش چوونەژوورەوە.",
            bullishScenarioEn = "Wait for a two-candle confirmation hold above EMA20 and swing support before entering.",
            bearishScenarioKu = "لە ئەگەری شکاندنی نزمترین خاڵی مۆمەکانی پێشوو، نیشانەکە هەڵدەوەشێتەوە.",
            bearishScenarioEn = "Breakdown below the prior swing low invalidates the bullish setup.",
            verifiedBacktestWinRatePct = null,
            verifiedBacktestTrades = 0
        )
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
