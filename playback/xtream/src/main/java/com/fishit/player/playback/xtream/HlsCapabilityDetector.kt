package com.fishit.player.playback.xtream

import com.fishit.player.infra.logging.UnifiedLog

/**
 * Detects whether HLS media source support is available at runtime.
 *
 * This checks if the Media3 HLS module (media3-exoplayer-hls) is present in the build
 * by attempting to load the HlsMediaSource class via reflection.
 *
 * **Architecture:**
 * - Used to enable automatic fallback to TS when HLS is unavailable
 * - Result is cached after first check for performance
 * - Thread-safe via lazy initialization
 *
 * **Usage:**
 * ```kotlin
 * if (HlsCapabilityDetector.isHlsAvailable()) {
 *     // Use m3u8 format
 * } else {
 *     // Fallback to ts format
 * }
 * ```
 */
object HlsCapabilityDetector {
    private const val TAG = "HlsCapabilityDetector"
    private const val HLS_MEDIA_SOURCE_CLASS = "androidx.media3.exoplayer.hls.HlsMediaSource"

    /**
     * Cached result of HLS capability detection.
     * Lazy-initialized on first access for thread-safe, single check.
     */
    private val hlsAvailable: Boolean by lazy {
        detectHlsCapability()
    }

    /**
     * Check if HLS support is available in the current build.
     *
     * @return true if HLS module is present, false otherwise
     */
    fun isHlsAvailable(): Boolean = hlsAvailable

    /**
     * Performs the actual HLS capability detection via reflection.
     *
     * Attempts to load the HlsMediaSource class. If successful, HLS is available.
     * Logs the result once for debugging purposes.
     */
    private fun detectHlsCapability(): Boolean {
        return try {
            Class.forName(HLS_MEDIA_SOURCE_CLASS)
            UnifiedLog.i(TAG) { "HLS module detected: media3-exoplayer-hls is present" }
            true
        } catch (e: ClassNotFoundException) {
            UnifiedLog.w(TAG) { "HLS module not found: media3-exoplayer-hls is not present in build" }
            false
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Unexpected error detecting HLS capability: ${e.message}" }
            false
        }
    }
}
