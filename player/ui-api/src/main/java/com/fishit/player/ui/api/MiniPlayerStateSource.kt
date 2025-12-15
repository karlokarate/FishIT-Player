package com.fishit.player.ui.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Public interface for mini-player state.
 *
 * Provides a reactive state source that mini-player can observe.
 * Implementations live in player:internal and are bound via DI.
 */
interface MiniPlayerStateSource {
    /**
     * Current mini-player state as a reactive flow.
     */
    val state: StateFlow<MiniPlayerStateSnapshot>
}
