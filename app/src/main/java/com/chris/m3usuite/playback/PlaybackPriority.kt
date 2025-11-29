package com.chris.m3usuite.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Global helper for playback-aware resource scheduling.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 3: Playback-Aware Worker Scheduling
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This object provides a single, read-only flow that workers can observe to
 * determine whether playback is currently active. When active, workers should
 * throttle their heavy operations to avoid impacting playback performance.
 *
 * **Usage in Workers:**
 * ```kotlin
 * if (PlaybackPriority.isPlaybackActive.value) {
 *     delay(PlaybackPriority.PLAYBACK_THROTTLE_MS)
 * }
 * ```
 *
 * **Definition of "Active":**
 * - `PlaybackSession.isPlaying.value == true` AND
 * - `PlaybackSession.lifecycleState.value` is in {PLAYING, PAUSED, BACKGROUND}
 *
 * Note: PAUSED and BACKGROUND are included because the user may resume
 * playback at any moment, and heavy background work could cause stuttering.
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 7
 * - INTERNAL_PLAYER_PHASE8_CHECKLIST.md Group 5
 */
object PlaybackPriority {

    /**
     * Throttle delay in milliseconds for workers when playback is active.
     * Workers should add this delay between heavy operations.
     */
    const val PLAYBACK_THROTTLE_MS = 500L

    /**
     * Lifecycle states considered "active" for playback priority.
     * Workers should throttle when playback is in any of these states.
     */
    private val activeLifecycleStates = setOf(
        SessionLifecycleState.PLAYING,
        SessionLifecycleState.PAUSED,
        SessionLifecycleState.BACKGROUND,
    )

    /**
     * Internal coroutine scope for the combined flow.
     * Uses SupervisorJob to ensure independent lifecycle from callers.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Flow indicating whether playback is currently active.
     *
     * Workers should check this value before heavy operations and add
     * appropriate delays when true to avoid impacting playback quality.
     *
     * **Definition:**
     * - Returns `true` when:
     *   - `PlaybackSession.isPlaying == true` AND
     *   - `PlaybackSession.lifecycleState` in {PLAYING, PAUSED, BACKGROUND}
     * - Returns `false` otherwise
     *
     * **Thread Safety:**
     * - This is a StateFlow, safe to read from any thread
     * - Value updates are atomic
     */
    val isPlaybackActive: StateFlow<Boolean> = combine(
        PlaybackSession.isPlaying,
        PlaybackSession.lifecycleState,
    ) { isPlaying, lifecycleState ->
        isPlaying && lifecycleState in activeLifecycleStates
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    /**
     * Convenience method to check if throttling should be applied.
     * Equivalent to checking `isPlaybackActive.value`.
     *
     * @return true if workers should throttle their operations
     */
    fun shouldThrottle(): Boolean = isPlaybackActive.value
}
