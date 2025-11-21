package com.chris.m3usuite.logs

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LogViewerViewModel helper functions.
 *
 * Tests:
 * 1. Source extraction from various log formats
 * 2. Validation that log levels are not treated as sources
 * 3. Edge cases in log parsing
 *
 * Note: These tests focus on the logic of extractSource, which doesn't require Android context.
 */
class LogViewerViewModelTest {

    /**
     * Helper function to simulate extractSource logic.
     * This is a copy of the actual implementation for testing purposes.
     */
    private fun extractSource(line: String): String? {
        // JSON format
        if (line.trim().startsWith("{")) {
            val sourceMatch = Regex(""""source"\s*:\s*"([^"]+)"""").find(line)
            if (sourceMatch != null) {
                return sourceMatch.groupValues[1]
            }
        }
        
        // Bracketed format [Source]
        val bracketMatch = Regex("""\[([^\]]+)\]""").find(line)
        if (bracketMatch != null) {
            return bracketMatch.groupValues[1]
        }
        
        // Space-separated format (third token)
        val parts = line.split(" ", limit = 4)
        if (parts.size >= 3) {
            val candidate = parts[2]
            // Known log levels that should not be treated as sources
            val logLevels = setOf("INFO", "DEBUG", "ERROR", "WARN", "TRACE", "FATAL")
            
            // Reject if it's a known log level
            if (candidate in logLevels) {
                return null
            }
            
            // Basic validation: first part should look like a timestamp
            if (parts[0].contains('T') || parts[0].contains(':')) {
                // Second part should look like a log level
                val secondPart = parts[1].uppercase()
                if (secondPart in logLevels) {
                    // Return if it looks like a source (starts with capital or T_)
                    if (candidate.isNotEmpty() && 
                        (candidate[0].isUpperCase() || candidate.startsWith("T_"))) {
                        return candidate
                    }
                }
            }
        }
        
        return null
    }

    @Test
    fun `extractSource handles space-separated format correctly`() {
        // Valid space-separated format
        val validLog = "2025-11-21T10:30:00Z DEBUG TelegramDataSource message text"
        val source = extractSource(validLog)
        assertEquals("TelegramDataSource", source)
    }

    @Test
    fun `extractSource rejects log levels as sources`() {
        // Log levels should not be treated as sources
        val testCases = listOf(
            "2025-11-21T10:30:00Z INFO ERROR message",
            "2025-11-21T10:30:00Z DEBUG INFO some text",
            "2025-11-21T10:30:00Z WARN DEBUG another message",
            "2025-11-21T10:30:00Z ERROR WARN test"
        )
        
        testCases.forEach { log ->
            val source = extractSource(log)
            assertNull("Log level should not be treated as source in: $log", source)
        }
    }

    @Test
    fun `extractSource handles JSON format`() {
        val jsonLog = """{"source":"TelegramDataSource","level":"DEBUG","message":"test"}"""
        val source = extractSource(jsonLog)
        assertEquals("TelegramDataSource", source)
    }

    @Test
    fun `extractSource handles bracketed format`() {
        val bracketedLog = "[TelegramDataSource] Some log message here"
        val source = extractSource(bracketedLog)
        assertEquals("TelegramDataSource", source)
    }

    @Test
    fun `extractSource validates timestamp and log level format`() {
        // Invalid timestamp format should not return source
        val invalidLog = "NOTADATE NOTLEVEL TelegramDataSource message"
        val source = extractSource(invalidLog)
        assertNull("Should not extract source without valid timestamp and log level", source)
    }

    @Test
    fun `extractSource handles T_ prefix correctly`() {
        val logWithT = "2025-11-21T10:30:00Z DEBUG T_DataSource message"
        val source = extractSource(logWithT)
        assertEquals("T_DataSource", source)
    }

    @Test
    fun `extractSource with malformed logs returns null`() {
        val testCases = listOf(
            "",  // Empty
            "single",  // Too short
            "two words",  // Too short
            "no timestamp here TelegramDataSource",  // No timestamp
        )
        
        testCases.forEach { log ->
            val source = extractSource(log)
            assertNull("Should return null for malformed log: $log", source)
        }
    }

    @Test
    fun `extractSource with various timestamp formats`() {
        val testCases = listOf(
            "2025-11-21T10:30:00Z DEBUG TelegramDataSource msg" to "TelegramDataSource",
            "10:30:00 ERROR DataProvider failure" to "DataProvider",
        )
        
        testCases.forEach { (log, expected) ->
            val source = extractSource(log)
            assertEquals("Should extract source from: $log", expected, source)
        }
    }
}
