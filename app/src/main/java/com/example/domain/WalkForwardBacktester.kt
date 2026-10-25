package com.example.domain

import com.example.model.BacktestReport
import com.example.model.BacktestTrade
import com.example.model.OkxCandle
import com.example.model.OkxTicker
import com.example.model.OkxTimeframe
import com.example.model.SignalCategory
import kotlin.math.abs
import kotlin.math.max

object WalkForwardBacktester {

    /**
     * Walk-forward historical backtesting engine with:
     * - Strict Anti-Look-Ahead Bias: At step `i`, only candles `0..i` are visible to the indicator engine.
     * - If a BUY WATCH (BUY_SETUP) triggers on closed candle `i`, entry executes at `candles[i + 1].open`
     *   adjusted for entry slippage and fee.
     * - Stop-loss (`invalidationPrice`) and Target (`target1Price`) are evaluated strictly on bars `i + 1 .. N`.
     * - Explicit In-Sample (70%) vs. Out-of-Sample (30% — داتای دەرەوەی نموونە) evaluation split.
     */
    fun runBacktest(
        instId: String,
        timeframe: OkxTimeframe,
        candles: List<OkxCandle>,
        feePctPerSide: Double = 0.10,
        slippagePctPerSide: Double = 0.05
    ): BacktestReport {
        if (candles.size < 45) {
            return BacktestReport(
                instId = instId,
                timeframe = timeframe,
                candleCount = candles.size,
                feePctPerSide = feePctPerSide,
                slippagePctPerSide = slippagePctPerSide,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePct = 0.0,
                netCumulativeReturnPct = 0.0,
                maxDrawdownPct = 0.0,
                profitFactor = 0.0,
                trades = emptyList()
            )
        }

        val costPerSideFraction = (feePctPerSide + slippagePctPerSide) / 100.0
        val roundTripCostPct = (feePctPerSide + slippagePctPerSide) * 2.0
        val outOfSampleStartIdx = (candles.size * 0.70).toInt().coerceAtLeast(36)
        val trades = mutableListOf<BacktestTrade>()

        var i = 35
        while (i < candles.size - 2) {
            // Strictly slice history up to bar i (NO future candles visible)
            val historySlice = candles.subList(0, i + 1)
            val latestBar = historySlice.last()
            val syntheticTicker = OkxTicker(
                instId = instId,
                baseSymbol = instId.substringBefore("-"),
                quoteSymbol = "USDT",
                lastPrice = latestBar.close,
                open24h = historySlice.first().open,
                high24h = historySlice.maxOf { it.high },
                low24h = historySlice.minOf { it.low },
                volBase24h = historySlice.sumOf { it.volumeBase },
                volUsdt24h = max(10_000_000.0, historySlice.sumOf { it.volumeQuoteUsdt }),
                change24hPct = 0.0,
                timestampMs = latestBar.timestampMs
            )

            val setup = QuantIndicatorEngine.evaluateOpportunitySetup(
                instId = instId,
                timeframe = timeframe,
                ticker = syntheticTicker,
                candles = historySlice
            )

            if (setup != null && setup.signalCategory == SignalCategory.BUY_SETUP) {
                // Execute entry at NEXT candle's open (bar i + 1)
                val entryIdx = i + 1
                val entryBar = candles[entryIdx]
                val rawEntryPrice = entryBar.open
                val effectiveEntryPrice = rawEntryPrice * (1.0 + costPerSideFraction)
                val stopPrice = setup.invalidationPrice
                val targetPrice = setup.target1Price

                var exitIdx = entryIdx
                var rawExitPrice = entryBar.close
                var exitReasonKu = "داخستن بە کۆتایی ماوەی هەڵگرتن (Time Exit)"
                var exitReasonEn = "Time-horizon exit (12 bars)"

                val maxHoldIdx = (i + 12).coerceAtMost(candles.lastIndex)
                for (j in entryIdx..maxHoldIdx) {
                    val bar = candles[j]
                    exitIdx = j
                    // Conservative intrabar assumption: check stop-loss first if both hit in same candle
                    if (bar.low <= stopPrice) {
                        rawExitPrice = stopPrice
                        exitReasonKu = "گەیشتن بە سنووری هەڵوەشاندنەوە (Stop-Loss)"
                        exitReasonEn = "Invalidation / Stop-Loss hit"
                        break
                    } else if (bar.high >= targetPrice) {
                        rawExitPrice = targetPrice
                        exitReasonKu = "گەیشتن بە ئامانجی یەکەم (Target 1 Hit)"
                        exitReasonEn = "Target 1 reached"
                        break
                    } else if (j == maxHoldIdx) {
                        rawExitPrice = bar.close
                    }
                }

                val effectiveExitPrice = rawExitPrice * (1.0 - costPerSideFraction)
                val grossReturnPct = ((rawExitPrice - rawEntryPrice) / rawEntryPrice) * 100.0
                val netReturnPct = ((effectiveExitPrice - effectiveEntryPrice) / effectiveEntryPrice) * 100.0

                trades.add(
                    BacktestTrade(
                        entryTimeMs = entryBar.timestampMs,
                        exitTimeMs = candles[exitIdx].timestampMs,
                        entryPrice = effectiveEntryPrice,
                        exitPrice = effectiveExitPrice,
                        grossReturnPct = grossReturnPct,
                        netReturnPct = netReturnPct,
                        exitReasonKu = exitReasonKu,
                        exitReasonEn = exitReasonEn,
                        isOutOfSample = entryIdx >= outOfSampleStartIdx
                    )
                )
                // Advance index past exit bar so trades never overlap or peek ahead
                i = exitIdx + 1
            } else {
                i++
            }
        }

        val wins = trades.count { it.netReturnPct > 0.0 }
        val losses = trades.count { it.netReturnPct <= 0.0 }
        val winRate = if (trades.isNotEmpty()) (wins.toDouble() / trades.size) * 100.0 else 0.0

        var equity = 100.0
        var peakEquity = 100.0
        var maxDrawdownPct = 0.0
        var grossProfitSum = 0.0
        var grossLossSum = 0.0

        var inSampleEquity = 100.0
        var outOfSampleEquity = 100.0

        for (t in trades) {
            equity *= (1.0 + (t.netReturnPct / 100.0))
            if (equity > peakEquity) peakEquity = equity
            val dd = ((peakEquity - equity) / peakEquity) * 100.0
            if (dd > maxDrawdownPct) maxDrawdownPct = dd

            if (t.netReturnPct > 0) grossProfitSum += t.netReturnPct
            else grossLossSum += abs(t.netReturnPct)

            if (t.isOutOfSample) {
                outOfSampleEquity *= (1.0 + (t.netReturnPct / 100.0))
            } else {
                inSampleEquity *= (1.0 + (t.netReturnPct / 100.0))
            }
        }

        val inSampleList = trades.filter { !it.isOutOfSample }
        val oosList = trades.filter { it.isOutOfSample }
        val oosWins = oosList.count { it.netReturnPct > 0.0 }
        val oosWinRate = if (oosList.isNotEmpty()) (oosWins.toDouble() / oosList.size) * 100.0 else 0.0

        val netCumulativeReturnPct = equity - 100.0
        val profitFactor = when {
            grossLossSum > 1e-6 -> grossProfitSum / grossLossSum
            grossProfitSum > 0.0 -> 3.0
            else -> 0.0
        }

        return BacktestReport(
            instId = instId,
            timeframe = timeframe,
            candleCount = candles.size,
            feePctPerSide = feePctPerSide,
            slippagePctPerSide = slippagePctPerSide,
            totalTrades = trades.size,
            winningTrades = wins,
            losingTrades = losses,
            winRatePct = winRate,
            netCumulativeReturnPct = netCumulativeReturnPct,
            maxDrawdownPct = maxDrawdownPct,
            profitFactor = profitFactor,
            trades = trades,
            inSampleTrades = inSampleList.size,
            inSampleReturnPct = inSampleEquity - 100.0,
            outOfSampleTrades = oosList.size,
            outOfSampleWinRatePct = oosWinRate,
            outOfSampleReturnPct = outOfSampleEquity - 100.0,
            totalFeesAndSlippagePct = trades.size * roundTripCostPct
        )
    }
}
