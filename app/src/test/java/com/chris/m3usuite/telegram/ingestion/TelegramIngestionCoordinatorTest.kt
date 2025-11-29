package com.chris.m3usuite.telegram.ingestion

import com.chris.m3usuite.telegram.domain.ChatScanState
import com.chris.m3usuite.telegram.domain.ScanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TelegramIngestionCoordinator.
 *
 * Tests the coordinator's API structure and state management logic.
 * Full integration tests would require Android context and TDLib.
 */
class TelegramIngestionCoordinatorTest {
    @Test
    fun `TelegramIngestionCoordinator class exists`() {
        val clazz = TelegramIngestionCoordinator::class.java
        assertTrue("Class should exist", clazz.name.isNotEmpty())
    }

    @Test
    fun `TelegramIngestionCoordinator has required methods`() {
        val clazz = TelegramIngestionCoordinator::class.java
        val methods = clazz.methods.map { it.name }

        assertTrue("Should have startBackfill method", methods.contains("startBackfill"))
        assertTrue("Should have resumeBackfill method", methods.contains("resumeBackfill"))
        assertTrue("Should have pauseBackfill method", methods.contains("pauseBackfill"))
        assertTrue("Should have getScanState method", methods.contains("getScanState"))
        assertTrue("Should have clearScanState method", methods.contains("clearScanState"))
        assertTrue("Should have observeScanStates method", methods.contains("observeScanStates"))
        assertTrue("Should have getScanStates property", methods.contains("getScanStates"))
    }

    @Test
    fun `ChatScanState tracks scan progress`() {
        val state =
            ChatScanState(
                chatId = 123L,
                lastScannedMessageId = 456L,
                hasMoreHistory = true,
                status = ScanStatus.SCANNING,
                lastError = null,
                updatedAt = System.currentTimeMillis(),
            )

        assertEquals(123L, state.chatId)
        assertEquals(456L, state.lastScannedMessageId)
        assertTrue(state.hasMoreHistory)
        assertEquals(ScanStatus.SCANNING, state.status)
    }

    @Test
    fun `ChatScanState status transitions are valid`() {
        // IDLE -> SCANNING
        val idle =
            ChatScanState(
                chatId = 1L,
                status = ScanStatus.IDLE,
            )
        val scanning = idle.copy(status = ScanStatus.SCANNING)
        assertEquals(ScanStatus.SCANNING, scanning.status)

        // SCANNING -> IDLE (completed)
        val completed = scanning.copy(status = ScanStatus.IDLE, hasMoreHistory = false)
        assertEquals(ScanStatus.IDLE, completed.status)
        assertFalse(completed.hasMoreHistory)

        // SCANNING -> ERROR
        val error = scanning.copy(status = ScanStatus.ERROR, lastError = "Connection failed")
        assertEquals(ScanStatus.ERROR, error.status)
        assertEquals("Connection failed", error.lastError)
    }

    @Test
    fun `ChatScanState preserves chatId on copy`() {
        val original =
            ChatScanState(
                chatId = 999L,
                lastScannedMessageId = 100L,
            )
        val updated = original.copy(lastScannedMessageId = 200L)

        assertEquals("chatId should be preserved", original.chatId, updated.chatId)
        assertEquals("lastScannedMessageId should be updated", 200L, updated.lastScannedMessageId)
    }

    @Test
    fun `ScanStatus enum has all expected values`() {
        val statuses = ScanStatus.values()

        assertEquals("Should have 3 status values", 3, statuses.size)
        assertTrue(statuses.contains(ScanStatus.IDLE))
        assertTrue(statuses.contains(ScanStatus.SCANNING))
        assertTrue(statuses.contains(ScanStatus.ERROR))
    }

    @Test
    fun `ChatScanState defaults are sensible`() {
        val state = ChatScanState(chatId = 1L)

        assertEquals(0L, state.lastScannedMessageId)
        assertTrue("hasMoreHistory should default to true", state.hasMoreHistory)
        assertEquals(ScanStatus.IDLE, state.status)
        assertEquals(null, state.lastError)
    }
}
