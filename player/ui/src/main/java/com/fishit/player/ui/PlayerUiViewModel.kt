package com.fishit.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.PlayerEntryPoint
import com.fishit.player.playback.domain.PlaybackException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the player UI.
 *
 * **Architecture:**
 * - Uses @HiltViewModel for clean dependency injection
 * - Constructor-injects only high-level abstractions (no engine wiring)
 * - Delegates playback to [PlayerEntryPoint] (domain interface)
 * - Never references internal engine implementation details
 *
 * @param playerEntryPoint High-level playback abstraction (from playback:domain)
 */
@HiltViewModel
class PlayerUiViewModel @Inject constructor(
    private val playerEntryPoint: PlayerEntryPoint,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var lastContext: PlaybackContext? = null

    /**
     * Starts playback with the given context.
     *
     * This method:
     * - Updates state to Loading
     * - Calls playerEntryPoint.start(context)
     * - Transitions to Playing or Error based on result
     *
     * @param context Source-agnostic playback descriptor
     */
    fun start(context: PlaybackContext) {
        lastContext = context
        _state.value = PlayerUiState.Loading

        UnifiedLog.d(TAG) {
            "player.ui.start.requested: canonicalId=${context.canonicalId}"
        }

        viewModelScope.launch {
            try {
                playerEntryPoint.start(context)
                _state.value = PlayerUiState.Playing

                UnifiedLog.d(TAG) {
                    "player.ui.start.succeeded: canonicalId=${context.canonicalId}"
                }
            } catch (e: PlaybackException) {
                _state.value = PlayerUiState.Error(
                    message = e.message ?: "Failed to start playback"
                )

                UnifiedLog.e(TAG) {
                    "player.ui.start.failed: error=${e.javaClass.simpleName}"
                }
            } catch (e: Exception) {
                _state.value = PlayerUiState.Error(
                    message = "Unexpected error: ${e.message}"
                )

                UnifiedLog.e(TAG) {
                    "player.ui.start.failed: error=${e.javaClass.simpleName}"
                }
            }
        }
    }

    /**
     * Retries playback using the last context.
     */
    fun retry() {
        lastContext?.let { context ->
            UnifiedLog.d(TAG) {
                "player.ui.retry: canonicalId=${context.canonicalId}"
            }
            start(context)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                playerEntryPoint.stop()
            } catch (e: Exception) {
                UnifiedLog.e(TAG) {
                    "player.ui.stop.failed: error=${e.javaClass.simpleName}"
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlayerUiViewModel"
    }
}
