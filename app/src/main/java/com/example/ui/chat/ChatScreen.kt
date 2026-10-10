package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.ChatMessageEntity
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.MarkdownRenderer
import com.example.ui.components.PersonaBottomSheet
import com.example.ui.components.TypingIndicator
import com.example.ui.settings.SettingsDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom on message list change or streaming change
    LaunchedEffect(uiState.messages.size, uiState.isGenerating) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    val isScrolledUp by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < total - 2
            }
        }
    }

    // Handle back button for drawer
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawerContent(
                sessions = uiState.sessions,
                activeSessionId = uiState.currentSessionId,
                onSelectSession = { sessionId ->
                    viewModel.selectSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { session ->
                    viewModel.requestDeleteSession(session)
                },
                onOpenSettings = {
                    viewModel.showSettings(true)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { viewModel.showPersonaSelector(true) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("top_bar_persona_chip")
                        ) {
                            Text(
                                text = uiState.currentPersona.iconEmoji,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = uiState.currentPersona.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "▾",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = uiState.currentModel.replace("gemini-", "").replace("-preview", ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("menu_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Chat History",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (uiState.messages.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.showClearConfirm(true) },
                                modifier = Modifier.testTag("clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear Chat",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.showSettings(true) },
                            modifier = Modifier.testTag("settings_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                ChatInputBar(
                    inputText = uiState.inputText,
                    isGenerating = uiState.isGenerating,
                    currentPersona = uiState.currentPersona,
                    onInputChanged = viewModel::onInputChanged,
                    onSendMessage = {
                        viewModel.sendMessage()
                        keyboardController?.hide()
                    },
                    onOpenPersonaSheet = { viewModel.showPersonaSelector(true) }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.messages.isEmpty()) {
                    EmptyChatWelcome(
                        persona = uiState.currentPersona,
                        hasApiKey = uiState.hasApiKey,
                        onPromptSelected = { prompt ->
                            viewModel.sendMessage(prompt)
                        },
                        onOpenSettings = {
                            viewModel.showSettings(true)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageItem(
                                message = message,
                                persona = uiState.currentPersona,
                                onRetry = { viewModel.retryLastMessage() }
                            )
                        }
                    }
                }

                // Scroll to bottom floating button
                AnimatedVisibility(
                    visible = isScrolledUp,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (uiState.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(uiState.messages.size - 1)
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("scroll_to_bottom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Scroll to latest",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Persona Selection Bottom Sheet
    if (uiState.showPersonaDialog) {
        PersonaBottomSheet(
            currentPersona = uiState.currentPersona,
            onPersonaSelected = viewModel::updateSessionPersona,
            onDismiss = { viewModel.showPersonaSelector(false) }
        )
    }

    // Settings Dialog
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            preferencesManager = viewModel.prefs,
            hasEffectiveKey = uiState.hasApiKey,
            onDismiss = { viewModel.showSettings(false) }
        )
    }

    // Confirm Clear Dialog
    if (uiState.showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearConfirm(false) },
            title = { Text("Clear this conversation?") },
            text = { Text("All messages in this chat will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmClearCurrentSession,
                    modifier = Modifier.testTag("confirm_clear_button")
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearConfirm(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Delete Session Dialog
    if (uiState.sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteSession,
            title = { Text("Delete Conversation?") },
            text = { Text("Delete \"${uiState.sessionToDelete?.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteSession,
                    modifier = Modifier.testTag("confirm_delete_session_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteSession) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyChatWelcome(
    persona: com.example.data.model.AiPersona,
    hasApiKey: Boolean,
    onPromptSelected: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Avatar
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_ai_avatar),
                contentDescription = "Aura Companion Avatar",
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to Aura AI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${persona.iconEmoji} ${persona.name} is ready to help you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!hasApiKey) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenSettings() }
                    .testTag("api_key_banner"),
                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set up your Gemini API Key",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                        Text(
                            text = "Tap here to configure or test your API key in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SUGGESTED STARTERS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            persona.starterPrompts.forEach { prompt ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPromptSelected(prompt) }
                        .testTag("starter_prompt_chip"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessageEntity,
    persona: com.example.data.model.AiPersona,
    onRetry: () -> Unit
) {
    val isUser = message.role == "user"
    val context = LocalContext.current

    if (isUser) {
        // User message bubble
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_message_item"),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    } else {
        // Model response message card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("model_message_item"),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = persona.iconEmoji, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (message.isStreaming && message.content.isBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TypingIndicator()
                            Text(
                                text = "Aura is thinking…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        MarkdownRenderer(
                            text = message.content,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )

                        if (message.isError) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Failed to complete",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    TextButton(
                                        onClick = onRetry,
                                        modifier = Modifier.testTag("retry_message_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        // Action bar: Copy, Share, Regenerate
                        if (!message.isStreaming && message.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Aura Response", message.content))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("copy_response_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy message",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, message.content)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share response"))
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("share_response_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share message",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    isGenerating: Boolean,
    currentPersona: com.example.data.model.AiPersona,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onOpenPersonaSheet: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Persona quick toggle pill
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpenPersonaSheet() }
                        .testTag("input_persona_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = currentPersona.iconEmoji, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Multiline Input Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    placeholder = {
                        Text(
                            text = "Ask Aura anything…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { onInputChanged("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear input",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button
                val canSend = inputText.trim().isNotEmpty() && !isGenerating
                Surface(
                    shape = CircleShape,
                    color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canSend) { onSendMessage() }
                        .testTag("send_message_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isGenerating) {
                            TypingIndicator(dotColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
