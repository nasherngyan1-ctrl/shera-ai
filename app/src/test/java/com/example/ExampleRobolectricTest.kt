package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SheraFirestoreRepository
import com.example.domain.QuantIndicatorEngine
import com.example.domain.SheraAiBrainService
import com.example.domain.WalkForwardBacktester
import com.example.model.AiModelChoice
import com.example.model.OkxCandle
import com.example.model.OkxTicker
import com.example.model.OkxTimeframe
import com.example.util.ensureFirebaseInitialized
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SHÊRA AI", appName)
  }

  @Test
  fun `firebase initializes safely even without google-services resources and MainActivity launches`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebaseApp.clearInstancesForTest()
    val initialized = ensureFirebaseInitialized(context)
    assertTrue("FirebaseApp should initialize with fallback options", initialized)
    assertNotNull(FirebaseApp.getInstance())

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
      }
    }
  }

  @Test
  fun `quant engine backtester repository and ai vision fallback execute without crashing`() = runBlocking {
    val candles = (0 until 80).map { idx ->
      val base = 90000.0 + idx * 120.0
      OkxCandle(
        timestampMs = 1700000000000L + idx * 3600_000L,
        open = base,
        high = base + 180.0,
        low = base - 90.0,
        close = base + 110.0,
        volumeBase = 150.0 + idx,
        volumeQuoteUsdt = (150.0 + idx) * base,
        isConfirmed = true
      )
    }
    val ticker = OkxTicker(
      instId = "BTC-USDT",
      baseSymbol = "BTC",
      quoteSymbol = "USDT",
      lastPrice = candles.last().close,
      open24h = candles.first().open,
      high24h = candles.maxOf { it.high },
      low24h = candles.minOf { it.low },
      volBase24h = 25000.0,
      volUsdt24h = 2_250_000_000.0,
      change24hPct = 4.2,
      timestampMs = candles.last().timestampMs
    )

    val setup = QuantIndicatorEngine.evaluateOpportunitySetup(
      instId = "BTC-USDT",
      timeframe = OkxTimeframe.TF_1H,
      ticker = ticker,
      candles = candles
    )
    assertNotNull(setup)

    val btReport = WalkForwardBacktester.runBacktest(
      instId = "BTC-USDT",
      timeframe = OkxTimeframe.TF_1H,
      candles = candles
    )
    assertEquals(80, btReport.candleCount)

    val repo = SheraFirestoreRepository(db = null)
    val watchRes = repo.upsertWatchlistItem("SOL-USDT", "Test note")
    assertTrue(watchRes.isSuccess)
    val alertRes = repo.createAlertRule("BTC-USDT", "PRICE_ABOVE", 99000.0, "1H", 30L)
    assertTrue(alertRes.isSuccess)

    val aiService = SheraAiBrainService()
    val chartBmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    val aiResult = aiService.analyzeChartImage(
      chartBitmap = chartBmp,
      selectedModel = AiModelChoice.GEMINI_3_8_FLASH,
      userQuestion = "Check structure",
      attachedLiveSetup = setup
    )
    assertTrue(aiResult.isSuccess)
    assertEquals("BTC-USDT", aiResult.getOrNull()?.detectedSymbol)
  }
}
