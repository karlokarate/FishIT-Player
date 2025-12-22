package com.fishit.player.infra.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LogRedactor].
 *
 * Verifies that all sensitive patterns are properly redacted.
 */
class LogRedactorTest {

    // ==================== Username/Password Patterns ====================

    @Test
    fun `redact replaces username in key=value format`() {
        val input = "Request with username=john.doe&other=param"
        val result = LogRedactor.redact(input)
        
        assertTrue(result.contains("username=***"))
        assertFalse(result.contains("john.doe"))
    }

    @Test
    fun `redact replaces password in key=value format`() {
        val input = "Login attempt: password=SuperSecret123!"
        val result = LogRedactor.redact(input)
        
        assertTrue(result.contains("password=***"))
        assertFalse(result.contains("SuperSecret123"))
    }

    @Test
    fun `redact replaces user and pass Xtream params`() {
        val input = "URL: http://server.com/get.php?user=admin&pass=secret123"
        val result = LogRedactor.redact(input)
        
        assertFalse(result.contains("admin"))
        assertFalse(result.contains("secret123"))
    }

    // ==================== Token/API Key Patterns ====================

    @Test
    fun `redact replaces Bearer token`() {
        val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
        val result = LogRedactor.redact(input)
        
        assertTrue(result.contains("Bearer ***"))
        assertFalse(result.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
    }

    @Test
    fun `redact replaces Basic auth`() {
        val input = "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
        val result = LogRedactor.redact(input)
        
        assertTrue(result.contains("Basic ***"))
        assertFalse(result.contains("YWRtaW46cGFzc3dvcmQ="))
    }

    @Test
    fun `redact replaces api_key parameter`() {
        val input = "API call with api_key=sk-12345abcde"
        val result = LogRedactor.redact(input)
        
        assertTrue(result.contains("api_key=***"))
        assertFalse(result.contains("sk-12345abcde"))
    }

    // ==================== JSON Patterns ====================

    @Test
    fun `redact replaces password in JSON`() {
        val input = """{"username": "admin", "password": "secret123"}"""
        val result = LogRedactor.redact(input)
        
        assertTrue(result.contains(""""password":"***""""))
        assertFalse(result.contains("secret123"))
    }

    @Test
    fun `redact replaces token in JSON`() {
        val input = """{"token": "abc123xyz", "other": "value"}"""
        val result = LogRedactor.redact(input)
        
        assertTrue(result.contains(""""token":"***""""))
        assertFalse(result.contains("abc123xyz"))
    }

    // ==================== Phone Number Patterns ====================

    @Test
    fun `redact replaces phone numbers`() {
        val input = "Telegram auth for +49123456789"
        val result = LogRedactor.redact(input)
        
        assertTrue(result.contains("***PHONE***"))
        assertFalse(result.contains("+49123456789"))
    }

    @Test
    fun `redact does not affect short numbers`() {
        val input = "Error code: 12345"
        val result = LogRedactor.redact(input)
        
        // Short numbers should not be redacted (not phone-like)
        assertTrue(result.contains("12345"))
    }

    // ==================== Edge Cases ====================

    @Test
    fun `redact handles empty string`() {
        assertEquals("", LogRedactor.redact(""))
    }

    @Test
    fun `redact handles blank string`() {
        assertEquals("   ", LogRedactor.redact("   "))
    }

    @Test
    fun `redact handles string without secrets`() {
        val input = "Normal log message without any sensitive data"
        assertEquals(input, LogRedactor.redact(input))
    }

    @Test
    fun `redact handles multiple secrets in one string`() {
        val input = "user=admin&password=secret&api_key=xyz123"
        val result = LogRedactor.redact(input)
        
        assertFalse(result.contains("admin"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains("xyz123"))
    }

    // ==================== Case Insensitivity ====================

    @Test
    fun `redact is case insensitive for keywords`() {
        val inputs = listOf(
            "USERNAME=test",
            "Username=test",
            "PASSWORD=secret",
            "Password=secret",
            "API_KEY=key",
            "Api_Key=key"
        )
        
        for (input in inputs) {
            val result = LogRedactor.redact(input)
            assertFalse("Failed for: $input", result.contains("test") || result.contains("secret") || result.contains("key"))
        }
    }

    // ==================== Throwable Redaction ====================

    @Test
    fun `redactThrowable handles null`() {
        assertEquals(null, LogRedactor.redactThrowable(null))
    }

    @Test
    fun `redactThrowable redacts exception message`() {
        val exception = IllegalArgumentException("Invalid password=secret123")
        val result = LogRedactor.redactThrowable(exception)
        
        assertFalse(result?.contains("secret123") ?: true)
    }

    // ==================== BufferedLogEntry Redaction ====================

    @Test
    fun `redactEntry creates redacted copy`() {
        val entry = BufferedLogEntry(
            timestamp = System.currentTimeMillis(),
            priority = android.util.Log.DEBUG,
            tag = "Test",
            message = "Login with password=secret123",
            throwable = null
        )
        
        val redacted = LogRedactor.redactEntry(entry)
        
        assertFalse(redacted.message.contains("secret123"))
        assertTrue(redacted.message.contains("password=***"))
        assertEquals(entry.timestamp, redacted.timestamp)
        assertEquals(entry.priority, redacted.priority)
        assertEquals(entry.tag, redacted.tag)
    }
}
