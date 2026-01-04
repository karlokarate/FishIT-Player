package com.fishit.player.miniplayer

import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates player session with MiniPlayer transitions.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 5 – MiniPlayer Coordinator
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This class provides high-level coordination between full player and MiniPlayer:
 * - Transition from full player to MiniPlayer overlay
 * - Transition from MiniPlayer back to full player
 * - State synchronization
 *
 * **Key Principles:**
 * - Does NOT own the player session (that's player:internal's responsibility)
 * - Does NOT handle navigation (that's the UI layer's responsibility)
 * - Only coordinates state transitions
 */
@Singleton
class MiniPlayerCoordinator
    @Inject
    constructor(
        private val miniPlayerManager: MiniPlayerManager,
    ) {
        companion object {
            private const val TAG = "MiniPlayerCoordinator"
        }

        /**
         * Request transition to MiniPlayer from full player.
         *
         * Call this when the user taps the "minimize" or "PiP" button on the full player.
         *
         * @param currentRoute The current navigation route (where to return when expanding)
         * @param mediaId Optional media ID for scroll restoration
         * @param rowIndex Optional row index for list position restoration
         * @param itemIndex Optional item index within row for focus restoration
         * @return True if transition was initiated
         */
        fun transitionToMiniPlayer(
            currentRoute: String,
            mediaId: Long? = null,
            rowIndex: Int? = null,
            itemIndex: Int? = null,
        ): Boolean {
            UnifiedLog.d(TAG, "Transitioning to MiniPlayer from route: $currentRoute")

            miniPlayerManager.enterMiniPlayer(
                fromRoute = currentRoute,
                mediaId = mediaId,
                rowIndex = rowIndex,
                itemIndex = itemIndex,
            )

            return true
        }

        /**
         * Request transition from MiniPlayer to full player.
         *
         * Call this when the user taps the MiniPlayer overlay to expand.
         *
         * @param navigateToPlayer Callback to navigate to full player screen
         * @return True if transition was initiated
         */
        fun transitionToFullPlayer(navigateToPlayer: () -> Unit): Boolean {
            val currentState = miniPlayerManager.state.value

            if (!currentState.visible) {
                UnifiedLog.w(TAG, "Cannot expand: MiniPlayer not visible")
                return false
            }

            UnifiedLog.d(TAG, "Transitioning to full player")

            // Exit MiniPlayer and trigger navigation
            miniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)
            navigateToPlayer()

            return true
        }

        /**
         * Dismiss the MiniPlayer without returning to full player.
         *
         * Call this when the user swipes away or closes the MiniPlayer.
         *
         * @param stopPlayback Whether to also stop playback
         * @param onStopPlayback Callback to stop the player session
         */
        fun dismissMiniPlayer(
            stopPlayback: Boolean = false,
            onStopPlayback: () -> Unit = {},
        ) {
            UnifiedLog.d(TAG, "Dismissing MiniPlayer, stopPlayback=$stopPlayback")

            miniPlayerManager.exitMiniPlayer(returnToFullPlayer = false)

            if (stopPlayback) {
                onStopPlayback()
            }
        }

        /**
         * Reset all MiniPlayer state.
         *
         * Call this when playback ends or when the user exits the player completely.
         */
        fun reset() {
            UnifiedLog.d(TAG, "Resetting MiniPlayer state")
            miniPlayerManager.reset()
        }

        /**
         * Get the return route for restoring navigation state.
         */
        fun getReturnRoute(): String? = miniPlayerManager.state.value.returnRoute

        /**
         * Get the return media ID for scroll restoration.
         */
        fun getReturnMediaId(): Long? = miniPlayerManager.state.value.returnMediaId

        /**
         * Check if MiniPlayer is currently visible.
         */
        val isMiniPlayerVisible: Boolean
            get() = miniPlayerManager.state.value.visible
    }
