package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.SheraFirestoreRepository
import com.example.model.AppLanguage
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.SheraLionEmblem
import com.example.ui.auth.signOut
import com.example.ui.screens.TopProfitTab
import com.example.ui.screens.BackgroundAlertsTab
import com.example.ui.screens.AiVisionTradingTab
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SheraGold
import com.example.ui.theme.SheraGoldBright
import com.example.ui.theme.TextSecondary
import com.example.util.configureAppCheck
import com.example.util.ensureFirebaseInitialized
import com.example.viewmodel.CloudUiState
import com.example.viewmodel.SheraMainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase & App Check configuration with launch intent before UI or Firebase operations
        ensureFirebaseInitialized(this)
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBackground
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

internal fun FirebaseAuth?.safeAuthStateFlow(): Flow<FirebaseUser?> {
    if (this == null) return flowOf(null)
    return callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        runCatching { addAuthStateListener(listener) }
            .onFailure { trySend(null) }
        awaitClose {
            runCatching { removeAuthStateListener(listener) }
        }
    }
}

@Composable
fun AppNavigation() {
    SheraAuthenticatedDashboard(currentUserId = "guest_trader")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheraAuthenticatedDashboard(
    currentUserId: String,
    onExitDirectTerminal: () -> Unit = {}
) {
    val viewModel: SheraMainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                ensureFirebaseInitialized(app)
                val db = runCatching {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    FirebaseFirestore.getInstance(databaseId)
                }.getOrNull()
                SheraMainViewModel(
                    firestoreRepository = SheraFirestoreRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }

    val language by viewModel.language.collectAsStateWithLifecycle()
    val tickers by viewModel.tickers.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val selectedInstId by viewModel.selectedInstId.collectAsStateWithLifecycle()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val chartDisplayMode by viewModel.chartDisplayMode.collectAsStateWithLifecycle()
    val selectedCandles by viewModel.selectedCandles.collectAsStateWithLifecycle()
    val selectedSetup by viewModel.selectedSetup.collectAsStateWithLifecycle()
    val derivativesTelemetry by viewModel.derivativesTelemetry.collectAsStateWithLifecycle()
    val isChartLoading by viewModel.isChartLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showWatchlistOnly by viewModel.showWatchlistOnly.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()

    val opportunities by viewModel.opportunities.collectAsStateWithLifecycle()
    val isScannerRunning by viewModel.isScannerRunning.collectAsStateWithLifecycle()
    val scannerCategoryFilter by viewModel.scannerCategoryFilter.collectAsStateWithLifecycle()
    val scannerRiskFilter by viewModel.scannerRiskFilter.collectAsStateWithLifecycle()
    val scannerStructureFilter by viewModel.scannerStructureFilter.collectAsStateWithLifecycle()

    val selectedAiModel by viewModel.selectedAiModel.collectAsStateWithLifecycle()
    val selectedChartBitmap by viewModel.selectedChartBitmap.collectAsStateWithLifecycle()
    val aiUserQuestion by viewModel.aiUserQuestion.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    val backtestState by viewModel.backtestState.collectAsStateWithLifecycle()
    val latestAlertBanner by viewModel.latestAlertBanner.collectAsStateWithLifecycle()

    val watchlistUiState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val alertRulesUiState by viewModel.alertRulesState.collectAsStateWithLifecycle()
    val signalHistoryUiState by viewModel.signalHistoryState.collectAsStateWithLifecycle()

    val watchlistItems = (watchlistUiState as? CloudUiState.Success)?.data.orEmpty()
    val alertRules = (alertRulesUiState as? CloudUiState.Success)?.data.orEmpty()
    val signalHistory = (signalHistoryUiState as? CloudUiState.Success)?.data.orEmpty()

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    val isKu = language == AppLanguage.KURDISH_SORANI

    // Periodically evaluate active alert rules whenever tickers update
    LaunchedEffect(tickers, alertRules) {
        if (tickers.isNotEmpty() && alertRules.isNotEmpty()) {
            viewModel.evaluateAlertsNow(context)
        }
    }

    if (currentTab != 0) {
        BackHandler {
            currentTab = 0
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            containerColor = ObsidianBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SheraLionEmblem(modifier = Modifier.size(34.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "SHÊRA AI • شێرە AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SheraGoldBright,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isKu) "شیکاری OKX و بینینی چارت بە AI" else "OKX Quant & AI Vision Terminal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = isKu,
                            onClick = {
                                viewModel.setLanguage(
                                    if (isKu) AppLanguage.ENGLISH else AppLanguage.KURDISH_SORANI
                                )
                            },
                            label = { Text(if (isKu) "کوردی" else "EN") },
                            modifier = Modifier.testTag("top_language_toggle")
                        )
                        IconButton(
                            onClick = {
                                signOut(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onSignOutComplete = onExitDirectTerminal,
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ObsidianSurface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = ObsidianSurface,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
                        label = { Text(if (isKu) "باشترین قازانج" else "Top Profit") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0A0D14),
                            selectedTextColor = SheraGoldBright,
                            indicatorColor = SheraGold
                        ),
                        modifier = Modifier.testTag("nav_tab_top_profit")
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                        label = { Text(if (isKu) "چات و وێنە" else "AI Chat & Vision") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0A0D14),
                            selectedTextColor = SheraGoldBright,
                            indicatorColor = SheraGold
                        ),
                        modifier = Modifier.testTag("nav_tab_ai_vision")
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                        label = { Text(if (isKu) "چاودێری ٢٤/٧" else "24/7 Alerts") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0A0D14),
                            selectedTextColor = SheraGoldBright,
                            indicatorColor = SheraGold
                        ),
                        modifier = Modifier.testTag("nav_tab_background_alerts")
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (latestAlertBanner != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2B210B))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("triggered_alert_banner"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = SheraGoldBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = latestAlertBanner.orEmpty(),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissAlertBanner() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss alert",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        0 -> TopProfitTab(
                            tickers = tickers,
                            selectedInstId = selectedInstId,
                            selectedTimeframe = selectedTimeframe,
                            chartDisplayMode = chartDisplayMode,
                            selectedCandles = selectedCandles,
                            selectedSetup = selectedSetup,
                            derivativesTelemetry = derivativesTelemetry,
                            isChartLoading = isChartLoading,
                            opportunities = opportunities,
                            isScannerRunning = isScannerRunning,
                            language = language,
                            onSelectPair = viewModel::selectPair,
                            onSelectTimeframe = viewModel::selectTimeframe,
                            onSelectChartMode = viewModel::setChartDisplayMode,
                            onRefreshClick = { viewModel.refreshMarketsAndSelectedPair(true) },
                            onOpenInAiVision = {
                                currentTab = 1
                                viewModel.captureCurrentOkxChartAsBitmapAndAnalyze()
                            }
                        )

                        1 -> AiVisionTradingTab(
                            selectedInstId = selectedInstId,
                            selectedSetup = selectedSetup,
                            selectedAiModel = selectedAiModel,
                            selectedChartBitmap = selectedChartBitmap,
                            aiUserQuestion = aiUserQuestion,
                            aiState = aiState,
                            language = language,
                            onSelectModel = viewModel::setAiModelChoice,
                            onBitmapSelected = viewModel::setSelectedChartBitmap,
                            onQuestionChange = viewModel::setAiUserQuestion,
                            onCaptureCurrentChartAndAnalyze = { viewModel.captureCurrentOkxChartAsBitmapAndAnalyze() },
                            onRunAiAnalysis = { viewModel.analyzeCurrentImageOrPairWithAi() }
                        )

                        2 -> BackgroundAlertsTab(
                            selectedInstId = selectedInstId,
                            alertRules = alertRules,
                            signalHistory = signalHistory,
                            language = language,
                            onCreateAlertRule = viewModel::addAlertRule,
                            onToggleAlertRule = viewModel::toggleAlertRule,
                            onDeleteAlertRule = viewModel::deleteAlertRule,
                            onEvaluateAlertsNow = { viewModel.evaluateAlertsNow(context) },
                            onDeleteSignalRecord = viewModel::deleteSignalRecord
                        )
                    }
                }
            }
        }
    }
}
