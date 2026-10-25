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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.model.WatchlistItem
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

@Composable
fun MarketsAndChartTab(
    tickers: List<OkxTicker>,
    watchlistItems: List<WatchlistItem>,
    connectionStatus: ConnectionStatus,
    selectedInstId: String,
    selectedTimeframe: OkxTimeframe,
    chartDisplayMode: ChartDisplayMode,
    selectedCandles: List<OkxCandle>,
    selectedSetup: OpportunitySetup?,
    derivativesTelemetry: DerivativesTelemetry?,
    isChartLoading: Boolean,
    searchQuery: String,
    showWatchlistOnly: Boolean,
    currentPage: Int,
    pageSize: Int,
    language: AppLanguage,
    onSearchQueryChange: (String) -> Unit,
    onToggleWatchlistOnly: (Boolean) -> Unit,
    onSelectPair: (String) -> Unit,
    onSelectTimeframe: (OkxTimeframe) -> Unit,
    onSelectChartMode: (ChartDisplayMode) -> Unit,
    onToggleWatchlistItem: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onRefreshClick: () -> Unit,
    onSendChartToAiVision: () -> Unit,
    onSaveSetupToCloud: (OpportunitySetup) -> Unit
) {
    val isKu = language == AppLanguage.KURDISH_SORANI
    val watchedSet = remember(watchlistItems) { watchlistItems.map { it.instId }.toSet() }

    val filteredTickers = remember(tickers, searchQuery, showWatchlistOnly, watchedSet) {
        tickers.filter { t ->
            val matchesQuery = searchQuery.isBlank() ||
                t.instId.contains(searchQuery.trim(), ignoreCase = true)
            val matchesWatch = !showWatchlistOnly || (t.instId in watchedSet)
            matchesQuery && matchesWatch
        }
    }

    val totalPages = remember(filteredTickers.size, pageSize) {
        ceil(filteredTickers.size.toDouble() / pageSize.toDouble()).toInt().coerceAtLeast(1)
    }
    val safePage = currentPage.coerceIn(0, totalPages - 1)
    val pageTickers = remember(filteredTickers, safePage, pageSize) {
        val start = safePage * pageSize
        val end = (start + pageSize).coerceAtMost(filteredTickers.size)
        if (start < filteredTickers.size) filteredTickers.subList(start, end) else emptyList()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("markets_lazy_column"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ConnectionTelemetryBar(
                status = connectionStatus,
                isKu = isKu,
                onRefreshClick = onRefreshClick
            )
        }

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
                onSendChartToAiVision = onSendChartToAiVision,
                onSaveSetupToCloud = onSaveSetupToCloud
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isKu) {
                            "بازاڕەکانی OKX SPOT/USDT (${filteredTickers.size} دراو)"
                        } else {
                            "OKX SPOT/USDT Markets (${filteredTickers.size} Pairs)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = SheraGoldBright
                    )
                    FilterChip(
                        selected = showWatchlistOnly,
                        onClick = { onToggleWatchlistOnly(!showWatchlistOnly) },
                        label = {
                            Text(
                                if (isKu) "لیستی چاودێری (${watchedSet.size})" else "Watchlist (${watchedSet.size})"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showWatchlistOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = SheraGold,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("watchlist_filter_chip")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            if (isKu) "گەڕان بەدوای دراو (وەک BTC, ETH, SOL, PEPE)..." else "Search SPOT/USDT pair (e.g. BTC, ETH, SOL)..."
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("market_search_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Pagination Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isKu) {
                            "پەڕەی ${safePage + 1} لە $totalPages (ڕیزبەند بەپێی قەبارەی 24h)"
                        } else {
                            "Page ${safePage + 1} of $totalPages (Sorted by 24h Volume)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onPageChange(safePage - 1) },
                            enabled = safePage > 0,
                            modifier = Modifier.testTag("prev_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                contentDescription = "Previous page"
                            )
                        }
                        IconButton(
                            onClick = { onPageChange(safePage + 1) },
                            enabled = safePage < totalPages - 1,
                            modifier = Modifier.testTag("next_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = "Next page"
                            )
                        }
                    }
                }
            }
        }

        items(pageTickers, key = { it.instId }) { ticker ->
            TickerRowCard(
                ticker = ticker,
                isSelected = ticker.instId == selectedInstId,
                isWatched = ticker.instId in watchedSet,
                isKu = isKu,
                onSelect = { onSelectPair(ticker.instId) },
                onToggleWatch = { onToggleWatchlistItem(ticker.instId) }
            )
        }
    }
}

@Composable
private fun ConnectionTelemetryBar(
    status: ConnectionStatus,
    isKu: Boolean,
    onRefreshClick: () -> Unit
) {
    val isStale = status.isStale()
    val dotColor = when {
        status.state == ConnectionStateType.ERROR -> CrimsonBear
        isStale || status.state == ConnectionStateType.RECONNECTING -> AmberWatch
        status.state == ConnectionStateType.LIVE_WS -> EmeraldBull
        else -> SheraGold
    }
    val statusText = when {
        status.state == ConnectionStateType.ERROR ->
            if (isKu) "هەڵە لە پەیوەندی: ${status.errorMessage.orEmpty()}" else "Connection Error: ${status.errorMessage.orEmpty()}"
        isStale ->
            if (isKu) "ئاگاداری: داتای بازاڕ کۆنە (>45s) — تکایە نوێی بکەرەوە" else "Warning: Market data is stale (>45s)"
        status.state == ConnectionStateType.LIVE_WS ->
            if (isKu) "پەیوەستە بە OKX WebSocket زیندوو (${status.activeSpotPairsCount} بازاڕ)" else "OKX WebSocket Live (${status.activeSpotPairsCount} SPOT pairs)"
        status.state == ConnectionStateType.RECONNECTING ->
            if (isKu) "دووبارە پەیوەستبوون بە OKX WebSocket (هەوڵی #${status.reconnectAttempt})..." else "Reconnecting to OKX WebSocket (#${status.reconnectAttempt})..."
        else ->
            if (isKu) "پەیوەستە بە OKX REST v5 (${status.activeSpotPairsCount} بازاڕ)" else "OKX REST v5 Active (${status.activeSpotPairsCount} SPOT pairs)"
    }
    val timeText = if (status.lastUpdatedEpochMs > 0L) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(status.lastUpdatedEpochMs))
    } else {
        "--:--:--"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceVariant)
            .border(1.dp, dotColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("connection_status_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isStale || status.state == ConnectionStateType.ERROR) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = dotColor,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                Text(
                    text = if (isKu) {
                        "دوایین نوێکردنەوە: $timeText • کاش: ${status.cachedPairsCount} • داتای گشتی بێ API Key"
                    } else {
                        "Last update: $timeText • Cache: ${status.cachedPairsCount} • Public API"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        IconButton(
            onClick = onRefreshClick,
            modifier = Modifier.testTag("refresh_markets_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh OKX Markets",
                tint = SheraGold
            )
        }
    }
}

@Composable
private fun TickerRowCard(
    ticker: OkxTicker,
    isSelected: Boolean,
    isWatched: Boolean,
    isKu: Boolean,
    onSelect: () -> Unit,
    onToggleWatch: () -> Unit
) {
    val changeColor = if (ticker.change24hPct >= 0.0) EmeraldBull else CrimsonBear
    val borderColor = if (isSelected) SheraGold else ObsidianCardBorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .testTag("ticker_row_${ticker.instId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1A2133) else ObsidianSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleWatch,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("watch_star_${ticker.instId}")
                ) {
                    Icon(
                        imageVector = if (isWatched) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Watchlist",
                        tint = if (isWatched) SheraGold else TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = ticker.baseSymbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${ticker.instId} • Vol $${formatCompactUsdt(ticker.volUsdt24h)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${QuantIndicatorEngine.formatPrice(ticker.lastPrice)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SheraMonoFont,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(changeColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${if (ticker.change24hPct >= 0) "+" else ""}${QuantIndicatorEngine.formatNum(ticker.change24hPct, 2)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = changeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatCompactUsdt(value: Double): String {
    return when {
        value >= 1_000_000_000.0 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000.0)
        value >= 1_000_000.0 -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
        value >= 1_000.0 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> String.format(Locale.US, "%.0f", value)
    }
}
