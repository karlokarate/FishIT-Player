package com.fishit.player.tools.cli

import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter

/**
 * CLI Pipelines container - lightweight alternative to core:app-startup.Pipelines.
 *
 * This is tool-owned and decoupled from app orchestration.
 */
data class CliPipelines(
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
