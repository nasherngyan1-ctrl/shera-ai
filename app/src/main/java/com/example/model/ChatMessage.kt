package com.example.model

import android.graphics.Bitmap
import java.util.UUID

enum class MessageSender {
    USER,
    AI
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: MessageSender,
    val bitmap: Bitmap? = null,
    val forecast: AiVisionForecast? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
