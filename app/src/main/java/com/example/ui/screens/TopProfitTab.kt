package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.QuantIndicatorEngine
import com.example.model.AppLanguage
import com.example.model.ChartDisplayMode
import com.example.model.ConnectionStateType
import com.example.model.ConnectionStatus
import com.example.model.DerivativesTelemetry
import com.example.model.OkxCandle
import com.example.model.OkxTicker
import com.example.model.OkxTimeframe
import com.example.model.OpportunitySetup
import com.example.model.RiskLevel
import com.example.model.SignalCategory
import com.example.ui.components.InteractiveCandlestickCard
import com.example.ui.theme.AmberWatch
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.SheraGold
import com.example.ui.theme.SheraGoldBright
import com.example.ui.theme.SheraMonoFont
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun TopProfitTab(
    tickers: List<OkxTicker>,
    selectedInstId: String,
    selectedTimeframe: OkxTimeframe,
    chartDisplayMode: ChartDisplayMode,
    selectedCandles: List<OkxCandle>,
    selectedSetup: OpportunitySetup?,
    derivativesTelemetry: DerivativesTelemetry?,
    isChartLoading: Boolean,
    opportunities: List<OpportunitySetup>,
    isScannerRunning: Boolean,
    language: AppLanguage,
    onSelectPair: (String) -> Unit,
    onSelectTimeframe: (OkxTimeframe) -> Unit,
    onSelectChartMode: (ChartDisplayMode) -> Unit,
    onRefreshClick: () -> Unit,
    onOpenInAiVision: () -> Unit
) {
    val isKu = language == AppLanguage.KURDISH_SORANI
    val topOpportunity = remember(opportunities) {
        opportunities.firstOrNull { it.signalCategory == SignalCategory.BUY_SETUP }
            ?: opportunities.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("top_profit_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Recommendation Card: Absolute Best Profit / Buy Signal Right Now
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, SheraGold, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SheraGold.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = SheraGoldBright,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isKu) "⭐ باشترین هەلی قازانج و کڕین (Top Buy Radar)" else "⭐ Top Profit & Buy Radar",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SheraGoldBright,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isKu) "سیستەمی زیرەک دەستنیشانی کرد کە ئەم دراوە باشترین قازانجی هەیە" else "AI identified this coin as the highest profit opportunity",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        IconButton(onClick = onRefreshClick) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = SheraGold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (topOpportunity != null) {
                        val setup = topOpportunity
                        val s = setup.snapshot
                        val changeColor = if (setup.ticker.change24hPct >= 0) EmeraldBull else CrimsonBear

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = setup.instId,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "RSI: ${QuantIndicatorEngine.formatNum(s.rsi14, 1)} • ${if (isKu) setup.signalCategory.labelKu else setup.signalCategory.labelEn}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldBull
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${QuantIndicatorEngine.formatPrice(s.currentPrice)} USDT",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = SheraMonoFont,
                                    color = Color.White
                                )
                                Text(
                                    text = "${if (setup.ticker.change24hPct >= 0) "+" else ""}${QuantIndicatorEngine.formatNum(setup.ticker.change24hPct, 2)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = changeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Clear Buy Action Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(EmeraldBull.copy(alpha = 0.15f))
                                .border(1.dp, EmeraldBull.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = EmeraldBull, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isKu) "💡 فەرمان: ئەم دراوە بکڕە (Buy Opportunity Active)" else "💡 Action: Buy This Coin Now",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldBull,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (isKu) {
                                        "چوونەژوورەوە: ${QuantIndicatorEngine.formatPrice(setup.entryZoneLow)} - ${QuantIndicatorEngine.formatPrice(setup.entryZoneHigh)} | ئامانج T1: ${QuantIndicatorEngine.formatPrice(setup.target1Price)} | Stop: ${QuantIndicatorEngine.formatPrice(setup.invalidationPrice)}"
                                    } else {
                                        "Entry: ${QuantIndicatorEngine.formatPrice(setup.entryZoneLow)} - ${QuantIndicatorEngine.formatPrice(setup.entryZoneHigh)} | Target T1: ${QuantIndicatorEngine.formatPrice(setup.target1Price)} | Stop: ${QuantIndicatorEngine.formatPrice(setup.invalidationPrice)}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = SheraMonoFont,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onSelectPair(setup.instId)
                                onOpenInAiVision()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SheraGold,
                                contentColor = Color(0xFF0A0D14)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("buy_recommendation_button")
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKu) "شیکاری تەواو و وێنەی چارت بە AI" else "View Full Chart & AI Analysis",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            if (isScannerRunning) {
                                CircularProgressIndicator(color = SheraGold)
                            } else {
                                Text(text = if (isKu) "خەریکی پشکنینی باشترین دراوەکان..." else "Scanning top profit coins...", color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Interactive Chart & Data Section for Selected Pair
        item {
            InteractiveCandlestickCard(
                instId = selectedInstId,
                timeframe = selectedTimeframe,
                chartDisplayMode = chartDisplayMode,
                candles = selectedCandles,
                setup = selectedSetup,
                derivativesTelemetry = derivativesTelemetry,
                isLoading = isChartLoading,
                language = language,
                onSelectTimeframe = onSelectTimeframe,
                onSelectChartMode = onSelectChartMode,
                onSendChartToAiVision = onOpenInAiVision,
                onSaveSetupToCloud = {}
            )
        }

        // List of other top profit coins
        item {
            Text(
                text = if (isKu) "📈 دراوە ترەیدکراوەکان و هەلەکان:" else "📈 Other Top Profit Candidates:",
                style = MaterialTheme.typography.titleMedium,
                color = SheraGoldBright,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(opportunities.take(12), key = { it.instId }) { setup ->
            OtherOpportunityRow(
                setup = setup,
                isSelected = setup.instId == selectedInstId,
                isKu = isKu,
                onSelect = { onSelectPair(setup.instId) }
            )
        }
    }
}

@Composable
private fun OtherOpportunityRow(
    setup: OpportunitySetup,
    isSelected: Boolean,
    isKu: Boolean,
    onSelect: () -> Unit
) {
    val catColor = when (setup.signalCategory) {
        SignalCategory.BUY_SETUP -> EmeraldBull
        SignalCategory.SELL_SETUP -> CrimsonBear
        SignalCategory.WATCH -> AmberWatch
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isSelected) SheraGold else ObsidianCardBorder, RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .testTag("profit_row_${setup.instId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF1A2133) else ObsidianSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = setup.instId,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "RSI: ${QuantIndicatorEngine.formatNum(setup.snapshot.rsi14, 1)} • Score: ${setup.confluenceScore}/100",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${QuantIndicatorEngine.formatPrice(setup.snapshot.currentPrice)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SheraMonoFont,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(catColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isKu) setup.signalCategory.labelKu else setup.signalCategory.labelEn,
                        style = MaterialTheme.typography.labelSmall,
                        color = catColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
