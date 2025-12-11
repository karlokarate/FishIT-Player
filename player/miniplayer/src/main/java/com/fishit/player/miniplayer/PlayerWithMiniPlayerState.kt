package com.fishit.player.miniplayer

import com.fishit.player.internal.session.InternalPlayerSession
import com.fishit.player.internal.state.InternalPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Combined state for player session with MiniPlayer overlay.
 *
 * ════════════════════════════════════════════════════════════════════════════════ PHASE 5 –
 * MiniPlayer Integration
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This data class combines the internal player state with MiniPlayer visibility for use in UI
 * layers that need to coordinate both.
 */
data class PlayerWithMiniPlayerState(
        /** Full player state from InternalPlayerSession */
        val playerState: InternalPlayerState,
        /** MiniPlayer overlay state */
        val miniPlayerState: MiniPlayerState
) {
    /** Whether the MiniPlayer overlay is currently visible */
    val isMiniPlayerVisible: Boolean
        get() = miniPlayerState.visible

    /** Whether the player is in MiniPlayer resize mode */
    val isMiniPlayerResizeMode: Boolean
        get() = miniPlayerState.mode == MiniPlayerMode.RESIZE

    /** Whether something is playing (either full or mini) */
    val isPlaybackActive: Boolean
        get() = playerState.isPlaying || playerState.positionMs > 0

    companion object {
        val INITIAL =
                PlayerWithMiniPlayerState(
                        playerState = InternalPlayerState.INITIAL,
                        miniPlayerState = MiniPlayerState.INITIAL
                )
    }
}

/**
 * Creates a combined StateFlow of player state and MiniPlayer state.
 *
 * This is useful for UI components that need to observe both states together.
 *
 * @param miniPlayerManager The MiniPlayer manager to observe
 * @param scope Coroutine scope for the combined flow
 * @return Combined StateFlow
 */
fun InternalPlayerSession.withMiniPlayer(
        miniPlayerManager: MiniPlayerManager,
        scope: CoroutineScope
): StateFlow<PlayerWithMiniPlayerState> {
    return combine(this.state, miniPlayerManager.state) { playerState, miniState ->
                PlayerWithMiniPlayerState(playerState, miniState)
            }
            .stateIn(
                    scope = scope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = PlayerWithMiniPlayerState.INITIAL
            )
}
