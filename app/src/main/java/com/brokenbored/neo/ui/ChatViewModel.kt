package com.brokenbored.neo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenbored.neo.AppContainer
import com.brokenbored.neo.data.ChatMessage
import com.brokenbored.neo.data.Conversation
import com.brokenbored.neo.providers.ChatMsg
import com.brokenbored.neo.providers.ProviderError
import com.brokenbored.neo.terminal.Approval
import com.brokenbored.neo.terminal.TermuxError
import com.brokenbored.neo.terminal.TermuxResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One chat conversation: streaming replies, the terminal agent loop, and
 * the approval flow. The agent loop is bounded: at most
 * [MAX_FOLLOW_UP_ROUNDS] follow-up model calls per user message, and every
 * command the model proposes goes through [Approval] first.
 */
class ChatViewModel(
    private val container: AppContainer,
    private val conversationId: String
) : ViewModel() {

    data class PendingCommand(val command: String, val dangerousReason: String?)

    private val dao = container.dao

    val messages: StateFlow<List<ChatMessage>> = dao.messages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    /** Non-null while a reply is streaming (holds the text so far). */
    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pendingCommands = MutableStateFlow<List<PendingCommand>>(emptyList())
    val pendingCommands: StateFlow<List<PendingCommand>> = _pendingCommands.asStateFlow()

    private val _runningCommand = MutableStateFlow<String?>(null)
    val runningCommand: StateFlow<String?> = _runningCommand.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    val settings = container.settings.state

    private var streamJob: Job? = null
    private var followUpRounds = 0

    init {
        viewModelScope.launch {
            _conversation.value = dao.conversation(conversationId)
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _streamingText.value != null) return
        _error.value = null
        followUpRounds = 0
        streamJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.insertMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = "user",
                    content = trimmed,
                    createdAt = now
                )
            )
            val conv = _conversation.value
            val title = if (conv == null || conv.title == "New chat") {
                trimmed.take(40)
            } else {
                conv.title
            }
            dao.touch(conversationId, now, title)
            _conversation.value = dao.conversation(conversationId)
            streamReply()
        }
    }

    /** Stops the reply. A command already running in Termux keeps running
     * there — there is no API to kill it — but its result is dropped. */
    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    private suspend fun streamReply() {
        val conv = _conversation.value ?: return
        val client = container.providers.clientFor(conv.providerId)
        val history = dao.messagesOnce(conversationId).map { m ->
            when (m.role) {
                "terminal" -> ChatMsg("user", "The terminal returned this:\n${m.content}")
                else -> ChatMsg(m.role, m.content)
            }
        }
        val request = listOf(ChatMsg("system", SYSTEM_PROMPT)) + history
        val accumulated = StringBuilder()
        _streamingText.value = ""
        try {
            client.streamChat(conv.model, request) { chunk ->
                accumulated.append(chunk)
                _streamingText.value = accumulated.toString()
            }
        } catch (e: ProviderError) {
            _streamingText.value = null
            if (accumulated.isNotBlank()) {
                insertAssistant(accumulated.toString())
            }
            _error.value = e.message
            return
        } catch (e: CancellationException) {
            _streamingText.value = null
            if (accumulated.isNotBlank()) {
                insertAssistant(accumulated.toString())
            }
            throw e
        }
        _streamingText.value = null
        val reply = accumulated.toString()
        if (reply.isBlank()) {
            _error.value = "The model sent back an empty reply. Try again."
            return
        }
        insertAssistant(reply)
        if (container.settings.current.agentEnabled) {
            processCommands(reply)
        }
    }

    private suspend fun processCommands(reply: String) {
        val settings = container.settings.current
        for (command in Approval.extractCommands(reply)) {
            when (Approval.decide(command, settings.alwaysAllow, settings.defaultAllowSafe)) {
                Approval.Decision.REFUSE -> insertTerminal(
                    "Command refused:\n$command\n\n${Approval.refusedReason(command)}"
                )
                Approval.Decision.ASK -> _pendingCommands.value += PendingCommand(
                    command = command,
                    dangerousReason = Approval.dangerousReason(command)
                )
                Approval.Decision.ALLOW -> runCommand(command)
            }
        }
    }

    fun approveCommand(pending: PendingCommand, always: Boolean) {
        _pendingCommands.value -= pending
        if (always) container.settings.addAlwaysAllow(pending.command)
        viewModelScope.launch { runCommand(pending.command) }
    }

    fun denyCommand(pending: PendingCommand) {
        _pendingCommands.value -= pending
        viewModelScope.launch {
            insertTerminal("Command declined by you:\n${pending.command}")
        }
    }

    private suspend fun runCommand(command: String) {
        _runningCommand.value = command
        try {
            val result = container.termux.run(command)
            insertTerminal(formatResult(command, result))
        } catch (e: TermuxError) {
            insertTerminal("Could not run this command:\n$command\n\n${e.message}")
            return
        } finally {
            _runningCommand.value = null
        }
        // One follow-up so the model can react to the result, bounded.
        if (followUpRounds < MAX_FOLLOW_UP_ROUNDS) {
            followUpRounds++
            streamReply()
        }
    }

    private fun formatResult(command: String, result: TermuxResult): String {
        val sb = StringBuilder()
        sb.append("Command: ").append(command).append("\n")
        sb.append("Exit code: ").append(result.exitCode ?: "unknown").append("\n")
        if (result.stdoutTruncated) {
            sb.append("(output was cut short — Termux caps it at 100 KB)\n")
        }
        if (result.stderrTruncated) sb.append("(error output was cut short)\n")
        if (result.stdout.isNotBlank()) {
            sb.append("\n--- output ---\n")
                .append(result.stdout.trimEnd().take(6000))
        }
        if (result.stderr.isNotBlank()) {
            sb.append("\n--- errors ---\n")
                .append(result.stderr.trimEnd().take(3000))
        }
        if (result.err != -1) sb.append("\n").append(result.failureLine())
        if (result.stdout.isBlank() && result.stderr.isBlank()) {
            sb.append("\n(no output)")
        }
        return sb.toString()
    }

    private suspend fun insertAssistant(text: String) {
        val now = System.currentTimeMillis()
        dao.insertMessage(
            ChatMessage(UUID.randomUUID().toString(), conversationId, "assistant", text, now)
        )
        dao.touch(conversationId, now, _conversation.value?.title ?: "New chat")
    }

    private suspend fun insertTerminal(text: String) {
        val now = System.currentTimeMillis()
        dao.insertMessage(
            ChatMessage(UUID.randomUUID().toString(), conversationId, "terminal", text, now)
        )
        dao.touch(conversationId, now, _conversation.value?.title ?: "New chat")
    }

    fun setProvider(providerId: String, model: String) {
        viewModelScope.launch {
            dao.setProvider(conversationId, providerId, model)
            _conversation.value = dao.conversation(conversationId)
            _availableModels.value = emptyList()
        }
    }

    /** Live model list for Ollama / llama-server. Cloud models are typed in. */
    fun refreshModels() {
        val providerId = _conversation.value?.providerId ?: return
        if (providerId != "ollama" && providerId != "llama") return
        viewModelScope.launch {
            _modelsLoading.value = true
            _availableModels.value =
                container.providers.clientFor(providerId).listModels()
            _modelsLoading.value = false
        }
    }

    companion object {
        const val MAX_FOLLOW_UP_ROUNDS = 3

        const val SYSTEM_PROMPT =
            "You are NEO, a helpful assistant running on Joshua's Android phone. " +
                "Answer plainly and briefly. When a task needs the terminal, put the " +
                "exact shell command in a ```sh fenced block, one block per command, " +
                "and say nothing else inside the block. Commands run in Termux " +
                "(bash, no keyboard input, non-interactive flags only). Never invent " +
                "command output — if you need to see output, propose the command " +
                "and wait for the result."
    }
}
