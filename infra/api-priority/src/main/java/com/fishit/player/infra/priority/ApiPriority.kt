package com.fishit.player.infra.priority

/**
 * API call priority levels for the FishIT Player.
 *
 * Priority determines execution order and whether background operations should yield.
 *
 * **Priority Hierarchy (highest to lowest):**
 * 1. CRITICAL_PLAYBACK - Play button pressed, blocking until resolved
 * 2. HIGH_USER_ACTION - User clicked tile → Detail screen
 * 3. BACKGROUND_SYNC - Catalog sync workers (yields to higher priorities)
 */
enum class ApiPriority {
    /**
     * Highest priority: Playback-critical calls (ensureEnriched before Play).
     * - Blocks until complete or timeout
     * - Other operations must yield
     */
    CRITICAL_PLAYBACK,

    /**
     * High priority: User-initiated detail fetch.
     * - Triggered when user clicks a tile on HomeScreen/LibraryScreen
     * - Background sync should pause/yield
     */
    HIGH_USER_ACTION,

    /**
     * Lowest priority: Background catalog synchronization.
     * - WorkManager-driven catalog updates
     * - Should yield to higher priority calls
     */
    BACKGROUND_SYNC,
}

/**
 * Current state of the priority system.
 *
 * Exposed as StateFlow for UI indicators and decision-making in workers.
 */
data class PriorityState(
    /** Number of active HIGH_USER_ACTION calls */
    val activeHighPriorityCalls: Int = 0,

    /** Number of active CRITICAL_PLAYBACK calls */
    val activeCriticalCalls: Int = 0,

    /** True if background operations should suspend/yield */
    val backgroundSuspended: Boolean = false,

    /** Tag of the current high-priority operation (for logging/debugging) */
    val currentOperation: String? = null,
) {
    /** True if any high-priority operation is active */
    val hasActiveHighPriority: Boolean
        get() = activeHighPriorityCalls > 0 || activeCriticalCalls > 0
}
