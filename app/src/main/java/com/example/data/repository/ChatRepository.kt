package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.PreferencesManager
import com.example.data.model.AiPersona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val preferencesManager: PreferencesManager
) {
    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getSession(sessionId: String): ChatSessionEntity? = withContext(Dispatchers.IO) {
        chatDao.getSessionById(sessionId)
    }

    suspend fun createNewSession(
        personaId: String = AiPersona.DEFAULT.id,
        modelId: String = preferencesManager.getSavedModel(),
        initialTitle: String = "New Chat"
    ): String = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = ChatSessionEntity(
            id = sessionId,
            title = initialTitle,
            personaId = personaId,
            modelId = modelId,
            createdAt = now,
            updatedAt = now
        )
        chatDao.insertSession(session)
        sessionId
    }

    suspend fun updateSessionPersona(sessionId: String, personaId: String) = withContext(Dispatchers.IO) {
        chatDao.updateSessionPersona(sessionId, personaId, System.currentTimeMillis())
    }

    suspend fun updateSessionModel(sessionId: String, modelId: String) = withContext(Dispatchers.IO) {
        chatDao.updateSessionModel(sessionId, modelId, System.currentTimeMillis())
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        chatDao.updateSessionTitle(sessionId, title, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.clearMessagesForSession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun clearSessionMessages(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.clearMessagesForSession(sessionId)
    }

    fun getEffectiveApiKey(): String {
        val userCustomKey = preferencesManager.getSavedApiKey()
        if (userCustomKey.isNotBlank()) {
            return userCustomKey
        }
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    suspend fun sendMessage(
        sessionId: String,
        promptText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedPrompt = promptText.trim()
        if (trimmedPrompt.isEmpty()) return@withContext Result.failure(Exception("Empty prompt"))

        val now = System.currentTimeMillis()
        val session = chatDao.getSessionById(sessionId)
            ?: return@withContext Result.failure(Exception("Session not found"))

        // Update session title if default
        if (session.title == "New Chat") {
            val titleCandidate = if (trimmedPrompt.length > 32) {
                trimmedPrompt.take(30).trim() + "…"
            } else {
                trimmedPrompt
            }
            chatDao.updateSessionTitle(sessionId, titleCandidate, now)
        } else {
            chatDao.updateSessionTitle(sessionId, session.title, now)
        }

        // 1. Insert User Message
        val userMsgId = UUID.randomUUID().toString()
        val userMessage = ChatMessageEntity(
            id = userMsgId,
            sessionId = sessionId,
            role = "user",
            content = trimmedPrompt,
            timestamp = now,
            isError = false,
            isStreaming = false
        )
        chatDao.insertMessage(userMessage)

        // 2. Insert Pending Model Message
        val modelMsgId = UUID.randomUUID().toString()
        val pendingModelMessage = ChatMessageEntity(
            id = modelMsgId,
            sessionId = sessionId,
            role = "model",
            content = "",
            timestamp = now + 1,
            isError = false,
            isStreaming = true
        )
        chatDao.insertMessage(pendingModelMessage)

        // 3. Check API key
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            val missingKeyMessage = "⚠️ Gemini API Key not configured.\n\nTo chat with Aura, please tap the ⚙️ Settings icon at the top right and enter your Gemini API key, or add your key to the AI Studio Secrets panel."
            chatDao.updateMessage(
                pendingModelMessage.copy(
                    content = missingKeyMessage,
                    isStreaming = false,
                    isError = true
                )
            )
            return@withContext Result.failure(Exception("API key missing"))
        }

        // 4. Build Request turns
        val persona = AiPersona.findById(session.personaId)
        val customInstructions = preferencesManager.getSavedCustomInstructions()
        val combinedSystemPrompt = buildString {
            append(persona.systemPrompt)
            if (customInstructions.isNotBlank()) {
                append("\n\nUser's Personal Custom Preferences: ")
                append(customInstructions)
            }
        }

        val allSessionMessages = chatDao.getMessagesListForSession(sessionId)
            .filter { it.id != modelMsgId && it.content.isNotBlank() && !it.isError }

        // Take up to last 16 messages for conversational context
        val contextHistory = allSessionMessages.takeLast(16)
        val contentsList = contextHistory.map { msg ->
            Content(
                role = if (msg.role == "user") "user" else "model",
                parts = listOf(Part(text = msg.content))
            )
        }

        val temperature = preferencesManager.getSavedTemperature()
        val request = GenerateContentRequest(
            contents = contentsList,
            systemInstruction = Content(parts = listOf(Part(text = combinedSystemPrompt))),
            generationConfig = GenerationConfig(
                temperature = temperature,
                maxOutputTokens = 4096
            )
        )

        val modelToUse = if (session.modelId.isNotBlank()) session.modelId else preferencesManager.getSavedModel()

        try {
            val response = RetrofitClient.service.generateContent(
                model = modelToUse,
                apiKey = apiKey,
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                val candidateText = body?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                val blockReason = body?.promptFeedback?.blockReason

                val finalContent = when {
                    !candidateText.isNullOrBlank() -> candidateText
                    blockReason != null -> "Response blocked due to policy: $blockReason"
                    else -> "I received an empty response. Please try rephrasing your message."
                }

                chatDao.updateMessage(
                    pendingModelMessage.copy(
                        content = finalContent,
                        isStreaming = false,
                        isError = false
                    )
                )
                Result.success(Unit)
            } else {
                val errorCode = response.code()
                val errorBodyStr = response.errorBody()?.string() ?: ""
                val friendlyError = when (errorCode) {
                    400 -> "Request error (400): Please check the message content or format.\n$errorBodyStr"
                    401, 403 -> "Authentication error ($errorCode): Your Gemini API Key appears invalid or unauthorized. Please verify your key in Settings."
                    429 -> "Rate limit reached (429): Aura is processing many queries right now. Please wait a few seconds and try again."
                    500, 503 -> "Server error ($errorCode): Google Gemini service temporarily unavailable. Please retry shortly."
                    else -> "API Error ($errorCode): $errorBodyStr"
                }

                chatDao.updateMessage(
                    pendingModelMessage.copy(
                        content = friendlyError,
                        isStreaming = false,
                        isError = true
                    )
                )
                Result.failure(Exception(friendlyError))
            }
        } catch (e: Exception) {
            val networkError = "Network error: Unable to connect to Gemini API. Please check your internet connection.\nDetails: ${e.localizedMessage ?: e.message}"
            chatDao.updateMessage(
                pendingModelMessage.copy(
                    content = networkError,
                    isStreaming = false,
                    isError = true
                )
            )
            Result.failure(e)
        }
    }

    suspend fun retryLastAssistantMessage(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val messages = chatDao.getMessagesListForSession(sessionId)
        val lastUserMessage = messages.lastOrNull { it.role == "user" }
            ?: return@withContext Result.failure(Exception("No user message to retry"))

        // Delete trailing error or incomplete model message
        val lastMessage = messages.lastOrNull()
        if (lastMessage?.role == "model") {
            chatDao.deleteMessage(lastMessage.id)
        }

        sendMessage(sessionId, lastUserMessage.content)
    }
}
