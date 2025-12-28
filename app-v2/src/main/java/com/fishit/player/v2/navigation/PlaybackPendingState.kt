package com.fishit.player.v2.navigation

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.feature.detail.UnifiedDetailEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds pending playback request between Detail and Player screens.
 *
 * This is a navigation-scoped state holder that allows the Detail screen to pass
 * full playback context to the Player screen without serializing everything into URL parameters.
 *
 * **Flow:**
 * 1. DetailScreen emits [UnifiedDetailEvent.StartPlayback]
 * 2. AppNavHost calls [setPendingPlayback] with the event data
 * 3. AppNavHost navigates to Player route
 * 4. PlayerNavScreen calls [consumePendingPlayback] to get full context
 * 5. State is cleared after consumption (one-shot pattern)
 *
 * **Why not URL parameters?**
 * - MediaSourceRef contains complex nested data (quality, format, languages)
 * - Resume position needs to be exact (not approximated from percentage)
 * - Avoids URL encoding/decoding issues
 *
 * **Thread Safety:**
 * - Uses StateFlow for thread-safe state management
 * - Consumption is atomic (swap with null)
 */
@Singleton
class PlaybackPendingState
    @Inject
    constructor() {
        private val _pending = MutableStateFlow<PendingPlayback?>(null)
        val pending: StateFlow<PendingPlayback?> = _pending.asStateFlow()

        /**
         * Sets the pending playback request.
         *
         * Called by navigation layer when DetailScreen emits StartPlayback.
         */
        fun setPendingPlayback(
            canonicalId: CanonicalMediaId,
            source: MediaSourceRef,
            resumePositionMs: Long,
            title: String? = null,
            posterUrl: String? = null,
        ) {
            _pending.value =
                PendingPlayback(
                    canonicalId = canonicalId,
                    source = source,
                    resumePositionMs = resumePositionMs,
                    title = title,
                    posterUrl = posterUrl,
                )
        }

        /**
         * Sets the pending playback from a StartPlayback event.
         */
        fun setPendingPlayback(
            event: UnifiedDetailEvent.StartPlayback,
            title: String? = null,
            posterUrl: String? = null,
        ) {
            setPendingPlayback(
                canonicalId = event.canonicalId,
                source = event.source,
                resumePositionMs = event.resumePositionMs,
                title = title,
                posterUrl = posterUrl,
            )
        }

        /**
         * Consumes and clears the pending playback.
         *
         * Called by PlayerNavScreen/PlayerNavViewModel to retrieve the full context.
         * Returns null if no pending playback exists (fallback to URL params).
         */
        fun consumePendingPlayback(): PendingPlayback? {
            val current = _pending.value
            _pending.value = null
            return current
        }

        /**
         * Checks if there's a pending playback without consuming it.
         */
        fun hasPendingPlayback(): Boolean = _pending.value != null

        /**
         * Clears any pending playback (e.g., on navigation back).
         */
        fun clearPending() {
            _pending.value = null
        }
    }

/**
 * Pending playback data transferred between Detail and Player screens.
 */
data class PendingPlayback(
    val canonicalId: CanonicalMediaId,
    val source: MediaSourceRef,
    val resumePositionMs: Long,
    val title: String?,
    val posterUrl: String?,
) {
    /**
     * Whether this has a significant resume position (>1 second).
     */
    val hasResume: Boolean
        get() = resumePositionMs > 1000
}
