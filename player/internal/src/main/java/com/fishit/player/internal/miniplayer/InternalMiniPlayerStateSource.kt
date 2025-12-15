package com.fishit.player.internal.miniplayer

import com.fishit.player.core.playermodel.PlaybackState
import com.fishit.player.internal.InternalPlayerEntryImpl
import com.fishit.player.ui.api.MiniPlayerStateSnapshot
import com.fishit.player.ui.api.MiniPlayerStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [MiniPlayerStateSource] that maps internal player state
 * to the public mini-player state contract.
 *
 * This adapter ensures that mini-player only depends on the stable public API
 * and not on internal engine implementation details.
 */
@Singleton
class InternalMiniPlayerStateSource @Inject constructor(
    private val playerEntry: InternalPlayerEntryImpl,
) : MiniPlayerStateSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(
        MiniPlayerStateSnapshot(
            title = null,
            isPlaying = false,
            isBuffering = false,
            positionMs = 0L,
            durationMs = 0L,
            progress = 0f,
        )
    )

    override val state: StateFlow<MiniPlayerStateSnapshot> = _state.asStateFlow()

    init {
        // Observe internal player state and map to public snapshot
        scope.launch {
            // Access the current session's state flow
            // Note: We need to handle session changes
            observePlayerState()
        }
    }

    private suspend fun observePlayerState() {
        // For now, emit a default state
        // In a real implementation, we'd observe the current session
        // This is a simplified version that provides the contract
        
        // TODO: Properly observe InternalPlayerSession.state when session is active
        // For now, just maintain the initial state
    }

    /**
     * Update state from internal player state.
     * This should be called by the session when state changes.
     */
    fun updateFromInternalState(
        title: String?,
        isPlaying: Boolean,
        playbackState: PlaybackState,
        positionMs: Long,
        durationMs: Long,
        progress: Float,
    ) {
        _state.value = MiniPlayerStateSnapshot(
            title = title,
            isPlaying = isPlaying,
            isBuffering = playbackState == PlaybackState.BUFFERING,
            positionMs = positionMs,
            durationMs = durationMs,
            progress = progress,
        )
    }
}
