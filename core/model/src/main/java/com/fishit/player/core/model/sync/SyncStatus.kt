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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * Represents the status of a catalog synchronization operation.
 *
 * This sealed interface provides a type-safe way to track sync progress
 * and results across all pipelines (Xtream, Telegram, IO, Audiobook).
 *
 * ## Design: Flow-Based Status Emission
 * Sync services emit `Flow<SyncStatus>` allowing UI/Workers to observe progress:
 * ```kotlin
 * syncService.sync(config)
 *     .onEach { status ->
 *         when (status) {
 *             is SyncStatus.InProgress -> updateProgressBar(status)
 *             is SyncStatus.Completed -> showSuccess(status)
 *             is SyncStatus.Error -> showError(status)
 *         }
 *     }
 *     .launchIn(scope)
 * ```
 *
 * ## Phase-Based Progress
 * Sync operations progress through [SyncPhase] stages, with each phase
 * potentially emitting multiple [InProgress] statuses.
 *
 * @since v2 Unified Sync Architecture
 */
sealed interface SyncStatus {
    /**
     * Source identifier for multi-source tracking.
     * E.g., "xtream", "telegram", "local"
     */
    val source: String

    /**
     * Sync operation has started.
     *
     * Emitted once at the beginning of sync.
     * Useful for initializing UI progress indicators.
     */
    data class Started(
        override val source: String,
        val accountKey: String,
        val isFullSync: Boolean,
        val estimatedPhases: List<SyncPhase> = emptyList(),
    ) : SyncStatus

    /**
     * Sync operation is in progress.
     *
     * Emitted multiple times during sync to report progress.
     * UI should update progress bars and phase indicators.
     */
    data class InProgress(
        override val source: String,
        val phase: SyncPhase,
        val processedItems: Int,
        val totalItems: Int?,
        val currentItemName: String? = null,
        val elapsedDuration: Duration = ZERO,
        val estimatedRemaining: Duration? = null,
    ) : SyncStatus {
        /**
         * Progress percentage (0.0 to 1.0), or null if total unknown.
         */
        val progressPercent: Float?
            get() =
                totalItems?.let { total ->
                    if (total > 0) processedItems.toFloat() / total else 0f
                }

        /**
         * Human-readable progress string.
         */
        val progressText: String
            get() =
                if (totalItems != null) {
                    "$processedItems / $totalItems"
                } else {
                    "$processedItems items"
                }
    }

    /**
     * A checkpoint was reached during sync.
     *
     * Emitted when sync state can be safely resumed from this point.
     * Used for incremental sync and crash recovery.
     */
    data class CheckpointReached(
        override val source: String,
        val checkpointId: String,
        val phase: SyncPhase,
        val processedItems: Int,
        val metadata: Map<String, Any> = emptyMap(),
    ) : SyncStatus

    /**
     * Sync operation completed successfully.
     *
     * Emitted once at the end of a successful sync.
     */
    data class Completed(
        override val source: String,
        val totalDuration: Duration,
        val itemCounts: ItemCounts,
        val wasIncremental: Boolean,
        val checkpointsSaved: Int = 0,
    ) : SyncStatus {
        /**
         * Item counts by category.
         */
        data class ItemCounts(
            val liveChannels: Int = 0,
            val vodMovies: Int = 0,
            val seriesShows: Int = 0,
            val seriesEpisodes: Int = 0,
            val audiobooks: Int = 0,
            val localFiles: Int = 0,
        ) {
            val total: Int
                get() = liveChannels + vodMovies + seriesShows + seriesEpisodes + audiobooks + localFiles

            /**
             * Items per second throughput.
             */
            fun throughput(duration: Duration): Double {
                val seconds = duration.inWholeMilliseconds / 1000.0
                return if (seconds > 0) total / seconds else 0.0
            }
        }
    }

    /**
     * Sync operation was cancelled.
     *
     * Emitted when sync is stopped before completion (user cancel, timeout, etc.)
     */
    data class Cancelled(
        override val source: String,
        val reason: CancelReason,
        val phase: SyncPhase,
        val processedItems: Int,
        val duration: Duration,
        val canResume: Boolean,
        val lastCheckpointId: String? = null,
    ) : SyncStatus {
        enum class CancelReason {
            USER_REQUESTED,
            TIMEOUT,
            MEMORY_PRESSURE,
            NETWORK_LOST,
            APP_BACKGROUNDED,
            WORKER_STOPPED,
        }
    }

    /**
     * Sync operation failed with an error.
     *
     * Emitted when sync encounters an unrecoverable error.
     */
    data class Error(
        override val source: String,
        val errorType: ErrorType,
        val message: String,
        val phase: SyncPhase,
        val processedItems: Int,
        val exception: Throwable? = null,
        val canRetry: Boolean = true,
        val retryAfter: Duration? = null,
    ) : SyncStatus {
        enum class ErrorType {
            NETWORK_ERROR,
            AUTH_FAILED,
            RATE_LIMITED,
            SERVER_ERROR,
            PARSE_ERROR,
            DATABASE_ERROR,
            OUT_OF_MEMORY,
            UNKNOWN,
        }
    }
}

// ============================================================================
// Extension Functions
// ============================================================================

/**
 * Returns true if this status represents a terminal state.
 */
val SyncStatus.isTerminal: Boolean
    get() =
        this is SyncStatus.Completed ||
            this is SyncStatus.Cancelled ||
            this is SyncStatus.Error

/**
 * Returns true if sync is still in progress (not terminal).
 */
val SyncStatus.isActive: Boolean
    get() = !isTerminal

/**
 * Returns the current phase, or null if not applicable.
 */
val SyncStatus.currentPhase: SyncPhase?
    get() =
        when (this) {
            is SyncStatus.Started -> SyncPhase.INITIALIZING
            is SyncStatus.InProgress -> phase
            is SyncStatus.CheckpointReached -> phase
            is SyncStatus.Completed -> SyncPhase.FINALIZING
            is SyncStatus.Cancelled -> phase
            is SyncStatus.Error -> phase
        }

/**
 * Returns processed item count, or 0 if not applicable.
 */
val SyncStatus.processedCount: Int
    get() =
        when (this) {
            is SyncStatus.Started -> 0
            is SyncStatus.InProgress -> processedItems
            is SyncStatus.CheckpointReached -> processedItems
            is SyncStatus.Completed -> itemCounts.total
            is SyncStatus.Cancelled -> processedItems
            is SyncStatus.Error -> processedItems
        }
