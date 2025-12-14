package com.fishit.player.playback.domain

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fishit.player.core.playermodel.PlaybackContext

/**
 * Entry point for initiating playback in FishIT Player v2.
 *
 * This interface provides a clean abstraction for feature modules to start playback
 * without depending on concrete player implementations.
 *
 * Architecture:
 * - Interface lives in playback/domain (abstraction layer)
 * - Implementation lives in player/internal (concrete player)
 * - Feature modules depend ONLY on this interface, NOT on player:internal
 * - All engine wiring (resolver, resume, kids gate, codec) lives behind this abstraction
 */
interface PlayerEntryPoint {

    /**
     * Initiates playback with the given context.
     *
     * This method:
     * - Checks kids playback gate
     * - Configures codecs if needed
     * - Resolves the playback source via PlaybackSourceResolver
     * - Applies resume position if available
     * - Initializes the player engine
     * - Starts playback
     *
     * @param context Source-agnostic playback descriptor
     * @throws PlaybackException if playback cannot be started
     */
    suspend fun start(context: PlaybackContext)

    /**
     * Stops current playback and releases resources.
     */
    suspend fun stop()

    /**
     * Renders the player UI for the current playback session.
     *
     * Must be called after [start] has completed successfully.
     * Renders video surface, controls, and handles lifecycle.
     *
     * @param onExit Callback when user wants to exit the player
     * @param modifier Modifier for the player container
     */
    @Composable
    fun RenderPlayerUi(
        onExit: () -> Unit,
        modifier: Modifier = Modifier
    )
}

/**
 * Exception thrown when playback cannot be started.
 */
class PlaybackException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
