package com.chris.m3usuite.player.internal.session

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.chris.m3usuite.core.playback.RememberPlayerController
import com.chris.m3usuite.data.repo.EpgRepository
import com.chris.m3usuite.player.datasource.DelegatingDataSourceFactory
import com.chris.m3usuite.player.internal.domain.DefaultKidsPlaybackGate
import com.chris.m3usuite.player.internal.domain.DefaultResumeManager
import com.chris.m3usuite.player.internal.domain.KidsGateState
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.live.DefaultLiveChannelRepository
import com.chris.m3usuite.player.internal.live.DefaultLiveEpgRepository
import com.chris.m3usuite.player.internal.live.DefaultLivePlaybackController
import com.chris.m3usuite.player.internal.live.SystemTimeProvider
import com.chris.m3usuite.player.internal.source.PlaybackSourceResolver
import com.chris.m3usuite.player.internal.source.ResolvedPlaybackSource
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.ui.util.ImageHeaders
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.chris.m3usuite.model.MediaItem as AppMediaItem

/**
 * SIP (Session Integration Point) - Builds and manages the ExoPlayer instance for the internal player.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 2 STATUS: NON-RUNTIME / REFERENCE IMPLEMENTATION
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This SIP session is part of the Phase 2 modular refactor infrastructure.
 *
 * **CURRENT STATUS:**
 * - This code path is NOT activated at runtime.
 * - Runtime flow: InternalPlayerEntry → legacy InternalPlayerScreen (monolithic)
 * - This session exists for Phase 3+ activation work.
 *
 * **LEGACY BEHAVIOR MIRRORING:**
 * - Resume logic mirrors InternalPlayerScreen L572-608, L692-722, L798-806
 * - Kids gate logic mirrors InternalPlayerScreen L547-569, L725-744
 * - Defensive guards match legacy behavior for edge cases
 *
 * **PHASE ACTIVATION ROADMAP:**
 * - Phase 3: Wire this session into InternalPlayerEntry (runtime activation)
 * - Phase 4: Sleep timer integration (playerSleepTimerMinutes)
 * - Phase 5: Subtitle support (MediaItem.subtitles field)
 * - Phase 7: RememberPlayerController full implementation
 * - Phase 8: Lifecycle management (ON_DESTROY save/clear)
 *
 * **INDEPENDENCE GUARANTEES:**
 * This session is fully self-contained and independent of:
 * - ViewModels (no ViewModel dependencies)
 * - Navigation (no NavController or routing)
 * - ObjectBox (uses abstracted repositories)
 * - Legacy screen state (no shared mutable state)
 *
 * Responsibilities:
 * - Create and configure ExoPlayer (buffering, seek increments)
 * - Wire up Media3 MediaSource via DelegatingDataSourceFactory
 * - Resolve the URL + mime type (incl. Telegram/Xtream) via PlaybackSourceResolver
 * - Push state updates into InternalPlayerUiState via onStateChanged
 * - Handle resume (load + periodic save/clear + on-ended)
 * - Handle kid/screentime gate (start + periodic tick)
 * - Log playback events into TelegramLogRepository
 *
 * This composable does NOT know about UI, DPAD, gestures or series/live logic.
 */
@Composable
fun rememberInternalPlayerSession(
    context: Context,
    url: String,
    startMs: Long?,
    mimeType: String?,
    preparedMediaItem: AppMediaItem?,
    playbackContext: PlaybackContext,
    settings: SettingsStore,
    networkHeaders: ImageHeaders,
    controller: RememberPlayerController,
    onStateChanged: (InternalPlayerUiState) -> Unit,
    onError: (PlaybackException) -> Unit,
): ExoPlayer? {
    val scope = rememberCoroutineScope()

    val resumeManager = remember(context) { DefaultResumeManager(context) }
    val kidsGate = remember(context, settings) { DefaultKidsPlaybackGate(context, settings) }

    // ════════════════════════════════════════════════════════════════════════════
    // Phase 3 Step 3.B: LivePlaybackController for LIVE sessions
    // ════════════════════════════════════════════════════════════════════════════
    //
    // Create LivePlaybackController only for LIVE playback type.
    // - Uses DefaultLiveChannelRepository to access ObxLive data
    // - Uses DefaultLiveEpgRepository to wrap existing EpgRepository
    // - Uses SystemTimeProvider for EPG overlay auto-hide timing
    val liveController =
        remember(context, settings, playbackContext.type) {
            if (playbackContext.type == PlaybackType.LIVE) {
                val liveRepo = DefaultLiveChannelRepository(context)
                val epgRepo = DefaultLiveEpgRepository(EpgRepository(context, settings))
                DefaultLivePlaybackController(
                    liveRepository = liveRepo,
                    epgRepository = epgRepo,
                    clock = SystemTimeProvider,
                )
            } else {
                null
            }
        }

    val playerState =
        remember {
            mutableStateOf(
                InternalPlayerUiState(
                    playbackType = playbackContext.type,
                ),
            )
        }
    val playerHolder = remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(url, startMs, mimeType, preparedMediaItem, networkHeaders, playbackContext.type) {
        // Release previous instance
        playerHolder.value?.release()
        playerHolder.value = null

        val headersMap = networkHeaders.asMap().toMutableMap()

        if (!headersMap.containsKey("Accept")) headersMap["Accept"] = "*/*"
        if (!headersMap.containsKey("Connection")) headersMap["Connection"] = "keep-alive"
        if (!headersMap.containsKey("Accept-Encoding")) headersMap["Accept-Encoding"] = "identity"

        val ua =
            headersMap["User-Agent"]
                ?: headersMap["user-agent"]
                ?: "IBOPlayer/1.4 (Android)"

        val isVodLike =
            playbackContext.type != com.chris.m3usuite.player.internal.domain.PlaybackType.LIVE &&
                (url.contains("/movie/", ignoreCase = true) || url.endsWith(".mp4", true))

        val httpFactory =
            DefaultHttpDataSource
                .Factory()
                .apply { if (ua.isNotBlank()) setUserAgent(ua) }
                .setAllowCrossProtocolRedirects(true)
                .apply { setDefaultRequestProperties(headersMap) }
                .setConnectTimeoutMs(if (isVodLike) 20_000 else 10_000)
                .setReadTimeoutMs(if (isVodLike) 30_000 else 15_000)

        val baseDataSource = DefaultDataSource.Factory(context, httpFactory)
        val delegatingFactory = DelegatingDataSourceFactory(context, baseDataSource)

        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    // minBufferMs =
                    15_000,
                    // maxBufferMs =
                    60_000,
                    // bufferForPlaybackMs =
                    1_000,
                    // bufferForPlaybackAfterRebufferMs =
                    3_000,
                ).build()

        val mediaSourceFactory = DefaultMediaSourceFactory(delegatingFactory)

        val newPlayer =
            ExoPlayer
                .Builder(context)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(10_000L)
                .setSeekForwardIncrementMs(10_000L)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

        playerHolder.value = newPlayer

        val resolver = PlaybackSourceResolver(context)
        val resolved: ResolvedPlaybackSource =
            resolver.resolve(
                url = url,
                explicitMimeType = mimeType,
                preparedMediaItem = preparedMediaItem,
            )

        val mediaItem =
            buildMediaItemWithTelegramExtras(
                resolved = resolved,
            )

        newPlayer.setMediaItem(mediaItem)
        newPlayer.prepare()

        // ════════════════════════════════════════════════════════════════════════
        // RESUME HANDLING - Mirrors legacy InternalPlayerScreen L572-608
        // ════════════════════════════════════════════════════════════════════════
        //
        // Phase 3 activation: This resume logic will be activated when
        // InternalPlayerEntry is switched to use this SIP session.
        //
        // Defensive guards match legacy behavior:
        // - Explicit startMs has priority over resume
        // - LIVE content skips resume (handled in ResumeManager)
        // - Position must be > 10s to be restored (handled in ResumeManager)
        // - Fail-open: On any error, continue without seeking
        try {
            val initialSeekMs: Long? =
                when {
                    // Guard: Explicit start position takes priority
                    startMs != null && startMs > 0 -> startMs
                    // Guard: LIVE playback ignores resume (defense-in-depth)
                    // Note: ResumeManager also returns null for LIVE, but this explicit
                    // check avoids the suspending call and makes the code self-documenting.
                    playbackContext.type == PlaybackType.LIVE -> null
                    // Default: Try loading resume from storage
                    else -> resumeManager.loadResumePositionMs(playbackContext)
                }

            // Guard: Only seek if position is valid and positive
            if (initialSeekMs != null && initialSeekMs > 0) {
                newPlayer.seekTo(initialSeekMs)
            }
        } catch (_: Throwable) {
            // Fail-open: Continue playback from beginning on any error
            // Matches legacy behavior at L567-569
        }

        controller.attachPlayer(newPlayer)

        // ════════════════════════════════════════════════════════════════════════
        // Phase 3 Step 3.B: LivePlaybackController initialization for LIVE sessions
        // ════════════════════════════════════════════════════════════════════════
        //
        // Initialize LivePlaybackController from PlaybackContext when type == LIVE.
        // This loads the channel list, resolves the initial channel, and fetches EPG data.
        if (liveController != null) {
            try {
                liveController.initFromPlaybackContext(playbackContext)
            } catch (_: Throwable) {
                // Fail-open: Continue playback even if live controller init fails
                // Channels and EPG will simply be unavailable
            }
        }

        // ════════════════════════════════════════════════════════════════════════
        // KIDS GATE - Mirrors legacy InternalPlayerScreen L547-569
        // ════════════════════════════════════════════════════════════════════════
        //
        // Phase 3 activation: Kids gate blocks playback when screen time is exhausted.
        //
        // Defensive guards match legacy behavior:
        // - Profile detection: currentProfileId.first() → ObxProfile lookup
        // - Kid profile type check: profile?.type == "kid"
        // - Daily quota: remainingMinutes() returns MINUTES for quota comparison
        // - Block condition: remain <= 0 → kidBlocked = true; playWhenReady = false
        // - Fail-open: On any exception, allow playback (kidActive = false)
        var kidsState: KidsGateState? = null
        try {
            val gateState = kidsGate.evaluateStart()
            kidsState = gateState

            val updated =
                playerState.value.copy(
                    kidActive = gateState.kidActive,
                    kidBlocked = gateState.kidBlocked,
                    kidProfileId = gateState.kidProfileId,
                )
            playerState.value = updated
            onStateChanged(updated)

            // Guard: Only block playback if kid is active AND blocked
            newPlayer.playWhenReady = !(gateState.kidActive && gateState.kidBlocked)
        } catch (_: Throwable) {
            // Fail-open: On any failure, fall back to autoplay
            // Matches legacy behavior at L567-569
            newPlayer.playWhenReady = true
        }

        // ════════════════════════════════════════════════════════════════════════
        // PLAYER LISTENER - Updates InternalPlayerUiState on player events
        // ════════════════════════════════════════════════════════════════════════
        //
        // Phase 3 activation: This listener will be active when SIP session is wired.
        //
        // Defensive guards for state updates:
        // - Negative positionMs: coerced to 0
        // - TIME_UNSET duration: treated as 0
        // - positionMs > durationMs: position is clamped by player internally
        newPlayer.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    // Guard: Ensure position is never negative
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    // Guard: Handle C.TIME_UNSET (-1) by treating as 0
                    val dur =
                        player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L)
                            ?: 0L
                    val isPlaying = player.isPlaying
                    val isBuffering = player.playbackState == Player.STATE_BUFFERING
                    val speed = player.playbackParameters.speed
                    val error = player.playerError

                    val newState =
                        playerState.value.copy(
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionMs = pos,
                            durationMs = dur,
                            playbackSpeed = speed,
                            playbackError = error,
                        )

                    playerState.value = newState
                    onStateChanged(newState)

                    if (resolved.isTelegram) {
                        TelegramLogRepository.debug(
                            source = "InternalPlayer",
                            message = "Telegram playback state change",
                            details =
                                mapOf(
                                    "url" to url,
                                    "mimeType" to resolved.mimeType,
                                    "positionMs" to pos.toString(),
                                    "durationMs" to dur.toString(),
                                    "isPlaying" to isPlaying.toString(),
                                    "isBuffering" to isBuffering.toString(),
                                    "playbackType" to playbackContext.type.name,
                                ),
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val newState =
                        playerState.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            playbackError = error,
                        )
                    playerState.value = newState
                    onStateChanged(newState)

                    TelegramLogRepository.error(
                        source = "InternalPlayer",
                        message = "Playback error: ${error.message}",
                        details =
                            mapOf(
                                "url" to url,
                                "mimeType" to (mimeType ?: "unknown"),
                                "playbackType" to playbackContext.type.name,
                            ),
                        exception = error,
                    )
                    onError(error)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // ════════════════════════════════════════════════════════════════
                    // PLAYBACK ENDED - Mirrors legacy InternalPlayerScreen L798-806
                    // ════════════════════════════════════════════════════════════════
                    //
                    // Phase 3 activation: Resume is cleared when playback reaches end.
                    //
                    // Defensive guards match legacy behavior:
                    // - Only triggers on STATE_ENDED (natural end of playback)
                    // - LIVE content: No resume to clear (handled in ResumeManager)
                    // - Fail-open: On any exception, continue silently
                    if (playbackState == Player.STATE_ENDED) {
                        scope.launch {
                            try {
                                resumeManager.handleEnded(playbackContext)
                            } catch (_: Throwable) {
                                // Fail-open: Continue silently on error
                                // Matches legacy behavior
                            }
                        }
                    }
                }
            },
        )

        // TODO: Sleep-Timer feature not yet implemented in SettingsStore
        // Uncomment when SettingsStore.playerSleepTimerMinutes is available (Phase 4+)

        /*
        val sleepMinutes = settings.playerSleepTimerMinutes
        if (sleepMinutes > 0) {
            val total = sleepMinutes * 60_000L
            val startTime = System.currentTimeMillis()
            scope.launch {
                while (isActive) {
                    if (playerHolder.value !== newPlayer) break
                    delay(1_000)
                    val elapsed = System.currentTimeMillis() - startTime
                    val remaining = max(0L, total - elapsed)
                    val newState = playerState.value.copy(sleepTimerRemainingMs = remaining)
                    playerState.value = newState
                    onStateChanged(newState)
                    if (remaining <= 0L) {
                        newPlayer.pause()
                        break
                    }
                }
            }
        }
         */

        // ════════════════════════════════════════════════════════════════════════════
        // PERIODIC TICK (~3s) - Resume save + Kids gate tick
        // ════════════════════════════════════════════════════════════════════════════
        //
        // Mirrors legacy InternalPlayerScreen:
        // - Resume tick: L692-722 (save resume every ~3s for VOD/SERIES)
        // - Kids tick: L725-744 (60s accumulation before quota decrement)
        //
        // Phase 3 activation: This loop will be active when SIP session is wired.
        //
        // Defensive guards match legacy behavior:
        // - Negative durationMs: Handled by ResumeManager (skips if dur <= 0)
        // - positionMs > durationMs: ResumeManager uses remaining calculation
        // - LIVE playback: ResumeManager ignores resume for LIVE type
        // - Kids gate: Only ticks when kidActive && playWhenReady && isPlaying
        // - Block transitions: kidBlocked triggers playWhenReady = false
        // - Fail-open: All exceptions caught, continue tick loop
        scope.launch {
            var tickAccumSecs = 0
            while (isActive) {
                if (playerHolder.value !== newPlayer) break

                try {
                    val pos = newPlayer.currentPosition
                    val dur = newPlayer.duration

                    // ────────────────────────────────────────────────────────────────
                    // Resume handling (VOD / Series) - Mirrors L692-722
                    // ────────────────────────────────────────────────────────────────
                    // Defensive guards in ResumeManager:
                    // - LIVE content: No-op
                    // - Invalid duration (<=0): No-op
                    // - Near-end (<10s remaining): Clear resume
                    // - Normal: Save current position
                    resumeManager.handlePeriodicTick(
                        context = playbackContext,
                        positionMs = pos,
                        durationMs = dur,
                    )

                    // ────────────────────────────────────────────────────────────────
                    // Screen-Time tick - Mirrors L725-744
                    // ────────────────────────────────────────────────────────────────
                    // Defensive guards:
                    // - Only tick when kidActive AND actually playing
                    // - 60-second accumulation before quota decrement
                    // - Block transition: Set playWhenReady = false
                    // - Reset accumulation when paused or not kid profile
                    val currentKids = kidsState
                    if (currentKids != null &&
                        currentKids.kidActive &&
                        newPlayer.playWhenReady &&
                        newPlayer.isPlaying
                    ) {
                        tickAccumSecs += 3
                        if (tickAccumSecs >= 60) {
                            val updatedKids = kidsGate.onPlaybackTick(currentKids, tickAccumSecs)
                            kidsState = updatedKids
                            tickAccumSecs = 0

                            // Log when the screen-time limit triggers a block
                            if (!currentKids.kidBlocked && updatedKids.kidBlocked) {
                                TelegramLogRepository.info(
                                    source = "InternalPlayer",
                                    message = "Kids screen-time limit reached, blocking playback",
                                    details =
                                        mapOf(
                                            "profileId" to (updatedKids.kidProfileId?.toString() ?: "null"),
                                            "playbackType" to playbackContext.type.name,
                                            "url" to url,
                                        ),
                                )
                            }

                            val updatedState =
                                playerState.value.copy(
                                    kidActive = updatedKids.kidActive,
                                    kidBlocked = updatedKids.kidBlocked,
                                    kidProfileId = updatedKids.kidProfileId,
                                )
                            playerState.value = updatedState
                            onStateChanged(updatedState)

                            if (updatedKids.kidBlocked) {
                                newPlayer.playWhenReady = false
                            }
                        }
                    } else {
                        // Reset accumulation when not playing or not kid profile
                        tickAccumSecs = 0
                    }
                } catch (_: Throwable) {
                    // Fail-open: Continue tick loop on any error
                }

                delay(3_000)
            }
        }

        // ════════════════════════════════════════════════════════════════════════════
        // Phase 3 Step 3.B: LivePlaybackController StateFlow → UiState mapping
        // ════════════════════════════════════════════════════════════════════════════
        //
        // Collect LivePlaybackController StateFlows and map them into InternalPlayerUiState.
        // This is a one-way mapping (controller → UiState). The session does not push UI
        // state back into the controller.
        //
        // Mapping:
        // - currentChannel.value?.name → liveChannelName
        // - epgOverlay.nowTitle → liveNowTitle
        // - epgOverlay.nextTitle → liveNextTitle
        // - epgOverlay.visible → epgOverlayVisible
        //
        // For non-LIVE playback types, the controller is null and these fields remain at
        // their defaults (null / false).
        if (liveController != null) {
            // Collect currentChannel StateFlow
            scope.launch {
                liveController.currentChannel.collect { channel ->
                    if (playerHolder.value !== newPlayer) return@collect

                    val updated =
                        playerState.value.copy(
                            liveChannelName = channel?.name,
                        )
                    playerState.value = updated
                    onStateChanged(updated)
                }
            }

            // Collect epgOverlay StateFlow
            scope.launch {
                liveController.epgOverlay.collect { overlay ->
                    if (playerHolder.value !== newPlayer) return@collect

                    val updated =
                        playerState.value.copy(
                            liveNowTitle = overlay.nowTitle,
                            liveNextTitle = overlay.nextTitle,
                            epgOverlayVisible = overlay.visible,
                        )
                    playerState.value = updated
                    onStateChanged(updated)
                }
            }
        }
    }

    // Release when the composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            playerHolder.value?.release()
            playerHolder.value = null
        }
    }

    return playerHolder.value
}

private fun buildMediaItemWithTelegramExtras(resolved: ResolvedPlaybackSource): MediaItem {
    val builder =
        MediaItem
            .Builder()
            .setUri(resolved.uri)
            .setMimeType(resolved.mimeType)

    val appItem = resolved.appMediaItem

    // TODO: Subtitles support - AppMediaItem.subtitles field not yet implemented
    // Uncomment when MediaItem has subtitles field (Phase 4+)

    /*
    // Subtitles from AppMediaItem (keeps Xtream + Telegram behaviour)
    appItem?.subtitles?.forEach { sub ->
        val subUri = Uri.parse(sub.url)
        val subMime = sub.mimeType ?: "text/vtt"
        val subConfig =
            MediaItem.SubtitleConfiguration
                .Builder(subUri)
                .setMimeType(subMime)
                .setLanguage(sub.language)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        builder.setSubtitleConfigurations(
            builder.build().subtitleConfigurations + subConfig,
        )
    }
     */

    val metadataBuilder =
        MediaMetadata
            .Builder()
            .setTitle(appItem?.name ?: "")

    // TODO: Artwork support - playerArtwork() returns wrong type
    // Uncomment when MediaItem.playerArtwork() returns ByteArray? (Phase 4+)

    /*
    // Artwork from AppMediaItem, if provided
    val artwork = appItem?.playerArtwork()
    if (artwork != null) {
        metadataBuilder.setArtworkData(
            artwork,
            MediaMetadata.PICTURE_TYPE_FRONT_COVER,
        )
    }
     */

    builder.setMediaMetadata(metadataBuilder.build())

    return builder.build()
}

/**
 * Thin command helpers so the screen/controller never talks
 * to ExoPlayer directly.
 */
fun applyPlayerCommand_PlayPause(player: ExoPlayer?) {
    player ?: return
    if (player.isPlaying) player.pause() else player.play()
}

fun applyPlayerCommand_SeekBy(
    player: ExoPlayer?,
    deltaMs: Long,
) {
    player ?: return
    val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
    player.seekTo(target)
}

fun applyPlayerCommand_SeekTo(
    player: ExoPlayer?,
    positionMs: Long,
) {
    player ?: return
    player.seekTo(positionMs.coerceAtLeast(0L))
}

fun applyPlayerCommand_ChangeSpeed(
    player: ExoPlayer?,
    speed: Float,
) {
    player ?: return
    player.playbackParameters = PlaybackParameters(speed)
}

fun applyPlayerCommand_ToggleLoop(
    player: ExoPlayer?,
    looping: Boolean,
) {
    player ?: return
    player.repeatMode =
        if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
}
