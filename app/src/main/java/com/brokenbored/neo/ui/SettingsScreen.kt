package com.brokenbored.neo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenbored.neo.AppContainer
import com.brokenbored.neo.data.KeyStore
import com.brokenbored.neo.providers.ProviderCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var providerMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle("Local models")
            OutlinedTextField(
                value = settings.ollamaUrl,
                onValueChange = {
                    container.settings.update { s -> s.copy(ollamaUrl = it) }
                },
                label = { Text("Ollama address") },
                supportingText = {
                    Text(
                        "Default is the Ollama on this phone. A LAN or " +
                            "Tailscale address reaches the PC's Ollama instead."
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.llamaUrl,
                onValueChange = {
                    container.settings.update { s -> s.copy(llamaUrl = it) }
                },
                label = { Text("llama-server address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Defaults for new chats")
            Box {
                OutlinedButton(onClick = { providerMenuOpen = true }) {
                    Text(ProviderCatalog.meta(settings.defaultProvider).label)
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
                                container.settings.update {
                                    it.copy(
                                        defaultProvider = meta.id,
                                        defaultModel = meta.defaultModel
                                    )
                                }
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = settings.defaultModel,
                onValueChange = {
                    container.settings.update { s -> s.copy(defaultModel = it) }
                },
                label = { Text("Default model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Cloud keys")
            Text(
                "Keys are encrypted on this phone and never leave it except " +
                    "to the provider they belong to. Leave a key empty and " +
                    "that provider simply stays unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            KeyField(label = "Groq key", providerId = "groq", keys = container.keys)
            KeyField(label = "Gemini key", providerId = "gemini", keys = container.keys)
            KeyField(label = "EXAONE key", providerId = "exaone", keys = container.keys)

            SectionTitle("Terminal agent")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Let NEO use the terminal")
                    Text(
                        "Off means NEO only chats — it never proposes " +
                            "commands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.agentEnabled,
                    onCheckedChange = {
                        container.settings.update { s -> s.copy(agentEnabled = it) }
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Run safe commands without asking")
                    Text(
                        "When on, ordinary commands run straight away. " +
                            "Dangerous ones still always ask, and refused " +
                            "ones never run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.defaultAllowSafe,
                    onCheckedChange = {
                        container.settings.update { s -> s.copy(defaultAllowSafe = it) }
                    }
                )
            }

            SectionTitle("Always-allowed commands")
            if (settings.alwaysAllow.isEmpty()) {
                Text(
                    "None yet. Commands you choose \"Always allow\" for " +
                        "appear here, and you can take any of them back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                settings.alwaysAllow.sorted().forEach { command ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            command,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                container.settings.removeAlwaysAllow(command)
                            }
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Stop always allowing"
                            )
                        }
                    }
                }
            }

            SectionTitle("About")
            Text(
                "NEO 0.1 — Broke n' Bored Studio.\nChats and keys stay on " +
                    "this phone. Local-first: Ollama is the default provider " +
                    "because the whole point of NEO is your own model, on " +
                    "your own phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun KeyField(label: String, providerId: String, keys: KeyStore) {
    var text by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(keys.hasKey(providerId)) }
    var visible by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; notice = null },
            label = { Text(label) },
            placeholder = {
                Text(
                    if (saved) "Key saved — enter a new one to replace it"
                    else "No key saved"
                )
            },
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Hide key" else "Show key"
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        keys.setKey(providerId, text)
                        text = ""
                        saved = true
                        notice = "Saved."
                    }
                },
                enabled = text.isNotBlank()
            ) { Text("Save") }
            if (saved) {
                TextButton(
                    onClick = {
                        keys.clearKey(providerId)
                        saved = false
                        notice = "Removed."
                    }
                ) { Text("Remove") }
            }
            notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
