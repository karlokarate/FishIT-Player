package com.fishit.player.player.ui

/**
 * UI state for PlayerScreen.
 *
 * @param isLoading True while player is initializing
 * @param isReady True when player is ready to render
 * @param error Error message if playback failed to start
 */
data class PlayerUiState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val error: String? = null
)
