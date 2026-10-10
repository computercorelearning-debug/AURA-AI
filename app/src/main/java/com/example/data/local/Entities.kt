package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long,
    val isError: Boolean = false,
    val isStreaming: Boolean = false
)
