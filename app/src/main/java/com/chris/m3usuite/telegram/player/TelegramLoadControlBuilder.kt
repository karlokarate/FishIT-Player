package com.chris.m3usuite.telegram.player

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import com.chris.m3usuite.telegram.domain.TelegramStreamingSettings

/**
 * Phase 5: Builder for ExoPlayer LoadControl with runtime Telegram streaming settings.
 *
 * Creates a LoadControl instance configured with:
 * - Buffer sizes (min, max, playback, rebuffer) from runtime settings
 * - Optimized for Telegram streaming with appropriate priorities
 *
 * **Usage:**
 * ```kotlin
 * val settings = settingsProvider.currentSettings
 * val loadControl = buildTelegramLoadControl(settings)
 * val player = ExoPlayer.Builder(context)
 *     .setLoadControl(loadControl)
 *     .build()
 * ```
 *
 * **Phase 5b TODO:**
 * Runtime LoadControl hot-swapping is not yet implemented.
 * Currently, LoadControl must be set during player creation and cannot be changed dynamically.
 * Future implementation will require player recreation or ExoPlayer API enhancements.
 *
 * @param settings Runtime streaming settings with buffer configuration
 * @return Configured LoadControl instance for ExoPlayer
 */
fun buildTelegramLoadControl(settings: TelegramStreamingSettings): LoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            minBufferMs = settings.exoMinBufferMs,
            maxBufferMs = settings.exoMaxBufferMs,
            bufferForPlaybackMs = settings.exoBufferForPlaybackMs,
            bufferForPlaybackAfterRebufferMs = settings.exoBufferForPlaybackAfterRebufferMs,
        )
        // Use default allocator and back buffer settings
        .build()
}
