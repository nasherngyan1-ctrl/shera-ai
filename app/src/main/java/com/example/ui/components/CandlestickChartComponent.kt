package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.QuantIndicatorEngine
import com.example.model.AppLanguage
import com.example.model.ChartDisplayMode
import com.example.model.DerivativesTelemetry
import com.example.model.OkxCandle
import com.example.model.OkxTimeframe
import com.example.model.OpportunitySetup
import com.example.model.RiskLevel
import com.example.model.SignalCategory
import com.example.ui.theme.AmberWatch
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.SheraGold
import com.example.ui.theme.SheraGoldBright
import com.example.ui.theme.SheraMonoFont
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun InteractiveCandlestickCard(
    instId: String,
    timeframe: OkxTimeframe,
    chartDisplayMode: ChartDisplayMode,
    candles: List<OkxCandle>,
    setup: OpportunitySetup?,
    derivativesTelemetry: DerivativesTelemetry?,
    isLoading: Boolean,
    language: AppLanguage,
    onSelectTimeframe: (OkxTimeframe) -> Unit,
    onSelectChartMode: (ChartDisplayMode) -> Unit,
    onSendChartToAiVision: () -> Unit,
    onSaveSetupToCloud: (OpportunitySetup) -> Unit
) {
    val isKu = language == AppLanguage.KURDISH_SORANI

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp))
            .testTag("candlestick_chart_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Pair Header + Price + 24h stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$instId • SPOT",
                        style = MaterialTheme.typography.titleLarge,
                        color = SheraGoldBright
                    )
                    if (setup != null) {
                        Text(
                            text = if (isKu) setup.snapshot.marketStructure.labelKu else setup.snapshot.marketStructure.labelEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                if (setup != null) {
                    val changePct = setup.ticker.change24hPct
                    val changeColor = if (changePct >= 0.0) EmeraldBull else CrimsonBear
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${QuantIndicatorEngine.formatPrice(setup.snapshot.currentPrice)} USDT",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = SheraMonoFont,
                            color = Color.White
                        )
                        Text(
                            text = "${if (changePct >= 0) "+" else ""}${QuantIndicatorEngine.formatNum(changePct, 2)}% (24h)",
                            style = MaterialTheme.typography.labelSmall,
                            color = changeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chart Engine Switcher: Native OKX Canvas vs Official TradingView Free Widget
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartDisplayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = chartDisplayMode == mode,
                        onClick = { onSelectChartMode(mode) },
                        label = {
                            Text(if (isKu) mode.labelKu else mode.labelEn)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == ChartDisplayMode.OKX_NATIVE_CANVAS) {
                                    Icons.Default.CandlestickChart
                                } else {
                                    Icons.Default.Insights
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("chart_mode_${mode.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6 Timeframe Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OkxTimeframe.entries.forEach { tf ->
                    val selected = tf == timeframe
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) SheraGold else ObsidianSurfaceVariant)
                            .clickable { onSelectTimeframe(tf) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("tf_chip_${tf.bar}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isKu) "${tf.bar} • ${tf.labelKu}" else tf.labelEn,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color(0xFF0A0D14) else Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend row for EMA 20/50/200 & S/R
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendDot(color = SheraGold, label = "EMA20")
                LegendDot(color = Color(0xFF38BDF8), label = "EMA50")
                LegendDot(color = Color(0xFFC084FC), label = "EMA200")
                LegendDot(color = EmeraldBull, label = if (isKu) "پشتگیری (S)" else "Support")
                LegendDot(color = CrimsonBear, label = if (isKu) "بەرگری (R)" else "Resistance")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chart Container (Always LTR so time flows left->right)
            androidx.compose.runtime.CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(255.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBackground)
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (chartDisplayMode == ChartDisplayMode.TRADINGVIEW_OFFICIAL) {
                        TradingViewOfficialWidgetView(
                            instId = instId,
                            timeframe = timeframe,
                            onFallbackToNative = { onSelectChartMode(ChartDisplayMode.OKX_NATIVE_CANVAS) }
                        )
                    } else {
                        if (isLoading && candles.isEmpty()) {
                            CircularProgressIndicator(color = SheraGold, modifier = Modifier.size(32.dp))
                        } else if (candles.isEmpty()) {
                            Text(
                                text = if (isKu) "چاوەڕێی داتای مۆمەکانی OKX..." else "Waiting for OKX candle data...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        } else {
                            CandlestickCanvas(
                                candles = candles.takeLast(55),
                                setup = setup
                            )
                        }
                    }
                }
            }

            if (setup != null) {
                Spacer(modifier = Modifier.height(12.dp))
                QuantMetricsGrid(setup = setup, isKu = isKu)
                Spacer(modifier = Modifier.height(10.dp))
                DerivativesAndCoinGlassStrip(
                    telemetry = derivativesTelemetry,
                    isKu = isKu
                )
                Spacer(modifier = Modifier.height(12.dp))
                SetupTradeLevelsStrip(
                    setup = setup,
                    isKu = isKu,
                    onSendChartToAiVision = onSendChartToAiVision,
                    onSaveSetupToCloud = { onSaveSetupToCloud(setup) }
                )
            }
        }
    }
}

/**
 * Embeds the official, free, license-compliant TradingView HTML5 Advanced Widget
 * mapped directly to `OKX:{SYMBOL}` (e.g., `OKX:BTCUSDT`).
 * If offline or blocked, automatically falls back to the Native OKX Canvas Chart.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TradingViewOfficialWidgetView(
    instId: String,
    timeframe: OkxTimeframe,
    onFallbackToNative: () -> Unit
) {
    val tvSymbol = "OKX:${instId.replace("-", "")}"
    val tvInterval = timeframe.tvInterval
    val htmlContent = remember(tvSymbol, tvInterval) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                html, body, #tv_chart_container {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background-color: #080A0F;
                    overflow: hidden;
                }
            </style>
        </head>
        <body>
            <div id="tv_chart_container"></div>
            <script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
            <script type="text/javascript">
                try {
                    new TradingView.widget({
                        "autosize": true,
                        "symbol": "$tvSymbol",
                        "interval": "$tvInterval",
                        "timezone": "Etc/UTC",
                        "theme": "dark",
                        "style": "1",
                        "locale": "en",
                        "backgroundColor": "#080A0F",
                        "gridColor": "rgba(27, 34, 52, 0.6)",
                        "hide_top_toolbar": false,
                        "hide_legend": false,
                        "save_image": false,
                        "container_id": "tv_chart_container"
                    });
                } catch (e) {
                    document.body.innerHTML = "<p style='color:#9AA5B8;font-family:sans-serif;padding:16px;'>TradingView widget unavailable offline.</p>";
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tradingview_webview"),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#080A0F"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            onFallbackToNative()
                        }
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://www.tradingview.com",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

@Composable
private fun DerivativesAndCoinGlassStrip(
    telemetry: DerivativesTelemetry?,
    isKu: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceVariant)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag("derivatives_coinglass_strip")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isKu) {
                    "شیکاری دێریڤەیتیڤ و CoinGlass (Funding • OI • Liquidations)"
                } else {
                    "Derivatives & CoinGlass Telemetry (Funding • OI • Liquidations)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = SheraGoldBright,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isKu) "داتای گشتی فەرمی" else "Public Verified",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldBull
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (telemetry == null || !telemetry.isAvailable) {
            Text(
                text = if (isKu) {
                    telemetry?.unavailableReasonKu
                        ?: "داتای Funding Rate و Open Interest بۆ ئەم دراوە بەردەست نییە — هیچ داتایەکی ساختە دروست نەکراوە."
                } else {
                    telemetry?.unavailableReasonEn
                        ?: "Funding Rate & Open Interest unavailable for this pair — zero fake data shown."
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        } else {
            val funding = telemetry.fundingRatePct
            val fundingColor = when {
                funding == null -> TextSecondary
                funding > 0.025 -> CrimsonBear // Crowded longs
                funding < -0.01 -> EmeraldBull // Short squeeze potential
                else -> EmeraldBull
            }
            val oiStr = telemetry.openInterestUsdt?.let { "$${formatCompactNumber(it)}" } ?: "N/A"
            val liqStr = if (telemetry.recentLiqCount > 0) {
                "L:${QuantIndicatorEngine.formatNum(telemetry.recentLongLiqBase ?: 0.0, 1)} / S:${QuantIndicatorEngine.formatNum(telemetry.recentShortLiqBase ?: 0.0, 1)}"
            } else {
                if (isKu) "٠ لەم خولەکەدا" else "0 Recent"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isKu) "Funding Rate (${telemetry.swapInstId})" else "Funding Rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = funding?.let { "${if (it >= 0) "+" else ""}${QuantIndicatorEngine.formatNum(it, 4)}%" } ?: "N/A",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = SheraMonoFont,
                        color = fundingColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = if (isKu) "Open Interest (قەبارەی گرێبەست)" else "Open Interest (OI)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = oiStr,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = SheraMonoFont,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isKu) "Liquidations (لیکویدەیشن)" else "Liquidations",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = liqStr,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = SheraMonoFont,
                        color = AmberWatch
                    )
                }
            }
        }
    }
}

private fun formatCompactNumber(value: Double): String {
    return when {
        value >= 1_000_000_000.0 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000.0)
        value >= 1_000_000.0 -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
        value >= 1_000.0 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> String.format(Locale.US, "%.0f", value)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun CandlestickCanvas(
    candles: List<OkxCandle>,
    setup: OpportunitySetup?
) {
    var inspectedIndex by remember(candles) { mutableStateOf<Int?>(null) }
    val ema20 = setup?.snapshot?.ema20Series?.takeLast(candles.size).orEmpty()
    val ema50 = setup?.snapshot?.ema50Series?.takeLast(candles.size).orEmpty()
    val ema200 = setup?.snapshot?.ema200Series?.takeLast(candles.size).orEmpty()
    val support = setup?.snapshot?.supportLevel
    val resistance = setup?.snapshot?.resistanceLevel

    val inspectedCandle = inspectedIndex?.let { candles.getOrNull(it) } ?: candles.lastOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        if (inspectedCandle != null) {
            val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(inspectedCandle.timestampMs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$timeStr  O:${QuantIndicatorEngine.formatPrice(inspectedCandle.open)}  H:${QuantIndicatorEngine.formatPrice(inspectedCandle.high)}  L:${QuantIndicatorEngine.formatPrice(inspectedCandle.low)}  C:${QuantIndicatorEngine.formatPrice(inspectedCandle.close)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = SheraMonoFont,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(candles) {
                    detectTapGestures { offset ->
                        if (candles.isNotEmpty() && size.width > 0) {
                            val idx = ((offset.x / size.width) * candles.size).toInt().coerceIn(0, candles.lastIndex)
                            inspectedIndex = idx
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val priceAreaH = h * 0.78f
            val volAreaTop = h * 0.82f
            val volAreaH = h * 0.18f

            val minLow = candles.minOfOrNull { it.low } ?: 0.0
            val maxHigh = candles.maxOfOrNull { it.high } ?: 1.0
            val priceSpan = (maxHigh - minLow).coerceAtLeast(1e-8)
            val maxVol = (candles.maxOfOrNull { it.volumeQuoteUsdt } ?: 1.0).coerceAtLeast(1e-8)

            fun priceToY(p: Double): Float {
                val norm = ((p - minLow) / priceSpan).toFloat().coerceIn(0f, 1f)
                return priceAreaH - (norm * (priceAreaH - 12f)) - 6f
            }

            // Horizontal grid lines
            val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            for (i in 1..3) {
                val gy = priceAreaH * (i / 4f)
                drawLine(
                    color = Color(0xFF1A2234),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1f,
                    pathEffect = gridDash
                )
            }

            // Support & Resistance dashed lines
            if (support != null && support in minLow..maxHigh) {
                val sy = priceToY(support)
                drawLine(
                    color = EmeraldBull.copy(alpha = 0.75f),
                    start = Offset(0f, sy),
                    end = Offset(w, sy),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            }
            if (resistance != null && resistance in minLow..maxHigh) {
                val ry = priceToY(resistance)
                drawLine(
                    color = CrimsonBear.copy(alpha = 0.75f),
                    start = Offset(0f, ry),
                    end = Offset(w, ry),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            }

            val count = candles.size.coerceAtLeast(1)
            val stepX = w / count
            val bodyW = (stepX * 0.64f).coerceAtLeast(2.5f)

            candles.forEachIndexed { idx, c ->
                val cx = idx * stepX + stepX / 2f
                val isBull = c.close >= c.open
                val color = if (isBull) EmeraldBull else CrimsonBear

                if (inspectedIndex == idx) {
                    drawLine(
                        color = SheraGold.copy(alpha = 0.55f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, h),
                        strokeWidth = 1.5f,
                        pathEffect = gridDash
                    )
                }

                // Wick
                drawLine(
                    color = color,
                    start = Offset(cx, priceToY(c.high)),
                    end = Offset(cx, priceToY(c.low)),
                    strokeWidth = 1.6f
                )

                // Candle body
                val yOpen = priceToY(c.open)
                val yClose = priceToY(c.close)
                val top = min(yOpen, yClose)
                val bottom = max(yOpen, yClose).coerceAtLeast(top + 2f)
                drawRect(
                    color = color,
                    topLeft = Offset(cx - bodyW / 2f, top),
                    size = Size(bodyW, bottom - top)
                )

                // Volume bar
                val volNorm = (c.volumeQuoteUsdt / maxVol).toFloat().coerceIn(0.05f, 1f)
                val vh = volAreaH * volNorm
                drawRect(
                    color = color.copy(alpha = 0.38f),
                    topLeft = Offset(cx - bodyW / 2f, volAreaTop + (volAreaH - vh)),
                    size = Size(bodyW, vh)
                )
            }

            fun drawEmaLine(series: List<Double>, lineColor: Color) {
                if (series.size < 2) return
                for (i in 1 until min(series.size, candles.size)) {
                    val x1 = (i - 1) * stepX + stepX / 2f
                    val x2 = i * stepX + stepX / 2f
                    drawLine(
                        color = lineColor,
                        start = Offset(x1, priceToY(series[i - 1])),
                        end = Offset(x2, priceToY(series[i])),
                        strokeWidth = 2.2f
                    )
                }
            }

            drawEmaLine(ema20, SheraGold)
            drawEmaLine(ema50, Color(0xFF38BDF8))
            drawEmaLine(ema200, Color(0xFFC084FC))
        }
    }
}

@Composable
private fun QuantMetricsGrid(setup: OpportunitySetup, isKu: Boolean) {
    val s = setup.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricMiniBox(
                modifier = Modifier.weight(1f),
                title = "RSI (14)",
                value = QuantIndicatorEngine.formatNum(s.rsi14, 1),
                valueColor = when {
                    s.rsi14 >= 70.0 -> CrimsonBear
                    s.rsi14 <= 32.0 -> EmeraldBull
                    else -> SheraGoldBright
                }
            )
            MetricMiniBox(
                modifier = Modifier.weight(1f),
                title = "MACD Hist",
                value = QuantIndicatorEngine.formatNum(s.macdHistogram, 4),
                valueColor = if (s.macdHistogram >= 0) EmeraldBull else CrimsonBear
            )
            MetricMiniBox(
                modifier = Modifier.weight(1f),
                title = "ATR (14)",
                value = "${QuantIndicatorEngine.formatNum(s.atrPct, 2)}%",
                valueColor = Color.White
            )
            MetricMiniBox(
                modifier = Modifier.weight(1f),
                title = if (isKu) "قەبارە (20m)" else "Vol Ratio",
                value = "${QuantIndicatorEngine.formatNum(s.volumeRatio20, 2)}x",
                valueColor = if (s.isVolumeAnomaly) SheraGoldBright else Color.White
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricMiniBox(
                modifier = Modifier.weight(1f),
                title = "EMA 20 / 50 / 200",
                value = "${QuantIndicatorEngine.formatPrice(s.ema20)} / ${QuantIndicatorEngine.formatPrice(s.ema50)} / ${QuantIndicatorEngine.formatPrice(s.ema200)}",
                valueColor = TextSecondary
            )
            MetricMiniBox(
                modifier = Modifier.weight(1f),
                title = if (isKu) "پشتگیری / بەرگری" else "Support / Resist",
                value = "${QuantIndicatorEngine.formatPrice(s.supportLevel)} / ${QuantIndicatorEngine.formatPrice(s.resistanceLevel)}",
                valueColor = SheraGold
            )
        }
    }
}

@Composable
private fun MetricMiniBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    valueColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = SheraMonoFont,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun SetupTradeLevelsStrip(
    setup: OpportunitySetup,
    isKu: Boolean,
    onSendChartToAiVision: () -> Unit,
    onSaveSetupToCloud: () -> Unit
) {
    val badgeColor = when (setup.signalCategory) {
        SignalCategory.BUY_SETUP -> EmeraldBull
        SignalCategory.SELL_SETUP -> CrimsonBear
        SignalCategory.WATCH -> AmberWatch
    }
    val riskColor = when (setup.riskLevel) {
        RiskLevel.LOW -> EmeraldBull
        RiskLevel.MEDIUM -> AmberWatch
        RiskLevel.HIGH -> CrimsonBear
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141A28))
            .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isKu) setup.signalCategory.labelKu else setup.signalCategory.labelEn,
                style = MaterialTheme.typography.titleMedium,
                color = badgeColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isKu) {
                    "نمرەی کوالێتی نیشانە: ${setup.confluenceScore}/100"
                } else {
                    "Signal Quality Score: ${setup.confluenceScore}/100"
                },
                style = MaterialTheme.typography.labelLarge,
                color = riskColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Historical Backtest Verification Row (Rule 5: Only display win probability when verified by Backtest)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = null,
                tint = if (setup.verifiedBacktestWinRatePct != null) EmeraldBull else TextMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (setup.verifiedBacktestWinRatePct != null && setup.verifiedBacktestSampleSize > 0) {
                    if (isKu) {
                        "ڕێژەی سەرکەوتنی مێژوویی (پشتڕاستکراوە بە Backtest): ${QuantIndicatorEngine.formatNum(setup.verifiedBacktestWinRatePct, 1)}% لە ${setup.verifiedBacktestSampleSize} مامەڵەدا • (نمرەی 100 ئەگەری قازانج نییە)"
                    } else {
                        "Backtest-Verified Historical Win Rate: ${QuantIndicatorEngine.formatNum(setup.verifiedBacktestWinRatePct, 1)}% (${setup.verifiedBacktestSampleSize} trades) • (Score/100 is not profit guarantee)"
                    }
                } else {
                    if (isKu) {
                        "ڕێژەی سەرکەوتنی مێژوویی: پشتڕاست نەکراوەتەوە لەسەر ئەم تایم‌فریمە (٠ مامەڵەی مێژوویی) • نمرەی 100 ئەگەری قازانج نییە"
                    } else {
                        "Historical Win Rate: Unverified on this timeframe (0 historical trades) • Score/100 is not profit probability"
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (setup.verifiedBacktestWinRatePct != null) EmeraldBull else TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LevelColumn(
                label = if (isKu) "ناوچەی چوونەژوورەوە" else "Entry Zone",
                value = "${QuantIndicatorEngine.formatPrice(setup.entryZoneLow)} - ${QuantIndicatorEngine.formatPrice(setup.entryZoneHigh)}",
                color = SheraGoldBright
            )
            LevelColumn(
                label = if (isKu) "هەڵوەشاندنەوە (Stop)" else "Invalidation",
                value = QuantIndicatorEngine.formatPrice(setup.invalidationPrice),
                color = CrimsonBear
            )
            LevelColumn(
                label = if (isKu) "ئامانج T1 / T2" else "Targets T1 / T2",
                value = "${QuantIndicatorEngine.formatPrice(setup.target1Price)} / ${QuantIndicatorEngine.formatPrice(setup.target2Price)}",
                color = EmeraldBull
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val reasons = if (isKu) setup.reasonsKurdish else setup.reasonsEnglish
        reasons.forEach { r ->
            Text(
                text = "• $r",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSendChartToAiVision,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SheraGold,
                    contentColor = Color(0xFF0A0D14)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("send_chart_to_ai_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RemoveRedEye,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isKu) "شیکاری چارت بە AI" else "Analyze Chart with AI",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onSaveSetupToCloud,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_setup_cloud_button")
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = "Save setup",
                    tint = SheraGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isKu) "پاشەکەوت" else "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = SheraGold
                )
            }
        }
    }
}

@Composable
private fun LevelColumn(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = SheraMonoFont,
            color = color
        )
    }
}
