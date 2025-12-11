package com.fishit.player.internal.session

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.PlaybackError
import com.fishit.player.core.playermodel.PlaybackState
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.internal.source.PlaybackSourceResolver
import com.fishit.player.internal.state.InternalPlayerState
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.ResumeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages an internal player session with ExoPlayer.
 *
 * Encapsulates:
 * - ExoPlayer lifecycle
 * - State emission via StateFlow
 * - Resume position tracking
 * - Kids gate integration
 * - Position updates
 * - Custom DataSource factories for Telegram/Xtream
 *
 * **Phase 3 Architecture:**
 * - Uses [PlaybackSourceResolver] with factory pattern
 * - Uses types from core:player-model
 * - Integrates with playback:domain interfaces
 * - Supports custom DataSource.Factory for source-specific streaming
 */
class InternalPlayerSession(
    private val context: Context,
    private val sourceResolver: PlaybackSourceResolver,
    private val resumeManager: ResumeManager,
    private val kidsPlaybackGate: KidsPlaybackGate,
    private val dataSourceFactories: Map<DataSourceType, DataSource.Factory> = emptyMap()
) {
    companion object {
        private const val TAG = "InternalPlayerSession"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var positionUpdateJob: Job? = null
    private var sessionStartTime: Long = 0L
    private var currentContext: PlaybackContext? = null

    private val _state = MutableStateFlow(InternalPlayerState.INITIAL)
    val state: StateFlow<InternalPlayerState> = _state.asStateFlow()

    /**
     * Returns the underlying ExoPlayer instance.
     *
     * Use this to attach the player to a PlayerView for video rendering.
     * May return null if the session is not initialized or has been released.
     *
     * **Thread Safety:** This must only be called from the main thread.
     */
    fun getPlayer(): Player? = player

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val newState = when (playbackState) {
                Player.STATE_IDLE -> PlaybackState.IDLE
                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                Player.STATE_READY -> if (player?.isPlaying == true) PlaybackState.PLAYING else PlaybackState.PAUSED
                Player.STATE_ENDED -> PlaybackState.ENDED
                else -> PlaybackState.IDLE
            }
            _state.update { it.copy(playbackState = newState) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update {
                it.copy(
                    isPlaying = isPlaying,
                    playbackState = if (isPlaying) PlaybackState.PLAYING else {
                        if (it.playbackState == PlaybackState.ENDED) PlaybackState.ENDED
                        else PlaybackState.PAUSED
                    }
                )
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            UnifiedLog.e(TAG, "Player error: ${error.errorCodeName}", error)
            _state.update {
                it.copy(
                    playbackState = PlaybackState.ERROR,
                    error = PlaybackError.unknown(
                        message = error.message ?: "Unknown playback error",
                        code = error.errorCode
                    )
                )
            }
        }
    }

    /**
     * Initializes the player and starts playback.
     *
     * Uses [PlaybackSourceResolver] to resolve the source via factories.
     */
    fun initialize(playbackContext: PlaybackContext) {
        release()

        currentContext = playbackContext
        sessionStartTime = System.currentTimeMillis()

        UnifiedLog.d(TAG, "Initializing playback: ${playbackContext.canonicalId}")

        // Resolve source asynchronously
        scope.launch {
            try {
                val source = sourceResolver.resolve(playbackContext)
                UnifiedLog.d(TAG, "Source resolved: ${source.uri}, dataSourceType: ${source.dataSourceType}")

                // Build MediaItem
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(source.uri)

                source.mimeType?.let { mediaItemBuilder.setMimeType(it) }

                val mediaItem = mediaItemBuilder.build()

                // Determine appropriate DataSource.Factory based on source type
                val dataSourceFactory = dataSourceFactories[source.dataSourceType]
                    ?: DefaultDataSource.Factory(context)

                // Create MediaSource.Factory with appropriate DataSource
                val mediaSourceFactory = DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)

                // Create and configure player on main thread
                player = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build().apply {
                    addListener(playerListener)
                    setMediaItem(mediaItem)

                    // Set start position if resuming
                    if (playbackContext.startPositionMs > 0) {
                        seekTo(playbackContext.startPositionMs)
                    }

                    prepare()
                    playWhenReady = true
                }

                _state.update {
                    it.copy(
                        context = playbackContext,
                        playbackState = PlaybackState.BUFFERING
                    )
                }

                startPositionUpdates()

            } catch (e: Exception) {
                UnifiedLog.e(TAG, "Failed to resolve source", e)
                _state.update {
                    it.copy(
                        playbackState = PlaybackState.ERROR,
                        error = PlaybackError.sourceNotFound(
                            message = "Failed to resolve source: ${e.message}",
                            sourceType = playbackContext.sourceType
                        )
                    )
                }
            }
        }
    }

    /**
     * Toggles play/pause.
     */
    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    /**
     * Seeks to a specific position.
     */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        updatePositionState()
    }

    /**
     * Seeks forward by the given amount.
     */
    fun seekForward(amountMs: Long = 10_000L) {
        player?.let {
            seekTo(it.currentPosition + amountMs)
        }
    }

    /**
     * Seeks backward by the given amount.
     */
    fun seekBackward(amountMs: Long = 10_000L) {
        player?.let {
            seekTo(it.currentPosition - amountMs)
        }
    }

    /**
     * Sets the volume (0.0 to 1.0).
     */
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        player?.volume = clampedVolume
        _state.update { it.copy(volume = clampedVolume, isMuted = clampedVolume == 0f) }
    }

    /**
     * Toggles mute.
     */
    fun toggleMute() {
        _state.value.let { currentState ->
            if (currentState.isMuted) {
                setVolume(1f)
            } else {
                setVolume(0f)
            }
        }
    }

    /**
     * Sets controls visibility.
     */
    fun setControlsVisible(visible: Boolean) {
        _state.update { it.copy(areControlsVisible = visible) }
    }

    /**
     * Toggles controls visibility.
     */
    fun toggleControls() {
        _state.update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }

    /**
     * Saves the current resume position.
     */
    suspend fun saveResumePosition() {
        val currentState = _state.value
        val ctx = currentState.context ?: return
        
        if (currentState.positionMs > 10_000L && currentState.remainingMs > 10_000L) {
            resumeManager.saveResumePoint(
                context = ctx,
                positionMs = currentState.positionMs,
                durationMs = currentState.durationMs
            )
        } else if (currentState.remainingMs <= 10_000L) {
            // Clear resume if near end
            resumeManager.clearResumePoint(ctx.canonicalId)
        }
    }

    /**
     * Releases the player and cleans up resources.
     */
    fun release() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
        
        player?.removeListener(playerListener)
        player?.release()
        player = null
        
        _state.value = InternalPlayerState.INITIAL
    }

    /**
     * Cleans up the session completely.
     */
    fun destroy() {
        release()
        scope.cancel()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                updatePositionState()
                checkKidsGate()
                delay(1000L) // Update every second
            }
        }
    }

    private fun updatePositionState() {
        player?.let { exo ->
            val sessionElapsed = System.currentTimeMillis() - sessionStartTime
            _state.update {
                it.copy(
                    positionMs = exo.currentPosition.coerceAtLeast(0L),
                    durationMs = exo.duration.coerceAtLeast(0L),
                    bufferedPositionMs = exo.bufferedPosition.coerceAtLeast(0L),
                    sessionElapsedMs = sessionElapsed
                )
            }
        }
    }

    private suspend fun checkKidsGate() {
        val currentState = _state.value
        val ctx = currentState.context ?: return
        
        when (val result = kidsPlaybackGate.tick(ctx, currentState.sessionElapsedMs)) {
            is KidsPlaybackGate.GateResult.Blocked -> {
                player?.pause()
                // In a real implementation, we'd show a UI message
            }
            is KidsPlaybackGate.GateResult.Warning -> {
                // In a real implementation, we'd show a warning
            }
            is KidsPlaybackGate.GateResult.Allowed -> {
                // Continue playing
            }
        }
    }
}
