package com.brokenbored.neo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.brokenbored.neo.AppContainer
import kotlinx.coroutines.launch

/**
 * Termux + Ollama setup, with a live status check and a real connection
 * test. Every step here is something the user does once, on their phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    var check by remember { mutableStateOf(container.termux.check()) }
    var probeResult by remember { mutableStateOf<String?>(null) }
    var probing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        copied = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termux and Ollama setup") },
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
            Text(
                "NEO runs terminal commands in Termux — the real terminal " +
                    "on your phone, not a fake shell inside the app. That " +
                    "takes a few one-time setup steps. This screen shows " +
                    "what is done and what is left.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Status on this phone", style = MaterialTheme.typography.titleSmall)
                    StatusLine(
                        ok = check.installed,
                        text = if (check.installed) {
                            "Termux is installed" +
                                (check.versionName?.let { " (version $it)" } ?: "")
                        } else {
                            "Termux is not installed"
                        }
                    )
                    StatusLine(
                        ok = check.runCommandApiPresent,
                        text = if (check.runCommandApiPresent) {
                            "Termux supports commands from NEO"
                        } else {
                            "This Termux build cannot take commands " +
                                "(Play Store build — use F-Droid instead)"
                        }
                    )
                    StatusLine(
                        ok = check.permissionGranted,
                        text = if (check.permissionGranted) {
                            "NEO has the Termux permission"
                        } else {
                            "NEO does not have the Termux permission yet"
                        }
                    )
                    OutlinedButton(onClick = { check = container.termux.check() }) {
                        Text("Check again")
                    }
                }
            }

            Text("1. Install Termux from F-Droid", style = MaterialTheme.typography.titleMedium)
            Text(
                "The Play Store version of Termux cannot take commands " +
                    "from other apps, so NEO needs the F-Droid (or GitHub) " +
                    "build. If you already have Termux from the Play Store, " +
                    "uninstall it first — the two cannot mix.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = {
                    uriHandler.openUri("https://f-droid.org/en/packages/com.termux/")
                }
            ) { Text("Open Termux on F-Droid") }

            Text("2. Give NEO the Termux permission", style = MaterialTheme.typography.titleMedium)
            Text(
                "In your phone Settings → Apps → NEO → Permissions → " +
                    "Additional permissions, turn on \"Run commands in " +
                    "Termux\". Without it, Android blocks every command " +
                    "before Termux ever sees it.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                    context.startActivity(intent)
                }
            ) { Text("Open NEO's app settings") }

            Text("3. Let Termux accept commands", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open Termux and run this one line. NEO cannot set it " +
                    "itself — Termux deliberately keeps this file " +
                    "untouchable by other apps.",
                style = MaterialTheme.typography.bodyMedium
            )
            CopyableCommand(
                "echo 'allow-external-apps=true' >> ~/.termux/termux.properties",
                onCopy = { copy(it) }
            )
            Text(
                "Then run termux-reload-settings (or restart Termux) so it " +
                    "takes effect.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text("4. Test the connection", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    probing = true
                    probeResult = null
                    scope.launch {
                        probeResult = container.termux.probe().fold(
                            onSuccess = { it },
                            onFailure = { it.message ?: "The test failed." }
                        )
                        probing = false
                    }
                },
                enabled = !probing
            ) {
                Text(if (probing) "Testing…" else "Test the Termux connection")
            }
            probeResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Text("5. Install Ollama inside Termux", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ollama runs your local model on the phone. In Termux:",
                style = MaterialTheme.typography.bodyMedium
            )
            CopyableCommand("pkg update && pkg install ollama", onCopy = { copy(it) })
            CopyableCommand("ollama serve", onCopy = { copy(it) })
            CopyableCommand(
                "ollama pull qwen2.5:1.5b",
                onCopy = { copy(it) }
            )
            Text(
                "Keep the model under half the phone's RAM. Bigger phones " +
                    "can pull bigger models — the model list in chat shows " +
                    "whatever you have installed.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (copied) {
                Text(
                    "Copied to the clipboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Good to know", style = MaterialTheme.typography.titleMedium)
            Text(
                "• Commands run while NEO is on screen. Android 12+ will " +
                    "not let a background app start Termux.\n" +
                    "• On Android 12+, the system may kill long or heavy " +
                    "Termux work (the \"phantom process\" killer). Short " +
                    "commands are fine; long model loads can be killed " +
                    "mid-run — NEO reports that plainly and you can run " +
                    "the command again.\n" +
                    "• Output is capped at 100 KB per command by Termux. " +
                    "NEO says so when output was cut short.\n" +
                    "• Granting NEO the Termux permission means NEO can " +
                    "run commands as the Termux user. That is why every " +
                    "command still asks you first, unless you chose " +
                    "\"Always allow\" for that exact command.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = if (ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun CopyableCommand(command: String, onCopy: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                command,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onCopy(command) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
            }
        }
    }
}
