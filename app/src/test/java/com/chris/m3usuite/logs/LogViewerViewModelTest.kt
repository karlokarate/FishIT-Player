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
 * Note: These tests use a test-only function that mirrors the production logic.
 * The actual extractSource method is internal and tested indirectly through integration tests.
 */
class LogViewerViewModelTest {

    /**
     * Test helper that mirrors the production extractSource logic.
     */
    private fun extractSourceForTest(line: String): String? {
        // JSON format
        val jsonRegex = Regex(""""source"\s*:\s*"([^"]+)"""")
        if (line.trim().startsWith("{")) {
            val sourceMatch = jsonRegex.find(line)
            if (sourceMatch != null) {
                return sourceMatch.groupValues[1]
            }
        }
        
        // Bracketed format [Source]
        val bracketRegex = Regex("""\[([^\]]+)\]""")
        val bracketMatch = bracketRegex.find(line)
        if (bracketMatch != null) {
            return bracketMatch.groupValues[1]
        }
        
        // Space-separated format (third token)
        val parts = line.split(" ", limit = 4)
        if (parts.size >= 3) {
            val candidate = parts[2]
            val logLevels = setOf("INFO", "DEBUG", "ERROR", "WARN", "TRACE", "FATAL")
            
            // Reject if candidate is a known log level
            if (candidate in logLevels) {
                return null
            }
            
            // Validate timestamp format
            if (!parts[0].contains('T') && !parts[0].contains(':')) {
                return null
            }
            
            // Validate log level
            if (parts[1].uppercase() !in logLevels) {
                return null
            }
            
            // Return if it looks like a source
            if (candidate.isNotEmpty() && 
                (candidate[0].isUpperCase() || candidate.startsWith("T_"))) {
                return candidate
            }
        }
        
        return null
    }


    @Test
    fun `extractSource handles space-separated format correctly`() {
        val validLog = "2025-11-21T10:30:00Z DEBUG TelegramDataSource message text"
        val source = extractSourceForTest(validLog)
        assertEquals("TelegramDataSource", source)
    }

    @Test
    fun `extractSource rejects log levels as sources`() {
        val testCases = listOf(
            "2025-11-21T10:30:00Z INFO ERROR message",
            "2025-11-21T10:30:00Z DEBUG INFO some text",
            "2025-11-21T10:30:00Z WARN DEBUG another message",
            "2025-11-21T10:30:00Z ERROR WARN test"
        )
        
        testCases.forEach { log ->
            val source = extractSourceForTest(log)
            assertNull("Log level should not be treated as source in: $log", source)
        }
    }

    @Test
    fun `extractSource handles JSON format`() {
        val jsonLog = """{"source":"TelegramDataSource","level":"DEBUG","message":"test"}"""
        val source = extractSourceForTest(jsonLog)
        assertEquals("TelegramDataSource", source)
    }

    @Test
    fun `extractSource handles bracketed format`() {
        val bracketedLog = "[TelegramDataSource] Some log message here"
        val source = extractSourceForTest(bracketedLog)
        assertEquals("TelegramDataSource", source)
    }

    @Test
    fun `extractSource validates timestamp and log level format`() {
        val invalidLog = "NOTADATE NOTLEVEL TelegramDataSource message"
        val source = extractSourceForTest(invalidLog)
        assertNull("Should not extract source without valid timestamp and log level", source)
    }

    @Test
    fun `extractSource handles T_ prefix correctly`() {
        val logWithT = "2025-11-21T10:30:00Z DEBUG T_DataSource message"
        val source = extractSourceForTest(logWithT)
        assertEquals("T_DataSource", source)
    }

    @Test
    fun `extractSource with malformed logs returns null`() {
        val testCases = listOf(
            "",
            "single",
            "two words",
            "no timestamp here TelegramDataSource",
        )
        
        testCases.forEach { log ->
            val source = extractSourceForTest(log)
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
            val source = extractSourceForTest(log)
            assertEquals("Should extract source from: $log", expected, source)
        }
    }
}
}
