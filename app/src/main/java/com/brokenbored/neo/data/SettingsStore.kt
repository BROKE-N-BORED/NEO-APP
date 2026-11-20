package com.brokenbored.neo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NEO settings, held in plain SharedPreferences (no secrets here — API keys
 * live in [KeyStore]). One [Settings] snapshot, reloaded on every write so
 * the UI always sees the current values.
 */
data class Settings(
    /** Local Ollama. Default is the Termux Ollama on this phone; a LAN or
     * Tailscale URL reaches the PC's Ollama instead. */
    val ollamaUrl: String = "http://127.0.0.1:11434",
    /** llama.cpp server fallback. */
    val llamaUrl: String = "http://127.0.0.1:8080",
    /** Default provider for new chats. Ollama-local is the default because
     * local-first is the point of the app. */
    val defaultProvider: String = "ollama",
    val defaultModel: String = "qwen2.5:1.5b",
    /** Master switch for the terminal agent. Off = NEO only chats. */
    val agentEnabled: Boolean = true,
    /**
     * When false (the default), every command asks first. When true, safe
     * commands run without asking — dangerous ones still always ask, and
     * refused ones never run.
     */
    val defaultAllowSafe: Boolean = false,
    /** Exact commands the user chose "Always allow" for. Exact match only. */
    val alwaysAllow: Set<String> = emptySet()
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("neo_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    val current: Settings get() = _state.value

    private fun load(): Settings = Settings(
        ollamaUrl = prefs.getString(KEY_OLLAMA_URL, Settings().ollamaUrl)!!,
        llamaUrl = prefs.getString(KEY_LLAMA_URL, Settings().llamaUrl)!!,
        defaultProvider = prefs.getString(KEY_PROVIDER, Settings().defaultProvider)!!,
        defaultModel = prefs.getString(KEY_MODEL, Settings().defaultModel)!!,
        agentEnabled = prefs.getBoolean(KEY_AGENT, true),
        defaultAllowSafe = prefs.getBoolean(KEY_ALLOW_SAFE, false),
        alwaysAllow = prefs.getStringSet(KEY_ALWAYS_ALLOW, emptySet())!!.toSet()
    )

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        prefs.edit()
            .putString(KEY_OLLAMA_URL, next.ollamaUrl)
            .putString(KEY_LLAMA_URL, next.llamaUrl)
            .putString(KEY_PROVIDER, next.defaultProvider)
            .putString(KEY_MODEL, next.defaultModel)
            .putBoolean(KEY_AGENT, next.agentEnabled)
            .putBoolean(KEY_ALLOW_SAFE, next.defaultAllowSafe)
            .putStringSet(KEY_ALWAYS_ALLOW, next.alwaysAllow)
            .apply()
        _state.value = next
    }

    fun addAlwaysAllow(command: String) {
        update { it.copy(alwaysAllow = it.alwaysAllow + command) }
    }

    fun removeAlwaysAllow(command: String) {
        update { it.copy(alwaysAllow = it.alwaysAllow - command) }
    }

    private companion object {
        const val KEY_OLLAMA_URL = "ollama_url"
        const val KEY_LLAMA_URL = "llama_url"
        const val KEY_PROVIDER = "default_provider"
        const val KEY_MODEL = "default_model"
        const val KEY_AGENT = "agent_enabled"
        const val KEY_ALLOW_SAFE = "default_allow_safe"
        const val KEY_ALWAYS_ALLOW = "always_allow"
    }
}
