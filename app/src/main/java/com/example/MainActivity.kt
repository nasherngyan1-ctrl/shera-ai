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
import com.example.ui.screens.SingleChatScreen
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

    val language by viewModel.language.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val selectedChartBitmap by viewModel.selectedChartBitmap.collectAsStateWithLifecycle()
    val inputMessage by viewModel.inputMessage.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val liveMinuteSignal by viewModel.liveMinuteSignal.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        SingleChatScreen(
            chatMessages = chatMessages,
            selectedChartBitmap = selectedChartBitmap,
            inputMessage = inputMessage,
            aiState = aiState,
            language = language,
            liveMinuteSignal = liveMinuteSignal,
            onInputMessageChange = viewModel::setInputMessage,
            onBitmapSelected = viewModel::setSelectedChartBitmap,
            onSendMessage = { text, bmp -> viewModel.sendChatMessage(text, bmp) },
            onLanguageChange = viewModel::setLanguage
        )
    }
}
