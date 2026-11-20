package com.brokenbored.neo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.brokenbored.neo.terminal.TermuxRunner
import com.brokenbored.neo.ui.ChatScreen
import com.brokenbored.neo.ui.ConversationsScreen
import com.brokenbored.neo.ui.HelpScreen
import com.brokenbored.neo.ui.SettingsScreen

sealed interface Screen {
    data object Conversations : Screen
    data class Chat(val id: String) : Screen
    data object Settings : Screen
    data object Help : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer()
        TermuxRunner.shared = container.termux
        setContent {
            NeoTheme {
                NeoApp(container)
            }
        }
    }
}

/** Bench-green accent, light and dark. */
@Composable
fun NeoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF7ED491),
            secondary = Color(0xFF7ED491)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1B5E20),
            secondary = Color(0xFF2E7D32)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun NeoApp(container: AppContainer) {
    var screen by remember { mutableStateOf<Screen>(Screen.Conversations) }
    BackHandler(enabled = screen != Screen.Conversations) {
        screen = Screen.Conversations
    }
    when (val current = screen) {
        is Screen.Conversations -> ConversationsScreen(
            container = container,
            onOpenChat = { screen = Screen.Chat(it) },
            onOpenSettings = { screen = Screen.Settings },
            onOpenHelp = { screen = Screen.Help }
        )
        is Screen.Chat -> ChatScreen(
            container = container,
            conversationId = current.id,
            onBack = { screen = Screen.Conversations },
            onOpenHelp = { screen = Screen.Help }
        )
        is Screen.Settings -> SettingsScreen(
            container = container,
            onBack = { screen = Screen.Conversations }
        )
        is Screen.Help -> HelpScreen(
            container = container,
            onBack = { screen = Screen.Conversations }
        )
    }
}
