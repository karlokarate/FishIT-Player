package com.fishit.player.pipeline.telegram.streaming

/**
 * Interface for providing Telegram streaming configuration.
 *
 * Phase 2: Stub interface only.
 * Phase 3+: Real implementation with user preferences and adaptive settings.
 *
 * This interface controls:
 * - Window size for zero-copy streaming
 * - Buffer configuration for Media3
 * - Download vs streaming preference
 * - Network-aware quality selection
 */
interface TelegramStreamingSettingsProvider {
    /**
     * Get the streaming window size in bytes.
     *
     * Default: 16MB (as per tdlibAgent.md Section 9 specifications)
     *
     * @return Window size in bytes
     */
    fun getStreamingWindowSize(): Long

    /**
     * Check if streaming is preferred over download.
     *
     * @return true if streaming is preferred, false if download-first
     */
    fun isStreamingPreferred(): Boolean

    /**
     * Get minimum buffer duration for playback start in milliseconds.
     *
     * @return Buffer duration in ms
     */
    fun getMinBufferMs(): Int

    /**
     * Get maximum buffer duration in milliseconds.
     *
     * @return Max buffer duration in ms
     */
    fun getMaxBufferMs(): Int

    /**
     * Check if playback should continue on network loss.
     *
     * @return true to continue with buffered data, false to pause
     */
    fun shouldContinueOnNetworkLoss(): Boolean

    /**
     * Get the quality preference (for future adaptive streaming).
     *
     * @return Quality level (e.g., "auto", "high", "medium", "low")
     */
    fun getQualityPreference(): String
}
