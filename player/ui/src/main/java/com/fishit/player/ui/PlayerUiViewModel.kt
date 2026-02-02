package com.fishit.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.fishit.player.core.playermodel.AudioSelectionState
import com.fishit.player.core.playermodel.AudioTrackId
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SubtitleSelectionState
import com.fishit.player.core.playermodel.SubtitleTrackId
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.internal.InternalPlayerEntryImpl
import com.fishit.player.internal.state.InternalPlayerState
import com.fishit.player.playback.domain.PlaybackException
import com.fishit.player.playback.domain.PlayerEntryPoint
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
 * - Injects [InternalPlayerEntryImpl] for access to player session
 * - Delegates playback to [PlayerEntryPoint] interface
 * - Exposes ExoPlayer instance and session state for UI binding
 *
 * **Player Access:**
 * - [getPlayer] returns the ExoPlayer instance for PlayerView binding
 * - [sessionState] exposes the internal player state flow (buffering, position, etc.)
 *
 * @param playerEntryImpl Concrete implementation that holds the session
 */
@HiltViewModel
class PlayerUiViewModel
    @Inject
    constructor(
        private val playerEntryImpl: InternalPlayerEntryImpl,
    ) : ViewModel() {
        // Cast to interface for abstraction where needed
        private val playerEntryPoint: PlayerEntryPoint = playerEntryImpl

        private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
        val state: StateFlow<PlayerUiState> = _state.asStateFlow()

        private var lastContext: PlaybackContext? = null

        /**
         * Returns the current ExoPlayer instance for PlayerView binding.
         * Returns null if no session is active.
         */
        fun getPlayer(): Player? = playerEntryImpl.getCurrentSession()?.getPlayer()

        /**
         * Returns the internal player state flow for observing detailed playback state.
         * Returns null if no session is active.
         */
        fun getSessionState(): StateFlow<InternalPlayerState>? = playerEntryImpl.getCurrentSession()?.state

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
                    _state.value =
                        PlayerUiState.Error(
                            message = e.message ?: "Failed to start playback",
                        )

                    UnifiedLog.e(TAG) {
                        "player.ui.start.failed: error=${e.javaClass.simpleName}"
                    }
                } catch (e: Exception) {
                    _state.value =
                        PlayerUiState.Error(
                            message = "Unexpected error: ${e.message}",
                        )

                    UnifiedLog.e(TAG) {
                        "player.ui.start.failed: error=${e.javaClass.simpleName}"
                    }
                }
            }
        }

        /**
         * Toggle play/pause.
         */
        fun togglePlayPause() {
            playerEntryImpl.getCurrentSession()?.togglePlayPause()
        }

        /**
         * Seek forward by 10 seconds.
         */
        fun seekForward() {
            playerEntryImpl.getCurrentSession()?.seekForward()
        }

        /**
         * Seek backward by 10 seconds.
         */
        fun seekBackward() {
            playerEntryImpl.getCurrentSession()?.seekBackward()
        }

        /**
         * Seek to a specific position.
         */
        fun seekTo(positionMs: Long) {
            playerEntryImpl.getCurrentSession()?.seekTo(positionMs)
        }

        /**
         * Toggle mute state.
         */
        fun toggleMute() {
            playerEntryImpl.getCurrentSession()?.toggleMute()
        }

        /**
         * Toggle controls visibility.
         */
        fun toggleControls() {
            playerEntryImpl.getCurrentSession()?.toggleControls()
        }

        /**
         * Hides the controls (used by auto-hide timer).
         */
        fun hideControls() {
            playerEntryImpl.getCurrentSession()?.hideControls()
        }

        // ══════════════════════════════════════════════════════════════════════════════
        // Audio Track APIs (Phase 7 Backend → UI Wiring)
        // ══════════════════════════════════════════════════════════════════════════════

        /**
         * Returns the audio selection state flow for observing available tracks.
         */
        fun getAudioState(): StateFlow<AudioSelectionState>? =
            playerEntryImpl.getCurrentSession()?.audioState

        /**
         * Selects an audio track by ID.
         */
        fun selectAudioTrack(trackId: AudioTrackId): Boolean =
            playerEntryImpl.getCurrentSession()?.selectAudioTrack(trackId) ?: false

        /**
         * Cycles to the next audio track (useful for remote control).
         */
        fun cycleAudioTrack() {
            playerEntryImpl.getCurrentSession()?.cycleAudioTrack()
        }

        // ══════════════════════════════════════════════════════════════════════════════
        // Subtitle Track APIs (Phase 6 Backend → UI Wiring)
        // ══════════════════════════════════════════════════════════════════════════════

        /**
         * Returns the subtitle selection state flow for observing available tracks.
         */
        fun getSubtitleState(): StateFlow<SubtitleSelectionState>? =
            playerEntryImpl.getCurrentSession()?.subtitleState

        /**
         * Selects a subtitle track by ID.
         */
        fun selectSubtitleTrack(trackId: SubtitleTrackId): Boolean =
            playerEntryImpl.getCurrentSession()?.selectSubtitleTrack(trackId) ?: false

        /**
         * Disables all subtitles.
         */
        fun disableSubtitles(): Boolean =
            playerEntryImpl.getCurrentSession()?.disableSubtitles() ?: false

        // ══════════════════════════════════════════════════════════════════════════════
        // Playback Speed API
        // ══════════════════════════════════════════════════════════════════════════════

        /**
         * Sets the playback speed.
         * @param speed Playback speed (0.5f to 2.0f recommended)
         */
        fun setPlaybackSpeed(speed: Float) {
            playerEntryImpl.getCurrentSession()?.setPlaybackSpeed(speed)
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
