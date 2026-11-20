package com.brokenbored.neo.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersTest {

    @Test
    fun estimateTokensIsCharsOverFour() {
        assertEquals(1, ProviderCatalog.estimateTokens(""))
        assertEquals(1, ProviderCatalog.estimateTokens("hi"))
        assertEquals(25, ProviderCatalog.estimateTokens("a".repeat(100)))
    }

    @Test
    fun catalogHasFiveProvidersAndNoGrok() {
        assertEquals(5, ProviderCatalog.all.size)
        assertTrue(
            ProviderCatalog.all.none {
                it.id.contains("grok") || it.id.contains("xai")
            }
        )
        assertEquals("ollama", ProviderCatalog.all.first().id)
    }

    @Test
    fun exaoneContextIs524288() {
        assertEquals(524288, ProviderCatalog.meta("exaone").contextWindow)
    }

    @Test
    fun ollamaLineParsesChunk() {
        val chunk = OllamaClient.parseLine(
            """{"model":"qwen2.5:1.5b","message":{"role":"assistant","content":"Hello"},"done":false}"""
        )
        assertEquals("Hello", chunk)
    }

    @Test
    fun ollamaLineDoneEndsStream() {
        assertThrows(DoneStreaming::class.java) {
            OllamaClient.parseLine("""{"done":true}""")
        }
    }

    @Test
    fun ollamaLineErrorThrowsProviderError() {
        assertThrows(ProviderError::class.java) {
            OllamaClient.parseLine("""{"error":"model not found"}""")
        }
    }

    @Test
    fun ollamaLineBadJsonIsSkipped() {
        assertNull(OllamaClient.parseLine("not json"))
    }

    @Test
    fun openAiSseLineParsesDelta() {
        val chunk = OpenAiClient.parseSseLine(
            """data: {"choices":[{"delta":{"content":"Hi"}}]}""",
            "groq"
        )
        assertEquals("Hi", chunk)
    }

    @Test
    fun openAiSseDoneEndsStream() {
        assertThrows(DoneStreaming::class.java) {
            OpenAiClient.parseSseLine("data: [DONE]", "groq")
        }
    }

    @Test
    fun openAiSseNonDataLineIsSkipped() {
        assertNull(OpenAiClient.parseSseLine(": ping", "groq"))
    }

    @Test
    fun openAiSseErrorThrowsProviderError() {
        assertThrows(ProviderError::class.java) {
            OpenAiClient.parseSseLine(
                """data: {"error":{"message":"bad key"}}""",
                "groq"
            )
        }
    }

    @Test
    fun geminiSseLineParsesText() {
        val chunk = GeminiClient.parseSseLine(
            """data: {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}"""
        )
        assertEquals("Hello", chunk)
    }

    @Test
    fun extractErrorReadsMessage() {
        assertEquals(
            "bad key",
            extractError("""{"error":{"message":"bad key","code":401}}""")
        )
        assertEquals("plain", extractError("""{"error":"plain"}"""))
    }
}
