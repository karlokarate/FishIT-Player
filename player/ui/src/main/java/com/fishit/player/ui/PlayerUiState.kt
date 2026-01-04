package com.fishit.player.ui

/**
 * UI state for the player screen.
 *
 * This sealed interface represents the different states of the player UI:
 * - Idle: Initial state before playback starts
 * - Loading: Playback is being initialized
 * - Playing: Playback is active
 * - Error: Playback failed to start
 */
sealed interface PlayerUiState {
    /**
     * Initial state before playback starts.
     */
    data object Idle : PlayerUiState

    /**
     * Playback is being initialized.
     */
    data object Loading : PlayerUiState

    /**
     * Playback is active.
     */
    data object Playing : PlayerUiState

    /**
     * Playback failed to start.
     *
     * @param message Human-readable error message
     */
    data class Error(
        val message: String,
    ) : PlayerUiState
}
