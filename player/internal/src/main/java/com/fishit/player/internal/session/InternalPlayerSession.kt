package com.fishit.player.internal.session

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.fishit.player.core.playermodel.AudioSelectionState
import com.fishit.player.core.playermodel.AudioTrack
import com.fishit.player.core.playermodel.AudioTrackId
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.PlaybackError
import com.fishit.player.core.playermodel.PlaybackState
import com.fishit.player.core.playermodel.SubtitleSelectionState
import com.fishit.player.core.playermodel.SubtitleTrackId
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.internal.audio.AudioTrackManager
import com.fishit.player.internal.source.PlaybackSourceResolver
import com.fishit.player.internal.state.InternalPlayerState
import com.fishit.player.internal.subtitle.SubtitleTrackManager
import com.fishit.player.nextlib.NextlibCodecConfigurator
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.LivePlaybackController
import com.fishit.player.playback.domain.ResumeManager
import com.fishit.player.playback.xtream.XtreamDataSourceFactoryProvider
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
import kotlinx.coroutines.withContext

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
 *
 * **Source-Agnostic Design:**
 * - Depends on provider interfaces, not concrete implementations
 * - Can compile/run with zero playback sources
 * - Xtream/Telegram support is optional via DI
 */
class InternalPlayerSession(
    private val context: Context,
    private val sourceResolver: PlaybackSourceResolver,
    private val resumeManager: ResumeManager,
    private val kidsPlaybackGate: KidsPlaybackGate,
    private val codecConfigurator: NextlibCodecConfigurator,
    private val dataSourceFactories: Map<DataSourceType, DataSource.Factory> = emptyMap(),
    private val xtreamDataSourceProvider: XtreamDataSourceFactoryProvider? = null,
    private val livePlaybackController: LivePlaybackController? = null,
) {
    companion object {
        private const val TAG = "InternalPlayerSession"

        /**
         * Adaptive streaming MIME types that MUST be forwarded to ExoPlayer's MediaItem
         * to route to the correct MediaSource (HlsMediaSource, DashMediaSource, etc.).
         *
         * Progressive MIME types (video/mp4, video/x-matroska, etc.) are NOT included
         * because they map to CONTENT_TYPE_OTHER → ProgressiveMediaSource, which uses
         * content-sniffing via DefaultExtractorsFactory regardless of the MIME type.
         * Setting them has zero effect and can cause confusion.
         */
        private val ADAPTIVE_MIME_TYPES =
            setOf(
                "application/x-mpegurl", // HLS
                "application/vnd.apple.mpegurl", // HLS alt
                "application/dash+xml", // DASH
                "application/vnd.ms-sstr+xml", // Smooth Streaming
            )

        /**
         * Check if a MIME type requires explicit routing to a dedicated MediaSource.
         *
         * @return true for HLS, DASH, Smooth Streaming; false for all progressive formats
         */
        private fun isAdaptiveStreamingMimeType(mimeType: String): Boolean = mimeType.lowercase() in ADAPTIVE_MIME_TYPES
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var positionUpdateJob: Job? = null
    private var sessionStartTime: Long = 0L
    private var currentContext: PlaybackContext? = null
    private var isKidMode: Boolean = false

    private val _state = MutableStateFlow(InternalPlayerState.INITIAL)
    val state: StateFlow<InternalPlayerState> = _state.asStateFlow()

    // Subtitle management
    private val subtitleTrackManager = SubtitleTrackManager()

    // Audio track management (Phase 7)
    private val audioTrackManager = AudioTrackManager()

    /**
     * Current subtitle selection state as a StateFlow.
     *
     * Observe this to react to subtitle track availability and selection changes.
     */
    val subtitleState: StateFlow<SubtitleSelectionState>
        get() = subtitleTrackManager.state

    /**
     * Current audio selection state as a StateFlow.
     *
     * Observe this to react to audio track availability and selection changes.
     */
    val audioState: StateFlow<AudioSelectionState>
        get() = audioTrackManager.state

    /**
     * Returns the underlying ExoPlayer instance.
     *
     * Use this to attach the player to a PlayerView for video rendering. May return null if the
     * session is not initialized or has been released.
     *
     * **Thread Safety:** This must only be called from the main thread.
     */
    fun getPlayer(): Player? = player

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val newState =
                    when (playbackState) {
                        Player.STATE_IDLE -> PlaybackState.IDLE
                        Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                        Player.STATE_READY -> {
                            // Log available tracks when player is ready (NextLib
                            // integration)
                            logCurrentTracks()
                            if (player?.isPlaying == true) {
                                PlaybackState.PLAYING
                            } else {
                                PlaybackState.PAUSED
                            }
                        }
                        Player.STATE_ENDED -> PlaybackState.ENDED
                        else -> PlaybackState.IDLE
                    }
                _state.update { it.copy(playbackState = newState) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update {
                    it.copy(
                        isPlaying = isPlaying,
                        playbackState =
                            if (isPlaying) {
                                PlaybackState.PLAYING
                            } else {
                                if (it.playbackState == PlaybackState.ENDED) {
                                    PlaybackState.ENDED
                                } else {
                                    PlaybackState.PAUSED
                                }
                            },
                    )
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                UnifiedLog.e(TAG, "Player error: ${error.errorCodeName}", error)
                _state.update {
                    it.copy(
                        playbackState = PlaybackState.ERROR,
                        error =
                            PlaybackError.unknown(
                                message = error.message ?: "Unknown playback error",
                                code = error.errorCode,
                            ),
                    )
                }
            }
        }

    /**
     * Initializes the player and starts playback.
     *
     * Uses [PlaybackSourceResolver] to resolve the source via factories.
     * For live content, also initializes EPG via [LivePlaybackController].
     *
     * **CRITICAL:** This is a suspend function that awaits player creation.
     * The caller MUST wait for this to complete before setting UI state to Playing,
     * otherwise getPlayer() will return null.
     */
    suspend fun initialize(playbackContext: PlaybackContext) {
        release()

        currentContext = playbackContext
        sessionStartTime = System.currentTimeMillis()

        UnifiedLog.d(TAG, "Initializing playback: ${playbackContext.canonicalId}")

        // Check if kid mode is active via KidsPlaybackGate (async, doesn't block player)
        scope.launch {
            isKidMode = kidsPlaybackGate.isActive()
            if (isKidMode) {
                UnifiedLog.i(TAG, "Kid mode active - subtitles will be disabled")
            }
        }

        // Initialize Live TV EPG if this is live content (async, doesn't block player)
        if (playbackContext.isLive && livePlaybackController != null) {
            scope.launch {
                try {
                    val channelInfo = livePlaybackController.switchToChannel(playbackContext.canonicalId)
                    if (channelInfo != null) {
                        _state.update { it.copy(liveChannelInfo = channelInfo) }
                        UnifiedLog.d(TAG) { "Live channel EPG loaded: ${channelInfo.name}" }
                    }
                } catch (e: Exception) {
                    UnifiedLog.d(TAG) { "EPG initialization failed: ${e.message}" }
                }
            }
        }

        // Resolve source and create player - this is the critical path that must complete
        // before returning, otherwise getPlayer() will return null
        // CRITICAL: ExoPlayer MUST be created on Main thread
        withContext(Dispatchers.Main) {
            try {
                val source = sourceResolver.resolve(playbackContext)
                UnifiedLog.d(TAG) {
                    "Source resolved: uri=${source.uri.take(100)}, " +
                        "mimeType=${source.mimeType}, " +
                        "headers=${source.headers.keys}, dataSourceType=${source.dataSourceType}"
                }

                // Build MediaItem
                // Sniffing-First Policy: Only set MIME type for adaptive streaming (HLS/DASH)
                // to route to the correct MediaSource. For progressive formats, ExoPlayer's
                // DefaultExtractorsFactory sniffs the container natively via magic bytes —
                // this is more reliable than extension-based or MIME-based guessing.
                val mediaItemBuilder = MediaItem.Builder().setUri(source.uri)

                source.mimeType?.let { mime ->
                    if (isAdaptiveStreamingMimeType(mime)) {
                        mediaItemBuilder.setMimeType(mime)
                        UnifiedLog.d(TAG) { "Set adaptive MIME type: $mime (routes to dedicated MediaSource)" }
                    } else {
                        UnifiedLog.d(TAG) { "Ignoring progressive MIME type: $mime (ExoPlayer will sniff natively)" }
                    }
                }

                val mediaItem = mediaItemBuilder.build()

                // Determine appropriate DataSource.Factory based on source type
                // CRITICAL: Apply HTTP headers for Xtream streams (Referer, User-Agent, etc.)
                // Without headers, many Xtream panels return 403/406/520
                val dataSourceFactory =
                    when (source.dataSourceType) {
                        DataSourceType.XTREAM_HTTP -> {
                            // Use Xtream provider if available (optional dependency)
                            if (xtreamDataSourceProvider != null) {
                                UnifiedLog.d(TAG) {
                                    "Using Xtream HTTP DataSource = OkHttpDataSource.Factory (redirect-safe)"
                                }
                                // Issue #564: debugMode is now compile-time via source sets
                                // Debug builds automatically include Chucker, release builds don't
                                xtreamDataSourceProvider.create(
                                    headers = source.headers,
                                )
                            } else {
                                // PLATINUM: Fallback with redirect safety
                                // This path is weaker than OkHttpDataSource but should still
                                // work
                                UnifiedLog.w(TAG) {
                                    "XtreamDataSourceProvider not available, falling back to DefaultHttpDataSource (redirect/header parity may be reduced)"
                                }
                                val httpFactory =
                                    DefaultHttpDataSource
                                        .Factory()
                                        .setAllowCrossProtocolRedirects(
                                            true,
                                        ) // PLATINUM: Enable HTTP->HTTPS redirects
                                        .setDefaultRequestProperties(source.headers)
                                DefaultDataSource.Factory(context, httpFactory)
                            }
                        }
                        else ->
                            when {
                                dataSourceFactories.containsKey(source.dataSourceType) ->
                                    dataSourceFactories[source.dataSourceType]!!
                                source.headers.isNotEmpty() -> {
                                    // HTTP streams need headers (legacy fallback)
                                    UnifiedLog.d(TAG) {
                                        "Applying ${source.headers.size} HTTP headers to DefaultHttpDataSource"
                                    }
                                    val httpFactory =
                                        DefaultHttpDataSource
                                            .Factory()
                                            .setDefaultRequestProperties(
                                                source.headers,
                                            )
                                    DefaultDataSource.Factory(context, httpFactory)
                                }
                                else -> DefaultDataSource.Factory(context)
                            }
                    }

                // Create MediaSource.Factory with appropriate DataSource
                val mediaSourceFactory =
                    DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

                // Create RenderersFactory with NextLib FFmpeg codecs
                val renderersFactory = codecConfigurator.createRenderersFactory(context)
                UnifiedLog.i(TAG, "SIP using NextLib NextRenderersFactory for FFmpeg codecs")

                // Create and configure player on main thread
                player =
                    ExoPlayer
                        .Builder(context)
                        .setRenderersFactory(renderersFactory)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .build()
                        .apply {
                            addListener(playerListener)
                            setMediaItem(mediaItem)

                            // Set start position if resuming
                            if (playbackContext.startPositionMs > 0) {
                                seekTo(playbackContext.startPositionMs)
                            }

                            prepare()
                            playWhenReady = true
                        }

                // Attach subtitle track manager (Phase 6)
                player?.let { exoPlayer -> subtitleTrackManager.attach(exoPlayer, isKidMode) }

                // Attach audio track manager (Phase 7)
                player?.let { exoPlayer ->
                    audioTrackManager.attach(
                        player = exoPlayer,
                        preferredLanguage = null, // TODO: Get from user preferences
                        preferSurroundSound = true, // TODO: Get from user preferences
                    )
                }

                _state.update {
                    it.copy(context = playbackContext, playbackState = PlaybackState.BUFFERING)
                }

                startPositionUpdates()
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "Failed to resolve source", e)
                _state.update {
                    it.copy(
                        playbackState = PlaybackState.ERROR,
                        error =
                            PlaybackError.sourceNotFound(
                                message = "Failed to resolve source: ${e.message}",
                                sourceType = playbackContext.sourceType,
                            ),
                    )
                }
            }
        }
    }

    /** Toggles play/pause. */
    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    /** Seeks to a specific position. */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        updatePositionState()
    }

    /** Seeks forward by the given amount. */
    fun seekForward(amountMs: Long = 10_000L) {
        player?.let { seekTo(it.currentPosition + amountMs) }
    }

    /** Seeks backward by the given amount. */
    fun seekBackward(amountMs: Long = 10_000L) {
        player?.let { seekTo(it.currentPosition - amountMs) }
    }

    /** Sets the volume (0.0 to 1.0). */
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        player?.volume = clampedVolume
        _state.update { it.copy(volume = clampedVolume, isMuted = clampedVolume == 0f) }
    }

    /** Toggles mute. */
    fun toggleMute() {
        _state.value.let { currentState ->
            if (currentState.isMuted) {
                setVolume(1f)
            } else {
                setVolume(0f)
            }
        }
    }

    // ========== Playback Speed API ==========

    /**
     * Sets the playback speed.
     *
     * @param speed Playback speed multiplier (0.25 to 4.0, recommended: 0.5 to 2.0)
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 4f)
        player?.setPlaybackSpeed(clampedSpeed)
        _state.update { it.copy(playbackSpeed = clampedSpeed) }
        UnifiedLog.d(TAG) { "Playback speed set to: ${clampedSpeed}x" }
    }

    /** Sets controls visibility. */
    fun setControlsVisible(visible: Boolean) {
        _state.update { it.copy(areControlsVisible = visible) }
    }

    /** Toggles controls visibility. */
    fun toggleControls() {
        _state.update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }

    /** Hides controls (used by auto-hide timer). */
    fun hideControls() {
        _state.update { it.copy(areControlsVisible = false) }
    }

    // ========== Subtitle APIs (Phase 6) ==========

    /**
     * Selects a subtitle track by ID.
     *
     * @param trackId The track ID to select.
     * @return true if selection was successful.
     */
    fun selectSubtitleTrack(trackId: SubtitleTrackId): Boolean {
        if (isKidMode) {
            UnifiedLog.d(TAG, "Cannot select subtitle: kid mode active")
            return false
        }
        return subtitleTrackManager.selectTrack(trackId)
    }

    /**
     * Disables all subtitle tracks.
     *
     * @return true if subtitles were successfully disabled.
     */
    fun disableSubtitles(): Boolean = subtitleTrackManager.disableSubtitles()

    /**
     * Selects a subtitle track by language code.
     *
     * @param languageCode BCP-47 language code (e.g., "en", "de").
     * @return true if a matching track was found and selected.
     */
    fun selectSubtitleByLanguage(languageCode: String): Boolean {
        if (isKidMode) {
            UnifiedLog.d(TAG, "Cannot select subtitle: kid mode active")
            return false
        }
        return subtitleTrackManager.selectTrackByLanguage(languageCode)
    }

    // ========== Audio Track APIs (Phase 7) ==========

    /**
     * Selects an audio track by ID.
     *
     * @param trackId The audio track ID to select.
     * @return true if selection was successful.
     */
    fun selectAudioTrack(trackId: AudioTrackId): Boolean = audioTrackManager.selectTrack(trackId)

    /**
     * Selects an audio track by language code.
     *
     * If multiple tracks exist for the language, prefers surround sound based on user preferences.
     *
     * @param languageCode BCP-47 language code (e.g., "en", "de").
     * @return true if a matching track was found and selected.
     */
    fun selectAudioByLanguage(languageCode: String): Boolean = audioTrackManager.selectTrackByLanguage(languageCode)

    /**
     * Cycles to the next audio track.
     *
     * Useful for quick audio switching via remote control.
     *
     * @return The newly selected audio track, or null if cycling failed.
     */
    fun cycleAudioTrack(): AudioTrack? = audioTrackManager.selectNextTrack()

    /**
     * Updates audio track preferences.
     *
     * @param preferredLanguage Preferred audio language (BCP-47 code).
     * @param preferSurroundSound Whether to prefer surround sound over stereo.
     */
    fun updateAudioPreferences(
        preferredLanguage: String? = null,
        preferSurroundSound: Boolean = true,
    ) {
        audioTrackManager.updatePreferences(preferredLanguage, preferSurroundSound)
    }

    /** Saves the current resume position. */
    suspend fun saveResumePosition() {
        val currentState = _state.value
        val ctx = currentState.context ?: return

        if (currentState.positionMs > 10_000L && currentState.remainingMs > 10_000L) {
            resumeManager.saveResumePoint(
                context = ctx,
                positionMs = currentState.positionMs,
                durationMs = currentState.durationMs,
            )
        } else if (currentState.remainingMs <= 10_000L) {
            // Clear resume if near end
            resumeManager.clearResumePoint(ctx.canonicalId)
        }
    }

    /** Releases the player and cleans up resources. */
    fun release() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null

        // Detach subtitle track manager (Phase 6)
        subtitleTrackManager.detach()

        // Detach audio track manager (Phase 7)
        audioTrackManager.detach()

        player?.removeListener(playerListener)
        player?.release()
        player = null

        _state.value = InternalPlayerState.INITIAL
    }

    /** Cleans up the session completely. */
    fun destroy() {
        release()
        scope.cancel()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Live TV Controls
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Switch to the next channel (for live TV).
     *
     * Uses [LivePlaybackController] to switch channels and updates the player
     * with the new stream. EPG data is fetched on-demand.
     *
     * @return true if channel switch was initiated, false if not in live mode or no channels
     */
    fun nextChannel(): Boolean {
        val controller = livePlaybackController ?: return false
        val ctx = currentContext ?: return false
        if (!ctx.isLive) return false

        scope.launch {
            try {
                val newChannel = controller.nextChannel()
                if (newChannel != null) {
                    _state.update { it.copy(liveChannelInfo = newChannel, epgOverlayVisible = true) }
                    UnifiedLog.d(TAG) { "Switched to next channel: ${newChannel.name}" }
                    // TODO: Actually switch stream URL when Xtream live context provides it
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to switch to next channel" }
            }
        }
        return true
    }

    /**
     * Switch to the previous channel (for live TV).
     *
     * @return true if channel switch was initiated, false if not in live mode
     */
    fun previousChannel(): Boolean {
        val controller = livePlaybackController ?: return false
        val ctx = currentContext ?: return false
        if (!ctx.isLive) return false

        scope.launch {
            try {
                val newChannel = controller.previousChannel()
                if (newChannel != null) {
                    _state.update { it.copy(liveChannelInfo = newChannel, epgOverlayVisible = true) }
                    UnifiedLog.d(TAG) { "Switched to previous channel: ${newChannel.name}" }
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to switch to previous channel" }
            }
        }
        return true
    }

    /**
     * Toggle EPG overlay visibility.
     *
     * When shown, displays current and next program info.
     */
    fun toggleEpgOverlay() {
        val ctx = currentContext ?: return
        if (!ctx.isLive) return

        _state.update { it.copy(epgOverlayVisible = !it.epgOverlayVisible) }
    }

    /**
     * Show EPG overlay temporarily (auto-hides after delay).
     *
     * @param autoHideDelayMs Delay before auto-hide (default 5 seconds)
     */
    fun showEpgOverlay(autoHideDelayMs: Long = 5000L) {
        val ctx = currentContext ?: return
        if (!ctx.isLive) return

        _state.update { it.copy(epgOverlayVisible = true) }

        // Auto-hide after delay
        scope.launch {
            delay(autoHideDelayMs)
            _state.update { it.copy(epgOverlayVisible = false) }
        }
    }

    /**
     * Refresh EPG data for the current channel.
     *
     * Call periodically or when user requests refresh.
     */
    fun refreshEpg() {
        val controller = livePlaybackController ?: return
        val ctx = currentContext ?: return
        if (!ctx.isLive) return

        scope.launch {
            try {
                controller.refreshEpg()
                // Update state from controller
                val channelInfo = controller.currentChannel.value
                if (channelInfo != null) {
                    _state.update { it.copy(liveChannelInfo = channelInfo) }
                }
            } catch (e: Exception) {
                UnifiedLog.d(TAG) { "EPG refresh failed: ${e.message}" }
            }
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob =
            scope.launch {
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
                    sessionElapsedMs = sessionElapsed,
                )
            }
        }
    }

    private suspend fun checkKidsGate() {
        val currentState = _state.value
        val ctx = currentState.context ?: return

        when (kidsPlaybackGate.tick(ctx, currentState.sessionElapsedMs)) {
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

    /**
     * Logs the current tracks when player reaches STATE_READY. This helps verify NextLib FFmpeg
     * codec integration.
     */
    private fun logCurrentTracks() {
        val exoPlayer = player ?: return
        val tracks = exoPlayer.currentTracks

        if (tracks.isEmpty) {
            UnifiedLog.d(TAG, "No tracks available in media")
            return
        }

        val trackInfo =
            buildString {
                appendLine("Available tracks (NextLib FFmpeg enabled):")

                tracks.groups.forEachIndexed { groupIndex, group ->
                    val trackType =
                        when (group.type) {
                            androidx.media3.common.C.TRACK_TYPE_VIDEO -> "Video"
                            androidx.media3.common.C.TRACK_TYPE_AUDIO -> "Audio"
                            androidx.media3.common.C.TRACK_TYPE_TEXT -> "Subtitle"
                            else -> "Other"
                        }

                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val isSelected = group.isTrackSelected(trackIndex)
                        val marker = if (isSelected) "▶" else " "

                        appendLine(
                            "  $marker [$trackType] ${format.sampleMimeType ?: "unknown"} " +
                                "(${format.language ?: "und"}, ${format.label ?: "no label"})",
                        )
                    }
                }
            }

        UnifiedLog.d(TAG, trackInfo)
    }
}
