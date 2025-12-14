package com.fishit.player.playback.domain

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
 */
interface PlayerEntryPoint {

    /**
     * Initiates playback with the given context.
     *
     * This method:
     * - Resolves the playback source via PlaybackSourceResolver
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
}

/**
 * Exception thrown when playback cannot be started.
 */
class PlaybackException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
