package com.chris.m3usuite.telegram.domain

/**
 * Domain-level configuration object for Telegram streaming engine.
 *
 * This data class consolidates all runtime settings needed by:
 * - T_TelegramFileDownloader (concurrency, prefix, margins, timeouts)
 * - Thumbnail prefetch logic
 * - ExoPlayer LoadControl
 * - Diagnostic overlays
 * - App-side logging
 *
 * All values are already converted to engine-ready units (bytes, milliseconds).
 * No DataStore or UI concerns leak into this layer.
 */
data class TelegramStreamingSettings(
    // TDLib log level (0-5, passed directly to TDLib)
    val tdlibLogLevel: Int,
    // Download concurrency limits
    val maxGlobalDownloads: Int,
    val maxVideoDownloads: Int,
    val maxThumbDownloads: Int,
    // ensureFileReady() streaming parameters (in bytes)
    val initialMinPrefixBytes: Long,
    val seekMarginBytes: Long,
    val ensureFileReadyTimeoutMs: Long,
    // Thumbnail prefetch settings
    val thumbPrefetchEnabled: Boolean,
    val thumbPrefetchBatchSize: Int,
    val thumbMaxParallel: Int,
    val thumbPauseWhileVodBuffering: Boolean,
    val thumbFullDownload: Boolean,
    // ExoPlayer buffer settings (in milliseconds)
    val exoMinBufferMs: Int,
    val exoMaxBufferMs: Int,
    val exoBufferForPlaybackMs: Int,
    val exoBufferForPlaybackAfterRebufferMs: Int,
    val exoExactSeek: Boolean,
    // Diagnostic overlays
    val showEngineOverlay: Boolean,
    val showStreamingOverlay: Boolean,
    // App-side logging
    val tgAppLogLevel: Int, // 0=ERROR, 1=WARN, 2=INFO, 3=DEBUG
    // Jank telemetry sampling
    val jankTelemetrySampleRate: Int, // Log every Nth frame
)
