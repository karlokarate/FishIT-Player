package com.chris.m3usuite.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Global ExoPlayer holder and unified PlaybackSession for the entire app.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – Unified PlaybackSession
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This singleton manages:
 * - The shared ExoPlayer instance (one per process)
 * - Reactive state flows for UI observation
 * - Command methods for playback control
 * - Player lifecycle management
 *
 * **Key Principles:**
 * - Single global session: One shared ExoPlayer per process
 * - No re-init on transitions: Player survives Full↔MiniPlayer navigation
 * - Thread-safe state updates via StateFlows
 * - Player is NOT destroyed when:
 *   - Leaving the full player
 *   - Opening the MiniPlayer
 *   - Navigating between screens
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Sections 3.1, 7
 */
object PlaybackSession : PlaybackSessionController {
    data class Holder(
        val player: ExoPlayer,
        val isNew: Boolean,
    )

    private val playerRef = AtomicReference<ExoPlayer?>()
    private val sourceRef = AtomicReference<String?>(null)
    private val listenerRef = AtomicReference<Player.Listener?>(null)

    // ══════════════════════════════════════════════════════════════════
    // STATE FLOWS (Phase 7)
    // ══════════════════════════════════════════════════════════════════

    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _isPlaying = MutableStateFlow(false)
    private val _buffering = MutableStateFlow(false)
    private val _error = MutableStateFlow<PlaybackException?>(null)
    private val _videoSize = MutableStateFlow<VideoSize?>(null)
    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    private val _isSessionActive = MutableStateFlow(false)

    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    override val error: StateFlow<PlaybackException?> = _error.asStateFlow()
    override val videoSize: StateFlow<VideoSize?> = _videoSize.asStateFlow()
    override val playbackState: StateFlow<Int> = _playbackState.asStateFlow()
    override val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    // ══════════════════════════════════════════════════════════════════
    // PLAYER ACQUISITION (existing + Phase 7 extensions)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Acquire the shared ExoPlayer instance.
     *
     * If no player exists, one is created using the provided builder.
     * If a player already exists, it is returned.
     *
     * Phase 7: This method now automatically attaches the state listener.
     *
     * @param context Android context (for player creation)
     * @param builder Factory function to create a new ExoPlayer instance
     * @return Holder containing the player and whether it was newly created
     */
    fun acquire(
        context: Context,
        builder: () -> ExoPlayer,
    ): Holder {
        var created = false
        var current = playerRef.get()
        if (current == null) {
            synchronized(this) {
                current = playerRef.get()
                if (current == null) {
                    current = builder()
                    playerRef.set(current)
                    created = true
                    attachStateListener(current!!)
                }
            }
        } else if (listenerRef.get() == null) {
            // Player exists but listener not attached (edge case after reset)
            attachStateListener(current!!)
        }
        _isSessionActive.value = true
        return Holder(current!!, created)
    }

    /**
     * Get the current ExoPlayer instance without creating one.
     * @return The current player, or null if none exists
     */
    fun current(): ExoPlayer? = playerRef.get()

    /**
     * Set the player instance directly.
     * Note: Prefer using acquire() for normal use cases.
     *
     * If a different player instance was previously set, it will be released.
     * If null is passed, the current player is released.
     */
    fun set(player: ExoPlayer?) {
        val previous = playerRef.getAndSet(player)

        // Remove listener from previous player
        val oldListener = listenerRef.getAndSet(null)
        if (oldListener != null && previous != null) {
            runCatching { previous.removeListener(oldListener) }
        }

        // Release previous player if different
        if (previous != null && previous !== player) {
            runCatching { previous.release() }
        }

        // Reset state if player is null
        if (player == null) {
            sourceRef.set(null)
            resetStateFlows()
        } else {
            // Attach listener to new player
            attachStateListener(player)
        }
    }

    fun currentSource(): String? = sourceRef.get()

    fun setSource(url: String?) {
        sourceRef.set(url)
    }

    // ══════════════════════════════════════════════════════════════════
    // PLAYBACK COMMANDS (PlaybackSessionController implementation)
    // ══════════════════════════════════════════════════════════════════

    override fun play() {
        playerRef.get()?.play()
    }

    override fun pause() {
        playerRef.get()?.pause()
    }

    override fun togglePlayPause() {
        val player = playerRef.get() ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun seekTo(positionMs: Long) {
        val player = playerRef.get() ?: return
        val safePosition = positionMs.coerceAtLeast(0L)
        player.seekTo(safePosition)
    }

    override fun seekBy(deltaMs: Long) {
        val player = playerRef.get() ?: return
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(target)
    }

    override fun setSpeed(speed: Float) {
        val player = playerRef.get() ?: return
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4.0f))
    }

    override fun enableTrickplay(speed: Float) {
        // Trickplay is implemented as fast playback speed
        // Negative speeds are not supported by ExoPlayer; for rewind trickplay,
        // the UI should perform periodic seekBy calls instead
        val player = playerRef.get() ?: return
        val safeSpeed = speed.coerceIn(0.25f, 8.0f)
        player.playbackParameters = PlaybackParameters(safeSpeed)
    }

    override fun stop() {
        playerRef.get()?.stop()
        _isSessionActive.value = false
    }

    override fun release() {
        val player = playerRef.getAndSet(null)
        val listener = listenerRef.getAndSet(null)

        if (listener != null && player != null) {
            runCatching { player.removeListener(listener) }
        }

        if (player != null) {
            runCatching { player.release() }
        }

        sourceRef.set(null)
        resetStateFlows()
    }

    // ══════════════════════════════════════════════════════════════════
    // INTERNAL STATE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════

    /**
     * Attach the state listener to the player.
     * This listener updates all StateFlows when player state changes.
     */
    private fun attachStateListener(player: ExoPlayer) {
        // Remove any existing listener first
        val oldListener = listenerRef.get()
        if (oldListener != null) {
            runCatching { player.removeListener(oldListener) }
        }

        val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    updateStateFromPlayer(player)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _playbackState.value = state
                    if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                        _isSessionActive.value = state != Player.STATE_IDLE
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlayerError(error: PlaybackException) {
                    _error.value = error
                    _isPlaying.value = false
                    _buffering.value = false
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    _error.value = error
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    _videoSize.value = videoSize
                }
            }

        listenerRef.set(listener)
        player.addListener(listener)

        // Initialize state from current player state
        updateStateFromPlayer(player)
    }

    /**
     * Update all state flows from current player state.
     */
    private fun updateStateFromPlayer(player: Player) {
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)

        val dur = player.duration
        _durationMs.value =
            if (dur == C.TIME_UNSET) {
                0L
            } else {
                dur.coerceAtLeast(0L)
            }

        _isPlaying.value = player.isPlaying
        _buffering.value = player.playbackState == Player.STATE_BUFFERING
        _playbackState.value = player.playbackState
        _error.value = player.playerError

        val vs = player.videoSize
        _videoSize.value = if (vs.width > 0 && vs.height > 0) vs else null
    }

    /**
     * Reset all state flows to initial values.
     */
    private fun resetStateFlows() {
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        _buffering.value = false
        _error.value = null
        _videoSize.value = null
        _playbackState.value = Player.STATE_IDLE
        _isSessionActive.value = false
    }

    // ══════════════════════════════════════════════════════════════════
    // TEST SUPPORT (for unit tests only)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Reset all state for testing purposes.
     * This should only be called from tests.
     */
    internal fun resetForTesting() {
        release()
    }
}
