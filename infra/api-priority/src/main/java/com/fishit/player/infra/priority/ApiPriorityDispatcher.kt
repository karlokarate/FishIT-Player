package com.fishit.player.infra.priority

import kotlinx.coroutines.flow.StateFlow

/**
 * Dispatcher for prioritizing API calls in FishIT Player.
 *
 * **Purpose:**
 * Ensures user-initiated actions (detail fetch, playback) have priority over background
 * operations (catalog sync). When a user clicks a tile, the detail API call should
 * execute immediately, even if catalog sync is running.
 *
 * **Usage Pattern:**
 *
 * ```kotlin
 * // In DetailEnrichmentServiceImpl:
 * override suspend fun enrichImmediate(media: CanonicalMediaWithSources) =
 *     priorityDispatcher.withHighPriority("DetailEnrich:${media.canonicalId}") {
 *         enrichFromXtream(media)
 *     }
 *
 * // In XtreamCatalogScanWorker:
 * items.chunked(BATCH_SIZE).forEach { batch ->
 *     if (priorityDispatcher.shouldYield()) {
 *         yield() // Let high-priority calls execute
 *     }
 *     processBatch(batch)
 * }
 * ```
 *
 * **Architecture:**
 * - Singleton scoped (one instance per app process)
 * - Uses Semaphore internally for coordination
 * - Background operations check [shouldYield] and cooperatively pause
 *
 * @see ApiPriority
 * @see PriorityState
 */
interface ApiPriorityDispatcher {
    /**
     * Current priority state as a hot StateFlow.
     *
     * Workers and UI can observe this to react to priority changes.
     */
    val priorityState: StateFlow<PriorityState>

    /**
     * Execute a high-priority API call (user action).
     *
     * While this call is active:
     * - [shouldYield] returns true
     * - [priorityState] reflects the active high-priority operation
     * - Background workers should pause at checkpoints
     *
     * @param tag Identifying tag for logging (e.g., "DetailEnrich:movie:123")
     * @param block The suspending API call to execute
     * @return Result of the API call
     */
    suspend fun <T> withHighPriority(
        tag: String,
        block: suspend () -> T,
    ): T

    /**
     * Execute a critical playback call with guaranteed execution slot.
     *
     * This is the highest priority level, used when the user presses Play.
     * The call will execute even if other high-priority calls are pending.
     *
     * @param tag Identifying tag for logging (e.g., "EnsureEnriched:movie:123")
     * @param timeoutMs Maximum time to wait for the operation (default: 8000ms)
     * @param block The suspending API call to execute
     * @return Result of the API call, or null if timeout exceeded
     */
    suspend fun <T> withCriticalPriority(
        tag: String,
        timeoutMs: Long = DEFAULT_CRITICAL_TIMEOUT_MS,
        block: suspend () -> T,
    ): T?

    /**
     * Execute background work, yielding to higher priorities.
     *
     * The block will execute normally, but [shouldYield] can be checked
     * within the block to cooperatively yield when high-priority calls arrive.
     *
     * @param tag Identifying tag for logging (e.g., "CatalogSync:xtream")
     * @param block The suspending work to execute
     * @return Result of the work
     */
    suspend fun <T> withBackgroundPriority(
        tag: String,
        block: suspend () -> T,
    ): T

    /**
     * Check if background operations should yield to higher priorities.
     *
     * Call this at batch boundaries in workers:
     * ```kotlin
     * items.chunked(100).forEach { batch ->
     *     if (priorityDispatcher.shouldYield()) {
     *         yield() // Coroutine checkpoint
     *     }
     *     processBatch(batch)
     * }
     * ```
     *
     * @return True if high-priority operations are pending/active
     */
    fun shouldYield(): Boolean

    /**
     * Await until high-priority operations complete.
     *
     * Use in workers that need to wait for user actions to finish:
     * ```kotlin
     * if (priorityDispatcher.shouldYield()) {
     *     priorityDispatcher.awaitHighPriorityComplete()
     *     // Continue after user action completes
     * }
     * ```
     */
    suspend fun awaitHighPriorityComplete()

    companion object {
        /** Default timeout for critical priority operations (8 seconds) */
        const val DEFAULT_CRITICAL_TIMEOUT_MS = 8000L
    }
}
