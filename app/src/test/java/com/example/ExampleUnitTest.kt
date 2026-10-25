package com.example

import com.example.data.remote.OkxMarketClient
import com.example.domain.QuantIndicatorEngine
import com.example.domain.WalkForwardBacktester
import com.example.model.OkxCandle
import com.example.model.OkxTicker
import com.example.model.OkxTimeframe
import com.example.model.SignalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun rsi14_and_ema_and_macd_calculations_areAccurate() {
        val risingPrices = (1..40).map { 100.0 + it * 1.5 }
        val rsi = QuantIndicatorEngine.calculateRsi(risingPrices, 14)
        assertTrue("Expected high RSI for monotonically rising series, got $rsi", rsi > 85.0)

        val ema20 = QuantIndicatorEngine.calculateEmaSeries(risingPrices, 20)
        assertEquals(risingPrices.size, ema20.size)
        assertTrue(ema20.last() > ema20.first())

        val macd = QuantIndicatorEngine.calculateMacd(risingPrices, 12, 26, 9)
        assertTrue("Expected positive MACD line in uptrend", macd.macdLine > 0.0)
    }

    @Test
    fun fallingKnife_withoutConfirmation_isNotClassifiedAsBuyWatch() {
        // Steep price drop without momentum turn must NOT be a BUY_SETUP (BUY WATCH)
        val candles = (0 until 50).map { i ->
            val close = 200.0 - (i * 2.2)
            OkxCandle(
                timestampMs = 1_700_000_000_000L + i * 3_600_000L,
                open = close + 1.8,
                high = close + 2.2,
                low = close - 0.5,
                close = close,
                volumeBase = 100.0,
                volumeQuoteUsdt = 20_000.0,
                isConfirmed = true
            )
        }
        val ticker = OkxTicker(
            instId = "BTC-USDT",
            baseSymbol = "BTC",
            quoteSymbol = "USDT",
            lastPrice = candles.last().close,
            open24h = candles.first().open,
            high24h = 200.0,
            low24h = candles.last().low,
            volBase24h = 5000.0,
            volUsdt24h = 25_000_000.0,
            change24hPct = -15.0,
            timestampMs = candles.last().timestampMs
        )

        val setup = QuantIndicatorEngine.evaluateOpportunitySetup(
            instId = "BTC-USDT",
            timeframe = OkxTimeframe.TF_1H,
            ticker = ticker,
            candles = candles
        )
        assertNotNull(setup)
        assertEquals("BUY WATCH", SignalCategory.BUY_SETUP.shortTag)
        assertEquals("SELL WATCH", SignalCategory.SELL_SETUP.shortTag)
        assertEquals("NEUTRAL", SignalCategory.WATCH.shortTag)
        assertTrue(
            "Falling price alone must never trigger BUY WATCH",
            setup!!.signalCategory != SignalCategory.BUY_SETUP
        )
    }

    @Test
    fun walkForwardBacktester_deductsFeesAndComputesOutOfSampleSplit() {
        val candles = (0 until 80).map { i ->
            val wave = kotlin.math.sin(i / 4.0) * 6.0
            val base = 100.0 + (i * 0.4) + wave
            OkxCandle(
                timestampMs = 1_700_000_000_000L + i * 3_600_000L,
                open = base - 0.8,
                high = base + 2.0,
                low = base - 1.5,
                close = base + 0.9,
                volumeBase = 500.0 + (i * 10.0),
                volumeQuoteUsdt = 50_000.0 + (i * 1200.0),
                isConfirmed = true
            )
        }

        val reportZeroCost = WalkForwardBacktester.runBacktest(
            instId = "SOL-USDT",
            timeframe = OkxTimeframe.TF_1H,
            candles = candles,
            feePctPerSide = 0.0,
            slippagePctPerSide = 0.0
        )
        val reportWithCost = WalkForwardBacktester.runBacktest(
            instId = "SOL-USDT",
            timeframe = OkxTimeframe.TF_1H,
            candles = candles,
            feePctPerSide = 0.10,
            slippagePctPerSide = 0.05
        )

        assertEquals(80, reportWithCost.candleCount)
        assertEquals(
            reportWithCost.totalTrades,
            reportWithCost.inSampleTrades + reportWithCost.outOfSampleTrades
        )
        if (reportWithCost.totalTrades > 0) {
            assertTrue(
                "Net return with fees & slippage must be strictly less than zero-cost return",
                reportWithCost.netCumulativeReturnPct < reportZeroCost.netCumulativeReturnPct
            )
            assertTrue(reportWithCost.totalFeesAndSlippagePct > 0.0)
        }
    }

    @Test
    fun okxJsonParsers_parseSpotUsdtTickersCandlesAndDerivativesCorrectly() {
        val tickerJson = """
            {"code":"0","msg":"","data":[
              {"instType":"SPOT","instId":"BTC-USDT","last":"92500.5","open24h":"90000.0","high24h":"93100.0","low24h":"89500.0","vol24h":"12400","volCcy24h":"1140000000","ts":"1700000000000"},
              {"instType":"SPOT","instId":"ETH-BTC","last":"0.035","open24h":"0.034","high24h":"0.036","low24h":"0.033","vol24h":"500","volCcy24h":"17","ts":"1700000000000"}
            ]}
        """.trimIndent()
        val parsedTickers = OkxMarketClient.parseTickersJson(tickerJson)
        assertEquals(1, parsedTickers.size)
        assertEquals("BTC-USDT", parsedTickers[0].instId)
        assertEquals(92500.5, parsedTickers[0].lastPrice, 0.01)

        val candleJson = """
            {"code":"0","msg":"","data":[
              ["1700003600000","91000","92600","90800","92500","120.5","11000000","11000000","1"],
              ["1700000000000","90000","91200","89900","91000","95.0","8600000","8600000","1"]
            ]}
        """.trimIndent()
        val parsedCandles = OkxMarketClient.parseCandlesJson(candleJson)
        assertEquals(2, parsedCandles.size)
        assertTrue("Candles should be sorted oldest to newest", parsedCandles[0].timestampMs < parsedCandles[1].timestampMs)

        val fundingJson = """
            {"code":"0","msg":"","data":[{"instId":"BTC-USDT-SWAP","fundingRate":"0.000125","nextFundingRate":"0.000150"}]}
        """.trimIndent()
        val fundingPair = OkxMarketClient.parseFundingRateJson(fundingJson)
        assertNotNull(fundingPair)
        assertEquals(0.0125, fundingPair!!.first, 0.0001)
        assertEquals(0.0150, fundingPair.second ?: 0.0, 0.0001)

        val oiJson = """
            {"code":"0","msg":"","data":[{"instId":"BTC-USDT-SWAP","oi":"52000","oiCcy":"520","oiUsd":"48100000"}]}
        """.trimIndent()
        val oiPair = OkxMarketClient.parseOpenInterestJson(oiJson, 92500.0)
        assertNotNull(oiPair)
        assertEquals(48_100_000.0, oiPair!!.first, 1.0)
        assertEquals(52_000.0, oiPair.second, 0.1)

        val liqJson = """
            {"code":"0","msg":"","data":[{"instId":"BTC-USDT-SWAP","details":[{"side":"sell","posSide":"long","sz":"3.5"},{"side":"buy","posSide":"short","sz":"1.2"}]}]}
        """.trimIndent()
        val liqTriple = OkxMarketClient.parseLiquidationsJson(liqJson)
        assertNotNull(liqTriple)
        assertEquals(3.5, liqTriple!!.first, 0.01)
        assertEquals(1.2, liqTriple.second, 0.01)
        assertEquals(2, liqTriple.third)
    }
}
