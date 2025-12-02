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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.chris.m3usuite.core.playback.RememberPlayerController
import com.chris.m3usuite.data.repo.EpgRepository
import com.chris.m3usuite.model.MediaItem as AppMediaItem
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.player.datasource.DelegatingDataSourceFactory
import com.chris.m3usuite.player.internal.domain.DefaultKidsPlaybackGate
import com.chris.m3usuite.player.internal.domain.DefaultResumeManager
import com.chris.m3usuite.player.internal.domain.KidsGateState
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.live.DefaultLiveChannelRepository
import com.chris.m3usuite.player.internal.live.DefaultLiveEpgRepository
import com.chris.m3usuite.player.internal.live.DefaultLivePlaybackController
import com.chris.m3usuite.player.internal.live.LivePlaybackController
import com.chris.m3usuite.player.internal.live.SystemTimeProvider
import com.chris.m3usuite.player.internal.source.PlaybackSourceResolver
import com.chris.m3usuite.player.internal.source.ResolvedPlaybackSource
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.domain.TelegramStreamingSettingsProviderHolder
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.player.buildTelegramLoadControl
import com.chris.m3usuite.ui.util.ImageHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * BUG 2 FIX: Result holder for rememberInternalPlayerSession.
 *
 * This data class exposes both the ExoPlayer and the LivePlaybackController, allowing
 * InternalPlayerEntry to wire the onJumpLiveChannel callback properly.
 */
data class InternalPlayerSessionResult(
        val player: ExoPlayer?,
        val liveController: LivePlaybackController?,
)

/**
 * SIP (Session Integration Point) - Manages the ExoPlayer instance for the internal player.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 STATUS: USES SHARED PlaybackSession
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * **PHASE 7 CHANGES:**
 * - Now uses `PlaybackSession.acquire()` instead of creating its own ExoPlayer instance
 * - Enables MiniPlayer continuity: same player survives Full↔MiniPlayer transitions
 * - Player is NOT released on dispose - managed by PlaybackSession singleton
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
 * - Phase 7: PlaybackSession integration (COMPLETE)
 * - Phase 8: Lifecycle management (ON_DESTROY save/clear)
 *
 * **INDEPENDENCE GUARANTEES:** This session is fully self-contained and independent of:
 * - ViewModels (no ViewModel dependencies)
 * - Navigation (no NavController or routing)
 * - ObjectBox (uses abstracted repositories)
 * - Legacy screen state (no shared mutable state)
 *
 * **PlaybackSession Integration (Phase 7):**
 * - Uses `PlaybackSession.acquire(context) { ... }` to get shared player instance
 * - Configures player with appropriate buffering and seek increments
 * - Does NOT release player on dispose (shared ownership via PlaybackSession)
 * - Sets source URL via `PlaybackSession.setSource(url)` for MiniPlayer visibility
 *
 * Responsibilities:
 * - Configure ExoPlayer (buffering, seek increments) via PlaybackSession
 * - Wire up Media3 MediaSource via DelegatingDataSourceFactory
 * - Resolve the URL + mime type (incl. Telegram/Xtream) via PlaybackSourceResolver
 * - Push state updates into InternalPlayerUiState via onStateChanged
 * - Handle resume (load + periodic save/clear + on-ended)
 * - Handle kid/screentime gate (start + periodic tick)
 * - Log playback events into TelegramLogRepository
 *
 * This composable does NOT know about UI, DPAD, gestures or series/live logic.
 *
 * @see PlaybackSession for the shared player management singleton
 *
 * @return InternalPlayerSessionResult containing the ExoPlayer and LivePlaybackController (if LIVE
 * type)
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
): InternalPlayerSessionResult {
    val scope = rememberCoroutineScope()

    val resumeManager = remember(context) { DefaultResumeManager(context) }
    val kidsGate = remember(context, settings) { DefaultKidsPlaybackGate(context, settings) }
    val streamingSettingsProvider =
            remember(context) { TelegramStreamingSettingsProviderHolder.get(context) }

    // ════════════════════════════════════════════════════════════════════════════
    // Phase 4 Group 3: Subtitle Style & Track Selection
    // ════════════════════════════════════════════════════════════════════════════
    //
    // Create subtitle managers for style and track selection.
    // - SubtitleStyleManager: Manages per-profile subtitle styling via DataStore
    // - SubtitleSelectionPolicy: Selects subtitle tracks based on language preferences
    val subtitleStyleManager =
            remember(settings, scope) {
                com.chris.m3usuite.player.internal.subtitles.DefaultSubtitleStyleManager(
                        settingsStore = settings,
                        scope = scope,
                )
            }

    val subtitleSelectionPolicy =
            remember(settings) {
                com.chris.m3usuite.player.internal.subtitles.DefaultSubtitleSelectionPolicy(
                        settingsStore = settings,
                )
            }

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

    // ════════════════════════════════════════════════════════════════════════════
    // Phase 3 Step 3.D: LivePlaybackController → UI callback wiring (TODO)
    // ════════════════════════════════════════════════════════════════════════════
    //
    // When a SIP screen uses this session + InternalPlayerContent, it must create
    // an InternalPlayerController that wires LivePlaybackController to the UI.
    //
    // Example wiring (for future SIP screen implementation):
    //
    // val controller = remember(liveController) {
    //     InternalPlayerController(
    //         onPlayPause = { /* ... */ },
    //         onSeekTo = { /* ... */ },
    //         // ... other callbacks ...
    //         onJumpLiveChannel = { delta ->
    //             if (playbackContext.type == PlaybackType.LIVE) {
    //                 liveController?.jumpChannel(delta)
    //             }
    //         }
    //     )
    // }
    //
    // Then pass this controller to InternalPlayerContent(..., controller = controller).
    //
    // This allows PlayerSurface gestures → onJumpLiveChannel → LivePlaybackController.jumpChannel.

    val playerState = remember {
        mutableStateOf(
                InternalPlayerUiState(
                        playbackType = playbackContext.type,
                ),
        )
    }
    val playerHolder = remember { mutableStateOf<ExoPlayer?>(null) }

    // ════════════════════════════════════════════════════════════════════════
    // BUFFERING WATCHDOG (Task 2) - Track buffering duration for Telegram VOD
    // ════════════════════════════════════════════════════════════════════════
    val bufferingStartTime = remember { mutableStateOf<Long?>(null) }
    val bufferingWatchdogTimeoutMs = 15_000L // 15 seconds
    val lastBufferingLogTime = remember { mutableStateOf<Long>(0L) }
    val bufferingLogThrottleMs = 30_000L // Log at most once every 30 seconds

    LaunchedEffect(
            url,
            startMs,
            mimeType,
            preparedMediaItem,
            networkHeaders,
            playbackContext.type
    ) {
        // ════════════════════════════════════════════════════════════════════════════
        // PHASE 7: Use PlaybackSession.acquire() instead of creating ExoPlayer directly
        // ════════════════════════════════════════════════════════════════════════════
        //
        // This change enables MiniPlayer continuity:
        // - Same player instance survives Full↔MiniPlayer transitions
        // - Player is NOT destroyed when navigating away from player screen
        // - PlaybackSession.acquire() returns existing player if one exists
        //
        // Note: We do NOT release the previous player instance anymore.
        // The shared player is managed by PlaybackSession singleton.
        // If source URL changes, we stop and set new media item instead.

        val headersMap = networkHeaders.asMap().toMutableMap()

        if (!headersMap.containsKey("Accept")) headersMap["Accept"] = "*/*"
        if (!headersMap.containsKey("Connection")) headersMap["Connection"] = "keep-alive"
        if (!headersMap.containsKey("Accept-Encoding")) headersMap["Accept-Encoding"] = "identity"

        val ua = headersMap["User-Agent"] ?: headersMap["user-agent"] ?: "IBOPlayer/1.4 (Android)"

        val isVodLike =
                playbackContext.type !=
                        com.chris.m3usuite.player.internal.domain.PlaybackType.LIVE &&
                        (url.contains("/movie/", ignoreCase = true) || url.endsWith(".mp4", true))

        val httpFactory =
                DefaultHttpDataSource.Factory()
                        .apply { if (ua.isNotBlank()) setUserAgent(ua) }
                        .setAllowCrossProtocolRedirects(true)
                        .apply { setDefaultRequestProperties(headersMap) }
                        .setConnectTimeoutMs(if (isVodLike) 20_000 else 10_000)
                        .setReadTimeoutMs(if (isVodLike) 30_000 else 15_000)

        val baseDataSource = DefaultDataSource.Factory(context, httpFactory)
        val delegatingFactory = DelegatingDataSourceFactory(context, baseDataSource)

        val streamingSettings = streamingSettingsProvider.currentSettings
        val loadControl = buildTelegramLoadControl(streamingSettings)
        val seekParameters =
                if (streamingSettings.exoExactSeek) SeekParameters.EXACT
                else SeekParameters.CLOSEST_SYNC

        val mediaSourceFactory = DefaultMediaSourceFactory(delegatingFactory)

        // ════════════════════════════════════════════════════════════════════════════
        // PHASE 7: Acquire shared player from PlaybackSession
        // ════════════════════════════════════════════════════════════════════════════
        //
        // Instead of: ExoPlayer.Builder(context).build()
        // Use: PlaybackSession.acquire(context) { ExoPlayer.Builder(context)...build() }
        //
        // This ensures:
        // - Only one ExoPlayer instance per process
        // - Player survives Full↔MiniPlayer transitions
        // - Consistent state management across the app
        val holder =
                PlaybackSession.acquire(context) {
                    ExoPlayer.Builder(context)
                            .setLoadControl(loadControl)
                            .setSeekBackIncrementMs(10_000L)
                            .setSeekForwardIncrementMs(10_000L)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .build()
                }

        val newPlayer = holder.player
        newPlayer.seekParameters = seekParameters
        playerHolder.value = newPlayer

        // Store source URL for MiniPlayer visibility checks
        PlaybackSession.setSource(url, playbackContext.type)

        val resolver = PlaybackSourceResolver(context)
        val resolved: ResolvedPlaybackSource =
                resolver.resolve(
                        url = url,
                        explicitMimeType = mimeType,
                        preparedMediaItem = preparedMediaItem,
                )

        // ════════════════════════════════════════════════════════════════════════════
        // BUG 1 FIX: Populate debug diagnostic fields after source resolution
        // Initialize durationMs from Telegram URL if available
        // ════════════════════════════════════════════════════════════════════════════
        val truncatedUrl = if (url.length > 100) url.take(100) + "..." else url
        // Use Telegram duration if available, otherwise keep player default (0L)
        // The player will update durationMs once it parses the media container
        val initialDurationMs = resolved.telegramDurationMs
        val debugUpdated =
                playerState.value.copy(
                        debugPlaybackUrl = truncatedUrl,
                        debugResolvedMimeType = resolved.mimeType,
                        debugInferredExtension = resolved.inferredExtension,
                        debugIsLiveFromUrl = resolved.isLiveFromUrl,
                        durationMs = initialDurationMs ?: playerState.value.durationMs,
                )
        playerState.value = debugUpdated
        onStateChanged(debugUpdated)

        if (resolved.isTelegram && resolved.telegramDurationMs != null) {
            TelegramLogRepository.info(
                    source = "InternalPlayerSession",
                    message = "Initialized duration from Telegram URL",
                    details =
                            mapOf(
                                    "durationMs" to resolved.telegramDurationMs.toString(),
                                    "fileSizeBytes" to
                                            (resolved.telegramFileSizeBytes?.toString()
                                                    ?: "unknown"),
                            ),
            )
        }

        val mediaItem =
                buildMediaItemWithTelegramExtras(
                        resolved = resolved,
                )

        // ════════════════════════════════════════════════════════════════════════
        // TELEGRAM PLAYBACK LOGGING (Task 1) - Log setMediaItem and prepare calls
        // ════════════════════════════════════════════════════════════════════════
        if (resolved.isTelegram) {
            TelegramLogRepository.info(
                    source = "InternalPlayerSession",
                    message = "setMediaItem() called for Telegram VOD",
                    details =
                            mapOf(
                                    "url" to url,
                                    "playbackType" to playbackContext.type.name,
                                    "mimeType" to (resolved.mimeType ?: "unknown"),
                                    "chatId" to
                                            (android.net.Uri.parse(url).getQueryParameter("chatId")
                                                    ?: "unknown"),
                                    "messageId" to
                                            (android.net.Uri.parse(url)
                                                    .getQueryParameter("messageId")
                                                    ?: "unknown"),
                                    "fileId" to
                                            (android.net.Uri.parse(url).pathSegments.lastOrNull()
                                                    ?: "unknown"),
                            ),
            )
        }

        newPlayer.setMediaItem(mediaItem)

        if (resolved.isTelegram) {
            TelegramLogRepository.info(
                    source = "InternalPlayerSession",
                    message = "prepare() called for Telegram VOD",
                    details =
                            mapOf(
                                    "url" to url,
                                    "playbackType" to playbackContext.type.name,
                            ),
            )
        }

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
                            remainingKidsMinutes = gateState.remainingMinutes,
                    )
            playerState.value = updated
            onStateChanged(updated)

            // Guard: Only block playback if kid is active AND blocked
            val shouldPlay = !(gateState.kidActive && gateState.kidBlocked)
            newPlayer.playWhenReady = shouldPlay

            // ════════════════════════════════════════════════════════════════════════
            // TELEGRAM PLAYBACK LOGGING (Task 1) - Log play() call
            // ════════════════════════════════════════════════════════════════════════
            if (resolved.isTelegram) {
                TelegramLogRepository.info(
                        source = "InternalPlayerSession",
                        message = "play() called (playWhenReady set) for Telegram VOD",
                        details =
                                mapOf(
                                        "url" to url,
                                        "playbackType" to playbackContext.type.name,
                                        "playWhenReady" to shouldPlay.toString(),
                                        "kidActive" to gateState.kidActive.toString(),
                                        "kidBlocked" to gateState.kidBlocked.toString(),
                                ),
                )
            }
        } catch (_: Throwable) {
            // Fail-open: On any failure, fall back to autoplay
            // Matches legacy behavior at L567-569
            newPlayer.playWhenReady = true

            if (resolved.isTelegram) {
                TelegramLogRepository.info(
                        source = "InternalPlayerSession",
                        message = "play() called (playWhenReady set - fallback) for Telegram VOD",
                        details =
                                mapOf(
                                        "url" to url,
                                        "playbackType" to playbackContext.type.name,
                                        "playWhenReady" to "true",
                                        "reason" to "kids_gate_exception",
                                ),
                )
            }
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

                        // ════════════════════════════════════════════════════════════════
                        // BUFFERING WATCHDOG (Task 2) - Track buffering duration
                        // ════════════════════════════════════════════════════════════════
                        if (resolved.isTelegram && playbackContext.type != PlaybackType.LIVE) {
                            if (isBuffering && bufferingStartTime.value == null) {
                                // Entering buffering state - start timer
                                bufferingStartTime.value = System.currentTimeMillis()
                                TelegramLogRepository.debug(
                                        source = "InternalPlayer",
                                        message = "Telegram VOD entered BUFFERING state",
                                        details =
                                                mapOf(
                                                        "url" to url,
                                                        "positionMs" to pos.toString(),
                                                        "durationMs" to dur.toString(),
                                                ),
                                )
                            } else if (!isBuffering && bufferingStartTime.value != null) {
                                // Exiting buffering state - clear timer
                                val bufferingDuration =
                                        System.currentTimeMillis() - bufferingStartTime.value!!
                                bufferingStartTime.value = null
                                TelegramLogRepository.debug(
                                        source = "InternalPlayer",
                                        message = "Telegram VOD exited BUFFERING state",
                                        details =
                                                mapOf(
                                                        "url" to url,
                                                        "positionMs" to pos.toString(),
                                                        "bufferingDurationMs" to
                                                                bufferingDuration.toString(),
                                                ),
                                )
                            } else if (isBuffering && bufferingStartTime.value != null) {
                                // Still buffering - check if timeout exceeded
                                val bufferingDuration =
                                        System.currentTimeMillis() - bufferingStartTime.value!!
                                val now = System.currentTimeMillis()
                                val timeSinceLastLog = now - lastBufferingLogTime.value

                                if (bufferingDuration > bufferingWatchdogTimeoutMs &&
                                                timeSinceLastLog > bufferingLogThrottleMs
                                ) {
                                    // Log diagnostic event (throttled to avoid spam)
                                    lastBufferingLogTime.value = now
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val uri = android.net.Uri.parse(url)
                                            val fileIdStr =
                                                    uri.pathSegments.lastOrNull() ?: "unknown"
                                            val fileId = fileIdStr.toIntOrNull() ?: 0

                                            val downloader =
                                                    com.chris.m3usuite.telegram.core
                                                            .T_TelegramServiceClient.getInstance(
                                                                    context,
                                                            )
                                                            .downloader()
                                            val fileInfo =
                                                    if (fileId > 0) downloader.getFileInfo(fileId)
                                                    else null

                                            val downloadedPrefix =
                                                    fileInfo?.local?.downloadedPrefixSize ?: 0
                                            val expectedSize = fileInfo?.expectedSize ?: 0

                                            TelegramLogRepository.warn(
                                                    source = "InternalPlayer",
                                                    message =
                                                            "Telegram VOD buffering watchdog triggered",
                                                    details =
                                                            mapOf(
                                                                    "url" to url,
                                                                    "positionMs" to pos.toString(),
                                                                    "durationMs" to dur.toString(),
                                                                    "bufferingDurationMs" to
                                                                            bufferingDuration
                                                                                    .toString(),
                                                                    "fileId" to fileIdStr,
                                                                    "downloadedPrefixSize" to
                                                                            downloadedPrefix
                                                                                    .toString(),
                                                                    "expectedSize" to
                                                                            expectedSize.toString(),
                                                                    "playWhenReady" to
                                                                            player.playWhenReady
                                                                                    .toString(),
                                                                    "playbackState" to
                                                                            when (player.playbackState
                                                                            ) {
                                                                                Player.STATE_IDLE ->
                                                                                        "IDLE"
                                                                                Player.STATE_BUFFERING ->
                                                                                        "BUFFERING"
                                                                                Player.STATE_READY ->
                                                                                        "READY"
                                                                                Player.STATE_ENDED ->
                                                                                        "ENDED"
                                                                                else -> "UNKNOWN"
                                                                            },
                                                            ),
                                            )
                                        } catch (e: Exception) {
                                            TelegramLogRepository.error(
                                                    source = "InternalPlayer",
                                                    message =
                                                            "Failed to collect buffering watchdog diagnostics",
                                                    exception = e,
                                            )
                                        }
                                    }
                                }
                            }
                        }

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
                        // TELEGRAM PLAYBACK LOGGING (Task 1) - Log playback state changes
                        // ════════════════════════════════════════════════════════════════
                        if (resolved.isTelegram) {
                            val stateStr =
                                    when (playbackState) {
                                        Player.STATE_IDLE -> "IDLE"
                                        Player.STATE_BUFFERING -> "BUFFERING"
                                        Player.STATE_READY -> "READY"
                                        Player.STATE_ENDED -> "ENDED"
                                        else -> "UNKNOWN($playbackState)"
                                    }

                            TelegramLogRepository.info(
                                    source = "InternalPlayerSession",
                                    message = "onPlaybackStateChanged for Telegram VOD",
                                    details =
                                            mapOf(
                                                    "url" to url,
                                                    "playbackType" to playbackContext.type.name,
                                                    "newState" to stateStr,
                                                    "playWhenReady" to
                                                            newPlayer.playWhenReady.toString(),
                                                    "isPlaying" to newPlayer.isPlaying.toString(),
                                                    "positionMs" to
                                                            newPlayer.currentPosition.toString(),
                                            ),
                            )

                            // Confirm we reach STATE_READY or STATE_PLAYING after ensureFileReady
                            // success
                            if (playbackState == Player.STATE_READY || newPlayer.isPlaying) {
                                TelegramLogRepository.info(
                                        source = "InternalPlayerSession",
                                        message = "Telegram VOD reached playable state",
                                        details =
                                                mapOf(
                                                        "url" to url,
                                                        "playbackType" to playbackContext.type.name,
                                                        "state" to stateStr,
                                                        "playWhenReady" to
                                                                newPlayer.playWhenReady.toString(),
                                                        "isPlaying" to
                                                                newPlayer.isPlaying.toString(),
                                                ),
                                )
                            }
                        }

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

                    override fun onPlayWhenReadyChanged(
                            playWhenReady: Boolean,
                            reason: Int,
                    ) {
                        // ════════════════════════════════════════════════════════════════
                        // TELEGRAM PLAYBACK LOGGING (Task 1) - Log playWhenReady changes
                        // ════════════════════════════════════════════════════════════════
                        if (resolved.isTelegram) {
                            val reasonStr =
                                    when (reason) {
                                        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ->
                                                "USER_REQUEST"
                                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ->
                                                "AUDIO_FOCUS_LOSS"
                                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ->
                                                "AUDIO_BECOMING_NOISY"
                                        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
                                        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM ->
                                                "END_OF_MEDIA_ITEM"
                                        else -> "UNKNOWN($reason)"
                                    }

                            TelegramLogRepository.info(
                                    source = "InternalPlayerSession",
                                    message = "onPlayWhenReadyChanged for Telegram VOD",
                                    details =
                                            mapOf(
                                                    "url" to url,
                                                    "playbackType" to playbackContext.type.name,
                                                    "playWhenReady" to playWhenReady.toString(),
                                                    "reason" to reasonStr,
                                                    "playbackState" to
                                                            when (newPlayer.playbackState) {
                                                                Player.STATE_IDLE -> "IDLE"
                                                                Player.STATE_BUFFERING ->
                                                                        "BUFFERING"
                                                                Player.STATE_READY -> "READY"
                                                                Player.STATE_ENDED -> "ENDED"
                                                                else -> "UNKNOWN"
                                                            },
                                                    "isPlaying" to newPlayer.isPlaying.toString(),
                                            ),
                            )
                        }
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        // ════════════════════════════════════════════════════════════════
                        // SUBTITLE TRACK SELECTION - Phase 4 Group 3
                        // ════════════════════════════════════════════════════════════════
                        //
                        // Select initial subtitle track based on user preferences and kid mode.
                        //
                        // Defensive guards:
                        // - Kid Mode: No subtitle track selected (returns null from policy)
                        // - LIVE content: Subtitle tracks allowed but not persisted
                        // - No tracks available: No-op
                        // - Fail-open: On any exception, continue without subtitles
                        try {
                            val isKidMode = kidsState?.kidActive == true

                            // Extract subtitle tracks from Media3 Tracks
                            val availableSubtitleTracks =
                                    mutableListOf<
                                            com.chris.m3usuite.player.internal.subtitles.SubtitleTrack>()
                            for (trackGroup in tracks.groups) {
                                val group = trackGroup.mediaTrackGroup
                                if (group.type == C.TRACK_TYPE_TEXT) {
                                    for (i in 0 until group.length) {
                                        val format = group.getFormat(i)
                                        availableSubtitleTracks.add(
                                                com.chris.m3usuite.player.internal.subtitles
                                                        .SubtitleTrack(
                                                                groupIndex =
                                                                        tracks.groups.indexOf(
                                                                                trackGroup
                                                                        ),
                                                                trackIndex = i,
                                                                language = format.language,
                                                                label = format.label
                                                                                ?: format.language
                                                                                        ?: "Unknown",
                                                                isDefault =
                                                                        (format.selectionFlags and
                                                                                C.SELECTION_FLAG_DEFAULT) !=
                                                                                0,
                                                        ),
                                        )
                                    }
                                }
                            }

                            // Always update available tracks in UiState (for CC button visibility)
                            val tracksUpdated =
                                    playerState.value.copy(
                                            availableSubtitleTracks =
                                                    availableSubtitleTracks.toList(),
                                    )
                            playerState.value = tracksUpdated
                            onStateChanged(tracksUpdated)

                            if (availableSubtitleTracks.isNotEmpty()) {
                                // Get system language and profile languages (TODO: actual profile
                                // language prefs)
                                val systemLang = java.util.Locale.getDefault().language
                                val preferredLanguages = listOf(systemLang)

                                // Select initial track using policy
                                val selectedTrack =
                                        subtitleSelectionPolicy.selectInitialTrack(
                                                availableTracks = availableSubtitleTracks,
                                                preferredLanguages = preferredLanguages,
                                                playbackType = playbackContext.type,
                                                isKidMode = isKidMode,
                                        )

                                // Apply track selection to Media3
                                if (selectedTrack != null && !isKidMode) {
                                    // Find the corresponding track group
                                    val trackGroupIndex = selectedTrack.groupIndex
                                    if (trackGroupIndex >= 0 && trackGroupIndex < tracks.groups.size
                                    ) {
                                        val trackGroup = tracks.groups[trackGroupIndex]
                                        val mediaTrackGroup = trackGroup.mediaTrackGroup

                                        // Create TrackSelectionOverride for the selected track
                                        val override =
                                                androidx.media3.common.TrackSelectionOverride(
                                                        mediaTrackGroup,
                                                        listOf(selectedTrack.trackIndex),
                                                )

                                        // Apply the override to the player
                                        newPlayer.trackSelectionParameters =
                                                newPlayer
                                                        .trackSelectionParameters
                                                        .buildUpon()
                                                        .setOverrideForType(override)
                                                        .build()
                                    }

                                    // Update UiState with selected track
                                    val updated =
                                            playerState.value.copy(
                                                    selectedSubtitleTrack = selectedTrack,
                                            )
                                    playerState.value = updated
                                    onStateChanged(updated)
                                } else {
                                    // No track selected or Kid Mode: clear subtitle track
                                    newPlayer.trackSelectionParameters =
                                            newPlayer
                                                    .trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                    .build()

                                    val updated =
                                            playerState.value.copy(
                                                    selectedSubtitleTrack = null,
                                            )
                                    playerState.value = updated
                                    onStateChanged(updated)
                                }
                            }
                        } catch (_: Throwable) {
                            // Fail-open: Continue without subtitles on error
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

                    // Live playback: keep EPG overlay timing/stale detection running
                    if (liveController != null) {
                        liveController.onPlaybackPositionChanged(pos)
                        scope.launch(Dispatchers.IO) {
                            try {
                                liveController.refreshEpgIfRequested()
                            } catch (_: Throwable) {
                                // swallow: live refresh is best-effort
                            }
                        }
                    }

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
                                        message =
                                                "Kids screen-time limit reached, blocking playback",
                                        details =
                                                mapOf(
                                                        "profileId" to
                                                                (updatedKids.kidProfileId
                                                                        ?.toString()
                                                                        ?: "null"),
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
                                            remainingKidsMinutes = updatedKids.remainingMinutes,
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
                var lastChannelId: Long? = null
                liveController.currentChannel.collect { channel ->
                    if (playerHolder.value !== newPlayer) return@collect
                    val changed = channel?.id != lastChannelId

                    val updated =
                            playerState.value.copy(
                                    liveChannelName = channel?.name,
                                    controlsVisible = true,
                                    controlsTick = playerState.value.controlsTick + 1,
                            )
                    playerState.value = updated
                    onStateChanged(updated)

                    if (changed && channel != null) {
                        lastChannelId = channel.id

                        // ════════════════════════════════════════════════════════════════
                        // BUG 2 FIX: Update player source when channel changes
                        // ════════════════════════════════════════════════════════════════
                        //
                        // When LivePlaybackController changes the current channel (via jumpChannel
                        // or selectChannel), we need to actually switch the player's media source.
                        // Without this, the player continues playing the old stream.
                        val channelUrl = channel.url
                        if (channelUrl.isNotBlank()) {
                            try {
                                // Update PlaybackSession source for MiniPlayer visibility
                                PlaybackSession.setSource(channelUrl, PlaybackType.LIVE)

                                // Build new MediaItem for the channel
                                val newMediaItem =
                                        MediaItem.Builder()
                                                .setUri(channelUrl)
                                                .setMediaMetadata(
                                                        MediaMetadata.Builder()
                                                                .setTitle(channel.name)
                                                                .build(),
                                                )
                                                .build()

                                // Set new media item and play
                                newPlayer.setMediaItem(newMediaItem)
                                newPlayer.prepare()
                                newPlayer.playWhenReady = true
                            } catch (e: Exception) {
                                // Fail-open: Log error but don't crash
                                // Catches IllegalStateException (player released),
                                // IllegalArgumentException (bad URL),
                                // SecurityException (missing permissions), and other runtime
                                // exceptions.
                                com.chris.m3usuite.core.logging.AppLog.log(
                                        category = "live",
                                        level = com.chris.m3usuite.core.logging.AppLog.Level.ERROR,
                                        message =
                                                "Failed to switch live channel: ${e.message ?: e::class.simpleName}",
                                        extras =
                                                mapOf(
                                                        "channelId" to channel.id.toString(),
                                                        "channelName" to channel.name,
                                                        "url" to channelUrl,
                                                        "exceptionType" to
                                                                (e::class.simpleName ?: "Unknown"),
                                                ),
                                )
                            }
                        }

                        scope.launch(Dispatchers.IO) {
                            try {
                                liveController.refreshEpgForCurrentChannel()
                            } catch (_: Throwable) {
                                // best-effort refresh
                            }
                        }
                    }
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

        // ════════════════════════════════════════════════════════════════════════════
        // Phase 4 Group 3: SubtitleStyleManager StateFlow → UiState mapping
        // ════════════════════════════════════════════════════════════════════════════
        //
        // Collect SubtitleStyleManager.currentStyle and map it into InternalPlayerUiState.
        // This is a one-way mapping (manager → UiState). The session does not push UI
        // state back into the manager.
        //
        // Kid Mode Behavior:
        // - Style is collected and stored in UiState normally
        // - BUT subtitle rendering is disabled by not selecting any subtitle track
        // - This allows settings/preview to still show styles for kid profiles
        scope.launch {
            subtitleStyleManager.currentStyle.collect { style ->
                if (playerHolder.value !== newPlayer) return@collect

                val updated =
                        playerState.value.copy(
                                subtitleStyle = style,
                        )
                playerState.value = updated
                onStateChanged(updated)

                // Apply style to Media3 subtitleView (if available)
                // Note: PlayerView setup happens in InternalPlayerContent composable
                // This will be applied when the PlayerView is created
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PHASE 7: Do NOT release the player when composable leaves composition
    // ════════════════════════════════════════════════════════════════════════════
    //
    // The player is now managed by PlaybackSession singleton and shared across:
    // - Full player screen
    // - MiniPlayer overlay
    // - Any other screens that need to observe playback state
    //
    // Player cleanup happens only when:
    // - User explicitly stops playback
    // - App is closed
    // - Errors require player recreation
    DisposableEffect(Unit) {
        onDispose {
            // Phase 7: Do NOT release the player - it's managed by PlaybackSession
            // Only clear the local holder reference
            playerHolder.value = null

            // Note: We intentionally keep the player alive via PlaybackSession.
            // This enables MiniPlayer to continue playback when navigating away
            // from the full player screen.
        }
    }

    // BUG 2 FIX: Return both player and liveController for proper channel zapping wiring
    return InternalPlayerSessionResult(
            player = playerHolder.value,
            liveController = liveController,
    )
}

private fun buildMediaItemWithTelegramExtras(resolved: ResolvedPlaybackSource): MediaItem {
    val builder = MediaItem.Builder().setUri(resolved.uri).setMimeType(resolved.mimeType)

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

    val metadataBuilder = MediaMetadata.Builder().setTitle(appItem?.name ?: "")

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

/** Thin command helpers so the screen/controller never talks to ExoPlayer directly. */
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
    player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
}
