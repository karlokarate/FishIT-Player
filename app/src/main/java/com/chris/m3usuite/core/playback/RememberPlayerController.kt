package com.chris.m3usuite.core.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.exoplayer.ExoPlayer

/**
 * Stub interface for player lifecycle management.
 * TODO: Real implementation in Phase 7 (PlaybackSession & MiniPlayer integration)
 *
 * This is a minimal stub to allow the SIP modules to compile as reference implementations.
 */
interface RememberPlayerController {
    /**
     * Attaches the ExoPlayer instance to this controller.
     * In a full implementation, this would manage player lifecycle,
     * mini-player state, and PiP coordination.
     */
    fun attachPlayer(player: ExoPlayer)
    
    /**
     * Detaches the current player instance.
     */
    fun detachPlayer()
}

/**
 * Stub implementation of RememberPlayerController.
 * TODO: Real implementation in Phase 7
 */
private class StubRememberPlayerController : RememberPlayerController {
    private var currentPlayer: ExoPlayer? = null
    
    override fun attachPlayer(player: ExoPlayer) {
        currentPlayer = player
    }
    
    override fun detachPlayer() {
        currentPlayer = null
    }
}

/**
 * Remember a RememberPlayerController instance.
 * TODO: Real implementation in Phase 7 should coordinate with PlaybackSession
 */
@Composable
fun rememberPlayerController(): RememberPlayerController {
    return remember { StubRememberPlayerController() }
}
