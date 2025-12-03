package com.chris.m3usuite.telegram.domain

import com.chris.m3usuite.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Provider for Telegram streaming runtime settings.
 *
 * Combines individual SettingsRepository flows into a single StateFlow<TelegramStreamingSettings>.
 * This avoids leaking DataStore concerns into engine code and provides a clean domain boundary.
 *
 * All unit conversions (KB→Bytes, sec→ms) happen here, so downstream consumers receive
 * engine-ready values.
 *
 * Usage:
 * ```
 * val settingsProvider = TelegramStreamingSettingsProvider(settingsRepository)
 *
 * // Reactive access
 * settingsProvider.settings.collect { settings ->
 *     // settings.maxGlobalDownloads, etc.
 * }
 *
 * // Synchronous access
 * val current = settingsProvider.currentSettings
 * ```
 */
class TelegramStreamingSettingsProvider(
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Reactive StateFlow of current settings.
     * Automatically updates when any underlying setting changes.
     */
    val settings: StateFlow<TelegramStreamingSettings> =
        combine(
            // TDLib native log level (0-5, passed directly to TDLib)
            settingsRepository.tgLogVerbosity,
            // Download concurrency
            settingsRepository.tgMaxGlobalDownloads,
            settingsRepository.tgMaxVideoDownloads,
            settingsRepository.tgMaxThumbDownloads,
            // Streaming parameters (already in bytes/ms)
            settingsRepository.tgInitialPrefixBytes,
            settingsRepository.tgSeekMarginBytes,
            settingsRepository.tgEnsureFileReadyTimeoutMs,
            // Thumbnail prefetch
            settingsRepository.tgThumbPrefetchEnabled,
            settingsRepository.tgThumbPrefetchBatchSize,
            settingsRepository.tgThumbMaxParallel,
            settingsRepository.tgThumbPauseWhileVodBuffering,
            settingsRepository.tgThumbFullDownload,
            // ExoPlayer buffers (already in ms)
            settingsRepository.exoMinBufferMs,
            settingsRepository.exoMaxBufferMs,
            settingsRepository.exoBufferForPlaybackMs,
            settingsRepository.exoBufferForPlaybackAfterRebufferMs,
            settingsRepository.exoExactSeek,
            // Overlays
            settingsRepository.tgShowEngineOverlay,
            settingsRepository.tgShowStreamingOverlay,
            // App-side logging (0=ERROR, 1=WARN, 2=INFO, 3=DEBUG)
            settingsRepository.tgAppLogLevel,
            // Jank telemetry
            settingsRepository.jankTelemetrySampleRate,
        ) { values ->
            TelegramStreamingSettings(
                tdlibLogLevel = values[0] as Int,
                maxGlobalDownloads = values[1] as Int,
                maxVideoDownloads = values[2] as Int,
                maxThumbDownloads = values[3] as Int,
                initialMinPrefixBytes = values[4] as Long,
                seekMarginBytes = values[5] as Long,
                ensureFileReadyTimeoutMs = values[6] as Long,
                thumbPrefetchEnabled = values[7] as Boolean,
                thumbPrefetchBatchSize = values[8] as Int,
                thumbMaxParallel = values[9] as Int,
                thumbPauseWhileVodBuffering = values[10] as Boolean,
                thumbFullDownload = values[11] as Boolean,
                exoMinBufferMs = values[12] as Int,
                exoMaxBufferMs = values[13] as Int,
                exoBufferForPlaybackMs = values[14] as Int,
                exoBufferForPlaybackAfterRebufferMs = values[15] as Int,
                exoExactSeek = values[16] as Boolean,
                showEngineOverlay = values[17] as Boolean,
                showStreamingOverlay = values[18] as Boolean,
                tgAppLogLevel = values[19] as Int,
                jankTelemetrySampleRate = values[20] as Int,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue =
                TelegramStreamingSettings(
                    tdlibLogLevel = 1, // Default TDLib log level (WARN equivalent)
                    maxGlobalDownloads = 5,
                    maxVideoDownloads = 2,
                    maxThumbDownloads = 3,
                    initialMinPrefixBytes = 256L * 1024L, // 256 KB
                    seekMarginBytes = 1024L * 1024L, // 1 MB
                    ensureFileReadyTimeoutMs = 10_000L, // 10 seconds
                    thumbPrefetchEnabled = true,
                    thumbPrefetchBatchSize = 8,
                    thumbMaxParallel = 2,
                    thumbPauseWhileVodBuffering = true,
                    thumbFullDownload = true,
                    exoMinBufferMs = 50_000, // 50 seconds
                    exoMaxBufferMs = 50_000, // 50 seconds
                    exoBufferForPlaybackMs = 2_500, // 2.5 seconds
                    exoBufferForPlaybackAfterRebufferMs = 5_000, // 5 seconds
                    exoExactSeek = true,
                    showEngineOverlay = false,
                    showStreamingOverlay = false,
                    tgAppLogLevel = 1, // WARN by default for app-side logs
                    jankTelemetrySampleRate = 10, // Log every 10th event
                ),
        )

    /**
     * Synchronous accessor for current settings.
     * Useful when you need immediate access without suspending.
     */
    val currentSettings: TelegramStreamingSettings
        get() = settings.value
}
