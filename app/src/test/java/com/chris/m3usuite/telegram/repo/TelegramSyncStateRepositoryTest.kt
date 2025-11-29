package com.chris.m3usuite.telegram.repo

import com.chris.m3usuite.data.obx.ObxChatScanState
import com.chris.m3usuite.telegram.domain.ChatScanState
import com.chris.m3usuite.telegram.domain.ScanStatus
import com.chris.m3usuite.telegram.domain.toDomain
import com.chris.m3usuite.telegram.domain.toObx
import com.chris.m3usuite.telegram.repository.TelegramSyncStateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for ChatScanState <-> ObxChatScanState round-trip mapping
 * and TelegramSyncStateRepository structure verification.
 */
class TelegramSyncStateRepositoryTest {
    @Test
    fun `ChatScanState round-trips correctly with IDLE status`() {
        val original =
            ChatScanState(
                chatId = -1001234567890L,
                lastScannedMessageId = 12345L,
                hasMoreHistory = true,
                status = ScanStatus.IDLE,
                lastError = null,
                updatedAt = 1700000000000L,
            )

        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        assertEquals(original.chatId, roundTripped.chatId)
        assertEquals(original.lastScannedMessageId, roundTripped.lastScannedMessageId)
        assertEquals(original.hasMoreHistory, roundTripped.hasMoreHistory)
        assertEquals(original.status, roundTripped.status)
        assertEquals(original.lastError, roundTripped.lastError)
        assertEquals(original.updatedAt, roundTripped.updatedAt)
    }

    @Test
    fun `ChatScanState round-trips correctly with SCANNING status`() {
        val original =
            ChatScanState(
                chatId = -1001234567890L,
                lastScannedMessageId = 22345L,
                hasMoreHistory = true,
                status = ScanStatus.SCANNING,
                lastError = null,
                updatedAt = 1700000001000L,
            )

        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        assertEquals(ScanStatus.SCANNING, roundTripped.status)
    }

    @Test
    fun `ChatScanState round-trips correctly with ERROR status`() {
        val original =
            ChatScanState(
                chatId = -1001234567890L,
                lastScannedMessageId = 32345L,
                hasMoreHistory = false,
                status = ScanStatus.ERROR,
                lastError = "Rate limit exceeded",
                updatedAt = 1700000002000L,
            )

        val obx = original.toObx()
        val roundTripped = obx.toDomain()

        assertEquals(ScanStatus.ERROR, roundTripped.status)
        assertEquals("Rate limit exceeded", roundTripped.lastError)
        assertEquals(false, roundTripped.hasMoreHistory)
    }

    @Test
    fun `ObxChatScanState default values are correct`() {
        val obx = ObxChatScanState()

        assertEquals(0L, obx.id)
        assertEquals(0L, obx.chatId)
        assertEquals(0L, obx.lastScannedMessageId)
        assertEquals(true, obx.hasMoreHistory)
        assertEquals("IDLE", obx.status)
        assertNull(obx.lastError)
        assertEquals(0L, obx.updatedAt)
    }

    @Test
    fun `ChatScanState default values are correct`() {
        val state = ChatScanState(chatId = -1001234567890L)

        assertEquals(-1001234567890L, state.chatId)
        assertEquals(0L, state.lastScannedMessageId)
        assertEquals(true, state.hasMoreHistory)
        assertEquals(ScanStatus.IDLE, state.status)
        assertNull(state.lastError)
        assertEquals(0L, state.updatedAt)
    }

    @Test
    fun `ScanStatus enum has expected values`() {
        val values = ScanStatus.entries.map { it.name }

        assertEquals(3, values.size)
        assert(values.contains("IDLE"))
        assert(values.contains("SCANNING"))
        assert(values.contains("ERROR"))
    }

    @Test
    fun `unknown status falls back to IDLE`() {
        val obx =
            ObxChatScanState(
                chatId = -1001234567890L,
                status = "UNKNOWN_STATUS",
            )

        val domain = obx.toDomain()
        assertEquals(ScanStatus.IDLE, domain.status)
    }

    @Test
    fun `TelegramSyncStateRepository class exists and has expected methods`() {
        val clazz = TelegramSyncStateRepository::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("observeScanStates")) {
            "TelegramSyncStateRepository should have observeScanStates method"
        }
        assert(methods.contains("updateScanState")) {
            "TelegramSyncStateRepository should have updateScanState method"
        }
        assert(methods.contains("getScanState")) {
            "TelegramSyncStateRepository should have getScanState method"
        }
        assert(methods.contains("clearScanState")) {
            "TelegramSyncStateRepository should have clearScanState method"
        }
        assert(methods.contains("clearAllScanStates")) {
            "TelegramSyncStateRepository should have clearAllScanStates method"
        }
    }

    @Test
    fun `observeScanStates return type is Flow`() {
        val clazz = TelegramSyncStateRepository::class
        val method = clazz.java.methods.find { it.name == "observeScanStates" }

        assertNotNull(method)
        assert(method!!.returnType.name.contains("Flow")) {
            "observeScanStates should return Flow type"
        }
    }
}
