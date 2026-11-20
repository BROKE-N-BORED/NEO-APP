package com.brokenbored.neo.providers

import com.brokenbored.neo.data.KeyStore
import com.brokenbored.neo.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Plain human-readable failure. The UI shows this message, never a fake reply. */
class ProviderError(message: String) : Exception(message)

/** Thrown by a line parser to end the stream cleanly (e.g. "[DONE]"). */
internal class DoneStreaming : Exception()

/** Pulls the readable message out of an error body, whatever its shape. */
internal fun extractError(body: String): String {
    if (body.isBlank()) return "no detail"
    return try {
        val obj = JSONObject(body)
        val err = obj.opt("error")
        when (err) {
            is JSONObject -> err.optString("message", body)
            is String -> err
            else -> obj.optString("message", body)
        }.ifBlank { body }
    } catch (_: Exception) {
        body
    }
}

data class ChatMsg(val role: String, val content: String)

data class ProviderMeta(
    val id: String,
    val label: String,
    val defaultModel: String,
    /** Approximate context window in tokens, for the context meter. */
    val contextWindow: Int,
    val needsKey: Boolean,
    val hint: String
)

/** The five providers, matching the local Muse clone and Code Forge's list.
 *  No xAI/Grok — Joshua does not use it. */
object ProviderCatalog {
    val all: List<ProviderMeta> = listOf(
        ProviderMeta(
            id = "ollama",
            label = "Ollama (local)",
            defaultModel = "qwen2.5:1.5b",
            contextWindow = 4096,
            needsKey = false,
            hint = "Start Ollama: ollama serve, then ollama pull <model>"
        ),
        ProviderMeta(
            id = "llama",
            label = "Phone model (llama-server)",
            defaultModel = "phone-model",
            contextWindow = 4096,
            needsKey = false,
            hint = "Start llama-server on the phone (see Help)"
        ),
        ProviderMeta(
            id = "groq",
            label = "Groq (free cloud)",
            defaultModel = "qwen/qwen3.8-27b",
            contextWindow = 32768,
            needsKey = true,
            hint = "Add your Groq key in Settings (console.groq.com, free tier)"
        ),
        ProviderMeta(
            id = "gemini",
            label = "Gemini (free cloud)",
            defaultModel = "gemini-3.8-flash",
            contextWindow = 1048576,
            needsKey = true,
            hint = "Add your Gemini key in Settings (aistudio.google.com, free)"
        ),
        ProviderMeta(
            id = "exaone",
            label = "EXAONE (LG, cloud)",
            defaultModel = "k-exaone_v2.5",
            contextWindow = 524288,
            needsKey = true,
            hint = "Add your EXAONE key in Settings (api.exaone.ai)"
        )
    )

    fun meta(id: String): ProviderMeta =
        all.firstOrNull { it.id == id } ?: all.first()

    /** Rough token estimate: ~4 chars per token. Always labeled an estimate. */
    fun estimateTokens(text: String): Int = maxOf(1, (text.length + 3) / 4)
}

interface ProviderClient {
    val id: String

    /** Streams the reply, calling [onChunk] for each piece of text. Throws
     * [ProviderError] with a plain message on failure. Cancelling the calling
     * coroutine stops the request. */
    suspend fun streamChat(
        model: String,
        messages: List<ChatMsg>,
        onChunk: (String) -> Unit
    )

    /** Models actually available, where the provider can list them. Empty
     * when the provider has no list endpoint (cloud models are typed in). */
    suspend fun listModels(): List<String> = emptyList()
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private fun httpClient(connectSeconds: Long, readSeconds: Long): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(connectSeconds, TimeUnit.SECONDS)
        .readTimeout(readSeconds, TimeUnit.SECONDS)
        .build()

/** Shared streaming plumbing: one POST, line-by-line read, plain errors. */
internal abstract class StreamingClient : ProviderClient {
    protected val streamHttp = httpClient(connectSeconds = 10, readSeconds = 300)
    protected val quickHttp = httpClient(connectSeconds = 3, readSeconds = 5)

    /** Set by streamChat before calling streamPost. */
    protected var chunkSink: (String) -> Unit = {}

    protected suspend fun streamPost(
        url: String,
        payload: JSONObject,
        headers: Map<String, String> = emptyMap(),
        /** Parses one response line into a text chunk, or null to skip it.
         * Throws [ProviderError] for an error payload and [DoneStreaming]
         * when the reply is finished. */
        onLine: (String) -> String?
    ) {
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url).post(body).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        val call = streamHttp.newCall(request)
        withContext(Dispatchers.IO) {
            currentCoroutineContext().job.invokeOnCancellation { call.cancel() }
            val response = try {
                call.execute()
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive) {
                    throw ProviderError("Could not reach server: ${e.message}")
                } else {
                    throw e
                }
            }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val detail = resp.body?.string().orEmpty()
                    throw ProviderError("HTTP ${resp.code}: ${extractError(detail)}")
                }
                val source = resp.body!!.source()
                while (isActive) {
                    val line = try {
                        source.readUtf8Line()
                    } catch (e: Exception) {
                        if (isActive) throw ProviderError("Connection broke: ${e.message}")
                        else break
                    } ?: break
                    if (line.isBlank()) continue
                    try {
                        val chunk = onLine(line)
                        if (!chunk.isNullOrEmpty()) {
                            // onChunk is called from the IO thread; StateFlow
                            // updates are thread-safe, so this is fine.
                            chunkSink(chunk)
                        }
                    } catch (e: DoneStreaming) {
                        break
                    }
                }
            }
        }
    }
}

/** Ollama: POST /api/chat, newline-delimited JSON replies. */
internal class OllamaClient(private val baseUrl: () -> String) : StreamingClient() {
    override val id = "ollama"

    override suspend fun streamChat(
        model: String,
        messages: List<ChatMsg>,
        onChunk: (String) -> Unit
    ) {
        chunkSink = onChunk
        val payload = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })
        }
        val base = baseUrl().trimEnd('/')
        try {
            streamPost("$base/api/chat", payload) { line -> parseLine(line) }
        } catch (e: ProviderError) {
            throw ProviderError(
                "Ollama is not reachable at $base. Is Ollama running? (${e.message})"
            )
        }
    }

    override suspend fun listModels(): List<String> {
        val base = baseUrl().trimEnd('/')
        val request = Request.Builder().url("$base/api/tags").get().build()
        return try {
            withContext(Dispatchers.IO) {
                quickHttp.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    val arr = obj.optJSONArray("models") ?: return@withContext emptyList()
                    (0 until arr.length()).mapNotNull {
                        arr.optJSONObject(it)?.optString("name")
                    }.filter { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        /**
         * Parses one newline-delimited JSON line from /api/chat. Returns
         * the text chunk, or null when the line carries none. Throws
         * [ProviderError] on an error payload, [DoneStreaming] when the
         * reply is finished. Pure — unit-tested without a server.
         */
        fun parseLine(line: String): String? {
            val obj = try {
                JSONObject(line)
            } catch (_: Exception) {
                return null
            }
            if (obj.has("error")) {
                throw ProviderError("Ollama: ${obj.optString("error")}")
            }
            if (obj.optBoolean("done", false)) throw DoneStreaming()
            return obj.optJSONObject("message")?.optString("content").orEmpty()
                .ifEmpty { null }
        }
    }
}

/** OpenAI-compatible chat completions (Groq, EXAONE, llama-server). */
internal class OpenAiClient(
    override val id: String,
    private val baseUrl: () -> String,
    private val apiKey: () -> String?,
    private val missingKeyMessage: String,
    private val extraFields: JSONObject.() -> Unit = {}
) : StreamingClient() {
    override suspend fun streamChat(
        model: String,
        messages: List<ChatMsg>,
        onChunk: (String) -> Unit
    ) {
        val key = apiKey()
        if (key.isNullOrBlank() && missingKeyMessage.isNotEmpty()) {
            throw ProviderError(missingKeyMessage)
        }
        chunkSink = onChunk
        val payload = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })
            extraFields()
        }
        val headers = if (!key.isNullOrBlank()) {
            mapOf("Authorization" to "Bearer $key")
        } else {
            emptyMap()
        }
        val base = baseUrl().trimEnd('/')
        try {
            streamPost("$base/chat/completions", payload, headers) { line ->
                parseSseLine(line, id)
            }
        } catch (e: ProviderError) {
            if (id == "llama") {
                throw ProviderError(
                    "llama-server is not reachable at $base. " +
                        "Start it on the phone first (see Help). (${e.message})"
                )
            }
            throw e
        }
    }

    override suspend fun listModels(): List<String> {
        if (id != "llama") return emptyList()
        val base = baseUrl().trimEnd('/')
        val request = Request.Builder().url("$base/v1/models").get().build()
        return try {
            withContext(Dispatchers.IO) {
                quickHttp.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    val arr = obj.optJSONArray("data") ?: return@withContext emptyList()
                    (0 until arr.length()).mapNotNull {
                        arr.optJSONObject(it)?.optString("id")
                    }.filter { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        /** Parses one SSE line from /chat/completions. Pure — unit-tested. */
        fun parseSseLine(line: String, providerId: String): String? {
            if (!line.startsWith("data:")) return null
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") throw DoneStreaming()
            val obj = try {
                JSONObject(data)
            } catch (_: Exception) {
                return null
            }
            if (obj.has("error")) {
                throw ProviderError("$providerId: ${extractError(data)}")
            }
            val choices = obj.optJSONArray("choices") ?: return null
            val sb = StringBuilder()
            for (i in 0 until choices.length()) {
                sb.append(
                    choices.optJSONObject(i)
                        ?.optJSONObject("delta")
                        ?.optString("content").orEmpty()
                )
            }
            return sb.toString().ifEmpty { null }
        }
    }
}

/** Gemini native REST API: streamGenerateContent with SSE. */
internal class GeminiClient(
    private val apiKey: () -> String?
) : StreamingClient() {
    override val id = "gemini"

    override suspend fun streamChat(
        model: String,
        messages: List<ChatMsg>,
        onChunk: (String) -> Unit
    ) {
        val key = apiKey()
        if (key.isNullOrBlank()) {
            throw ProviderError(
                "Gemini needs a key — get a free one at aistudio.google.com " +
                    "and add it in Settings."
            )
        }
        chunkSink = onChunk
        val contents = JSONArray()
        val systemText = StringBuilder()
        messages.forEach { m ->
            if (m.role == "system") {
                systemText.append(m.content).append("\n")
            } else {
                val role = if (m.role == "user") "user" else "model"
                contents.put(
                    JSONObject().put("role", role).put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", m.content))
                    )
                )
            }
        }
        val payload = JSONObject().put("contents", contents)
        if (systemText.isNotBlank()) {
            payload.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemText.toString()))
                )
            )
        }
        val url = "https://generativelanguage.googleapis.com/v1beta" +
            "/models/$model:streamGenerateContent?alt=sse&key=$key"
        try {
            streamPost(url, payload) { line -> parseSseLine(line) }
        } catch (e: ProviderError) {
            throw ProviderError("Gemini: ${e.message}")
        }
    }

    companion object {
        /** Parses one SSE line from streamGenerateContent. Pure — unit-tested. */
        fun parseSseLine(line: String): String? {
            if (!line.startsWith("data:")) return null
            val obj = try {
                JSONObject(line.removePrefix("data:").trim())
            } catch (_: Exception) {
                return null
            }
            if (obj.has("error")) {
                throw ProviderError("Gemini: ${extractError(line)}")
            }
            val candidates = obj.optJSONArray("candidates") ?: return null
            val sb = StringBuilder()
            for (i in 0 until candidates.length()) {
                val parts = candidates.optJSONObject(i)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: continue
                for (j in 0 until parts.length()) {
                    sb.append(parts.optJSONObject(j)?.optString("text").orEmpty())
                }
            }
            return sb.toString().ifEmpty { null }
        }
    }
}

/** Builds clients from the current settings and stored keys. */
class ProviderFactory(
    private val settings: SettingsStore,
    private val keys: KeyStore
) {
    fun clientFor(providerId: String): ProviderClient = when (providerId) {
        "ollama" -> OllamaClient { settings.current.ollamaUrl }
        "llama" -> OpenAiClient(
            id = "llama",
            baseUrl = { settings.current.llamaUrl },
            apiKey = { null },
            missingKeyMessage = ""
        )
        "groq" -> OpenAiClient(
            id = "groq",
            baseUrl = { "https://api.groq.com/openai/v1" },
            apiKey = { keys.getKey("groq") },
            missingKeyMessage = "Groq needs a key — get a free one at " +
                "console.groq.com and add it in Settings."
        )
        "exaone" -> OpenAiClient(
            id = "exaone",
            baseUrl = { "https://api.exaone.ai/v1" },
            apiKey = { keys.getKey("exaone") },
            missingKeyMessage = "EXAONE needs a key — add yours in Settings.",
            extraFields = { put("reasoning_effort", "low") }
        )
        "gemini" -> GeminiClient { keys.getKey("gemini") }
        else -> OllamaClient { settings.current.ollamaUrl }
    }
}
