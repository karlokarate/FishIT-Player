package com.fishit.player.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.PlayerEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for PlayerScreen.
 *
 * Manages player lifecycle via PlayerEntryPoint abstraction.
 * This ensures app-v2 doesn't depend on player:internal directly.
 * All engine wiring is fully encapsulated in PlayerEntryPoint.
 *
 * @param playerEntryPoint Abstraction for starting playback and rendering UI (from playback:domain)
 */
@HiltViewModel
class PlayerUiViewModel @Inject constructor(
    val playerEntryPoint: PlayerEntryPoint
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /**
     * Starts playback with the given context.
     *
     * PlayerEntryPoint handles all engine wiring:
     * - Kids playback gate check
     * - Codec configuration
     * - Playback source resolution
     * - Resume position application
     * - Player engine initialization
     *
     * Uses LaunchedEffect key to prevent repeated starts for the same context.
     */
    fun startPlayback(context: PlaybackContext) {
        viewModelScope.launch {
            try {
                _state.value = PlayerUiState(isLoading = true)
                
                UnifiedLog.d(TAG) { "Starting playback via PlayerEntryPoint: ${context.canonicalId}" }
                playerEntryPoint.start(context)
                
                _state.value = PlayerUiState(isReady = true)
                UnifiedLog.d(TAG) { "Playback ready: ${context.canonicalId}" }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to start playback: ${context.canonicalId}" }
                _state.value = PlayerUiState(error = e.message ?: "Playback failed")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Stop playback when ViewModel is cleared
        viewModelScope.launch {
            try {
                playerEntryPoint.stop()
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Error stopping playback" }
            }
        }
    }

    companion object {
        private const val TAG = "PlayerUiViewModel"
    }
}
