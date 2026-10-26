package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.QuantIndicatorEngine
import com.example.model.AlertRule
import com.example.model.AppLanguage
import com.example.model.SignalRecord
import com.example.ui.theme.AmberWatch
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.SheraGold
import com.example.ui.theme.SheraGoldBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun BackgroundAlertsTab(
    selectedInstId: String,
    alertRules: List<AlertRule>,
    signalHistory: List<SignalRecord>,
    language: AppLanguage,
    onCreateAlertRule: (String, String, Double, String, Long) -> Unit,
    onToggleAlertRule: (AlertRule) -> Unit,
    onDeleteAlertRule: (String) -> Unit,
    onEvaluateAlertsNow: () -> Unit,
    onDeleteSignalRecord: (String) -> Unit
) {
    val isKu = language == AppLanguage.KURDISH_SORANI
    var alertInstId by remember(selectedInstId) { mutableStateOf(selectedInstId) }
    var alertThreshold by remember { mutableStateOf("95000") }

    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onEvaluateAlertsNow()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("background_alerts_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 24/7 Background Service Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldBull.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = EmeraldBull,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isKu) "🔔 سیستەمی چاودێری ٢٤ کاتژمێری (24/7 Background Radar)" else "🔔 24/7 Background Profit & Price Radar",
                                style = MaterialTheme.typography.titleMedium,
                                color = SheraGoldBright,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isKu) "ئەپەکە لە باگراونددا بەدوای دابەزینی نرخ و باشترین قازانجدا دەگەڕێت و نۆتیفیکەیشن دەبنێرێت" else "Continuously monitors price drops, profit surges, and sends background notifications",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onEvaluateAlertsNow()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldBull,
                                contentColor = Color(0xFF041B13)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isKu) "پشکنینی ئێستای نۆتیفیکەیشن" else "Test Notifications Now",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Add Quick Alert Rule
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
                        text = if (isKu) "➕ زیادکردنی ئاگادارکردنەوەی نوێ" else "➕ Add New 24/7 Alert Rule",
                        style = MaterialTheme.typography.titleMedium,
                        color = SheraGoldBright
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = alertInstId,
                            onValueChange = { alertInstId = it.uppercase() },
                            label = { Text(if (isKu) "دراو (وەک BTC-USDT)" else "Coin (e.g. BTC-USDT)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = alertThreshold,
                            onValueChange = { alertThreshold = it },
                            label = { Text(if (isKu) "سنووری نرخ ($)" else "Price Threshold") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val thresh = alertThreshold.toDoubleOrNull() ?: 0.0
                            onCreateAlertRule(alertInstId, "PRICE_BELOW", thresh, "1H", 30L)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SheraGold,
                            contentColor = Color(0xFF0A0D14)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.AddAlert, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isKu) "تۆمارکردنی ئاگادارکردنەوەی کڕین/دابەزین" else "Save Dip & Profit Alert",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Alert Rules List
        item {
            Text(
                text = if (isKu) "📋 یاسا چالاکەکانی چاودێری (${alertRules.size}):" else "📋 Active Alert Rules (${alertRules.size}):",
                style = MaterialTheme.typography.titleMedium,
                color = SheraGoldBright
            )
        }

        if (alertRules.isEmpty()) {
            item {
                Text(
                    text = if (isKu) "هیچ یاسایەکی چاودێری نییە. لە سەرەوە زیاد بکە." else "No alert rules set yet. Add one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
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
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${rule.instId} • ${rule.ruleType}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Threshold: $${QuantIndicatorEngine.formatPrice(rule.thresholdValue)}",
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
                                    contentDescription = "Delete",
                                    tint = CrimsonBear
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
