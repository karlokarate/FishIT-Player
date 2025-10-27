package com.chris.m3usuite.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.chris.m3usuite.R
import com.chris.m3usuite.data.repo.ResumeRepository
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.home.MiniPlayerDescriptor
import com.chris.m3usuite.ui.home.MiniPlayerState
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import android.widget.Toast
import android.os.Build
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.player.datasource.DelegatingDataSourceFactory
import com.chris.m3usuite.player.datasource.TelegramDataSource
import com.chris.m3usuite.telegram.service.TelegramServiceClient

/**
 * Interner Player (Media3) mit:
 * - Untertitel-Stil aus Settings
 * - Resume-Speicherung alle ~3s & beim Schließen
 * - optionaler Startposition
 *
 * type: "vod" | "series" | "live"  (live wird nicht persistiert)
 */
@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InternalPlayerScreen(
    url: String,
    type: String,                 // "vod" | "series" | "live"
    mediaId: Long? = null,        // VOD
    episodeId: Int? = null,       // Series (legacy id)
    seriesId: Int? = null,        // Series composite
    season: Int? = null,
    episodeNum: Int? = null,
    startPositionMs: Long? = null,
    headers: Map<String, String> = emptyMap(),
    mimeType: String? = null,
    onExit: () -> Unit,
    // Live-TV context hints for navigation & lists
    originLiveLibrary: Boolean = false,
    liveCategoryHint: String? = null,
    liveProviderHint: String? = null
) {
    LaunchedEffect(type, mediaId, seriesId, episodeId) {
        com.chris.m3usuite.metrics.RouteTag.set("player")
        val node = when (type) {
            "live" -> "player:live"
            "vod" -> "player:vod"
            "series" -> "player:series"
            else -> "player:?"
        }
        val idNode = when (type) {
            "series" -> "tile:${seriesId ?: mediaId}"
            else -> "tile:${mediaId ?: -1}"
        }
        com.chris.m3usuite.core.debug.GlobalDebug.logTree(node, idNode)
    }
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val isTv = remember(ctx) { FocusKit.isTvDevice(ctx) }

    LaunchedEffect(Unit) { MiniPlayerState.hide() }

    val obxStore = remember(ctx) { ObxStore.get(ctx) }
    val resumeRepo = remember(ctx) { ResumeRepository(ctx) }
    val store = remember(ctx) { SettingsStore(ctx) }
    val mediaRepo = remember(ctx) { com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store) }
    val screenTimeRepo = remember(ctx) { ScreenTimeRepository(ctx) }
    val epgRepo = remember(ctx) { com.chris.m3usuite.data.repo.EpgRepository(ctx, store) }

    // Pause seeding workers during playback; restore afterwards if they were enabled
    DisposableEffect(Unit) {
        var originallyEnabled: Boolean? = null
        // Snapshot + pause + cancel in-flight Xtream work
        runBlocking {
            try {
                originallyEnabled = store.m3uWorkersEnabled.first()
                if (originallyEnabled == true) {
                    store.setM3uWorkersEnabled(false)
                }
            } catch (_: Throwable) {}
        }
        // Best-effort: cancel active Xtream jobs so they don't compete during playback
        runCatching { com.chris.m3usuite.work.SchedulingGateway.cancelXtreamWork(ctx) }
        onDispose {
            // Restore only if we paused it here (keep user-chosen OFF intact)
            val shouldRestore = (originallyEnabled == true)
            if (shouldRestore) {
                runCatching { scope.launch { store.setM3uWorkersEnabled(true) } }
            }
        }
    }

    // Settings (Untertitel)
    val subScale by store.subtitleScale.collectAsStateWithLifecycle(initialValue = 0.06f)
    val subFg by store.subtitleFg.collectAsStateWithLifecycle(initialValue = 0xF2FFFFFF.toInt())
    val subBg by store.subtitleBg.collectAsStateWithLifecycle(initialValue = 0x66000000)
    val subFgOpacity by store.subtitleFgOpacityPct.collectAsStateWithLifecycle(initialValue = 90)
    val subBgOpacity by store.subtitleBgOpacityPct.collectAsStateWithLifecycle(initialValue = 40)
    val rotationLocked by store.rotationLocked.collectAsStateWithLifecycle(initialValue = false)
    val autoplayNext by store.autoplayNext.collectAsStateWithLifecycle(initialValue = false)

    // HTTP Factory mit Headern
    val extraJson by store.extraHeadersJson.collectAsStateWithLifecycle(initialValue = "")
    val mergedHeaders = remember(headers, extraJson) {
        val extras = com.chris.m3usuite.core.http.RequestHeadersProvider.parseExtraHeaders(extraJson)
        com.chris.m3usuite.core.http.RequestHeadersProvider.merge(headers, extras)
    }
    val httpFactory = remember(mergedHeaders, url, type) {
        val ua = mergedHeaders["User-Agent"] ?: "IBOPlayer/1.4 (Android)"
        val props = HashMap<String, String>(mergedHeaders)
        if (!props.containsKey("Accept")) props["Accept"] = "*/*"
        if (!props.containsKey("Connection")) props["Connection"] = "keep-alive"
        // For Xtream VOD/progressive MP4 some panels behave better with identity encoding
        if (!props.containsKey("Accept-Encoding")) props["Accept-Encoding"] = "identity"

        // Lightweight diagnostics
        runCatching {
            android.util.Log.d(
                "PlayerHTTP",
                "prepare ua=\"${ua}\" ref=\"${props["Referer"] ?: ""}\" accept=\"${props["Accept"]}\""
            )
        }

        val baseFactory = DefaultHttpDataSource.Factory()
            .apply { if (ua.isNotBlank()) setUserAgent(ua) }
            .setAllowCrossProtocolRedirects(true)
            .apply { if (props.isNotEmpty()) setDefaultRequestProperties(props) }

        // VOD hardening: extend timeouts for movie URLs only
        val isVod = type == "vod" || url.contains("/movie/")
        if (isVod) {
            // 20s connect, 30s read to avoid premature timeouts on slow panels
            baseFactory.setConnectTimeoutMs(20_000)
            baseFactory.setReadTimeoutMs(30_000)
        }
        baseFactory
    }
    // Detect telegram early and prepare a low-RAM allocator (shared with player listeners below)
    val isTelegramContent = remember(url) { url.startsWith("tg://", ignoreCase = true) }
    val is32BitDevice = remember { try { Build.SUPPORTED_64_BIT_ABIS.isEmpty() } catch (_: Throwable) { true } }
    val allocator = remember(isTelegramContent, is32BitDevice) {
        androidx.media3.exoplayer.upstream.DefaultAllocator(/* trimOnReset = */ true, /* segmentSize = */ if (isTelegramContent || is32BitDevice) 16 * 1024 else 64 * 1024)
    }

    val telegramServiceClient = remember(ctx) { TelegramServiceClient(ctx.applicationContext) }

    val dataSourceFactory = remember(httpFactory, ctx, telegramServiceClient) {
        val base = DefaultDataSource.Factory(ctx, httpFactory)
        DelegatingDataSourceFactory(ctx, base) { TelegramDataSource(telegramServiceClient) }
    }

    val preferredVideoMimeTypes = remember {
        arrayOf(
            MimeTypes.VIDEO_DOLBY_VISION,
            MimeTypes.VIDEO_H265,
            MimeTypes.VIDEO_AV1,
            MimeTypes.VIDEO_VP9,
            MimeTypes.VIDEO_H264,
            MimeTypes.VIDEO_VP8
        )
    }
    val preferredAudioMimeTypes = remember {
        arrayOf(
            MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_TRUEHD,
            MimeTypes.AUDIO_E_AC3,
            MimeTypes.AUDIO_AC4,
            MimeTypes.AUDIO_AC3,
            MimeTypes.AUDIO_DTS_HD,
            MimeTypes.AUDIO_DTS,
            MimeTypes.AUDIO_FLAC,
            MimeTypes.AUDIO_OPUS,
            MimeTypes.AUDIO_AAC,
            MimeTypes.AUDIO_MPEG,
            MimeTypes.AUDIO_MPEG_L2,
            MimeTypes.AUDIO_MPEG_L1,
            MimeTypes.AUDIO_VORBIS
        )
    }
    val trackSelectionParameters = remember {
        TrackSelectionParameters.Builder()
            .setForceHighestSupportedBitrate(true)
            .setPreferredVideoMimeTypes(*preferredVideoMimeTypes)
            .setPreferredAudioMimeTypes(*preferredAudioMimeTypes)
            .build()
    }
    val trackSelector = remember(ctx) { DefaultTrackSelector(ctx) }
    DisposableEffect(trackSelector) {
        trackSelector.setParameters(trackSelectionParameters)
        onDispose { }
    }

    // Player (shared via PlaybackSession so mini player can attach after leaving screen)
    val session = remember(url, headers, dataSourceFactory, mimeType, trackSelector) {
        val renderers = PlayerComponents.renderersFactory(ctx)

        val loadControl = if (isTelegramContent) {
            // Keep RAM small; rely on TDLib on-disk caching while downloading
            DefaultLoadControl.Builder()
                .setAllocator(allocator)
                .setBufferDurationsMs(
                    /* minBufferMs = */ 2_000,
                    /* maxBufferMs = */ 5_000,
                    /* bufferForPlaybackMs = */ 1_000,
                    /* bufferForPlaybackAfterRebufferMs = */ 2_000
                )
                .setTargetBufferBytes(2 * 1024 * 1024) // ~2 MiB target buffer
                .setBackBuffer(0, false)
                .build()
        } else DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setTargetBufferBytes(6 * 1024 * 1024) // small target to avoid large pools on low-RAM devices
            .build()

        // Align button-based seek increments (also used by PlayerView controller buttons)
        val seekIncrementMs = 10_000L

        PlaybackSession.acquire(ctx) {
            ExoPlayer.Builder(ctx)
                .setRenderersFactory(renderers)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                )
                .setSeekBackIncrementMs(seekIncrementMs)
                .setSeekForwardIncrementMs(seekIncrementMs)
                .setLoadControl(loadControl)
                .build()
        }
    }
    val exoPlayer = session.player

    LaunchedEffect(exoPlayer, trackSelectionParameters) {
        if (exoPlayer.trackSelectionParameters != trackSelectionParameters) {
            exoPlayer.trackSelectionParameters = trackSelectionParameters
        }
    }

    LaunchedEffect(session.isNew, url, mimeType, startPositionMs) {
        val needsConfig = session.isNew || PlaybackSession.currentSource() != url
        if (needsConfig) {
            android.util.Log.d("ExoSetup", "setMediaItem url=${url}")
            val resolvedUri = if (url.startsWith("tg://", ignoreCase = true)) {
                val parsed = runCatching { Uri.parse(url) }.getOrNull()
                when {
                    parsed == null -> Uri.parse(url)
                    parsed.host.equals("file", true) -> parsed
                    parsed.host.equals("message", true) -> {
                        val chatId = parsed.getQueryParameter("chatId")?.toLongOrNull()
                        val messageId = parsed.getQueryParameter("messageId")?.toLongOrNull()
                        if (chatId != null && messageId != null) {
                            if (!telegramServiceClient.isReady()) {
                                Toast.makeText(
                                    ctx,
                                    "Telegram noch nicht verbunden oder TDLib noch nicht initialisiert…",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onExit()
                                return@LaunchedEffect
                            }
                            runCatching {
                                PlayUrlHelper.tgPlayUri(chatId = chatId, messageId = messageId, svc = telegramServiceClient)
                            }.getOrElse { parsed }
                        } else parsed
                    }
                    else -> parsed
                }
            } else {
                Uri.parse(url)
            }
            val finalUrl = resolvedUri.toString()
            val inferredMime = mimeType ?: PlayUrlHelper.guessMimeType(finalUrl, null)
            val mediaItem = MediaItem.Builder()
                .setUri(resolvedUri)
                .also { builder -> inferredMime?.let { builder.setMimeType(it) } }
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            exoPlayer.playWhenReady = false // Phase 4: erst nach Screen-Time-Check starten
            startPositionMs?.let { exoPlayer.seekTo(it) }
            PlaybackSession.setSource(finalUrl)
        }
    }

    // Phase 4: Kid-Profil + Screen-Time-Gate vor Start
    var kidBlocked by remember { mutableStateOf(false) }
    var kidActive by remember { mutableStateOf(false) }
    var kidIdState by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        try {
            val id = store.currentProfileId.first()
            if (id > 0) {
                val prof = withContext(Dispatchers.IO) { obxStore.boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(id) }
                kidActive = prof?.type == "kid"
                kidIdState = if (kidActive) id else null
            }
            if (kidActive) {
                val remain = screenTimeRepo.remainingMinutes(kidIdState!!)
                kidBlocked = remain <= 0
            }
            if (!kidBlocked) {
                exoPlayer.playWhenReady = true
            }
        } catch (_: Throwable) {
            exoPlayer.playWhenReady = true
        }
    }

    // resume: load (seek to saved position if available and >10s)
    LaunchedEffect(type, mediaId, episodeId, seriesId, season, episodeNum, exoPlayer) {
        if ((type == "vod" && mediaId != null) || (type == "series" && (episodeId != null || (seriesId != null && season != null && episodeNum != null)))) {
            try {
                if (startPositionMs == null) {
                    val posSecs = withContext(Dispatchers.IO) {
                        if (type == "vod") resumeRepo.recentVod(1).firstOrNull { it.mediaId == mediaId }?.positionSecs else if (seriesId != null && season != null && episodeNum != null) {
                            resumeRepo.recentEpisodes(50).firstOrNull { it.seriesId == seriesId && it.season == season && it.episodeNum == episodeNum }?.positionSecs
                        } else null
                    }
                    val p = (posSecs ?: 0)
                    if (p > 10) {
                        exoPlayer.seekTo(p.toLong() * 1000L)
                    }
                }
            } catch (_: Throwable) {
                // ignore, best-effort
            }
        }
    }

    // Lifecycle: Pause/Resume/Release + resume: clear/save on destroy
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Don't pause when entering Picture-in-Picture (guard by SDK)
                    val act = ctx as? Activity
                    val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        act?.isInPictureInPictureMode == true
                    } else false
                    if (!inPip) {
                        exoPlayer.playWhenReady = false
                    }
                    // Free up pooled buffer segments when pausing (TV v7a)
                    try { allocator.trim() } catch (_: Throwable) {}
                }
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_DESTROY -> {
                    // resume: clear if near end (<10s), otherwise save
                    runBlocking {
                        try {
                            if (type != "live") {
                                val dur = exoPlayer.duration
                                val pos = exoPlayer.currentPosition
                                val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
                                if ((type == "vod" && mediaId != null) || (type == "series" && (seriesId != null && season != null && episodeNum != null))) {
                                    if (dur > 0 && remaining in 0..9999) {
                                        withContext(Dispatchers.IO) {
                                            if (type == "vod") resumeRepo.clearVod(mediaId!!) else Unit
                                        }
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            val posSecs = (pos / 1000L).toInt().coerceAtLeast(0)
                                            if (type == "vod" && mediaId != null) resumeRepo.setVodResume(mediaId, posSecs) else if (type == "series" && (seriesId != null && season != null && episodeNum != null)) {
                                                resumeRepo.setSeriesResume(seriesId, season, episodeNum, posSecs)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    exoPlayer.release()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Rotation-Lock nur in diesem Screen anwenden/aufheben
    DisposableEffect(rotationLocked) {
        val activity = (ctx as? Activity)
        val prev = activity?.requestedOrientation
        if (rotationLocked) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (rotationLocked) {
                activity?.requestedOrientation = prev ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // resume: save/clear periodically (~3s) + Phase 4: Screen-Time tick (Kids)
    LaunchedEffect(url, type, mediaId, episodeId, exoPlayer) {
        var tickAccum = 0
        while (isActive) {
            try {
                if ((type == "vod" && mediaId != null) || (type == "series" && seriesId != null && season != null && episodeNum != null)) {
                    val dur = exoPlayer.duration
                    val pos = exoPlayer.currentPosition
                    val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
                    if (dur > 0 && remaining in 0..9999) {
                        withContext(Dispatchers.IO) {
                            if (type == "vod") resumeRepo.clearVod(mediaId!!) else resumeRepo.clearSeriesResume(seriesId!!, season!!, episodeNum!!)
                        }
                    } else if (type != "live") {
                        val posSecs = (pos / 1000L).toInt().coerceAtLeast(0)
                        withContext(Dispatchers.IO) {
                            if (type == "vod" && mediaId != null) resumeRepo.setVodResume(mediaId, posSecs) else resumeRepo.setSeriesResume(seriesId!!, season!!, episodeNum!!, posSecs)
                        }
                    }
                }

                // Screen-Time: alle 60s Verbrauch ticken und Limit prüfen (nur Kids)
                if (kidActive && exoPlayer.playWhenReady && exoPlayer.isPlaying) {
                    tickAccum += 3
                    if (tickAccum >= 60) {
                        val kidId = kidIdState
                        if (kidId != null) {
                            screenTimeRepo.tickUsageIfPlaying(kidId, tickAccum)
                            tickAccum = 0
                            val remain = screenTimeRepo.remainingMinutes(kidId)
                            if (remain <= 0) {
                                exoPlayer.playWhenReady = false
                                kidBlocked = true
                            }
                        } else {
                            tickAccum = 0
                        }
                    }
                } else {
                    tickAccum = 0
                }
            } catch (_: Throwable) {
            }
            delay(3000)
        }
    }

    // errors + resume: clear on playback ended
    DisposableEffect(exoPlayer, type, mediaId, episodeId) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e(
                    "ExoErr",
                    "playback_error type=${type} code=${error.errorCode} cause=${error.cause?.javaClass?.simpleName}: ${error.message}"
                )
                try { Toast.makeText(ctx, "Wiedergabefehler: ${error.errorCodeName}", Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if ((type == "vod" && mediaId != null) || (type == "series" && seriesId != null && season != null && episodeNum != null)) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                if (type == "vod") resumeRepo.clearVod(mediaId!!) else resumeRepo.clearSeriesResume(seriesId!!, season!!, episodeNum!!)
                            } catch (_: Throwable) {}
                        }
                    }
                    // Phase 5: autoplay next for series
                    if (type == "series" && (seriesId != null && season != null && episodeNum != null) && autoplayNext) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val box = obxStore.boxFor(com.chris.m3usuite.data.obx.ObxEpisode::class.java)
                                val curSeason = season
                                val curEp = episodeNum
                                val q = box.query(
                                    com.chris.m3usuite.data.obx.ObxEpisode_.seriesId.equal(seriesId.toLong())
                                ).build()
                                val list = q.find().sortedWith(compareBy<com.chris.m3usuite.data.obx.ObxEpisode> { it.season }.thenBy { it.episodeNum })
                                val idx = list.indexOfFirst { it.season == curSeason && it.episodeNum == curEp }
                                val next = if (idx >= 0 && idx + 1 < list.size) list[idx + 1] else null
                                if (next != null) {
                                    val nextUrl = com.chris.m3usuite.data.obx.buildEpisodePlayUrl(
                                        ctx = ctx,
                                        seriesStreamId = seriesId,
                                        season = next.season,
                                        episodeNum = next.episodeNum,
                                        episodeExt = next.playExt,
                                        episodeId = next.episodeId.takeIf { it > 0 }
                                    )
                                    if (!nextUrl.isNullOrBlank()) {
                                        withContext(Dispatchers.Main) {
                                            exoPlayer.setMediaItem(MediaItem.fromUri(nextUrl))
                                            exoPlayer.prepare()
                                            exoPlayer.playWhenReady = true
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Auto-save resume on exit without prompting
    var finishing by remember { mutableStateOf(false) }

    // --- Overlays: Title/Episode/Channel and EPG (Live) ---
    var overlayTitle by remember { mutableStateOf("") }
    var showOverlayTitle by remember { mutableStateOf(false) }
    var epgNow by remember { mutableStateOf("") }
    var epgNext by remember { mutableStateOf("") }
    var showEpgOverlay by remember { mutableStateOf(false) }
    var currentLiveId by remember { mutableStateOf(mediaId) }
    var activeLiveCategory by remember { mutableStateOf(liveCategoryHint) }
    var activeLiveProvider by remember { mutableStateOf(liveProviderHint) }

    suspend fun computeOverlayTitle(): String {
        return withContext(Dispatchers.IO) {
            try {
                val store = ObxStore.get(ctx)
                when (type) {
                    "live" -> {
                        val id = currentLiveId ?: mediaId ?: return@withContext "Live"
                        store.boxFor(com.chris.m3usuite.data.obx.ObxLive::class.java)
                            .query(com.chris.m3usuite.data.obx.ObxLive_.streamId.equal((id - 1_000_000_000_000L).toInt().toLong()))
                            .build().findFirst()?.name ?: "Live"
                    }
                    "vod" -> {
                        val id = mediaId ?: return@withContext "Film"
                        store.boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                            .query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal((id - 2_000_000_000_000L).toInt().toLong()))
                            .build().findFirst()?.name ?: "Film"
                    }
                    else -> { // series
                        val epBox = store.boxFor(com.chris.m3usuite.data.obx.ObxEpisode::class.java)
                        val epi = when {
                            episodeId != null && episodeId > 0 -> epBox.query(
                                com.chris.m3usuite.data.obx.ObxEpisode_.episodeId.equal(episodeId.toLong())
                            ).build().findFirst()
                            seriesId != null && season != null && episodeNum != null -> epBox.query(
                                com.chris.m3usuite.data.obx.ObxEpisode_.seriesId.equal(seriesId.toLong())
                                    .and(com.chris.m3usuite.data.obx.ObxEpisode_.season.equal(season.toLong()))
                                    .and(com.chris.m3usuite.data.obx.ObxEpisode_.episodeNum.equal(episodeNum.toLong()))
                            ).build().findFirst()
                            else -> null
                        }

                        // Resolve series name either from mediaId or from the episode’s seriesId
                        val seriesNameFromMediaId = mediaId?.let { mId ->
                            store.boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                                .query(com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal((mId - 3_000_000_000_000L).toInt().toLong()))
                                .build().findFirst()?.name
                        }
                        val seriesName = seriesNameFromMediaId ?: run {
                            val sid = epi?.seriesId
                            if (sid != null && sid > 0) store.boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                                .query(com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(sid.toLong()))
                                .build().findFirst()?.name else null
                        }

                        if (epi != null) {
                            val s = epi.season
                            val e = epi.episodeNum
                            val epTitle = epi.title?.takeIf { it.isNotBlank() } ?: "Episode $e"
                            val sName = seriesName ?: "Serie"
                            "$sName – S${s}E${e} $epTitle"
                        } else {
                            seriesName ?: "Serie"
                        }
                    }
                }
            } catch (_: Throwable) { null }
        } ?: when (type) { "live" -> "Live"; "series" -> "Serie"; else -> "Film" }
    }

    // Aggressively trim allocator on state changes / pauses (TV v7a low-RAM safety)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || !exoPlayer.playWhenReady) {
                    try { allocator.trim() } catch (_: Throwable) {}
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) { try { allocator.trim() } catch (_: Throwable) {} }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    suspend fun refreshEpgOverlayForLive(id: Long?) {
        if (id == null) return
        val sid = (id - 1_000_000_000_000L).toInt()
        try {
            val list = epgRepo.nowNext(sid, 2)
            val first = list.getOrNull(0)
            val second = list.getOrNull(1)
            epgNow = first?.title.orEmpty()
            epgNext = second?.title.orEmpty()
        } catch (_: Throwable) {
            epgNow = ""; epgNext = ""
        }
    }

    fun miniSubtitle(
        type: String,
        provider: String?,
        category: String?,
        seasonValue: Int?,
        episodeValue: Int?,
        mime: String?
    ): String? = when (type) {
        "live" -> listOfNotNull(
            provider?.takeIf { it.isNotBlank() },
            category?.takeIf { it.isNotBlank() }
        ).joinToString(" • ").takeIf { it.isNotBlank() }
        "series" -> if (seasonValue != null && seasonValue > 0 && episodeValue != null && episodeValue > 0) {
            "Staffel $seasonValue Folge $episodeValue"
        } else null
        else -> mime?.takeIf { it.isNotBlank() }
    }

    LaunchedEffect(
        type,
        mediaId,
        currentLiveId,
        seriesId,
        season,
        episodeNum,
        episodeId,
        url,
        mimeType,
        activeLiveCategory,
        activeLiveProvider,
        originLiveLibrary
    ) {
        val resolvedTitle = computeOverlayTitle()
        val resolvedMediaId = if (type == "live") currentLiveId ?: mediaId else mediaId
        val descriptor = MiniPlayerDescriptor(
            type = type,
            url = url,
            mediaId = resolvedMediaId,
            seriesId = seriesId,
            season = season,
            episodeNum = episodeNum,
            episodeId = episodeId,
            mimeType = mimeType,
            origin = if (originLiveLibrary) "lib" else null,
            liveCategory = activeLiveCategory,
            liveProvider = activeLiveProvider,
            title = resolvedTitle,
            subtitle = miniSubtitle(type, activeLiveProvider, activeLiveCategory, season, episodeNum, mimeType)
        )
        MiniPlayerState.setDescriptor(descriptor)
    }

    fun scheduleAutoHide(overTitleMs: Long = 4000L, epgMs: Long = 3000L) {
        if (showOverlayTitle) scope.launch { delay(overTitleMs); showOverlayTitle = false }
        if (showEpgOverlay) scope.launch { delay(epgMs); showEpgOverlay = false }
    }

    // Show overlays on initial READY
    DisposableEffect(exoPlayer, type, mediaId) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    scope.launch {
                        overlayTitle = computeOverlayTitle()
                        showOverlayTitle = true
                        if (type == "live") {
                            refreshEpgOverlayForLive(currentLiveId ?: mediaId)
                            showEpgOverlay = true
                        }
                        scheduleAutoHide()
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // --- Live channel navigation (favorites vs. library) ---
    var favorites by remember { mutableStateOf<List<com.chris.m3usuite.model.MediaItem>>(emptyList()) }
    var libraryLive by remember { mutableStateOf<List<com.chris.m3usuite.model.MediaItem>>(emptyList()) }

    LaunchedEffect(type) {
        if (type == "live") {
            // Favorites list
            val favCsv = store.favoriteLiveIdsCsv.first()
            val favIds = favCsv.split(',').mapNotNull { it.toLongOrNull() }.toSet()
            val allowed = mediaRepo.listByTypeFiltered("live", 6000, 0)
            favorites = allowed.filter { it.id in favIds }
            // Library list (all allowed, optionally narrowed by category hint)
            var base = allowed
            if (!liveCategoryHint.isNullOrBlank()) {
                // attach category labels for filter
                val catMap = withContext(Dispatchers.IO) {
                    val box = ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxCategory::class.java)
                    box.query(com.chris.m3usuite.data.obx.ObxCategory_.kind.equal("live")).build().find()
                        .associate { it.categoryId to it.categoryName }
                }
                base = base.map { mi -> mi.copy(categoryName = catMap[mi.categoryId]) }
                    .filter { it.categoryName == liveCategoryHint }
            }
            libraryLive = base
        }
    }

    fun switchToLive(mi: com.chris.m3usuite.model.MediaItem) {
        activeLiveCategory = mi.categoryName ?: activeLiveCategory
        activeLiveProvider = mi.providerKey ?: activeLiveProvider
        currentLiveId = mi.id
        scope.launch {
            overlayTitle = mi.name
            showOverlayTitle = true
            refreshEpgOverlayForLive(mi.id)
            showEpgOverlay = true
            scheduleAutoHide()
        }
        // Build URL and switch asynchronously (suspend call)
        scope.launch {
            val nextUrl = mi.url ?: runCatching {
                PlayUrlHelper.forLive(ctx, store, mi)?.url
            }.getOrNull()
            if (!nextUrl.isNullOrBlank()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(nextUrl))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
    }

    fun jumpLive(direction: Int) { // -1 = prev, +1 = next
        if (type != "live") return
        val list = if (originLiveLibrary) libraryLive else favorites
        if (list.isEmpty()) return
        val curId = currentLiveId ?: mediaId ?: return
        val idx = list.indexOfFirst { it.id == curId }.let { if (it >= 0) it else 0 }
        val nextIdx = (idx + direction).mod(list.size)
        val next = list[nextIdx]
        switchToLive(next)
    }

    fun finishAndRelease() {
        if (finishing) return
        // Auto-save resume without confirmation
        val dur = exoPlayer.duration
        val pos = exoPlayer.currentPosition
        finishing = true
        scope.launch(Dispatchers.IO) {
            try {
                if (type != "live" && ((type == "vod" && mediaId != null) || (type == "series" && episodeId != null))) {
                    val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
                    if (dur > 0 && remaining in 0..9999) {
                        if (type == "vod") resumeRepo.clearVod(mediaId!!) else Unit
                    } else {
                        val posSecs = (pos / 1000L).toInt().coerceAtLeast(0)
                        if (type == "vod" && mediaId != null) resumeRepo.setVodResume(mediaId, posSecs)
                    }
                }
            } catch (_: Throwable) {}
            withContext(Dispatchers.Main) {
                val keepForMini = isTv && MiniPlayerState.visible.value
                if (!keepForMini) {
                    runCatching { exoPlayer.release() }
                    PlaybackSession.setSource(null)
                    PlaybackSession.set(null)
                    MiniPlayerState.clearDescriptor()
                }
                onExit()
            }
        }
    }

    // DPAD quick actions state
    var quickActionsVisible by remember { mutableStateOf(false) }
    val pipFocusRequester = remember { FocusRequester() }
    val ccFocusRequester = remember { FocusRequester() }
    val resizeFocusRequester = remember { FocusRequester() }
    fun requestPictureInPicture() {
        val act = ctx as? Activity ?: return
        if (isTv) {
            MiniPlayerState.show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                act.enterPictureInPictureMode(params)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                @Suppress("DEPRECATION")
                act.enterPictureInPictureMode()
            } else {
                runCatching { Toast.makeText(ctx, "PiP nicht verfügbar", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    var quickActionsFocusActive by remember { mutableStateOf(false) }

    // Phase 6: Subtitle/CC menu (Adult only)
    var showCcMenu by remember { mutableStateOf(false) }
    val isAdult = !kidActive

    // Local, session-only overrides applied immediately (persist optional)
    var localScale by remember { mutableStateOf<Float?>(null) }
    var localFg by remember { mutableStateOf<Int?>(null) }
    var localBg by remember { mutableStateOf<Int?>(null) }
    var localFgOpacity by remember { mutableStateOf<Int?>(null) }
    var localBgOpacity by remember { mutableStateOf<Int?>(null) }

    fun effectiveScale(): Float = localScale ?: subScale
    fun effectiveFg(): Int = localFg ?: subFg
    fun effectiveBg(): Int = localBg ?: subBg
    fun effectiveFgOpacity(): Int = localFgOpacity ?: subFgOpacity
    fun effectiveBgOpacity(): Int = localBgOpacity ?: subBgOpacity

    data class SubOpt(val label: String, val groupIndex: Int?, val trackIndex: Int?)
    var subOptions by remember { mutableStateOf(listOf<SubOpt>()) }
    var selectedSub by remember { mutableStateOf<SubOpt?>(null) }
    data class AudioOpt(val label: String, val groupIndex: Int?, val trackIndex: Int?)
    var audioOptions by remember { mutableStateOf(listOf<AudioOpt>()) }
    var selectedAudio by remember { mutableStateOf<AudioOpt?>(null) }

    

    fun refreshSubtitleOptions() {
        val tracks: Tracks = exoPlayer.currentTracks
        val opts = mutableListOf<SubOpt>()
        opts += SubOpt("Aus", null, null)
        var currentSel: SubOpt? = null
        tracks.groups.forEachIndexed { gi, g ->
            if (g.type == C.TRACK_TYPE_TEXT) {
                for (ti in 0 until g.length) {
                    val fmt = g.getTrackFormat(ti)
                    val lang = fmt.language?.uppercase() ?: ""
                    val base = fmt.label ?: (if (lang.isNotBlank()) "Untertitel $lang" else "Untertitel ${gi}-${ti}")
                    val label = if (lang.isNotBlank() && !base.contains("[$lang]")) "$base [$lang]" else base
                    val opt = SubOpt(label, gi, ti)
                    opts += opt
                    if (g.isTrackSelected(ti)) currentSel = opt
                }
            }
        }
        subOptions = opts
        selectedSub = currentSel ?: opts.firstOrNull()
    }

    fun refreshAudioOptions() {
        val tracks: Tracks = exoPlayer.currentTracks
        val opts = mutableListOf<AudioOpt>()
        opts += AudioOpt("Auto", null, null)
        var currentSel: AudioOpt? = null
        tracks.groups.forEachIndexed { gi, g ->
            if (g.type == C.TRACK_TYPE_AUDIO) {
                for (ti in 0 until g.length) {
                    val fmt = g.getTrackFormat(ti)
                    val lang = fmt.language?.uppercase() ?: ""
                    val ch = if (fmt.channelCount != androidx.media3.common.Format.NO_VALUE) "${fmt.channelCount}ch" else ""
                    val br = if (fmt.averageBitrate != androidx.media3.common.Format.NO_VALUE) "${fmt.averageBitrate/1000}kbps" else ""
                    val base0 = fmt.label ?: (if (lang.isNotBlank()) "Audio $lang" else "Audio ${gi}-${ti}")
                    val base = if (lang.isNotBlank() && !base0.contains("[$lang]")) "$base0 [$lang]" else base0
                    val meta = listOf(ch, br).filter { it.isNotBlank() }.joinToString(" · ")
                    val label = if (meta.isNotBlank()) "$base ($meta)" else base
                    val opt = AudioOpt(label, gi, ti)
                    opts += opt
                    if (g.isTrackSelected(ti)) currentSel = opt
                }
            }
        }
        audioOptions = opts
        selectedAudio = currentSel ?: opts.firstOrNull()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                refreshSubtitleOptions()
                refreshAudioOptions()
            }
        }
        exoPlayer.addListener(listener)
        refreshSubtitleOptions()
        refreshAudioOptions()
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Fullscreen player with tap-to-show controls overlay
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableStateOf(0) }
    var sliderValue by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var canSeek by remember { mutableStateOf(false) }

    // Back handling: close popups first, then exit
    // Moved below aspect-state declarations to ensure references are resolved
    // Focus helpers for TV/D-Pad navigation
    val centerFocus = remember { FocusRequester() }
    val sliderFocus = remember { FocusRequester() }
    val quickPipFocus = remember { FocusRequester() }
    val quickCcFocus = remember { FocusRequester() }
    val quickAspectFocus = remember { FocusRequester() }

    // Aspect / resize controls
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    // Overlays: make container fully transparent (only buttons visible)
    var showAspectMenu by remember { mutableStateOf(false) }
    var customScaleEnabled by remember { mutableStateOf(false) }
    var customScaleX by remember { mutableStateOf(1f) }
    var customScaleY by remember { mutableStateOf(1f) }

    fun cycleResize() {
        customScaleEnabled = false
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun togglePlayPause() {
        val nowPlaying = exoPlayer.playWhenReady && exoPlayer.isPlaying
        exoPlayer.playWhenReady = !nowPlaying
    }

    BackHandler {
        when {
            showCcMenu -> {
                localScale?.let { scope.launch { store.setFloat(com.chris.m3usuite.prefs.Keys.SUB_SCALE, it) } }
                localFg?.let { scope.launch { store.setInt(com.chris.m3usuite.prefs.Keys.SUB_FG, it) } }
                localBg?.let { scope.launch { store.setInt(com.chris.m3usuite.prefs.Keys.SUB_BG, it) } }
                localFgOpacity?.let { scope.launch { store.setSubtitleFgOpacityPct(it) } }
                localBgOpacity?.let { scope.launch { store.setSubtitleBgOpacityPct(it) } }
                showCcMenu = false
            }
            showAspectMenu -> { showAspectMenu = false }
            quickActionsVisible -> { quickActionsVisible = false; quickActionsFocusActive = false }
            controlsVisible -> { controlsVisible = false }
            else -> finishAndRelease()
        }
    }

    // Update seekability from player (handles live/dynamic cases correctly)
    DisposableEffect(exoPlayer) {
        canSeek = exoPlayer.isCurrentMediaItemSeekable
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                canSeek = exoPlayer.isCurrentMediaItemSeekable
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                canSeek = exoPlayer.isCurrentMediaItemSeekable
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                canSeek = exoPlayer.isCurrentMediaItemSeekable
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Auto-hide controls after inactivity: phone/tablet 5s, TV 10s
    LaunchedEffect(controlsVisible, controlsTick, isTv) {
        if (!controlsVisible) return@LaunchedEffect
        // Don't auto-hide while modal sheets/menus are open
        val blockingPopupOpen = showCcMenu || showAspectMenu || (type == "live" && quickActionsVisible)
        if (blockingPopupOpen) return@LaunchedEffect
        val timeoutMs = if (isTv) 10_000L else 5_000L
        val startTick = controlsTick
        delay(timeoutMs)
        // Hide only if still visible and there was no interaction
        if (controlsVisible && controlsTick == startTick) {
            controlsVisible = false
        }
    }
    

    Box(Modifier.fillMaxSize()) {
        // Live list sheet state must be declared before key handlers
        var showLiveListSheet by remember { mutableStateOf(false) }
        // Seek preview overlay state
        var seekPreviewVisible by remember { mutableStateOf(false) }
        var seekPreviewTargetMs by remember { mutableStateOf<Long?>(null) }
        var seekPreviewBaseMs by remember { mutableStateOf(0L) }
        val seekPreviewAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (seekPreviewVisible) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "seekPreviewAlpha"
        )
        val trickplaySpeeds = remember { floatArrayOf(2f, 3f, 5f) }
        var ffStage by remember { mutableStateOf(0) }
        var rwStage by remember { mutableStateOf(0) }
        var rwJob by remember { mutableStateOf<Job?>(null) }
        var seekPreviewHideJob by remember { mutableStateOf<Job?>(null) }

        fun stopTrickplay(resume: Boolean) {
            ffStage = 0
            rwStage = 0
            rwJob?.cancel(); rwJob = null
            seekPreviewHideJob?.cancel(); seekPreviewHideJob = null
            seekPreviewVisible = false
            seekPreviewTargetMs = null
            exoPlayer.playbackParameters = PlaybackParameters(1f)
            if (resume) {
                exoPlayer.playWhenReady = true
                runCatching { exoPlayer.play() }
            }
        }

        fun showSeekPreview(base: Long, target: Long, autoHide: Boolean = true) {
            seekPreviewBaseMs = base
            seekPreviewTargetMs = target
            seekPreviewVisible = true
            seekPreviewHideJob?.cancel()
            if (autoHide) {
                seekPreviewHideJob = scope.launch {
                    delay(900)
                    seekPreviewVisible = false
                    seekPreviewTargetMs = null
                }
            } else {
                seekPreviewHideJob = null
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                rwJob?.cancel()
                seekPreviewHideJob?.cancel()
            }
        }

        val playerModifier = Modifier.fillMaxSize()


        AndroidView(
            modifier = playerModifier
                .graphicsLayer {
                    if (customScaleEnabled) {
                        scaleX = customScaleX.coerceIn(0.5f, 2.0f)
                        scaleY = customScaleY.coerceIn(0.5f, 2.0f)
                    }
                },
            factory = { LayoutInflater.from(ctx).inflate(R.layout.compose_player_view, null, false) as PlayerView },
            update = { view ->
                view.player = exoPlayer
                view.useController = false
                view.resizeMode = resizeMode
                // Ensure view can receive media keys
                try {
                    view.isFocusable = true
                    view.isFocusableInTouchMode = true
                    view.requestFocus()

                    view.setOnKeyListener { _, keyCode, event ->
                        val isDown = event.action == android.view.KeyEvent.ACTION_DOWN

                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            android.view.KeyEvent.KEYCODE_SPACE,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                            android.view.KeyEvent.KEYCODE_ENTER -> {
                                if (isDown) {
                                    com.chris.m3usuite.core.debug.GlobalDebug.logDpad("CENTER/PLAY_PAUSE", mapOf("screen" to "player", "type" to type))
                                    val trickplayActive = ffStage > 0 || rwStage > 0 || rwJob != null || exoPlayer.playbackParameters.speed != 1f
                                    if (type == "live") {
                                        stopTrickplay(resume = true)
                                        return@setOnKeyListener if (!quickActionsVisible) {
                                            quickActionsVisible = true; quickActionsFocusActive = false; true
                                        } else if (quickActionsFocusActive) {
                                            false
                                        } else {
                                            quickActionsVisible = false; true
                                        }
                                    } else {
                                        if (trickplayActive) {
                                            stopTrickplay(resume = true)
                                            controlsVisible = true; controlsTick++
                                        } else {
                                            controlsVisible = true; controlsTick++
                                            togglePlayPause()
                                        }
                                    }
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                                if (!isDown) return@setOnKeyListener true
                                com.chris.m3usuite.core.debug.GlobalDebug.logDpad("FFWD", mapOf("screen" to "player", "type" to type))
                                if (!canSeek || type == "live") return@setOnKeyListener false
                                rwStage = 0
                                rwJob?.cancel(); rwJob = null
                                seekPreviewHideJob?.cancel()
                                seekPreviewVisible = false
                                seekPreviewTargetMs = null
                                ffStage = (ffStage + 1) % (trickplaySpeeds.size + 1)
                                if (ffStage in trickplaySpeeds.indices) {
                                    val speed = trickplaySpeeds[ffStage]
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    exoPlayer.playWhenReady = true
                                    runCatching { exoPlayer.play() }
                                    controlsVisible = true; controlsTick++
                                } else {
                                    stopTrickplay(resume = false)
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                                if (!isDown) return@setOnKeyListener true
                                com.chris.m3usuite.core.debug.GlobalDebug.logDpad("REW", mapOf("screen" to "player", "type" to type))
                                if (!canSeek || type == "live") return@setOnKeyListener false
                                ffStage = 0
                                exoPlayer.playbackParameters = PlaybackParameters(1f)
                                exoPlayer.playWhenReady = false
                                val nextStage = (rwStage + 1) % (trickplaySpeeds.size + 1)
                                rwStage = nextStage
                                rwJob?.cancel(); rwJob = null
                                if (nextStage in trickplaySpeeds.indices) {
                                    val speed = trickplaySpeeds[nextStage]
                                    val stepMs = 2_000L
                                    val interval = (300f / speed).coerceAtLeast(60f).toLong()
                                    val base = exoPlayer.currentPosition
                                    seekPreviewBaseMs = base
                                    seekPreviewTargetMs = base
                                    seekPreviewVisible = true
                                    seekPreviewHideJob?.cancel()
                                    rwJob = scope.launch {
                                        while (isActive) {
                                            val cur = exoPlayer.currentPosition
                                            val target = (cur - stepMs).coerceAtLeast(0L)
                                            exoPlayer.seekTo(target)
                                            seekPreviewTargetMs = target
                                            controlsVisible = true; controlsTick++
                                            delay(interval)
                                        }
                                    }
                                } else {
                                    stopTrickplay(resume = false)
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (type != "live" && !canSeek) return@setOnKeyListener false
                                if (isDown && event.repeatCount == 0) {
                                    com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT", mapOf("screen" to "player", "type" to type))
                                    stopTrickplay(resume = false)
                                    if (type == "live") {
                                        jumpLive(-1)
                                    } else {
                                        val current = exoPlayer.currentPosition
                                        val target = (current - 10_000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                        showSeekPreview(current, target)
                                        controlsVisible = true; controlsTick++
                                    }
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (type != "live" && !canSeek) return@setOnKeyListener false
                                if (isDown && event.repeatCount == 0) {
                                    com.chris.m3usuite.core.debug.GlobalDebug.logDpad("RIGHT", mapOf("screen" to "player", "type" to type))
                                    stopTrickplay(resume = false)
                                    if (type == "live") {
                                        jumpLive(+1)
                                    } else {
                                        val current = exoPlayer.currentPosition
                                        val max = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                        val target = (current + 10_000L).coerceAtMost(max)
                                        exoPlayer.seekTo(target)
                                        showSeekPreview(current, target)
                                        controlsVisible = true; controlsTick++
                                    }
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                if (isDown) {
                                    com.chris.m3usuite.core.debug.GlobalDebug.logDpad("UP", mapOf("screen" to "player", "type" to type))
                                    stopTrickplay(resume = false)
                                    if (type == "live") {
                                        showLiveListSheet = true
                                    } else {
                                        controlsVisible = true; controlsTick++
                                        sliderFocus.requestFocus()
                                    }
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (!isDown) return@setOnKeyListener (type == "live")
                                com.chris.m3usuite.core.debug.GlobalDebug.logDpad("DOWN", mapOf("screen" to "player", "type" to type))
                                stopTrickplay(resume = false)
                                if (type == "live") {
                                    if (quickActionsVisible) {
                                        try { quickPipFocus.requestFocus() } catch (_: Throwable) {}
                                        quickActionsFocusActive = true
                                    } else {
                                        scope.launch { refreshEpgOverlayForLive(currentLiveId ?: mediaId) }
                                        showEpgOverlay = true
                                        scheduleAutoHide(overTitleMs = 0L, epgMs = 3000L)
                                    }
                                    true
                                } else false
                            }
                            android.view.KeyEvent.KEYCODE_BACK -> {
                                if (isDown) {
                                    com.chris.m3usuite.core.debug.GlobalDebug.logDpad("BACK", mapOf("screen" to "player"))
                                    stopTrickplay(resume = false)
                                }
                                true
                            }
                            else -> false
                        }
                    }
                } catch (_: Throwable) {}
                view.subtitleView?.apply {
                    setApplyEmbeddedStyles(true)
                    setApplyEmbeddedFontSizes(true)
                    setFractionalTextSize(effectiveScale())

                    val fg = withOpacity(effectiveFg(), effectiveFgOpacity())
                    val bg = withOpacity(effectiveBg(), effectiveBgOpacity())
                    val style = CaptionStyleCompat(
                        fg,
                        bg,
                        0x00000000, // windowColor transparent
                        CaptionStyleCompat.EDGE_TYPE_NONE,
                        0x00000000,
                        /* typeface = */ null
                    )
                    setStyle(style)
                }
            }
        )

        // Seek preview overlay (shows absolute position and delta while seeking)
        if (seekPreviewAlpha > 0f && type != "live") {
            val tgt = seekPreviewTargetMs ?: 0L
            val base = seekPreviewBaseMs
            val delta = tgt - base
            fun fmt(ms: Long): String {
                val total = (ms / 1000).toInt()
                val h = total / 3600
                val m = (total % 3600) / 60
                val s = total % 60
                return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
            }
            val deltaStr = when {
                delta > 0 -> "+${delta / 1000}s"
                delta < 0 -> "-${(-delta) / 1000}s"
                else -> "0s"
            }
            androidx.compose.material3.Surface(
                color = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = seekPreviewAlpha }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Tiny progress arc
                    val prog = run {
                        val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                        if (dur > 0) (tgt.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                    }
                    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        drawArc(
                            color = Color.White.copy(alpha = 0.8f),
                            startAngle = -90f,
                            sweepAngle = 360f * prog,
                            useCenter = false,
                            style = stroke
                        )
                    }
                    Text(text = fmt(tgt), style = MaterialTheme.typography.titleMedium)
                    Text(text = deltaStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Touch: tap toggles controls; swipe left/right switches live channel; swipe up/down opens list/EPG
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible; if (controlsVisible) controlsTick++ })
                }
                .pointerInput(type, originLiveLibrary) {
                    if (type != "live") return@pointerInput
                    val threshold = 60f
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            dx += dragAmount.x; dy += dragAmount.y
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > threshold) {
                                if (dx < 0) jumpLive(+1) else jumpLive(-1)
                            } else if (kotlin.math.abs(dy) > threshold) {
                                if (dy < 0) { showLiveListSheet = true } else { scope.launch { refreshEpgOverlayForLive(currentLiveId ?: mediaId) }; showEpgOverlay = true; scheduleAutoHide(overTitleMs = 0L, epgMs = 3000L) }
                            }
                            dx = 0f; dy = 0f
                        }
                    )
                }
        )

        // Title/Episode/Channel overlay (top-left)
        if (showOverlayTitle && overlayTitle.isNotBlank()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 8.dp)
                        .graphicsLayer { }
                ) {
                    Text(text = overlayTitle, color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // Live EPG overlay (top-left, below title)
        if (type == "live" && showEpgOverlay && (epgNow.isNotBlank() || epgNext.isNotBlank())) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = if (showOverlayTitle) 30.dp else 8.dp)
                ) {
                    if (epgNow.isNotBlank()) Text(text = epgNow, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                    if (epgNext.isNotBlank()) Text(text = epgNext, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        
        // Live list sheets (favorites/global) inside player Box
        if (type == "live" && showLiveListSheet) {
            ModalBottomSheet(onDismissRequest = { showLiveListSheet = false }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (originLiveLibrary) "Senderliste" else "Favoriten", style = MaterialTheme.typography.titleMedium)
                    if (originLiveLibrary) {
                        // Nur Kategorien (keine Provider-Chips)
                        val itemsWithCats = remember(libraryLive) {
                            val catMap = runCatching {
                                val box = ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxCategory::class.java)
                                box.query(com.chris.m3usuite.data.obx.ObxCategory_.kind.equal("live")).build().find()
                                    .associate { it.categoryId to it.categoryName }
                            }.getOrNull() ?: emptyMap()
                            libraryLive.map { it.copy(categoryName = catMap[it.categoryId]) }
                        }
                        var selCat by remember { mutableStateOf(liveCategoryHint) }
                        val cats = remember(itemsWithCats) { itemsWithCats.mapNotNull { it.categoryName }.distinct().sorted() }
                        // Category chips (TV-friendly focus/scroll)
                        run {
                            com.chris.m3usuite.ui.tv.TvFocusRow(
                                stateKey = "live_list_chip_row",
                                itemSpacing = 8.dp,
                                itemCount = cats.size + 1,
                                itemKey = { idx -> if (idx == 0) "__all__" else cats[idx - 1] }
                            ) { idx ->
                                if (idx == 0) {
                                    androidx.compose.material3.FilterChip(
                                        modifier = FocusKit.run {
                                            Modifier.tvClickable { selCat = null }
                                        },
                                        selected = selCat == null,
                                        onClick = { selCat = null },
                                        label = { Text("Alle Kategorien") }
                                    )
                                } else {
                                    val c = cats[idx - 1]
                                    androidx.compose.material3.FilterChip(
                                        modifier = FocusKit.run {
                                            Modifier.tvClickable { selCat = c }
                                        },
                                        selected = selCat == c,
                                        onClick = { selCat = c },
                                        label = { Text(c) }
                                    )
                                }
                            }
                        }
                        val list = remember(itemsWithCats, selCat) {
                            itemsWithCats.filter { selCat == null || it.categoryName == selCat }
                        }
                        androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(list.size, key = { idx -> list[idx].id }) { idx ->
                                val mi = list[idx]
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .then(FocusKit.run { Modifier.tvClickable(onClick = { showLiveListSheet = false; switchToLive(mi) }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) })
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = mi.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    } else {
                        val items = favorites
                        androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(items.size, key = { idx -> items[idx].id }) { idx ->
                                val mi = items[idx]
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .then(FocusKit.run { Modifier.tvClickable(onClick = { showLiveListSheet = false; switchToLive(mi) }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) })
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = mi.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.focusScaleOnTv(), onClick = { showLiveListSheet = false }) { Text("Schließen") }
                    }
                }
            }
        }

        // Controls overlay (focusable on TV)
        if (controlsVisible) Popup(
            alignment = Alignment.Center,
            properties = PopupProperties(focusable = true, dismissOnBackPress = false, usePlatformDefaultWidth = false)
        ) {
            Box(Modifier
                .fillMaxSize()
                .then(FocusKit.run { Modifier.focusGroup() })
                // Any key activity within the overlay resets the auto-hide timer
                .onPreviewKeyEvent {
                    controlsTick++
                    false
                }
            ) {
                // When controls open, request focus to center control (TV)
                LaunchedEffect(Unit) { centerFocus.requestFocus() }
                // Top seekbar + close (80% opacity)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FocusKit.TvIconButton(onClick = { controlsTick++; finishAndRelease() }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Schließen",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    if (canSeek) {
                        Column(modifier = Modifier.weight(1f)) {
                            LaunchedEffect(Unit) {
                                while (true) {
                                    durationMs = exoPlayer.duration.coerceAtLeast(0)
                                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0)
                                    if (!isSeeking && durationMs > 0) sliderValue = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                    delay(250)
                                }
                            }
                            androidx.compose.material3.Slider(
                                value = sliderValue,
                                onValueChange = { isSeeking = true; sliderValue = it; controlsTick++ },
                                onValueChangeFinished = {
                                    val target = (sliderValue * durationMs).toLong().coerceIn(0L, durationMs)
                                    exoPlayer.seekTo(target)
                                    isSeeking = false
                                    controlsTick++
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(sliderFocus)
                            )
                            // Elapsed and remaining time (VOD/Series only)
                            if (type != "live") {
                                val elapsedText = formatTime(positionMs)
                                val remainMs = (durationMs - positionMs).coerceAtLeast(0L)
                                val remainingText = "-" + formatTime(remainMs)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(elapsedText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.90f))
                                    Text(remainingText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.90f))
                                }
                            }
                        }
                    }
                }

                // Center controls (transparent container)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusKit.TvIconButton(modifier = Modifier.focusRequester(centerFocus), onClick = {
                        controlsTick++
                        val pos = exoPlayer.currentPosition
                        exoPlayer.seekTo((pos - 10_000L).coerceAtLeast(0L))
                    }) { Icon(painter = painterResource(android.R.drawable.ic_media_rew), contentDescription = "-10s", tint = Color.White.copy(alpha = 0.8f)) }
                    FocusKit.TvIconButton(onClick = { controlsTick++; val playing = exoPlayer.playWhenReady && exoPlayer.isPlaying; exoPlayer.playWhenReady = !playing }) {
                        val icon = if (exoPlayer.playWhenReady && exoPlayer.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        Icon(painter = painterResource(icon), contentDescription = "Play/Pause", tint = Color.White.copy(alpha = 0.8f))
                    }
                    FocusKit.TvIconButton(onClick = {
                        controlsTick++
                        val pos = exoPlayer.currentPosition
                        val dur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        exoPlayer.seekTo((pos + 10_000L).coerceAtMost(dur))
                    }) { Icon(painter = painterResource(android.R.drawable.ic_media_ff), contentDescription = "+10s", tint = Color.White.copy(alpha = 0.8f)) }
                }

                // Bottom-right icon-only tiles (70% opacity, colored)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                OverlayIconButton(
                    modifier = Modifier.focusRequester(pipFocusRequester),
                    iconRes = android.R.drawable.ic_menu_slideshow,
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                    contentColor = Color.White
                ) {
                    controlsTick++
                    requestPictureInPicture()
                }
                OverlayIconButton(
                    modifier = Modifier.focusRequester(ccFocusRequester),
                    iconRes = android.R.drawable.ic_menu_sort_by_size,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    contentColor = Color.White
                ) {
                    controlsTick++
                    if (!showCcMenu) {
                        localScale = effectiveScale(); localFg = effectiveFg(); localBg = effectiveBg()
                        localFgOpacity = effectiveFgOpacity(); localBgOpacity = effectiveBgOpacity()
                        refreshSubtitleOptions()
                    }
                    showCcMenu = true
                }
                OverlayIconButton(
                    modifier = Modifier.focusRequester(resizeFocusRequester),
                    iconRes = android.R.drawable.ic_menu_crop,
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    onLongClick = { showAspectMenu = true }
                ) {
                        controlsTick++
                        customScaleEnabled = false
                        cycleResize()
                    }
                }
            }
        }
        }

        // Quick actions popup: bottom-right buttons only, persistent until toggled off
        if (type == "live" && quickActionsVisible) {
            Box(Modifier
                .fillMaxSize()
                .then(FocusKit.run { Modifier.focusGroup() })
            ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverlayIconButton(
                    modifier = Modifier.focusRequester(quickPipFocus),
                    iconRes = android.R.drawable.ic_menu_slideshow,
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                    contentColor = Color.White
                ) {
                    requestPictureInPicture()
                }
                OverlayIconButton(
                    modifier = Modifier.focusRequester(quickCcFocus),
                    iconRes = android.R.drawable.ic_menu_sort_by_size,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    contentColor = Color.White
                ) {
                    if (!showCcMenu) {
                        localScale = effectiveScale(); localFg = effectiveFg(); localBg = effectiveBg()
                        localFgOpacity = effectiveFgOpacity(); localBgOpacity = effectiveBgOpacity()
                        refreshSubtitleOptions()
                    }
                    showCcMenu = true
                }
                OverlayIconButton(
                    modifier = Modifier.focusRequester(quickAspectFocus),
                    iconRes = android.R.drawable.ic_menu_crop,
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    contentColor = Color.White,
                    onLongClick = { showAspectMenu = true }
                ) {
                    customScaleEnabled = false
                    cycleResize()
                }
            }
            }
        }

        if (kidBlocked && kidActive) {
                    AlertDialog(
                        onDismissRequest = { /* block */ },
                        title = { Text("Limit erreicht") },
                        text = { Text("Das Screen-Time-Limit für heute ist aufgebraucht.") },
                        confirmButton = {
                            TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { finishAndRelease() }) { Text("OK") }
                        }
                    )
        }
        // No exit confirmation; resume is always auto-saved/cleared per logic above

        if (showCcMenu && isAdult) {
                    ModalBottomSheet(onDismissRequest = { showCcMenu = false }) {
                        // Audio + Subtitle options + live style controls (scrollable)
                        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                            Text("Audio")
                            Spacer(Modifier.padding(4.dp))
                            audioOptions.forEach { opt ->
                                val selected = selectedAudio == opt
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                                    selectedAudio = opt
                                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                    builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                    builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    if (opt.groupIndex != null && opt.trackIndex != null) {
                                        val group = exoPlayer.currentTracks.groups[opt.groupIndex]
                                        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(opt.trackIndex))
                                        builder.addOverride(override)
                                    }
                                    exoPlayer.trackSelectionParameters = builder.build()
                                }) { Text((if (selected) "• " else "") + opt.label) }
                                Spacer(Modifier.padding(2.dp))
                            }

                            Spacer(Modifier.padding(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.padding(8.dp))

                            Text("Untertitel",)
                            Spacer(Modifier.padding(4.dp))
                            // Track selection
                            subOptions.forEach { opt ->
                                val selected = selectedSub == opt
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                                    selectedSub = opt
                                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                    if (opt.groupIndex == null) {
                                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    } else {
                                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        val group = exoPlayer.currentTracks.groups[opt.groupIndex]
                                        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(opt.trackIndex!!))
                                        builder.addOverride(override)
                                    }
                                    exoPlayer.trackSelectionParameters = builder.build()
                                }, enabled = true) {
                                    Text((if (selected) "• " else "") + (opt.label))
                                }
                                Spacer(Modifier.padding(2.dp))
                            }

                            Spacer(Modifier.padding(8.dp))
                            Text("Größe: ${String.format("%.2f", effectiveScale())}")
                            Slider(value = effectiveScale(), onValueChange = { v -> localScale = v }, valueRange = 0.04f..0.12f, steps = 8)

                            Text("Text-Deckkraft: ${effectiveFgOpacity()}%")
                            Slider(value = effectiveFgOpacity().toFloat(), onValueChange = { v -> localFgOpacity = v.toInt().coerceIn(0,100) }, valueRange = 0f..100f, steps = 10)

                            Text("Hintergrund-Deckkraft: ${effectiveBgOpacity()}%")
                            Slider(value = effectiveBgOpacity().toFloat(), onValueChange = { v -> localBgOpacity = v.toInt().coerceIn(0,100) }, valueRange = 0f..100f, steps = 10)

                            Spacer(Modifier.padding(8.dp))
                            // Quick presets
                            Text("Presets")
                            Row { 
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = { localScale = 0.05f }) { Text("Klein") }
                                Spacer(Modifier.padding(4.dp))
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = { localScale = 0.06f }) { Text("Standard") }
                                Spacer(Modifier.padding(4.dp))
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = { localScale = 0.08f }) { Text("Groß") }
                            }
                            Spacer(Modifier.padding(4.dp))
                            Row { Button(modifier = Modifier.focusScaleOnTv(), onClick = { localFg = 0xFFFFFFFF.toInt(); localBg = 0x66000000 }) { Text("Hell auf dunkel") }; Spacer(Modifier.padding(4.dp)); Button(modifier = Modifier.focusScaleOnTv(), onClick = { localFg = 0xFF000000.toInt(); localBg = 0x66FFFFFF }) { Text("Dunkel auf hell") } }
                            Spacer(Modifier.padding(4.dp))
                            Row { Button(modifier = Modifier.focusScaleOnTv(), onClick = { localBg = 0x66000000 }) { Text("BG Schwarz") }; Spacer(Modifier.padding(4.dp)); Button(modifier = Modifier.focusScaleOnTv(), onClick = { localBg = 0x66FFFFFF }) { Text("BG Weiß") } }

                            Spacer(Modifier.padding(8.dp))
                            Row {
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = { showCcMenu = false }) { Text("Schließen") }
                                Spacer(Modifier.padding(8.dp))
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = {
                                    // Persist as default
                                    localScale?.let { scope.launch { store.setFloat(com.chris.m3usuite.prefs.Keys.SUB_SCALE, it) } }
                                    localFg?.let { scope.launch { store.setInt(com.chris.m3usuite.prefs.Keys.SUB_FG, it) } }
                                    localBg?.let { scope.launch { store.setInt(com.chris.m3usuite.prefs.Keys.SUB_BG, it) } }
                                    localFgOpacity?.let { scope.launch { store.setSubtitleFgOpacityPct(it) } }
                                    localBgOpacity?.let { scope.launch { store.setSubtitleBgOpacityPct(it) } }
                                    showCcMenu = false
                                }) { Text("Als Standard speichern") }
                            }
                }
            }
            // Bottom controls now rendered inside the consolidated popup above

            if (showAspectMenu) {
                ModalBottomSheet(onDismissRequest = { showAspectMenu = false }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bildformat", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(modifier = Modifier.focusScaleOnTv(), onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; customScaleEnabled = false }) { Text("Original") }
                            Button(modifier = Modifier.focusScaleOnTv(), onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; customScaleEnabled = false }) { Text("Vollbild") }
                            Button(modifier = Modifier.focusScaleOnTv(), onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; customScaleEnabled = false }) { Text("Stretch") }
                        }
                        Spacer(Modifier.padding(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Custom")
                            androidx.compose.material3.Switch(checked = customScaleEnabled, onCheckedChange = { enabled ->
                                customScaleEnabled = enabled
                                if (enabled) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            })
                        }
                        if (customScaleEnabled) {
                            Text("Horizontal: ${String.format("%.2f", customScaleX)}x")
                            Slider(value = customScaleX, onValueChange = { customScaleX = it }, valueRange = 0.5f..2.0f, steps = 10)
                            Text("Vertikal: ${String.format("%.2f", customScaleY)}x")
                            Slider(value = customScaleY, onValueChange = { customScaleY = it }, valueRange = 0.5f..2.0f, steps = 10)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(modifier = Modifier.focusScaleOnTv(), onClick = { customScaleX = 1f; customScaleY = 1f }) { Text("Reset") }
                            }
                        }
                        Row(Modifier.padding(top = 8.dp)) {
                            Button(modifier = Modifier.focusScaleOnTv(), onClick = { showAspectMenu = false }) { Text("Schließen") }
                        }
                    }
                }
            }
        }
    }

    // (moved) Live list sheets are rendered inside the player Box above
// helper: apply opacity to ARGB color
private fun withOpacity(argb: Int, percent: Int): Int {
    val p = percent.coerceIn(0, 100)
    val a = (p / 100f * 255f).toInt().coerceIn(0, 255)
    val rgb = argb and 0x00FFFFFF
    return (a shl 24) or rgb
}

// helper: format milliseconds to mm:ss or hh:mm:ss
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val s = (total % 60).toInt()
    val m = ((total / 60) % 60).toInt()
    val h = (total / 3600).toInt()
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

@Composable
private fun OverlayActionTile(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = contentColorFor(MaterialTheme.colorScheme.surface)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverlayIconButton(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val clickableModifier = if (onLongClick != null) {
        modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        modifier.then(FocusKit.run { Modifier.tvClickable(onClick = onClick) })
    }
    ElevatedCard(
        shape = shape,
        elevation = CardDefaults.elevatedCardElevation(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = clickableModifier
            .focusable()
            .focusScaleOnTv()
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = contentColor)
        }
    }
}
