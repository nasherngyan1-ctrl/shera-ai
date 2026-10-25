package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Radar
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
import com.example.model.MarketStructure
import com.example.model.OkxTimeframe
import com.example.model.OpportunitySetup
import com.example.model.RiskLevel
import com.example.model.SignalCategory
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
fun OpportunityScannerTab(
    opportunities: List<OpportunitySetup>,
    isScannerRunning: Boolean,
    selectedTimeframe: OkxTimeframe,
    categoryFilter: SignalCategory?,
    riskFilter: RiskLevel?,
    structureFilter: MarketStructure?,
    language: AppLanguage,
    onSelectTimeframe: (OkxTimeframe) -> Unit,
    onCategoryFilterChange: (SignalCategory?) -> Unit,
    onRiskFilterChange: (RiskLevel?) -> Unit,
    onStructureFilterChange: (MarketStructure?) -> Unit,
    onRunScannerClick: () -> Unit,
    onOpenPairInChart: (String) -> Unit,
    onOpenPairInAiVision: (String) -> Unit
) {
    val isKu = language == AppLanguage.KURDISH_SORANI

    val filteredList = remember(opportunities, categoryFilter, riskFilter, structureFilter) {
        opportunities.filter { item ->
            val matchCat = categoryFilter == null || item.signalCategory == categoryFilter
            val matchRisk = riskFilter == null || item.riskLevel == riskFilter
            val matchStruct = structureFilter == null || item.snapshot.marketStructure == structureFilter
            matchCat && matchRisk && matchStruct
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("opportunity_scanner_list"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isKu) "سیستەمی دۆزینەوەی هەلەکان" else "Multi-Factor Opportunity Scanner",
                                style = MaterialTheme.typography.titleLarge,
                                color = SheraGoldBright
                            )
                            Text(
                                text = if (isKu) {
                                    "ڕیزبەندی بەپێی کوالێتی نیشانە (/100)، قەبارە، لیکویدیتی و مەترسی • جیاکردنەوەی BUY WATCH / SELL WATCH / NEUTRAL"
                                } else {
                                    "Ranked by Signal Quality Score (/100), 24h liquidity & risk • BUY WATCH / SELL WATCH / NEUTRAL"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = onRunScannerClick,
                            enabled = !isScannerRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SheraGold,
                                contentColor = Color(0xFF0A0D14)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("scan_now_button")
                        ) {
                            if (isScannerRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF0A0D14),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isKu) "پشکنینی بازاڕ" else "Scan Now",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Timeframe row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OkxTimeframe.entries.forEach { tf ->
                            FilterChip(
                                selected = tf == selectedTimeframe,
                                onClick = { onSelectTimeframe(tf) },
                                label = { Text(tf.bar) }
                            )
                        }
                    }

                    // Signal Category & Risk Filter Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = categoryFilter == null,
                            onClick = { onCategoryFilterChange(null) },
                            label = { Text(if (isKu) "هەموو نیشانەکان" else "All Setups") }
                        )
                        SignalCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = categoryFilter == cat,
                                onClick = {
                                    onCategoryFilterChange(if (categoryFilter == cat) null else cat)
                                },
                                label = { Text(if (isKu) cat.labelKu else cat.labelEn) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RiskLevel.entries.forEach { r ->
                            FilterChip(
                                selected = riskFilter == r,
                                onClick = { onRiskFilterChange(if (riskFilter == r) null else r) },
                                label = { Text(if (isKu) r.labelKu else r.labelEn) }
                            )
                        }
                        MarketStructure.entries.forEach { st ->
                            FilterChip(
                                selected = structureFilter == st,
                                onClick = {
                                    onStructureFilterChange(if (structureFilter == st) null else st)
                                },
                                label = { Text(if (isKu) st.labelKu else st.labelEn) }
                            )
                        }
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isScannerRunning) {
                            if (isKu) "خەریکی پشکنینی مۆمەکان و حیسابکردنی RSI/EMA/MACD و Backtest..." else "Scanning OKX SPOT/USDT candles & verifying historical backtest..."
                        } else {
                            if (isKu) "هیچ دراوێک لەگەڵ ئەم فلتەرەدا نەدۆزرایەوە." else "No opportunities match the active filter."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(filteredList, key = { "${it.instId}_${it.timeframe.bar}" }) { item ->
                OpportunityItemCard(
                    setup = item,
                    isKu = isKu,
                    onOpenInChart = { onOpenPairInChart(item.instId) },
                    onOpenInAiVision = { onOpenPairInAiVision(item.instId) }
                )
            }
        }
    }
}

@Composable
private fun OpportunityItemCard(
    setup: OpportunitySetup,
    isKu: Boolean,
    onOpenInChart: () -> Unit,
    onOpenInAiVision: () -> Unit
) {
    val catColor = when (setup.signalCategory) {
        SignalCategory.BUY_SETUP -> EmeraldBull
        SignalCategory.SELL_SETUP -> CrimsonBear
        SignalCategory.WATCH -> AmberWatch
    }
    val riskColor = when (setup.riskLevel) {
        RiskLevel.LOW -> EmeraldBull
        RiskLevel.MEDIUM -> AmberWatch
        RiskLevel.HIGH -> CrimsonBear
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, catColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable { onOpenInChart() }
            .testTag("opportunity_card_${setup.instId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = setup.instId,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = setup.timeframe.bar,
                            style = MaterialTheme.typography.labelSmall,
                            color = SheraGold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(catColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${setup.signalCategory.shortTag} • ${if (isKu) "کوالێتی" else "Score"}: ${setup.confluenceScore}/100",
                        style = MaterialTheme.typography.labelLarge,
                        color = catColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Verified Backtest Win Rate Row (Rule 5: Only shown when verified by historical backtest)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = if (setup.verifiedBacktestWinRatePct != null) EmeraldBull else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (setup.verifiedBacktestWinRatePct != null && setup.verifiedBacktestSampleSize > 0) {
                        if (isKu) {
                            "پشتڕاستکراوە بە Backtest: سەرکەوتنی مێژوویی ${QuantIndicatorEngine.formatNum(setup.verifiedBacktestWinRatePct, 1)}% (${setup.verifiedBacktestSampleSize} مامەڵە) • نمرەی 100 ئەگەری قازانج نییە"
                        } else {
                            "Backtest Verified Win Rate: ${QuantIndicatorEngine.formatNum(setup.verifiedBacktestWinRatePct, 1)}% (${setup.verifiedBacktestSampleSize} trades) • Score/100 is not profit guarantee"
                        }
                    } else {
                        if (isKu) {
                            "ڕێژەی سەرکەوتنی مێژوویی: بێ مامەڵەی مێژوویی لەم تایم‌فریمەدا • نمرەی 100 ئەگەری قازانج نییە"
                        } else {
                            "Historical Win Rate: Unverified (0 historical trades on timeframe) • Score/100 is not profit guarantee"
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (setup.verifiedBacktestWinRatePct != null) EmeraldBull else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rapid Move or Volume Anomaly Badges
            if (setup.snapshot.isVolumeAnomaly || setup.snapshot.isRapidPriceMove) {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (setup.snapshot.isVolumeAnomaly) {
                        AnomalyChip(
                            text = if (isKu) {
                                "بازدانی قەبارە ${QuantIndicatorEngine.formatNum(setup.snapshot.volumeRatio20, 2)}x"
                            } else {
                                "Volume Spike ${QuantIndicatorEngine.formatNum(setup.snapshot.volumeRatio20, 2)}x"
                            },
                            color = SheraGoldBright
                        )
                    }
                    if (setup.snapshot.isRapidPriceMove) {
                        AnomalyChip(
                            text = if (isKu) {
                                "جووڵەی خێرا ${QuantIndicatorEngine.formatNum(setup.snapshot.rapidMovePct3Bars, 2)}%"
                            } else {
                                "Rapid 3-Bar Move ${QuantIndicatorEngine.formatNum(setup.snapshot.rapidMovePct3Bars, 2)}%"
                            },
                            color = EmeraldBull
                        )
                    }
                }
            }

            // Key Prices & R:R Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceItem(
                    label = if (isKu) "نرخی ئێستا" else "Last Price",
                    value = QuantIndicatorEngine.formatPrice(setup.snapshot.currentPrice),
                    color = Color.White
                )
                PriceItem(
                    label = if (isKu) "هەڵوەشاندنەوە" else "Invalidation",
                    value = QuantIndicatorEngine.formatPrice(setup.invalidationPrice),
                    color = CrimsonBear
                )
                PriceItem(
                    label = if (isKu) "ئامانج T1" else "Target T1",
                    value = QuantIndicatorEngine.formatPrice(setup.target1Price),
                    color = EmeraldBull
                )
                PriceItem(
                    label = if (isKu) "مەترسی / R:R" else "Risk / R:R",
                    value = "${setup.riskLevel.code} (1:${QuantIndicatorEngine.formatNum(setup.riskRewardRatio, 1)})",
                    color = riskColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val reasons = if (isKu) setup.reasonsKurdish else setup.reasonsEnglish
            reasons.forEach { reason ->
                Text(
                    text = "• $reason",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onOpenInAiVision,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SheraGold,
                        contentColor = Color(0xFF0A0D14)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("ai_deep_check_${setup.instId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isKu) "شیکاری قووڵی AI" else "Deep AI Forecast",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AnomalyChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun PriceItem(label: String, value: String, color: Color) {
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
