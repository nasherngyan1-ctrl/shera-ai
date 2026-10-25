package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.QuantIndicatorEngine
import com.example.model.AiModelChoice
import com.example.model.AiVisionForecast
import com.example.model.AppLanguage
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
import com.example.viewmodel.AiAnalysisUiState

@Composable
fun AiVisionTradingTab(
    selectedInstId: String,
    selectedSetup: OpportunitySetup?,
    selectedAiModel: AiModelChoice,
    selectedChartBitmap: Bitmap?,
    aiUserQuestion: String,
    aiState: AiAnalysisUiState,
    language: AppLanguage,
    onSelectModel: (AiModelChoice) -> Unit,
    onBitmapSelected: (Bitmap?) -> Unit,
    onQuestionChange: (String) -> Unit,
    onCaptureCurrentChartAndAnalyze: () -> Unit,
    onRunAiAnalysis: () -> Unit
) {
    val context = LocalContext.current
    val isKu = language == AppLanguage.KURDISH_SORANI

    // Zero-permission Android Photo Picker (Google Play Policy Compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                onBitmapSelected(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_vision_lazy_column"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheraGold.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = SheraGoldBright,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isKu) {
                                    "ژووری شیکاری وێنەی چارت و پێشبینی بە AI"
                                } else {
                                    "SHÊRA AI Chart Vision & Quant Forecast Desk"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = SheraGoldBright
                            )
                            Text(
                                text = if (isKu) {
                                    "وێنەی چارت بنێرە یان چارتی زیندووی OKX شیکار بکە بە بەهێزترین مۆدێلی Gemini"
                                } else {
                                    "Upload any chart screenshot or analyze live OKX telemetry with Gemini AI"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // AI Model Selection
                    Text(
                        text = if (isKu) "١. هەڵبژاردنی مێشکی زیرەکی دەستکرد بۆ ترەیدینگ:" else "1. Select Trading AI Engine:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    AiModelChoice.entries.forEach { model ->
                        val isSelected = model == selectedAiModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1F263B) else ObsidianSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) SheraGold else ObsidianCardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectModel(model) }
                                .padding(10.dp)
                                .testTag("ai_model_option_${model.name}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectModel(model) },
                                colors = RadioButtonDefaults.colors(selectedColor = SheraGold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isKu) model.titleKu else model.titleEn,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) SheraGoldBright else Color.White
                                )
                                Text(
                                    text = if (isKu) model.badgeKu else model.badgeEn,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBull
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Chart Image Upload / Snapshot Section
                    Text(
                        text = if (isKu) "٢. ناردنی وێنەی چارت یان بەکارهێنانی چارتی OKX:" else "2. Upload Chart Screenshot or Use Live OKX Chart:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SheraGold,
                                contentColor = Color(0xFF0A0D14)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_chart_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isKu) "ناردنی وێنە لە مۆبایلەوە" else "Upload Chart Image",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onCaptureCurrentChartAndAnalyze,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("capture_okx_chart_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = SheraGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isKu) "وێنەی چارتی $selectedInstId" else "Snap $selectedInstId Chart",
                                style = MaterialTheme.typography.labelLarge,
                                color = SheraGold
                            )
                        }
                    }

                    // Preview selected chart image if present
                    if (selectedChartBitmap != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianBackground)
                                .border(1.dp, SheraGold, RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = selectedChartBitmap.asImageBitmap(),
                                contentDescription = "Selected chart image for AI analysis",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { onBitmapSelected(null) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove image",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (selectedSetup != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isKu) {
                                "داتای زیندووی هاوپێچکراو: ${selectedSetup.instId} (${selectedSetup.timeframe.bar}) • نرخ: $${QuantIndicatorEngine.formatPrice(selectedSetup.snapshot.currentPrice)} • RSI: ${QuantIndicatorEngine.formatNum(selectedSetup.snapshot.rsi14, 1)}"
                            } else {
                                "Attached Live Context: ${selectedSetup.instId} (${selectedSetup.timeframe.bar}) • Price: $${QuantIndicatorEngine.formatPrice(selectedSetup.snapshot.currentPrice)} • RSI: ${QuantIndicatorEngine.formatNum(selectedSetup.snapshot.rsi14, 1)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldBull
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = aiUserQuestion,
                        onValueChange = onQuestionChange,
                        placeholder = {
                            Text(
                                if (isKu) {
                                    "پرسیار یان تێبینیی تایبەت بنووسە (ئارەزوومەندانە: وەک ئایا ئەم چارتە بۆ کڕین گونجاوە؟)..."
                                } else {
                                    "Optional question (e.g., Is this breakout valid or a liquidity trap?)..."
                                }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_question_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRunAiAnalysis,
                        enabled = aiState !is AiAnalysisUiState.Loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldBull,
                            contentColor = Color(0xFF041B13)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("run_ai_vision_button")
                    ) {
                        if (aiState is AiAnalysisUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF041B13),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isKu) "SHÊRA AI خەریکی خوێندنەوەی چارت و ژمارەکانە..." else "SHÊRA AI is analyzing chart & quant levels...",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKu) {
                                    if (selectedChartBitmap != null) "شیکارکردنی وێنەی چارت و پێشبینی بە AI"
                                    else "شیکاری قووڵی $selectedInstId بە AI"
                                } else {
                                    if (selectedChartBitmap != null) "Analyze Chart Image & Forecast"
                                    else "Run Deep AI Forecast on $selectedInstId"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        when (aiState) {
            is AiAnalysisUiState.Idle -> {}
            is AiAnalysisUiState.Loading -> {}
            is AiAnalysisUiState.Error -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CrimsonBear, RoundedCornerShape(14.dp))
                            .testTag("ai_error_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1219))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = if (isKu) "هەڵە لە کاتی شیکاری زیرەکی دەستکرد:" else "AI Analysis Error:",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrimsonBear
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = aiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            is AiAnalysisUiState.Success -> {
                item {
                    AiForecastResultCard(forecast = aiState.forecast, isKu = isKu)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiForecastResultCard(
    forecast: AiVisionForecast,
    isKu: Boolean
) {
    val catColor = when (forecast.signalCategory) {
        SignalCategory.BUY_SETUP -> EmeraldBull
        SignalCategory.SELL_SETUP -> CrimsonBear
        SignalCategory.WATCH -> AmberWatch
    }
    val riskColor = when (forecast.riskLevel) {
        RiskLevel.LOW -> EmeraldBull
        RiskLevel.MEDIUM -> AmberWatch
        RiskLevel.HIGH -> CrimsonBear
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, catColor, RoundedCornerShape(18.dp))
            .testTag("ai_forecast_result_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${forecast.detectedSymbol} • ${forecast.detectedTimeframe}",
                        style = MaterialTheme.typography.titleLarge,
                        color = SheraGoldBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Model: ${forecast.modelUsed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(catColor.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${forecast.signalCategory.shortTag} • ${if (isKu) "نمرەی کوالێتی" else "Quality Score"}: ${forecast.confidenceScore}/100",
                        style = MaterialTheme.typography.labelLarge,
                        color = catColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (forecast.verifiedBacktestWinRatePct != null && forecast.verifiedBacktestTrades > 0) {
                    if (isKu) {
                        "پشتڕاستکراوە بە Backtest: ڕێژەی سەرکەوتنی مێژوویی ${QuantIndicatorEngine.formatNum(forecast.verifiedBacktestWinRatePct, 1)}% (${forecast.verifiedBacktestTrades} مامەڵە) • نمرەی کوالێتی 100 ئەگەری قازانج نییە"
                    } else {
                        "Backtest-Verified Win Rate: ${QuantIndicatorEngine.formatNum(forecast.verifiedBacktestWinRatePct, 1)}% (${forecast.verifiedBacktestTrades} trades) • Quality Score/100 is not profit guarantee"
                    }
                } else {
                    if (isKu) {
                        "ڕێژەی سەرکەوتنی مێژوویی: پشتڕاست نەکراوەتەوە بە Backtest لەسەر ئەم وێنە/تایم‌فریمە • نمرەی کوالێتی 100 ئەگەری قازانج نییە"
                    } else {
                        "Historical Win Rate: Unverified by Backtest on this chart • Quality Score/100 is not profit probability"
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (forecast.verifiedBacktestWinRatePct != null) EmeraldBull else TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Structure summary
            Text(
                text = if (isKu) forecast.marketStructureSummaryKu else forecast.marketStructureSummaryEn,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )

            if (forecast.detectedPatterns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    forecast.detectedPatterns.forEach { pat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianSurfaceVariant)
                                .border(1.dp, SheraGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = pat,
                                style = MaterialTheme.typography.labelSmall,
                                color = SheraGoldBright
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Price Action Map (Entry, Stop/Invalidation, Targets T1/T2/T3)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isKu) "نەخشەی ئاستەکانی چارت و پێشبینی (Price Action Map)" else "AI Extracted Price Action Map",
                    style = MaterialTheme.typography.titleMedium,
                    color = SheraGoldBright
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ForecastKeyVal(
                        label = if (isKu) "خاڵی چوونەژوورەوە" else "Suggested Entry",
                        value = forecast.entryZone,
                        color = SheraGoldBright
                    )
                    ForecastKeyVal(
                        label = if (isKu) "سنووری هەڵوەشاندنەوە (Stop)" else "Invalidation (Stop)",
                        value = forecast.invalidationLevel,
                        color = CrimsonBear
                    )
                    ForecastKeyVal(
                        label = if (isKu) "مەترسی / R:R" else "Risk / R:R",
                        value = "${if (isKu) forecast.riskLevel.labelKu else forecast.riskLevel.labelEn} (${forecast.riskRewardEstimate})",
                        color = riskColor
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ForecastKeyVal(
                        label = if (isKu) "ئامانجی یەکەم (T1)" else "Target 1 (T1)",
                        value = forecast.target1,
                        color = EmeraldBull
                    )
                    ForecastKeyVal(
                        label = if (isKu) "ئامانجی دووەم (T2)" else "Target 2 (T2)",
                        value = forecast.target2,
                        color = EmeraldBull
                    )
                    ForecastKeyVal(
                        label = if (isKu) "ئامانجی سێیەم (T3)" else "Target 3 (T3)",
                        value = forecast.target3,
                        color = EmeraldBull
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ForecastKeyVal(
                        label = if (isKu) "ناوچەی پشتگیری (Support)" else "Support Zone",
                        value = forecast.supportZone,
                        color = Color.White
                    )
                    ForecastKeyVal(
                        label = if (isKu) "ناوچەی بەرگری (Resistance)" else "Resistance Zone",
                        value = forecast.resistanceZone,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bullish vs Bearish Scenarios
            ScenarioBox(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = if (isKu) "سیناریۆی ئەرێنی و بەرزبوونەوە (Bullish Scenario)" else "Bullish Continuation / Reversal Scenario",
                body = if (isKu) forecast.bullishScenarioKu else forecast.bullishScenarioEn,
                accentColor = EmeraldBull
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScenarioBox(
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                title = if (isKu) "سیناریۆی نەرێنی و هەڵوەشاندنەوە (Bearish / Invalidation)" else "Bearish Breakdown & Invalidation Scenario",
                body = if (isKu) forecast.bearishScenarioKu else forecast.bearishScenarioEn,
                accentColor = CrimsonBear
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isKu) {
                    "تێبینی: ئەم شیکارییە بە شێوەی خۆکار لە مێژووی نیشانە هەورییەکانت (Firestore) پاشەکەوت کرا. هیچ پێشبینییەک مسۆگەری ١٠٠٪ی قازانج نییە."
                } else {
                    "Note: Saved automatically to your Cloud Signal History. All setups are probabilistic with no profit guarantees."
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ForecastKeyVal(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = SheraMonoFont,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ScenarioBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.10f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
