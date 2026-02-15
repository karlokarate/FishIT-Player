/*
 * Copyright (C) 2024-2026 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Module: core/model
 * Layer: 1 (Foundation - Zero Dependencies)
 *
 * This file is part of the unified sync architecture.
 * See: docs/v2/XTREAM_SYNC_REFACTORING_PLAN.md
 */
package com.fishit.player.core.model.sync

/**
 * Represents the current phase of a catalog synchronization operation.
 *
 * Phases are source-agnostic and can be used by any pipeline (Xtream, Telegram, IO, Audiobook).
 * The phase sequence may vary depending on the source and configuration.
 *
 * ## Usage
 * ```kotlin
 * val status = SyncStatus.InProgress(
 *     phase = SyncPhase.VOD_MOVIES,
 *     processedItems = 150,
 *     totalItems = 500,
 * )
 * ```
 *
 * @since v2 Unified Sync Architecture
 */
enum class SyncPhase {
    /**
     * Initial setup phase: validating credentials, loading configuration,
     * checking network connectivity, and preparing resources.
     */
    INITIALIZING,

    /**
     * Syncing live TV channels.
     * Typically fast due to smaller payloads per item.
     */
    LIVE_CHANNELS,

    /**
     * Syncing Video-on-Demand movies.
     * May include metadata enrichment via TMDB.
     */
    VOD_MOVIES,

    /**
     * Syncing series index (show metadata without episodes).
     * Lighter than full series sync.
     */
    SERIES_INDEX,

    /**
     * Syncing individual episodes for series.
     * Often the most data-intensive phase.
     */
    SERIES_EPISODES,

    /**
     * Syncing audiobook content (Telegram pipeline specific).
     */
    AUDIOBOOKS,

    /**
     * Syncing local file system content (IO pipeline specific).
     */
    LOCAL_FILES,

    /**
     * Final cleanup: persisting checkpoints, closing resources,
     * computing final statistics.
     */
    FINALIZING,

    /**
     * Sync was cancelled before completion.
     */
    CANCELLED,

    /**
     * Sync encountered an unrecoverable error.
     */
    ERROR,
    ;

    /**
     * Returns true if this phase represents a terminal state (cannot continue).
     */
    val isTerminal: Boolean
        get() = this == CANCELLED || this == ERROR || this == FINALIZING

    /**
     * Returns true if this phase is actively processing content.
     */
    val isProcessing: Boolean
        get() = this in setOf(LIVE_CHANNELS, VOD_MOVIES, SERIES_INDEX, SERIES_EPISODES, AUDIOBOOKS, LOCAL_FILES)

    companion object {
        /**
         * Standard Xtream sync phases in order of execution.
         */
        val XTREAM_STANDARD_ORDER: List<SyncPhase> =
            listOf(
                INITIALIZING,
                LIVE_CHANNELS,
                VOD_MOVIES,
                SERIES_INDEX,
                SERIES_EPISODES,
                FINALIZING,
            )

        /**
         * Telegram sync phases in order of execution.
         */
        val TELEGRAM_STANDARD_ORDER: List<SyncPhase> =
            listOf(
                INITIALIZING,
                VOD_MOVIES,
                AUDIOBOOKS,
                FINALIZING,
            )
    }
}
