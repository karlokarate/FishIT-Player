package com.fishit.player.core.appstartup

import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter

/**
 * Shared pipeline startup interface.
 *
 * Used by both Android app and CLI to consistently initialize pipelines.
 * Ensures behavior parity between production app and test/debug tools.
 *
 * **Architecture:**
 * - Single point of pipeline initialization
 * - Handles transport client creation and wiring
 * - Returns ready-to-use pipeline adapters
 *
 * **Usage:**
 * ```kotlin
 * val startup = AppStartupImpl(...)
 * val pipelines = startup.startPipelines(config)
 *
 * // Use pipelines
 * pipelines.telegram?.mediaUpdates?.collect { ... }
 * pipelines.xtream?.loadVodItems()
 * ```
 */
interface AppStartup {
    /**
     * Initialize and start configured pipelines.
     *
     * @param config Configuration specifying which pipelines to enable
     * @return Initialized pipelines (null if not configured)
     */
    suspend fun startPipelines(config: AppStartupConfig): Pipelines
}

/**
 * Container for initialized pipeline adapters.
 *
 * @property telegram Telegram pipeline adapter (null if not configured)
 * @property xtream Xtream pipeline adapter (null if not configured)
 */
data class Pipelines(
    val telegram: TelegramPipelineAdapter?,
    val xtream: XtreamPipelineAdapter?,
) {
    /** Returns true if at least one pipeline is available. */
    val hasAnyPipeline: Boolean
        get() = telegram != null || xtream != null

    /** Returns true if Telegram pipeline is available. */
    val hasTelegram: Boolean
        get() = telegram != null

    /** Returns true if Xtream pipeline is available. */
    val hasXtream: Boolean
        get() = xtream != null
}
