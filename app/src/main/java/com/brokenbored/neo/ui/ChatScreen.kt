package com.brokenbored.neo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brokenbored.neo.AppContainer
import com.brokenbored.neo.data.ChatMessage
import com.brokenbored.neo.providers.ProviderCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    conversationId: String,
    onBack: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(container, conversationId) as T
            }
        }
    )
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCommands.collectAsStateWithLifecycle()
    val running by viewModel.runningCommand.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var modelText by remember(conversation?.model) {
        mutableStateOf(conversation?.model ?: "")
    }

    val listState = rememberLazyListState()
    val isStreaming = streamingText != null
    val totalItems = messages.size + if (isStreaming) 1 else 0
    LaunchedEffect(totalItems, streamingText?.length) {
        if (totalItems > 0) listState.scrollToItem(totalItems - 1)
    }

    val providerId = conversation?.providerId ?: "ollama"
    val window = ProviderCatalog.meta(providerId).contextWindow
    val usedTokens = remember(messages, streamingText) {
        messages.sumOf { ProviderCatalog.estimateTokens(it.content) } +
            ProviderCatalog.estimateTokens(streamingText.orEmpty()) +
            ProviderCatalog.estimateTokens(ChatViewModel.SYSTEM_PROMPT)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversation?.title ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Provider + context meter.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box {
                    OutlinedButton(onClick = { providerMenuOpen = true }) {
                        Text(ProviderCatalog.meta(providerId).label)
                    }
                    DropdownMenu(
                        expanded = providerMenuOpen,
                        onDismissRequest = { providerMenuOpen = false }
                    ) {
                        ProviderCatalog.all.forEach { meta ->
                            DropdownMenuItem(
                                text = { Text(meta.label) },
                                onClick = {
                                    providerMenuOpen = false
                                    viewModel.setProvider(meta.id, meta.defaultModel)
                                }
                            )
                        }
                    }
                }
                Text(
                    "Context (estimated): $usedTokens / $window",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { (usedTokens.toFloat() / window).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )

            // Model row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { viewModel.setProvider(providerId, modelText.trim()) }
                    )
                )
                IconButton(
                    onClick = { viewModel.setProvider(providerId, modelText.trim()) }
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Use this model")
                }
                if (providerId == "ollama" || providerId == "llama") {
                    IconButton(
                        onClick = { viewModel.refreshModels() },
                        enabled = !modelsLoading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "List installed models")
                    }
                }
            }
            if (availableModels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableModels.forEach { model ->
                        SuggestionChip(
                            onClick = { viewModel.setProvider(providerId, model) },
                            label = { Text(model) }
                        )
                    }
                }
            } else if (modelsLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Messages.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (isStreaming) {
                    item(key = "streaming") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                MarkdownText(streamingText.orEmpty())
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Running command.
            running?.let { command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Running in Termux…",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    command,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                )
            }

            // Approval cards.
            pending.forEach { command ->
                ApprovalCard(
                    pending = command,
                    onAllowOnce = { viewModel.approveCommand(command, always = false) },
                    onAlways = { viewModel.approveCommand(command, always = true) },
                    onDeny = { viewModel.denyCommand(command) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Error.
            error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            // Input.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Talk to NEO…") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            viewModel.send(input)
                            input = ""
                        }
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isStreaming) {
                    IconButton(onClick = { viewModel.stop() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop")
                    }
                } else {
                    IconButton(
                        onClick = {
                            viewModel.send(input)
                            input = ""
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
            // Small help link, easy to miss otherwise.
            TextButton(
                onClick = onOpenHelp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Termux and Ollama setup help")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    when (message.role) {
        "user" -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        "terminal" -> {
            val clipboard = LocalClipboardManager.current
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Terminal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(message.content))
                            }
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy output"
                            )
                        }
                    }
                    Text(
                        message.content,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
        else -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            MarkdownText(
                message.content,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    pending: ChatViewModel.PendingCommand,
    onAllowOnce: () -> Unit,
    onAlways: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "NEO wants to run this in Termux:",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            pending.dangerousReason?.let { reason ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                pending.command,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllowOnce) { Text("Allow once") }
                OutlinedButton(onClick = onAlways) { Text("Always allow") }
                TextButton(onClick = onDeny) { Text("Deny") }
            }
        }
    }
}
