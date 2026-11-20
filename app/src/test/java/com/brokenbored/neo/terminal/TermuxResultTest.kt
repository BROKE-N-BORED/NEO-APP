package com.brokenbored.neo.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxResultTest {

    @Test
    fun parsesSuccessfulResult() {
        val result = TermuxRunner.parseResultMap(
            mapOf(
                "stdout" to "hello\n",
                "stderr" to "",
                "exitCode" to 0,
                "err" to -1
            )
        )
        assertEquals(0, result.exitCode)
        assertEquals("hello\n", result.stdout)
        assertTrue(result.succeeded)
        assertFalse(result.stdoutTruncated)
    }

    @Test
    fun detectsTruncationFromStringLengths() {
        // The wiki's example reads original lengths as strings.
        val result = TermuxRunner.parseResultMap(
            mapOf(
                "stdout" to "short",
                "stdout_original_length" to "150000",
                "exitCode" to 0,
                "err" to -1
            )
        )
        assertTrue(result.stdoutTruncated)
    }

    @Test
    fun detectsTruncationFromIntLengths() {
        val result = TermuxRunner.parseResultMap(
            mapOf(
                "stderr" to "e",
                "stderr_original_length" to 200000,
                "exitCode" to 1,
                "err" to -1
            )
        )
        assertTrue(result.stderrTruncated)
        assertFalse(result.succeeded)
    }

    @Test
    fun missingExitCodeIsNullAndErrmsgSurfaces() {
        val result = TermuxRunner.parseResultMap(
            mapOf("err" to 2, "errmsg" to "no termux")
        )
        assertNull(result.exitCode)
        assertEquals("no termux", result.failureLine())
    }

    @Test
    fun nullBundleIsEmptyResult() {
        val result = TermuxRunner.parseResult(null)
        assertNull(result.exitCode)
        assertTrue(result.errmsg!!.contains("empty result"))
    }
}
