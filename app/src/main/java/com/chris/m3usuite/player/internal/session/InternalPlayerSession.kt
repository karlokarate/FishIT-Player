package com.chris.m3usuite.player.internal.session

import android.content.Context
import android.net.Uri
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
import com.chris.m3usuite.model.MediaItem as AppMediaItem
import com.chris.m3usuite.player.datasource.DelegatingDataSourceFactory
import com.chris.m3usuite.player.internal.domain.DefaultKidsPlaybackGate
import com.chris.m3usuite.player.internal.domain.DefaultResumeManager
import com.chris.m3usuite.player.internal.domain.KidsGateState
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.source.PlaybackSourceResolver
import com.chris.m3usuite.player.internal.source.ResolvedPlaybackSource
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.core.playback.RememberPlayerController
import com.chris.m3usuite.ui.util.ImageHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Builds and manages the ExoPlayer instance for the internal player.
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
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 60_000,
                    /* bufferForPlaybackMs = */ 1_000,
                    /* bufferForPlaybackAfterRebufferMs = */ 3_000,
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

        // Initial seek: explicit startMs has priority, otherwise try resume
        try {
            val initialSeekMs: Long? =
                when {
                    startMs != null && startMs > 0 -> startMs
                    else -> resumeManager.loadResumePositionMs(playbackContext)
                }

            if (initialSeekMs != null && initialSeekMs > 0) {
                newPlayer.seekTo(initialSeekMs)
            }
        } catch (_: Throwable) {
            // best-effort
        }

        controller.attachPlayer(newPlayer)

        // Kid gate before starting playback
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

            newPlayer.playWhenReady = !(gateState.kidActive && gateState.kidBlocked)
        } catch (_: Throwable) {
            // On any failure, fall back to autoplay
            newPlayer.playWhenReady = true
        }

        // Player listener → update InternalPlayerUiState
        newPlayer.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
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
                    if (playbackState == Player.STATE_ENDED) {
                        scope.launch {
                            try {
                                resumeManager.handleEnded(playbackContext)
                            } catch (_: Throwable) {
                                // best-effort
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

        // Resume + Screen-Time periodic tick (~3s)
        scope.launch {
            var tickAccumSecs = 0
            while (isActive) {
                if (playerHolder.value !== newPlayer) break

                try {
                    val pos = newPlayer.currentPosition
                    val dur = newPlayer.duration

                    // Resume handling (VOD / Series)
                    resumeManager.handlePeriodicTick(
                        context = playbackContext,
                        positionMs = pos,
                        durationMs = dur,
                    )

                    // Screen-Time: only when kidActive and actually playing
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
                        tickAccumSecs = 0
                    }
                } catch (_: Throwable) {
                    // ignore, best-effort
                }

                delay(3_000)
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

private fun buildMediaItemWithTelegramExtras(
    resolved: ResolvedPlaybackSource,
): MediaItem {
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

fun applyPlayerCommand_ToggleLoop(player: ExoPlayer?, looping: Boolean) {
    player ?: return
    player.repeatMode =
        if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
}