package com.fishit.player.pipeline.telegram.streaming

/**
 * Stub implementation of TelegramStreamingSettingsProvider for Phase 2.
 *
 * Returns sensible default values based on tdlibAgent.md specifications.
 */
class TelegramStreamingSettingsProviderStub : TelegramStreamingSettingsProvider {
    /**
     * Returns 16MB window size as per tdlibAgent.md Section 9.
     */
    override fun getStreamingWindowSize(): Long {
        return 16 * 1024 * 1024L // 16MB
    }

    /**
     * Returns true (streaming preferred) in stub phase.
     */
    override fun isStreamingPreferred(): Boolean = true

    /**
     * Returns 2 seconds minimum buffer.
     */
    override fun getMinBufferMs(): Int = 2000

    /**
     * Returns 10 seconds maximum buffer.
     */
    override fun getMaxBufferMs(): Int = 10000

    /**
     * Returns true (continue playback with buffered data).
     */
    override fun shouldContinueOnNetworkLoss(): Boolean = true

    /**
     * Returns "auto" quality preference.
     */
    override fun getQualityPreference(): String = "auto"
}
