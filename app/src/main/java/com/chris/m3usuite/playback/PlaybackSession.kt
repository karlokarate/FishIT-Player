package com.chris.m3usuite.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.miniplayer.DefaultMiniPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Global ExoPlayer holder and unified PlaybackSession for the entire app.
 *
 * ════════════════════════════════════════════════════════════════════════════════ PHASE 7 –
 * Unified PlaybackSession PHASE 8 – SessionLifecycleState added PHASE 8 – Task 6: Structured
 * PlaybackError + AppLog integration
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This singleton manages:
 * - The shared ExoPlayer instance (one per process)
 * - Reactive state flows for UI observation
 * - Command methods for playback control
 * - Player lifecycle management
 * - Session lifecycle state machine (Phase 8)
 * - Structured error handling with AppLog integration (Phase 8 Task 6)
 *
 * **Key Principles:**
 * - Single global session: One shared ExoPlayer per process
 * - No re-init on transitions: Player survives Full↔MiniPlayer navigation
 * - Thread-safe state updates via StateFlows
 * - Player is NOT destroyed when:
 * - Leaving the full player
 * - Opening the MiniPlayer
 * - Navigating between screens
 * - Lifecycle state enables warm resume (Phase 8)
 * - Errors logged via AppLog with category "PLAYER_ERROR" (Phase 8 Task 6)
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Sections 3.1, 7
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 4
 * - INTERNAL_PLAYER_PHASE8_CHECKLIST.md Group 8
 */
object PlaybackSession : PlaybackSessionController {
    data class Holder(
        val player: ExoPlayer,
        val isNew: Boolean,
    )

    /** Metadata describing the currently tracked playback source. */
    data class PlaybackSourceState(
        val url: String?,
        val playbackType: PlaybackType?,
    ) {
        val isTelegram: Boolean
            get() = url?.startsWith("tg://", ignoreCase = true) == true

        val isVodLike: Boolean
            get() = playbackType != null && playbackType != PlaybackType.LIVE
    }

    private val playerRef = AtomicReference<ExoPlayer?>()
    private val sourceRef = AtomicReference<String?>(null)
    private val listenerRef = AtomicReference<Player.Listener?>(null)

    // ══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════

    /** Minimum playback speed multiplier */
    private const val MIN_PLAYBACK_SPEED = 0.25f

    /** Maximum normal playback speed multiplier */
    private const val MAX_PLAYBACK_SPEED = 4.0f

    /** Maximum trickplay speed multiplier */
    private const val MAX_TRICKPLAY_SPEED = 8.0f

    /** AppLog category for player errors (Phase 8 Task 6) */
    private const val LOG_CATEGORY_PLAYER_ERROR = "PLAYER_ERROR"

    // ══════════════════════════════════════════════════════════════════
    // STATE FLOWS (Phase 7 + Phase 8 lifecycle + Phase 8 Task 6 errors)
    // ══════════════════════════════════════════════════════════════════

    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _isPlaying = MutableStateFlow(false)
    private val _buffering = MutableStateFlow(false)
    private val _error = MutableStateFlow<PlaybackException?>(null)
    private val _playbackError = MutableStateFlow<PlaybackError?>(null)
    private val _videoSize = MutableStateFlow<VideoSize?>(null)
    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    private val _isSessionActive = MutableStateFlow(false)
    private val _lifecycleState = MutableStateFlow(SessionLifecycleState.IDLE)
    private val _currentSourceState = MutableStateFlow<PlaybackSourceState?>(null)

    /** Current media ID for error logging (Phase 8 Task 6) */
    private val _currentMediaId = AtomicReference<String?>(null)

    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    override val error: StateFlow<PlaybackException?> = _error.asStateFlow()
    override val playbackError: StateFlow<PlaybackError?> = _playbackError.asStateFlow()
    override val videoSize: StateFlow<VideoSize?> = _videoSize.asStateFlow()
    override val playbackState: StateFlow<Int> = _playbackState.asStateFlow()
    override val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()
    override val lifecycleState: StateFlow<SessionLifecycleState> = _lifecycleState.asStateFlow()
    val currentSourceState: StateFlow<PlaybackSourceState?> = _currentSourceState.asStateFlow()

    // ══════════════════════════════════════════════════════════════════
    // PHASE 8 – LIFECYCLE HELPERS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Helper to check if the session is currently active (not IDLE or RELEASED). This is a
     * convenience property derived from lifecycleState.
     */
    val isSessionActiveByLifecycle: Boolean
        get() =
            _lifecycleState.value !in
                setOf(SessionLifecycleState.IDLE, SessionLifecycleState.RELEASED)

    /** Helper to check if playback can be resumed (session in a resumable state). */
    val canResume: Boolean
        get() =
            _lifecycleState.value in
                setOf(
                    SessionLifecycleState.PREPARED,
                    SessionLifecycleState.PLAYING,
                    SessionLifecycleState.PAUSED,
                    SessionLifecycleState.BACKGROUND,
                )

    // ══════════════════════════════════════════════════════════════════
    // PLAYER ACQUISITION (existing + Phase 7 extensions)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Acquire the shared ExoPlayer instance.
     *
     * If no player exists, one is created using the provided builder. If a player already exists,
     * it is returned.
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
     * Set the player instance directly. Note: Prefer using acquire() for normal use cases.
     *
     * If a different player instance was previously set, it will be released. If null is passed,
     * the current player is released.
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

    fun setSource(
        url: String?,
        playbackType: PlaybackType? = null,
    ) {
        sourceRef.set(url)
        _currentSourceState.value =
            if (url == null && playbackType == null) {
                null
            } else {
                PlaybackSourceState(url = url, playbackType = playbackType)
            }
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
        player.playbackParameters =
            PlaybackParameters(speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED))
    }

    override fun enableTrickplay(speed: Float) {
        // Trickplay is implemented as fast playback speed
        // Negative speeds are not supported by ExoPlayer; for rewind trickplay,
        // the UI should perform periodic seekBy calls instead
        val player = playerRef.get() ?: return
        val safeSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_TRICKPLAY_SPEED)
        player.playbackParameters = PlaybackParameters(safeSpeed)
    }

    override fun stop() {
        playerRef.get()?.stop()
        _isSessionActive.value = false
        // Phase 8: Transition to STOPPED state
        if (_lifecycleState.value != SessionLifecycleState.RELEASED) {
            _lifecycleState.value = SessionLifecycleState.STOPPED
        }
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
        // Phase 8: Transition to RELEASED state (final state)
        _lifecycleState.value = SessionLifecycleState.RELEASED
    }

    // ══════════════════════════════════════════════════════════════════
    // PHASE 8 – LIFECYCLE STATE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════

    /**
     * Notify PlaybackSession that the app has moved to background. Called from Activity.onPause()
     * or Activity.onStop().
     *
     * If playback is active, transitions to BACKGROUND state. This allows audio to continue playing
     * in background if desired.
     */
    fun onAppBackground() {
        val currentState = _lifecycleState.value
        if (currentState == SessionLifecycleState.PLAYING ||
            currentState == SessionLifecycleState.PAUSED
        ) {
            _lifecycleState.value = SessionLifecycleState.BACKGROUND
        }
    }

    /**
     * Notify PlaybackSession that the app has returned to foreground. Called from
     * Activity.onResume().
     *
     * If in BACKGROUND state, transitions back to PLAYING or PAUSED based on current player state.
     * Does NOT recreate ExoPlayer.
     */
    fun onAppForeground() {
        if (_lifecycleState.value == SessionLifecycleState.BACKGROUND) {
            val player = playerRef.get()
            if (player != null && player.isPlaying) {
                _lifecycleState.value = SessionLifecycleState.PLAYING
            } else if (player != null) {
                _lifecycleState.value = SessionLifecycleState.PAUSED
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // INTERNAL STATE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════

    /**
     * Attach the state listener to the player. This listener updates all StateFlows when player
     * state changes.
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
                    // Phase 8: Update lifecycle state based on player state
                    updateLifecycleFromPlaybackState(state)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    // Phase 8: Transition between PLAYING and PAUSED
                    updateLifecycleFromIsPlaying(isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    handlePlayerError(error)
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    handlePlayerErrorChanged(error)
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

    /** Update all state flows from current player state. */
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
     * Update lifecycle state based on ExoPlayer playback state changes. Phase 8: Maps
     * Player.STATE_* to SessionLifecycleState
     */
    private fun updateLifecycleFromPlaybackState(exoPlayerState: Int) {
        // Don't update if already in terminal states
        val currentLifecycle = _lifecycleState.value
        if (currentLifecycle == SessionLifecycleState.RELEASED ||
            currentLifecycle == SessionLifecycleState.STOPPED
        ) {
            return
        }

        when (exoPlayerState) {
            Player.STATE_READY -> {
                // Player is ready - transition to PREPARED if coming from IDLE
                if (currentLifecycle == SessionLifecycleState.IDLE) {
                    _lifecycleState.value = SessionLifecycleState.PREPARED
                }
            }
            Player.STATE_ENDED -> {
                // Playback ended - transition to STOPPED
                _lifecycleState.value = SessionLifecycleState.STOPPED
            }
            Player.STATE_IDLE -> {
                // Player idle - only transition if not already released
                if (currentLifecycle != SessionLifecycleState.RELEASED) {
                    _lifecycleState.value = SessionLifecycleState.IDLE
                }
            }
            // STATE_BUFFERING doesn't change lifecycle state
        }
    }

    /**
     * Update lifecycle state based on isPlaying changes. Phase 8: Transitions between PLAYING and
     * PAUSED
     */
    private fun updateLifecycleFromIsPlaying(isPlaying: Boolean) {
        val currentLifecycle = _lifecycleState.value
        // Only transition if in an active playback state
        if (currentLifecycle !in
            setOf(
                SessionLifecycleState.PREPARED,
                SessionLifecycleState.PLAYING,
                SessionLifecycleState.PAUSED,
                SessionLifecycleState.BACKGROUND,
            )
        ) {
            return
        }

        if (isPlaying) {
            _lifecycleState.value = SessionLifecycleState.PLAYING
        } else if (currentLifecycle == SessionLifecycleState.PLAYING) {
            // Only transition to PAUSED if we were previously PLAYING
            _lifecycleState.value = SessionLifecycleState.PAUSED
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PHASE 8 – TASK 6: ERROR HANDLING & APPLOG INTEGRATION
    // ══════════════════════════════════════════════════════════════════

    private fun handlePlayerError(error: PlaybackException) {
        _error.value = error
        _isPlaying.value = false
        _buffering.value = false
        val structuredError = PlaybackError.fromPlaybackException(error)
        updatePlaybackError(structuredError)
    }

    private fun handlePlayerErrorChanged(error: PlaybackException?) {
        _error.value = error
        if (error == null) {
            if (_playbackError.value != null) {
                _playbackError.value = null
            }
        } else {
            val structuredError = PlaybackError.fromPlaybackException(error)
            updatePlaybackError(structuredError)
        }
    }

    private fun updatePlaybackError(error: PlaybackError?) {
        val previousError = _playbackError.value
        _playbackError.value = error
        if (previousError == null && error != null) {
            logPlaybackErrorToAppLog(error)
        }
    }

    private fun logPlaybackErrorToAppLog(error: PlaybackError) {
        val extras =
            buildMap {
                put("type", error.typeName)
                error.httpOrNetworkCodeAsString?.let { put("code", it) }
                error.urlOrNull?.let { put("url", it) }
                _currentMediaId.get()?.let { put("mediaId", it) }
                put("positionMs", _positionMs.value.toString())
                put("durationMs", _durationMs.value.toString())
                runCatching {
                    put(
                        "miniPlayerVisible",
                        DefaultMiniPlayerManager.state.value.visible
                            .toString(),
                    )
                }
            }
        AppLog.log(
            category = LOG_CATEGORY_PLAYER_ERROR,
            level = AppLog.Level.ERROR,
            message = "Playback error: ${error.toShortSummary()}",
            extras = extras,
            bypassMaster = true,
        )
    }

    fun setCurrentMediaId(mediaId: String?) {
        _currentMediaId.set(mediaId)
    }

    fun clearError() {
        _error.value = null
        _playbackError.value = null
    }

    fun retry(): Boolean {
        val player = playerRef.get() ?: return false
        clearError()
        player.prepare()
        player.play()
        return true
    }

    /**
     * Reset all state flows to initial values. Note: Does NOT reset lifecycleState - that is
     * handled by stop()/release() explicitly.
     */
    private fun resetStateFlows() {
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        _buffering.value = false
        _error.value = null
        _playbackError.value = null
        _videoSize.value = null
        _playbackState.value = Player.STATE_IDLE
        _isSessionActive.value = false
        _currentMediaId.set(null)
        _currentSourceState.value = null
        // Note: _lifecycleState is set to RELEASED by release(), not here.
        // resetForTesting() sets it back to IDLE after release() for clean test state.
    }

    // ══════════════════════════════════════════════════════════════════
    // TEST SUPPORT (for unit tests only)
    // ══════════════════════════════════════════════════════════════════

    /** Reset all state for testing purposes. This should only be called from tests. */
    internal fun resetForTesting() {
        release()
        // Also reset lifecycle to IDLE for clean test state
        _lifecycleState.value = SessionLifecycleState.IDLE
    }
}
