package com.fishit.player.pipeline.telegram

import com.fishit.player.pipeline.telegram.streaming.TelegramStreamingSettingsProviderStub
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for TelegramStreamingSettingsProviderStub.
 */
class TelegramStreamingSettingsProviderStubTest {
    private val provider = TelegramStreamingSettingsProviderStub()

    @Test
    fun `getStreamingWindowSize returns 16MB`() {
        val windowSize = provider.getStreamingWindowSize()
        assertEquals(16 * 1024 * 1024L, windowSize)
    }

    @Test
    fun `isStreamingPreferred returns true`() {
        val preferred = provider.isStreamingPreferred()
        assertTrue(preferred)
    }

    @Test
    fun `getMinBufferMs returns 2000`() {
        val minBuffer = provider.getMinBufferMs()
        assertEquals(2000, minBuffer)
    }

    @Test
    fun `getMaxBufferMs returns 10000`() {
        val maxBuffer = provider.getMaxBufferMs()
        assertEquals(10000, maxBuffer)
    }

    @Test
    fun `shouldContinueOnNetworkLoss returns true`() {
        val shouldContinue = provider.shouldContinueOnNetworkLoss()
        assertTrue(shouldContinue)
    }

    @Test
    fun `getQualityPreference returns auto`() {
        val quality = provider.getQualityPreference()
        assertEquals("auto", quality)
    }
}
