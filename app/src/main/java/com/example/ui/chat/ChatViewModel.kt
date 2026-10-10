package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiModels
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.PreferencesManager
import com.example.data.model.AiPersona
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentSessionId: String? = null,
    val currentSession: ChatSessionEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val sessions: List<ChatSessionEntity> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val currentPersona: AiPersona = AiPersona.DEFAULT,
    val currentModel: String = GeminiModels.FLASH,
    val hasApiKey: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showPersonaDialog: Boolean = false,
    val showClearConfirmDialog: Boolean = false,
    val sessionToDelete: ChatSessionEntity? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val preferencesManager = PreferencesManager(application)
    val repository = ChatRepository(database.chatDao(), preferencesManager)
    val prefs = preferencesManager

    private val _currentSessionId = MutableStateFlow<String?>(null)
    private val _inputText = MutableStateFlow("")
    private val _isGenerating = MutableStateFlow(false)
    private val _showSettingsDialog = MutableStateFlow(false)
    private val _showPersonaDialog = MutableStateFlow(false)
    private val _showClearConfirmDialog = MutableStateFlow(false)
    private val _sessionToDelete = MutableStateFlow<ChatSessionEntity?>(null)

    val allSessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<ChatUiState> = combine(
        _currentSessionId,
        allSessions,
        currentMessages,
        _inputText,
        _isGenerating,
        _showSettingsDialog,
        _showPersonaDialog,
        _showClearConfirmDialog,
        _sessionToDelete
    ) { params ->
        val sessionId = params[0] as String?
        @Suppress("UNCHECKED_CAST")
        val sessions = params[1] as List<ChatSessionEntity>
        @Suppress("UNCHECKED_CAST")
        val messages = params[2] as List<ChatMessageEntity>
        val input = params[3] as String
        val generating = params[4] as Boolean
        val showSettings = params[5] as Boolean
        val showPersona = params[6] as Boolean
        val showClear = params[7] as Boolean
        val sessionDelete = params[8] as ChatSessionEntity?

        val activeSession = sessions.find { it.id == sessionId }
        val activePersona = if (activeSession != null) {
            AiPersona.findById(activeSession.personaId)
        } else {
            AiPersona.DEFAULT
        }
        val activeModel = activeSession?.modelId ?: preferencesManager.getSavedModel()
        val hasKey = repository.getEffectiveApiKey().isNotBlank()
        val isAnyStreaming = messages.any { it.isStreaming }

        ChatUiState(
            currentSessionId = sessionId,
            currentSession = activeSession,
            messages = messages,
            sessions = sessions,
            inputText = input,
            isGenerating = generating || isAnyStreaming,
            currentPersona = activePersona,
            currentModel = activeModel,
            hasApiKey = hasKey,
            showSettingsDialog = showSettings,
            showPersonaDialog = showPersona,
            showClearConfirmDialog = showClear,
            sessionToDelete = sessionDelete
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ChatUiState()
    )

    init {
        // Automatically select the most recent session or create one
        viewModelScope.launch {
            repository.allSessions.collect { sessionsList ->
                if (_currentSessionId.value == null && sessionsList.isNotEmpty()) {
                    _currentSessionId.value = sessionsList.first().id
                }
            }
        }
    }

    fun onInputChanged(newText: String) {
        _inputText.value = newText
    }

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun startNewChat(personaId: String = AiPersona.DEFAULT.id) {
        viewModelScope.launch {
            val newId = repository.createNewSession(personaId = personaId)
            _currentSessionId.value = newId
        }
    }

    fun sendMessage(customPrompt: String? = null) {
        val promptToSend = customPrompt ?: _inputText.value
        if (promptToSend.trim().isEmpty()) return

        if (customPrompt == null) {
            _inputText.value = ""
        }

        viewModelScope.launch {
            var sessionId = _currentSessionId.value
            if (sessionId == null) {
                val newId = repository.createNewSession()
                _currentSessionId.value = newId
                sessionId = newId
            }

            _isGenerating.value = true
            try {
                repository.sendMessage(sessionId, promptToSend)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun retryLastMessage() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                repository.retryLastAssistantMessage(sessionId)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun updateSessionPersona(persona: AiPersona) {
        val sessionId = _currentSessionId.value
        if (sessionId != null) {
            viewModelScope.launch {
                repository.updateSessionPersona(sessionId, persona.id)
            }
        }
        _showPersonaDialog.value = false
    }

    fun updateSessionModel(modelId: String) {
        val sessionId = _currentSessionId.value
        if (sessionId != null) {
            viewModelScope.launch {
                repository.updateSessionModel(sessionId, modelId)
            }
        }
    }

    fun confirmClearCurrentSession() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            repository.clearSessionMessages(sessionId)
            _showClearConfirmDialog.value = false
        }
    }

    fun requestDeleteSession(session: ChatSessionEntity) {
        _sessionToDelete.value = session
    }

    fun confirmDeleteSession() {
        val session = _sessionToDelete.value ?: return
        viewModelScope.launch {
            val isCurrent = _currentSessionId.value == session.id
            repository.deleteSession(session.id)
            _sessionToDelete.value = null
            if (isCurrent) {
                val remaining = allSessions.value.filter { it.id != session.id }
                if (remaining.isNotEmpty()) {
                    _currentSessionId.value = remaining.first().id
                } else {
                    val newId = repository.createNewSession()
                    _currentSessionId.value = newId
                }
            }
        }
    }

    fun dismissDeleteSession() {
        _sessionToDelete.value = null
    }

    fun showSettings(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun showPersonaSelector(show: Boolean) {
        _showPersonaDialog.value = show
    }

    fun showClearConfirm(show: Boolean) {
        _showClearConfirmDialog.value = show
    }
}
