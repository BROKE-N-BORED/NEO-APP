package com.brokenbored.neo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenbored.neo.AppContainer
import com.brokenbored.neo.data.Conversation
import com.brokenbored.neo.providers.ProviderCatalog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    container: AppContainer,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val conversations by container.dao.conversations()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<Conversation?>(null) }
    val dateFormat = remember {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NEO") },
                actions = {
                    IconButton(onClick = onOpenHelp) {
                        Icon(Icons.Filled.Info, contentDescription = "Help")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val settings = container.settings.current
                        val id = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        container.dao.insertConversation(
                            Conversation(
                                id = id,
                                title = "New chat",
                                providerId = settings.defaultProvider,
                                model = settings.defaultModel,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        onOpenChat(id)
                    }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New chat")
            }
        }
    ) { innerPadding ->
        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No chats yet.",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Start one with the + button. NEO talks to your local " +
                        "Ollama by default — no account, no cloud, nothing " +
                        "leaves the phone unless you pick a cloud provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    Card(
                        onClick = { onOpenChat(conversation.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    conversation.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    ProviderCatalog.meta(conversation.providerId).label +
                                        " · " + conversation.model,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    dateFormat.format(Date(conversation.updatedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { toDelete = conversation }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete chat"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this chat?") },
            text = {
                Text(
                    "\"${conversation.title}\" and all its messages will be " +
                        "deleted from this phone. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            container.dao.deleteConversation(conversation.id)
                        }
                        toDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Keep it") }
            }
        )
    }
}
