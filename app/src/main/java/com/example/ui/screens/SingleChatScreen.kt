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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.model.AiVisionForecast
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.model.SignalCategory
import com.example.ui.auth.SheraLionEmblem
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.SheraGold
import com.example.ui.theme.SheraGoldBright
import com.example.ui.theme.SheraMonoFont
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AiAnalysisUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleChatScreen(
    chatMessages: List<ChatMessage>,
    selectedChartBitmap: Bitmap?,
    inputMessage: String,
    aiState: AiAnalysisUiState,
    language: AppLanguage,
    liveMinuteSignal: String,
    onInputMessageChange: (String) -> Unit,
    onBitmapSelected: (Bitmap?) -> Unit,
    onSendMessage: (String, Bitmap?) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val isKu = language == AppLanguage.KURDISH_SORANI
    val listState = rememberLazyListState()

    // Photo Picker for unlimited chart image uploads
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

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SheraLionEmblem(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SHÊRA AI • شێرە AI",
                                style = MaterialTheme.typography.titleMedium,
                                color = SheraGoldBright,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isKu) "چاتی بێسنوور و شیکاری وێنەی چارت" else "Unlimited AI Chat & Chart Vision",
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
                            onLanguageChange(if (isKu) AppLanguage.ENGLISH else AppLanguage.KURDISH_SORANI)
                        },
                        label = { Text(if (isKu) "کوردی" else "EN") },
                        modifier = Modifier.testTag("top_language_toggle")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface)
            )
        },
        bottomBar = {
            Surface(
                color = ObsidianSurface,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (selectedChartBitmap != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, SheraGold, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = selectedChartBitmap.asImageBitmap(),
                                    contentDescription = "Attached chart preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKu) "وێنەی چارت هاوپێچکرا بۆ ناردن (زیان یان قازانج؟)" else "Chart image attached for AI profit/loss analysis",
                                style = MaterialTheme.typography.labelMedium,
                                color = SheraGoldBright,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onBitmapSelected(null) }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = CrimsonBear)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(ObsidianSurfaceVariant, RoundedCornerShape(12.dp))
                                .border(1.dp, SheraGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .testTag("attach_image_button")
                        ) {
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Upload Chart", tint = SheraGoldBright)
                        }

                        OutlinedTextField(
                            value = inputMessage,
                            onValueChange = onInputMessageChange,
                            placeholder = {
                                Text(
                                    if (isKu) "پرسیار بنووسە یاخود وێنەی چارت بنێرە..." else "Type message or send chart image...",
                                    color = TextSecondary
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            maxLines = 3
                        )

                        Button(
                            onClick = {
                                if (inputMessage.isNotBlank() || selectedChartBitmap != null) {
                                    val textToSend = inputMessage
                                    val bmpToSend = selectedChartBitmap
                                    onInputMessageChange("")
                                    onBitmapSelected(null)
                                    onSendMessage(textToSend, bmpToSend)
                                }
                            },
                            enabled = aiState !is AiAnalysisUiState.Loading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SheraGold,
                                contentColor = Color(0xFF0A0D14)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("send_chat_button")
                        ) {
                            if (aiState is AiAnalysisUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0A0D14), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        containerColor = ObsidianBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live 1-Minute Ticking Signal Ticker at the Top
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .border(1.dp, SheraGold, RoundedCornerShape(14.dp))
                    .testTag("live_minute_signal_banner"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldBull.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = EmeraldBull,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isKu) "⚡ ڕێنمایی نوێ (هەموو ١ دەقە):" else "⚡ Live 1-Min Signal Update:",
                            style = MaterialTheme.typography.labelSmall,
                            color = SheraGoldBright,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = liveMinuteSignal,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Chat Messages Feed (Unlimited Chat + Image Uploads)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    ChatMessageBubble(msg = msg, isKu = isKu)
                }

                if (aiState is AiAnalysisUiState.Loading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SheraGold)
                            Text(
                                text = if (isKu) "SHÊRA AI خەریکی خوێندنەوەی وێنە و پێشبینییە..." else "SHÊRA AI is analyzing chart and forecasting profit/loss...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(msg: ChatMessage, isKu: Boolean) {
    val isUser = msg.sender == MessageSender.USER
    val bubbleBg = if (isUser) Color(0xFF1F293D) else ObsidianSurface
    val borderColor = if (isUser) SheraGold.copy(alpha = 0.4f) else EmeraldBull.copy(alpha = 0.6f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleBg)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBackground)
                    ) {
                        Image(
                            bitmap = msg.bitmap.asImageBitmap(),
                            contentDescription = "User uploaded chart",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                if (msg.forecast != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = ObsidianCardBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val f = msg.forecast
                    val verdictColor = if (f.signalCategory == SignalCategory.BUY_SETUP) EmeraldBull else CrimsonBear
                    val verdictText = if (isKu) {
                        if (f.signalCategory == SignalCategory.BUY_SETUP) "🟢 پێشبینی: قازانج دەکات (کڕین باشە)" else "🔴 پێشبینی: زیان / مەترسی (فرۆشتن/چاوەڕوان)"
                    } else {
                        if (f.signalCategory == SignalCategory.BUY_SETUP) "🟢 Forecast: PROFIT / BUY NOW" else "🔴 Forecast: LOSS RISK / AVOID"
                    }

                    Text(
                        text = verdictText,
                        style = MaterialTheme.typography.titleSmall,
                        color = verdictColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isKu) f.marketStructureSummaryKu else f.marketStructureSummaryEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isKu) {
                            "چوونەژوورەوە: ${f.entryZone} • Stop: ${f.invalidationLevel} • T1: ${f.target1}"
                        } else {
                            "Entry: ${f.entryZone} • Stop: ${f.invalidationLevel} • T1: ${f.target1}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = SheraMonoFont,
                        color = SheraGoldBright
                    )
                }
            }
        }
    }
}
