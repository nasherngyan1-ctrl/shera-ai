package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.QuantIndicatorEngine
import com.example.model.AlertRule
import com.example.model.AppLanguage
import com.example.model.BacktestReport
import com.example.model.OkxTimeframe
import com.example.model.SignalRecord
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
import com.example.viewmodel.BacktestUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BacktestAndAlertsTab(
    selectedInstId: String,
    selectedTimeframe: OkxTimeframe,
    backtestState: BacktestUiState,
    alertRules: List<AlertRule>,
    signalHistory: List<SignalRecord>,
    language: AppLanguage,
    onRunBacktest: (Double, Double) -> Unit,
    onCreateAlertRule: (String, String, Double, String, Long) -> Unit,
    onToggleAlertRule: (AlertRule) -> Unit,
    onDeleteAlertRule: (String) -> Unit,
    onEvaluateAlertsNow: () -> Unit,
    onDeleteSignalRecord: (String) -> Unit
) {
    val isKu = language == AppLanguage.KURDISH_SORANI
    var activeSection by remember { mutableIntStateOf(0) } // 0 = Backtest, 1 = 24/7 Alerts, 2 = History

    var feeInput by remember { mutableStateOf("0.10") }
    var slippageInput by remember { mutableStateOf("0.05") }

    // Alert creation form state
    var alertInstId by remember(selectedInstId) { mutableStateOf(selectedInstId) }
    var alertType by remember { mutableStateOf("PRICE_ABOVE") }
    var alertThreshold by remember { mutableStateOf("95000") }
    var alertCooldown by remember { mutableStateOf("30") }

    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onEvaluateAlertsNow()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("backtest_alerts_lazy_column"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeSection == 0,
                    onClick = { activeSection = 0 },
                    label = { Text(if (isKu) "تاقیکردنەوەی مێژوویی" else "Backtest Lab") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("subtab_backtest")
                )
                FilterChip(
                    selected = activeSection == 1,
                    onClick = { activeSection = 1 },
                    label = { Text(if (isKu) "ئاگادارکردنەوەی 24/7 (${alertRules.size})" else "Alerts (${alertRules.size})") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("subtab_alerts")
                )
                FilterChip(
                    selected = activeSection == 2,
                    onClick = { activeSection = 2 },
                    label = { Text(if (isKu) "مێژووی نیشانەکان (${signalHistory.size})" else "History (${signalHistory.size})") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("subtab_history")
                )
            }
        }

        when (activeSection) {
            0 -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (isKu) {
                                    "تاقیکردنەوەی مێژوویی (Walk-Forward Backtesting) • $selectedInstId (${selectedTimeframe.bar})"
                                } else {
                                    "Walk-Forward Historical Backtest • $selectedInstId (${selectedTimeframe.bar})"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = SheraGoldBright
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isKu) {
                                    "پارێزراو دژە Look-ahead bias: نیشانە لەسەر مۆمی داخراوی (i) حیساب دەکرێت و چوونەژوورەوە لە کرانەوەی مۆمی (i+1) جێبەجێ دەکرێت بە لێدەرکردنی کرێ و خلیسکان. (جیاکراوە لە نیشانەی بازاڕی زیندوو)"
                                } else {
                                    "Zero Look-Ahead Bias: Signals computed strictly on closed candle [i], executed at open of candle [i+1] with fee & slippage deducted. (Strictly separated from live market signals)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = feeInput,
                                    onValueChange = { feeInput = it },
                                    label = { Text(if (isKu) "کرێی بۆرسە Fee %" else "OKX Fee %") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = slippageInput,
                                    onValueChange = { slippageInput = it },
                                    label = { Text(if (isKu) "خلیسکانی نرخ Slippage %" else "Slippage %") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val fee = feeInput.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.10
                                    val slip = slippageInput.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.05
                                    onRunBacktest(fee, slip)
                                },
                                enabled = backtestState !is BacktestUiState.Running,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SheraGold,
                                    contentColor = Color(0xFF0A0D14)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("run_backtest_button")
                            ) {
                                if (backtestState is BacktestUiState.Running) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF0A0D14),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isKu) "دەستپێکردنی تاقیکردنەوەی مێژوویی لەسەر مۆمەکانی OKX" else "Run Historical Backtest on OKX Candles",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                when (backtestState) {
                    is BacktestUiState.Idle -> {}
                    is BacktestUiState.Running -> {}
                    is BacktestUiState.Error -> {
                        item {
                            Text(
                                text = backtestState.message,
                                color = CrimsonBear,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is BacktestUiState.Success -> {
                        item {
                            BacktestReportCard(report = backtestState.report, isKu = isKu)
                        }
                    }
                }
            }

            1 -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (isKu) "دانانی یاسای ئاگادارکردنەوەی ٢٤/٧ (Cloud Synced)" else "Create 24/7 Market Alert Rule",
                                style = MaterialTheme.typography.titleLarge,
                                color = SheraGoldBright
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = alertInstId,
                                    onValueChange = { alertInstId = it.uppercase() },
                                    label = { Text(if (isKu) "دراو (وەک BTC-USDT)" else "Pair (e.g. BTC-USDT)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = alertThreshold,
                                    onValueChange = { alertThreshold = it },
                                    label = { Text(if (isKu) "سنووری نرخ / قەبارە" else "Threshold") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val ruleTypes = listOf(
                                "PRICE_ABOVE" to (if (isKu) "نرخ بەرزتر لە" else "Price Above"),
                                "PRICE_BELOW" to (if (isKu) "نرخ نزمتر لە" else "Price Below"),
                                "SUPPORT_BREAK" to (if (isKu) "شکانی پشتگیری" else "Support Break"),
                                "RESISTANCE_BREAK" to (if (isKu) "شکانی بەرگری" else "Resistance Break"),
                                "VOLUME_SPIKE" to (if (isKu) "بازدانی قەبارە" else "Volume Spike"),
                                "SIGNAL_CHANGE" to (if (isKu) "گۆڕانی نیشانە" else "Signal Change")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ruleTypes.forEach { (code, label) ->
                                    FilterChip(
                                        selected = alertType == code,
                                        onClick = { alertType = code },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = alertCooldown,
                                onValueChange = { alertCooldown = it },
                                label = {
                                    Text(
                                        if (isKu) "ماوەی وەستان لە نێوان دوو ئاگادارکردنەوە بە خولەک (Cooldown)" else "Deduplication Cooldown (Minutes)"
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val thresh = alertThreshold.toDoubleOrNull() ?: 0.0
                                        val cd = alertCooldown.toLongOrNull()?.coerceIn(1L, 1440L) ?: 30L
                                        onCreateAlertRule(
                                            alertInstId.trim().ifBlank { selectedInstId },
                                            alertType,
                                            thresh,
                                            selectedTimeframe.bar,
                                            cd
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SheraGold,
                                        contentColor = Color(0xFF0A0D14)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_alert_rule_button")
                                ) {
                                    Icon(imageVector = Icons.Default.AddAlert, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isKu) "زیادکردنی ئاگادارکردنەوە" else "Save Alert Rule",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onEvaluateAlertsNow()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("check_alerts_now_button")
                                ) {
                                    Text(
                                        text = if (isKu) "پشکنینی ئێستا" else "Test Now",
                                        color = SheraGold
                                    )
                                }
                            }
                        }
                    }
                }

                items(alertRules, key = { it.id }) { rule ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${rule.instId} • ${rule.ruleType} (${rule.timeframe})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Threshold: ${QuantIndicatorEngine.formatPrice(rule.thresholdValue)} • Cooldown: ${rule.cooldownMinutes}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { onToggleAlertRule(rule) }
                                )
                                IconButton(onClick = { onDeleteAlertRule(rule.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete alert rule",
                                        tint = CrimsonBear
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (signalHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isKu) {
                                    "هێشتا هیچ نیشانەیەک یان شیکارییەکی وێنە پاشەکەوت نەکراوە."
                                } else {
                                    "No saved technical or AI chart vision signals yet."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    items(signalHistory, key = { it.id }) { record ->
                        val badgeColor = when (record.signalCategory) {
                            "BUY_SETUP" -> EmeraldBull
                            "SELL_SETUP" -> CrimsonBear
                            else -> AmberWatch
                        }
                        val dateStr = record.createdAt?.toDate()?.let {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(it)
                        } ?: ""

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${record.instId} (${record.timeframe}) • ${record.sourceType}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = SheraGoldBright,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$dateStr • Score: ${record.confluenceScore}% • Risk: ${record.riskLevel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = record.signalCategory,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = badgeColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(onClick = { onDeleteSignalRecord(record.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete signal record",
                                                tint = TextMuted
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isKu) record.summaryKurdish else record.summaryEnglish,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BacktestReportCard(report: BacktestReport, isKu: Boolean) {
    val netColor = if (report.netCumulativeReturnPct >= 0.0) EmeraldBull else CrimsonBear
    val oosColor = if (report.outOfSampleReturnPct >= 0.0) EmeraldBull else CrimsonBear

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SheraGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("backtest_report_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (isKu) {
                    "ئەنجامی تاقیکردنەوەی مێژوویی (جیا لە بازاڕی زیندوو) • ${report.candleCount} مۆم"
                } else {
                    "Historical Backtest Results (Separate from Live Market) • ${report.candleCount} Candles"
                },
                style = MaterialTheme.typography.titleMedium,
                color = SheraGoldBright
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BtMetric(
                    label = if (isKu) "کۆی مامەڵە" else "Trades",
                    value = "${report.totalTrades} (${report.winningTrades}W/${report.losingTrades}L)",
                    color = Color.White
                )
                BtMetric(
                    label = if (isKu) "ڕێژەی بردنەوە" else "Win Rate",
                    value = "${QuantIndicatorEngine.formatNum(report.winRatePct, 1)}%",
                    color = SheraGoldBright
                )
                BtMetric(
                    label = if (isKu) "داهاتی صافی" else "Net Return",
                    value = "${if (report.netCumulativeReturnPct >= 0) "+" else ""}${QuantIndicatorEngine.formatNum(report.netCumulativeReturnPct, 2)}%",
                    color = netColor
                )
                BtMetric(
                    label = "Max DD / PF",
                    value = "-${QuantIndicatorEngine.formatNum(report.maxDrawdownPct, 2)}% / ${QuantIndicatorEngine.formatNum(report.profitFactor, 2)}",
                    color = AmberWatch
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Out-of-Sample (30%) vs In-Sample (70%) & Total Costs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BtMetric(
                    label = if (isKu) "ناو نموونە (70% In-Sample)" else "In-Sample (70%)",
                    value = "${report.inSampleTrades}T (${if (report.inSampleReturnPct >= 0) "+" else ""}${QuantIndicatorEngine.formatNum(report.inSampleReturnPct, 2)}%)",
                    color = Color.White
                )
                BtMetric(
                    label = if (isKu) "دەرەوەی نموونە (30% OOS)" else "Out-of-Sample (30%)",
                    value = "${report.outOfSampleTrades}T • WR ${QuantIndicatorEngine.formatNum(report.outOfSampleWinRatePct, 0)}% (${if (report.outOfSampleReturnPct >= 0) "+" else ""}${QuantIndicatorEngine.formatNum(report.outOfSampleReturnPct, 2)}%)",
                    color = oosColor
                )
                BtMetric(
                    label = if (isKu) "کۆی کرێ+خلیسکان" else "Total Fee+Slip",
                    value = "-${QuantIndicatorEngine.formatNum(report.totalFeesAndSlippagePct, 2)}%",
                    color = CrimsonBear
                )
            }

            if (report.trades.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isKu) "دوایین مامەڵە تاقیکراوەکان (پاش دەرکردنی Fee و Slippage):" else "Simulated Walk-Forward Trades (Net of Fee & Slippage):",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                report.trades.takeLast(6).reversed().forEach { trade ->
                    val tColor = if (trade.netReturnPct >= 0) EmeraldBull else CrimsonBear
                    val sampleTag = if (trade.isOutOfSample) {
                        if (isKu) "[دەرەوەی نموونە OOS]" else "[Out-of-Sample]"
                    } else {
                        if (isKu) "[ناو نموونە]" else "[In-Sample]"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$sampleTag Entry: $${QuantIndicatorEngine.formatPrice(trade.entryPrice)} -> Exit: $${QuantIndicatorEngine.formatPrice(trade.exitPrice)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = SheraMonoFont,
                                color = Color.White
                            )
                            Text(
                                text = if (isKu) trade.exitReasonKu else trade.exitReasonEn,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "${if (trade.netReturnPct >= 0) "+" else ""}${QuantIndicatorEngine.formatNum(trade.netReturnPct, 2)}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = SheraMonoFont,
                            color = tColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BtMetric(label: String, value: String, color: Color) {
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
