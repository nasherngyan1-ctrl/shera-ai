package com.example.domain

import com.example.model.MarketStructure
import com.example.model.OkxCandle
import com.example.model.OkxTicker
import com.example.model.OkxTimeframe
import com.example.model.OpportunitySetup
import com.example.model.RiskLevel
import com.example.model.SignalCategory
import com.example.model.TechnicalSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object QuantIndicatorEngine {

    fun computeSnapshot(
        instId: String,
        timeframe: OkxTimeframe,
        candles: List<OkxCandle>
    ): TechnicalSnapshot? {
        if (candles.size < 25) return null
        val closes = candles.map { it.close }
        val currentPrice = closes.last()

        val rsi14 = calculateRsi(closes, 14)
        val ema20Series = calculateEmaSeries(closes, 20)
        val ema50Series = calculateEmaSeries(closes, 50)
        val ema200Series = calculateEmaSeries(closes, 200)

        val ema20 = ema20Series.lastOrNull() ?: currentPrice
        val ema50 = ema50Series.lastOrNull() ?: currentPrice
        val ema200 = ema200Series.lastOrNull() ?: currentPrice

        val macd = calculateMacd(closes, 12, 26, 9)
        val atr14 = calculateAtr(candles, 14)
        val atrPct = if (currentPrice > 0.0) (atr14 / currentPrice) * 100.0 else 0.0

        // Volume analysis over last 20 closed candles
        val recentVols = candles.takeLast(21)
        val avgVol20 = if (recentVols.size > 1) {
            recentVols.dropLast(1).map { it.volumeQuoteUsdt }.average().coerceAtLeast(1e-9)
        } else {
            candles.last().volumeQuoteUsdt.coerceAtLeast(1e-9)
        }
        val latestVol = candles.last().volumeQuoteUsdt
        val volumeRatio20 = latestVol / avgVol20
        val isVolumeAnomaly = volumeRatio20 >= 1.80

        // 3-bar rapid price change detection
        val refIdx = (candles.size - 4).coerceAtLeast(0)
        val refClose = candles[refIdx].close.coerceAtLeast(1e-9)
        val rapidMovePct3Bars = ((currentPrice - refClose) / refClose) * 100.0
        val isRapidPriceMove = abs(rapidMovePct3Bars) >= 2.20

        // Support & Resistance from recent swing pivots (excluding current unconfirmed bar)
        val lookback = candles.takeLast(45)
        val (supportLevel, resistanceLevel) = findSupportAndResistance(lookback, currentPrice, atr14)

        val marketStructure = classifyMarketStructure(
            currentPrice = currentPrice,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            candles = lookback
        )

        return TechnicalSnapshot(
            instId = instId,
            timeframe = timeframe,
            currentPrice = currentPrice,
            rsi14 = rsi14,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            macdLine = macd.macdLine,
            macdSignal = macd.signalLine,
            macdHistogram = macd.histogram,
            prevMacdHistogram = macd.prevHistogram,
            atr14 = atr14,
            atrPct = atrPct,
            volumeRatio20 = volumeRatio20,
            isVolumeAnomaly = isVolumeAnomaly,
            rapidMovePct3Bars = rapidMovePct3Bars,
            isRapidPriceMove = isRapidPriceMove,
            supportLevel = supportLevel,
            resistanceLevel = resistanceLevel,
            marketStructure = marketStructure,
            ema20Series = ema20Series,
            ema50Series = ema50Series,
            ema200Series = ema200Series
        )
    }

    /**
     * Evaluates multi-indicator confluence for an OKX pair.
     * Enforces:
     * 1. A price drop alone is NOT a buy signal; requires confirmation from RSI, MACD momentum shift,
     *    EMA/Support structure, and volume.
     * 2. Never classifies a coin as a BUY_SETUP based on a single isolated candle spike.
     */
    fun evaluateOpportunitySetup(
        instId: String,
        timeframe: OkxTimeframe,
        ticker: OkxTicker,
        candles: List<OkxCandle>
    ): OpportunitySetup? {
        val snap = computeSnapshot(instId, timeframe, candles) ?: return null
        val price = snap.currentPrice
        val reasonsKu = mutableListOf<String>()
        val reasonsEn = mutableListOf<String>()

        var bullishPoints = 0
        var bearishPoints = 0

        // 1. Multi-candle confirmation check (prevent single-candle false breakouts)
        val last3 = candles.takeLast(3)
        val closedBullishCount = last3.count { it.close > it.open }
        val closedBearishCount = last3.count { it.close < it.open }
        val singleCandleSpike = last3.size == 3 &&
            ((last3[2].close > last3[2].open && last3[0].close < last3[0].open && last3[1].close < last3[1].open) ||
                abs(last3[2].close - last3[2].open) > 2.8 * snap.atr14)

        // 2. Market Structure & EMA Alignment
        when (snap.marketStructure) {
            MarketStructure.UPTREND -> {
                bullishPoints += 24
                reasonsKu.add("پێکهاتەی بازاڕ لە ئاراستەی بەرزبوونەوەدایە (نرخ لەسەرووی EMA20 و EMA50)")
                reasonsEn.add("Bullish market structure with price holding above EMA20 & EMA50")
            }
            MarketStructure.DOWNTREND -> {
                bearishPoints += 24
                reasonsKu.add("پێکهاتەی بازاڕ لە ژێر فشاری دابەزیندایە (خوار EMA20 و EMA50)")
                reasonsEn.add("Bearish trend structure below EMA20 and EMA50")
            }
            MarketStructure.RANGING -> {
                reasonsKu.add("بازاڕ لە دۆخی ئاسۆیی و کۆکردنەوەدایە لە نێوان پشتگیری و بەرگری")
                reasonsEn.add("Market ranging between key support and resistance zones")
            }
        }

        if (price > snap.ema200) {
            bullishPoints += 12
            reasonsKu.add("پاراستنی ئاراستەی گشتی لەسەرووی هێڵی گرنگی EMA(200)")
            reasonsEn.add("Macro trend supported above EMA(200)")
        } else {
            bearishPoints += 10
        }

        // 3. RSI(14) momentum & mean-reversion confirmation
        when {
            snap.rsi14 in 30.0..45.0 && snap.macdHistogram > snap.prevMacdHistogram -> {
                bullishPoints += 22
                reasonsKu.add("گەڕانەوەی ئەرێنی لە ناوچەی فرۆشتنی زۆر RSI(${formatNum(snap.rsi14, 1)}) بە پشتڕاستکردنەوەی مۆمێنتۆم")
                reasonsEn.add("Confirmed RSI(${formatNum(snap.rsi14, 1)}) recovery from oversold zone with rising momentum")
            }
            snap.rsi14 < 30.0 && snap.macdHistogram <= snap.prevMacdHistogram -> {
                // Falling knife protection: price drop alone without momentum turn is NOT a buy
                bearishPoints += 15
                reasonsKu.add("ئاگاداری: RSI(${formatNum(snap.rsi14, 1)}) نزمە بەڵام هێشتا نیشانەی وەستانی دابەزین دەرنەکەوتووە (تەنها دابەزین بەڵگەی کڕین نییە)")
                reasonsEn.add("Caution: RSI(${formatNum(snap.rsi14, 1)}) oversold without reversal confirmation (falling price alone is not a buy)")
            }
            snap.rsi14 in 50.0..66.0 -> {
                bullishPoints += 16
                reasonsKu.add("هێزی کڕیاران جێگیرە لەسەر RSI(${formatNum(snap.rsi14, 1)}) بەبێ تێربوونی کڕین")
                reasonsEn.add("Healthy bullish momentum on RSI(${formatNum(snap.rsi14, 1)}) below overbought zone")
            }
            snap.rsi14 >= 72.0 -> {
                bearishPoints += 24
                reasonsKu.add("چوونە ناوچەی تێربوونی کڕین RSI(${formatNum(snap.rsi14, 1)}) و ئەگەری وەرگرتنی قازانج")
                reasonsEn.add("Overbought RSI(${formatNum(snap.rsi14, 1)}) signaling elevated pullback / take-profit risk")
            }
        }

        // 4. MACD(12, 26, 9) confirmation
        if (snap.macdLine > snap.macdSignal && snap.macdHistogram > 0) {
            bullishPoints += 18
            reasonsKu.add("پەڕینەوەی ئەرێنی هێڵی MACD لەسەرووی هێڵی نیشانە و هیستۆگرامی سەوز")
            reasonsEn.add("Bullish MACD crossover above signal line with positive histogram")
        } else if (snap.macdHistogram > snap.prevMacdHistogram && snap.macdHistogram > -0.5 * snap.atr14) {
            bullishPoints += 10
            reasonsKu.add("لاوازبوونی فشاری فرۆشیاران لەسەر هیستۆگرامی MACD")
            reasonsEn.add("Selling pressure waning on MACD histogram")
        } else if (snap.macdLine < snap.macdSignal && snap.macdHistogram < 0) {
            bearishPoints += 18
            reasonsKu.add("مۆمێنتۆمی نەرێنی لەسەر MACD لە خوار هێڵی نیشانە")
            reasonsEn.add("Bearish MACD momentum below signal line")
        }

        // 5. Proximity to Support / Resistance
        val distToSupportPct = if (price > 0) ((price - snap.supportLevel) / price) * 100.0 else 5.0
        val distToResistancePct = if (price > 0) ((snap.resistanceLevel - price) / price) * 100.0 else 5.0

        if (distToSupportPct in 0.1..2.8 && closedBullishCount >= 1) {
            bullishPoints += 16
            reasonsKu.add("کاردانەوەی نرخ لە نزیک ئاستی پشتگیریی بەهێز (${formatPrice(snap.supportLevel)})")
            reasonsEn.add("Price defending key structural support (${formatPrice(snap.supportLevel)})")
        }
        if (distToResistancePct in 0.0..1.5 && snap.rsi14 > 65.0) {
            bearishPoints += 16
            reasonsKu.add("نزیکبوونەوەی نرخ لە دیواری بەرگری (${formatPrice(snap.resistanceLevel)})")
            reasonsEn.add("Price approaching major resistance barrier (${formatPrice(snap.resistanceLevel)})")
        }

        // 6. Volume & Liquidity Confirmation
        if (snap.volumeRatio20 >= 1.35 && closedBullishCount >= 2) {
            bullishPoints += 14
            reasonsKu.add("زیادبوونی قەبارەی مامەڵە (${formatNum(snap.volumeRatio20, 2)}x ی تێکڕای ٢٠ مۆم) لەگەڵ کڕیندا")
            reasonsEn.add("Above-average volume expansion (${formatNum(snap.volumeRatio20, 2)}x 20-bar avg) confirming buyers")
        } else if (snap.volumeRatio20 >= 1.50 && closedBearishCount >= 2) {
            bearishPoints += 14
            reasonsKu.add("بەرزبوونەوەی قەبارەی فرۆشتن (${formatNum(snap.volumeRatio20, 2)}x ی تێکڕا)")
            reasonsEn.add("Elevated selling volume (${formatNum(snap.volumeRatio20, 2)}x average)")
        }

        // Enforce anti-single-candle guardrail
        if (singleCandleSpike) {
            bullishPoints = (bullishPoints - 18).coerceAtLeast(0)
            reasonsKu.add("پارێزبەندی دژە-فریودان: جووڵەکە لەسەر یەک مۆمی تاکە؛ پێویستی بە داخستنی مۆمی پشتڕاستکەرەوە هەیە")
            reasonsEn.add("Single-candle spike guardrail active: awaiting multi-candle confirmation")
        }

        val signalCategory: SignalCategory
        val confluenceScore: Int

        if (bullishPoints >= 62 && bullishPoints > bearishPoints + 18 && closedBullishCount >= 2 && !singleCandleSpike) {
            signalCategory = SignalCategory.BUY_SETUP
            confluenceScore = bullishPoints.coerceIn(62, 95)
        } else if (bearishPoints >= 58 && bearishPoints > bullishPoints + 15) {
            signalCategory = SignalCategory.SELL_SETUP
            confluenceScore = bearishPoints.coerceIn(58, 94)
        } else {
            signalCategory = SignalCategory.WATCH
            confluenceScore = max(bullishPoints, bearishPoints).coerceIn(32, 68)
        }

        // Risk assessment based on ATR volatility, 24h liquidity, and structure
        val riskLevel = when {
            snap.atrPct > 3.8 || ticker.volUsdt24h < 750_000.0 || singleCandleSpike -> RiskLevel.HIGH
            snap.atrPct > 1.8 || ticker.volUsdt24h < 5_000_000.0 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        // Entry zone, Invalidation (Stop), and Targets (T1/T2) based on ATR & Support/Resistance
        val safeAtr = snap.atr14.coerceAtLeast(price * 0.006)
        val entryZoneLow: Double
        val entryZoneHigh: Double
        val invalidationPrice: Double
        val target1Price: Double
        val target2Price: Double

        when (signalCategory) {
            SignalCategory.BUY_SETUP -> {
                entryZoneLow = max(snap.supportLevel, price - 0.35 * safeAtr)
                entryZoneHigh = price + 0.15 * safeAtr
                invalidationPrice = min(snap.supportLevel - 0.4 * safeAtr, price - 1.55 * safeAtr).coerceAtLeast(price * 0.5)
                val riskDist = (price - invalidationPrice).coerceAtLeast(safeAtr * 0.5)
                target1Price = max(price + riskDist * 1.6, min(snap.resistanceLevel, price + 2.2 * safeAtr))
                target2Price = price + riskDist * 2.8
            }
            SignalCategory.SELL_SETUP -> {
                entryZoneLow = price - 0.15 * safeAtr
                entryZoneHigh = min(snap.resistanceLevel, price + 0.35 * safeAtr)
                invalidationPrice = max(snap.resistanceLevel + 0.4 * safeAtr, price + 1.55 * safeAtr)
                val riskDist = (invalidationPrice - price).coerceAtLeast(safeAtr * 0.5)
                target1Price = (price - riskDist * 1.5).coerceAtLeast(snap.supportLevel)
                target2Price = (price - riskDist * 2.5).coerceAtLeast(price * 0.3)
            }
            SignalCategory.WATCH -> {
                entryZoneLow = snap.supportLevel
                entryZoneHigh = price
                invalidationPrice = (snap.supportLevel - 0.8 * safeAtr).coerceAtLeast(price * 0.5)
                target1Price = snap.resistanceLevel
                target2Price = snap.resistanceLevel + 1.2 * safeAtr
            }
        }

        val rewardDist = abs(target1Price - price)
        val riskDist = abs(price - invalidationPrice).coerceAtLeast(price * 0.002)
        val rr = (rewardDist / riskDist).coerceIn(0.4, 8.0)

        return OpportunitySetup(
            instId = instId,
            timeframe = timeframe,
            ticker = ticker,
            snapshot = snap,
            signalCategory = signalCategory,
            confluenceScore = confluenceScore,
            riskLevel = riskLevel,
            entryZoneLow = entryZoneLow,
            entryZoneHigh = entryZoneHigh,
            invalidationPrice = invalidationPrice,
            target1Price = target1Price,
            target2Price = target2Price,
            riskRewardRatio = rr,
            reasonsKurdish = reasonsKu.take(4),
            reasonsEnglish = reasonsEn.take(4),
            singleCandleWarning = singleCandleSpike
        )
    }

    fun calculateRsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 50.0
        var avgGain = 0.0
        var avgLoss = 0.0

        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change > 0) avgGain += change else avgLoss -= change
        }
        avgGain /= period
        avgLoss /= period

        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
        }

        if (avgLoss <= 1e-12) return 100.0
        val rs = avgGain / avgLoss
        return (100.0 - (100.0 / (1.0 + rs))).coerceIn(0.0, 100.0)
    }

    fun calculateEmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val result = ArrayList<Double>(values.size)
        val seedCount = min(period, values.size)
        var ema = values.take(seedCount).average()
        for (i in values.indices) {
            if (i < seedCount) {
                ema = values.take(i + 1).average()
            } else {
                ema = (values[i] - ema) * k + ema
            }
            result.add(ema)
        }
        return result
    }

    data class MacdResult(
        val macdLine: Double,
        val signalLine: Double,
        val histogram: Double,
        val prevHistogram: Double
    )

    fun calculateMacd(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdResult {
        if (closes.size < 5) return MacdResult(0.0, 0.0, 0.0, 0.0)
        val fastEma = calculateEmaSeries(closes, fastPeriod)
        val slowEma = calculateEmaSeries(closes, slowPeriod)
        val macdSeries = closes.indices.map { i -> fastEma[i] - slowEma[i] }
        val signalSeries = calculateEmaSeries(macdSeries, signalPeriod)

        val lastIdx = macdSeries.lastIndex
        val prevIdx = (lastIdx - 1).coerceAtLeast(0)
        val macdLine = macdSeries[lastIdx]
        val signalLine = signalSeries[lastIdx]
        val histogram = macdLine - signalLine
        val prevHist = macdSeries[prevIdx] - signalSeries[prevIdx]

        return MacdResult(
            macdLine = macdLine,
            signalLine = signalLine,
            histogram = histogram,
            prevHistogram = prevHist
        )
    }

    fun calculateAtr(candles: List<OkxCandle>, period: Int = 14): Double {
        if (candles.isEmpty()) return 0.0
        if (candles.size == 1) return candles[0].high - candles[0].low

        val trList = ArrayList<Double>(candles.size)
        trList.add(candles[0].high - candles[0].low)
        for (i in 1 until candles.size) {
            val high = candles[i].high
            val low = candles[i].low
            val prevClose = candles[i - 1].close
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trList.add(tr)
        }

        val seed = min(period, trList.size)
        var atr = trList.take(seed).average()
        for (i in seed until trList.size) {
            atr = ((atr * (period - 1)) + trList[i]) / period
        }
        return atr
    }

    private fun findSupportAndResistance(
        candles: List<OkxCandle>,
        currentPrice: Double,
        atr: Double
    ): Pair<Double, Double> {
        if (candles.isEmpty()) return (currentPrice * 0.97) to (currentPrice * 1.03)
        val lowsBelow = candles.map { it.low }.filter { it < currentPrice }.sortedDescending()
        val highsAbove = candles.map { it.high }.filter { it > currentPrice }.sorted()

        val support = lowsBelow.drop((lowsBelow.size * 0.25).toInt()).firstOrNull()
            ?: (currentPrice - 1.5 * atr).coerceAtLeast(currentPrice * 0.90)
        val resistance = highsAbove.drop((highsAbove.size * 0.25).toInt()).firstOrNull()
            ?: (currentPrice + 1.5 * atr)

        return support to resistance
    }

    private fun classifyMarketStructure(
        currentPrice: Double,
        ema20: Double,
        ema50: Double,
        ema200: Double,
        candles: List<OkxCandle>
    ): MarketStructure {
        val recent = candles.takeLast(20)
        val firstHalfHigh = recent.take(10).maxOfOrNull { it.high } ?: currentPrice
        val secondHalfHigh = recent.takeLast(10).maxOfOrNull { it.high } ?: currentPrice
        val firstHalfLow = recent.take(10).minOfOrNull { it.low } ?: currentPrice
        val secondHalfLow = recent.takeLast(10).minOfOrNull { it.low } ?: currentPrice

        return when {
            currentPrice > ema20 && ema20 >= ema50 && secondHalfHigh >= firstHalfHigh && secondHalfLow >= firstHalfLow ->
                MarketStructure.UPTREND
            currentPrice < ema20 && ema20 <= ema50 && secondHalfHigh <= firstHalfHigh && secondHalfLow <= firstHalfLow ->
                MarketStructure.DOWNTREND
            else -> {
                if (currentPrice > ema50 && ema50 > ema200) MarketStructure.UPTREND
                else if (currentPrice < ema50 && ema50 < ema200) MarketStructure.DOWNTREND
                else MarketStructure.RANGING
            }
        }
    }

    fun formatPrice(price: Double): String {
        return when {
            price >= 1000.0 -> String.format(java.util.Locale.US, "%.2f", price)
            price >= 1.0 -> String.format(java.util.Locale.US, "%.4f", price)
            price >= 0.01 -> String.format(java.util.Locale.US, "%.5f", price)
            else -> String.format(java.util.Locale.US, "%.7f", price)
        }
    }

    fun formatNum(value: Double, decimals: Int = 2): String {
        return String.format(java.util.Locale.US, "%.${decimals}f", value)
    }
}
