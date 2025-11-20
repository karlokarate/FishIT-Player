package com.chris.m3usuite.telegram.logging

import com.chris.m3usuite.telegram.logging.TgLogEntry.LogLevel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TelegramLogRepository.
 *
 * Tests verify:
 * - Log entry creation and storage
 * - Ringbuffer behavior (max 500 entries)
 * - Level-based filtering
 * - Source-based filtering
 * - Entry retrieval
 * - Log clearing
 */
class TelegramLogRepositoryTest {

    @Before
    fun setup() {
        // Clear log before each test
        TelegramLogRepository.clear()
    }

    @After
    fun teardown() {
        // Clean up after each test
        TelegramLogRepository.clear()
    }

    @Test
    fun `log creates entry with correct properties`() {
        TelegramLogRepository.log(
            level = LogLevel.INFO,
            source = "TestSource",
            message = "Test message",
            details = mapOf("key1" to "value1")
        )

        val entries = TelegramLogRepository.entries.value
        assertEquals(1, entries.size)
        
        val entry = entries.first()
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("TestSource", entry.source)
        assertEquals("Test message", entry.message)
        assertEquals("value1", entry.details?.get("key1"))
    }

    @Test
    fun `info convenience method creates INFO entry`() {
        TelegramLogRepository.info("TestSource", "Info message")

        val entries = TelegramLogRepository.entries.value
        assertEquals(1, entries.size)
        assertEquals(LogLevel.INFO, entries.first().level)
    }

    @Test
    fun `warn convenience method creates WARN entry`() {
        TelegramLogRepository.warn("TestSource", "Warning message")

        val entries = TelegramLogRepository.entries.value
        assertEquals(1, entries.size)
        assertEquals(LogLevel.WARN, entries.first().level)
    }

    @Test
    fun `error convenience method creates ERROR entry`() {
        TelegramLogRepository.error("TestSource", "Error message")

        val entries = TelegramLogRepository.entries.value
        assertEquals(1, entries.size)
        assertEquals(LogLevel.ERROR, entries.first().level)
    }

    @Test
    fun `debug convenience method creates DEBUG entry`() {
        TelegramLogRepository.debug("TestSource", "Debug message")

        val entries = TelegramLogRepository.entries.value
        assertEquals(1, entries.size)
        assertEquals(LogLevel.DEBUG, entries.first().level)
    }

    @Test
    fun `multiple log entries are stored in order`() {
        TelegramLogRepository.info("Source1", "Message 1")
        Thread.sleep(10) // Ensure different timestamps
        TelegramLogRepository.warn("Source2", "Message 2")
        Thread.sleep(10)
        TelegramLogRepository.error("Source3", "Message 3")

        val entries = TelegramLogRepository.entries.value
        assertEquals(3, entries.size)
        
        // Verify order (oldest to newest)
        assertEquals("Message 1", entries[0].message)
        assertEquals("Message 2", entries[1].message)
        assertEquals("Message 3", entries[2].message)
        
        // Verify timestamps are increasing
        assertTrue(entries[0].timestamp <= entries[1].timestamp)
        assertTrue(entries[1].timestamp <= entries[2].timestamp)
    }

    @Test
    fun `getEntriesByLevel filters correctly`() {
        TelegramLogRepository.info("Source1", "Info message")
        TelegramLogRepository.warn("Source2", "Warn message")
        TelegramLogRepository.error("Source3", "Error message")

        val infoEntries = TelegramLogRepository.getEntriesByLevel(LogLevel.INFO)
        val warnEntries = TelegramLogRepository.getEntriesByLevel(LogLevel.WARN)
        val errorEntries = TelegramLogRepository.getEntriesByLevel(LogLevel.ERROR)

        assertEquals(1, infoEntries.size)
        assertEquals(1, warnEntries.size)
        assertEquals(1, errorEntries.size)
        
        assertEquals("Info message", infoEntries.first().message)
        assertEquals("Warn message", warnEntries.first().message)
        assertEquals("Error message", errorEntries.first().message)
    }

    @Test
    fun `getEntriesBySource filters correctly`() {
        TelegramLogRepository.info("SourceA", "Message 1")
        TelegramLogRepository.warn("SourceB", "Message 2")
        TelegramLogRepository.error("SourceA", "Message 3")

        val sourceAEntries = TelegramLogRepository.getEntriesBySource("SourceA")
        val sourceBEntries = TelegramLogRepository.getEntriesBySource("SourceB")

        assertEquals(2, sourceAEntries.size)
        assertEquals(1, sourceBEntries.size)
    }

    @Test
    fun `clear removes all entries`() {
        TelegramLogRepository.info("Source", "Message 1")
        TelegramLogRepository.warn("Source", "Message 2")
        TelegramLogRepository.error("Source", "Message 3")

        assertEquals(3, TelegramLogRepository.entries.value.size)

        TelegramLogRepository.clear()

        assertEquals(0, TelegramLogRepository.entries.value.size)
    }

    @Test
    fun `getAllSources returns unique sorted sources`() {
        TelegramLogRepository.info("SourceC", "Message 1")
        TelegramLogRepository.warn("SourceA", "Message 2")
        TelegramLogRepository.error("SourceB", "Message 3")
        TelegramLogRepository.info("SourceA", "Message 4")

        val sources = TelegramLogRepository.getAllSources()

        assertEquals(3, sources.size)
        assertEquals(listOf("SourceA", "SourceB", "SourceC"), sources)
    }

    @Test
    fun `TgLogEntry formattedTime formats timestamp`() {
        val entry = TgLogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.INFO,
            source = "Test",
            message = "Test message"
        )

        val formatted = entry.formattedTime()
        
        // Format is HH:mm:ss.SSS
        assertTrue(formatted.matches(Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
    }

    @Test
    fun `TgLogEntry formattedDetails returns null for empty details`() {
        val entry = TgLogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.INFO,
            source = "Test",
            message = "Test message",
            details = null
        )

        assertNull(entry.formattedDetails())
    }

    @Test
    fun `TgLogEntry formattedDetails formats map correctly`() {
        val entry = TgLogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.INFO,
            source = "Test",
            message = "Test message",
            details = mapOf("key1" to "value1", "key2" to "value2")
        )

        val formatted = entry.formattedDetails()
        
        assertNotNull(formatted)
        assertTrue(formatted!!.contains("key1=value1"))
        assertTrue(formatted.contains("key2=value2"))
    }
}
