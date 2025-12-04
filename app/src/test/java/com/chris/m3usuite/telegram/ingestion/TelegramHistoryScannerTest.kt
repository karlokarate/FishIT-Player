package com.chris.m3usuite.telegram.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TelegramHistoryScanner.
 *
 * Tests the scanner's configuration and API structure.
 * Full integration tests would require TDLib native libraries.
 */
class TelegramHistoryScannerTest {
    @Test
    fun `ScanConfig has correct defaults`() {
        val config = TelegramHistoryScanner.ScanConfig()

        assertEquals("Default pageSize should be 100", 100, config.pageSize)
        assertEquals("Default maxPages should be Int.MAX_VALUE for unlimited scanning", Int.MAX_VALUE, config.maxPages)
        assertEquals("Default maxRetries should be 5", 5, config.maxRetries)
        assertEquals("Default onlyLocal should be false", false, config.onlyLocal)
        assertEquals("Default fromMessageId should be 0", 0L, config.fromMessageId)
    }

    @Test
    fun `ScanConfig can be customized`() {
        val config =
            TelegramHistoryScanner.ScanConfig(
                pageSize = 50,
                maxPages = 20,
                maxRetries = 3,
                onlyLocal = true,
                fromMessageId = 12345L,
            )

        assertEquals(50, config.pageSize)
        assertEquals(20, config.maxPages)
        assertEquals(3, config.maxRetries)
        assertTrue(config.onlyLocal)
        assertEquals(12345L, config.fromMessageId)
    }

    @Test
    fun `ScanResult has all required fields`() {
        val result =
            TelegramHistoryScanner.ScanResult(
                oldestMessageId = 100L,
                hasMoreHistory = true,
                rawMessageCount = 50,
                convertedCount = 45,
                pagesProcessed = 5,
            )

        assertEquals(100L, result.oldestMessageId)
        assertTrue(result.hasMoreHistory)
        assertEquals(50, result.rawMessageCount)
        assertEquals(45, result.convertedCount)
        assertEquals(5, result.pagesProcessed)
    }

    @Test
    fun `ScanResult tracks conversion ratio`() {
        val result =
            TelegramHistoryScanner.ScanResult(
                oldestMessageId = 0L,
                hasMoreHistory = false,
                rawMessageCount = 100,
                convertedCount = 90,
                pagesProcessed = 10,
            )

        // 90% conversion rate
        val conversionRate = result.convertedCount.toDouble() / result.rawMessageCount.toDouble()
        assertEquals(0.9, conversionRate, 0.001)
    }

    @Test
    fun `TelegramHistoryScanner class has required methods`() {
        val clazz = TelegramHistoryScanner::class.java
        val methods = clazz.methods.map { it.name }

        assertTrue("Should have scan method", methods.contains("scan"))
        assertTrue("Should have scanSingleBatch method", methods.contains("scanSingleBatch"))
    }

    @Test
    fun `ScanConfig copy works correctly`() {
        val original = TelegramHistoryScanner.ScanConfig()
        val copied = original.copy(fromMessageId = 999L)

        assertEquals("Original fromMessageId unchanged", 0L, original.fromMessageId)
        assertEquals("Copied fromMessageId updated", 999L, copied.fromMessageId)
        assertEquals("Other fields preserved", original.pageSize, copied.pageSize)
    }
}
