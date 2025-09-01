package com.chris.m3usuite.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.chris.m3usuite.R
import com.chris.m3usuite.data.db.AppDatabase
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.ResumeMark
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
    episodeId: Int? = null,       // Series
    startPositionMs: Long? = null,
    headers: Map<String, String> = emptyMap(),
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val db: AppDatabase = remember(ctx) { DbProvider.get(ctx) }
    val store = remember(ctx) { SettingsStore(ctx) }
    val screenTimeRepo = remember(ctx) { ScreenTimeRepository(ctx) }

    // Settings (Untertitel)
    val subScale by store.subtitleScale.collectAsState(initial = 0.06f)
    val subFg by store.subtitleFg.collectAsState(initial = 0xF2FFFFFF.toInt())
    val subBg by store.subtitleBg.collectAsState(initial = 0x66000000)
    val subFgOpacity by store.subtitleFgOpacityPct.collectAsState(initial = 90)
    val subBgOpacity by store.subtitleBgOpacityPct.collectAsState(initial = 40)
    val rotationLocked by store.rotationLocked.collectAsState(initial = false)
    val autoplayNext by store.autoplayNext.collectAsState(initial = false)

    // HTTP Factory mit Headern
    val extraJson by store.extraHeadersJson.collectAsState(initial = "")
    val mergedHeaders = remember(headers, extraJson) {
        val props = headers.toMutableMap()
        try {
            val o = org.json.JSONObject(extraJson)
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                props[k] = o.optString(k)
            }
        } catch (_: Throwable) {}
        props.toMap()
    }
    val httpFactory = remember(mergedHeaders) {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (mergedHeaders.isNotEmpty()) setDefaultRequestProperties(mergedHeaders)
            }
    }
    val dataSourceFactory = remember(httpFactory, ctx) {
        DefaultDataSource.Factory(ctx, httpFactory)
    }

    // Player
    val exoPlayer = remember(url, headers, dataSourceFactory) {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = false // Phase 4: erst nach Screen-Time-Check starten
                startPositionMs?.let { seekTo(it) }
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
                val prof = withContext(Dispatchers.IO) { db.profileDao().byId(id) }
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
    LaunchedEffect(type, mediaId, episodeId, exoPlayer) {
        if ((type == "vod" && mediaId != null) || (type == "series" && episodeId != null)) {
            try {
                if (startPositionMs == null) {
                    val posSecs = withContext(Dispatchers.IO) {
                        val dao = db.resumeDao()
                        val mark = if (type == "vod") dao.getVod(mediaId ?: -1) else dao.getEpisode(episodeId ?: -1)
                        mark?.positionSecs
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
                Lifecycle.Event.ON_PAUSE -> exoPlayer.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_DESTROY -> {
                    // resume: clear if near end (<10s), otherwise save
                    runBlocking {
                        try {
                            if (type != "live") {
                                val dur = exoPlayer.duration
                                val pos = exoPlayer.currentPosition
                                val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
                                if ((type == "vod" && mediaId != null) || (type == "series" && episodeId != null)) {
                                    if (dur > 0 && remaining in 0..9999) {
                                        withContext(Dispatchers.IO) {
                                            val dao = db.resumeDao()
                                            if (type == "vod") dao.clearVod(mediaId!!) else dao.clearEpisode(episodeId!!)
                                        }
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            val dao = db.resumeDao()
                                            val posSecs = (pos / 1000L).toInt().coerceAtLeast(0)
                                            val mark = ResumeMark(
                                                type = if (type == "vod") "vod" else "series",
                                                mediaId = if (type == "vod") mediaId else null,
                                                episodeId = if (type == "series") episodeId else null,
                                                positionSecs = posSecs,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                            dao.upsert(mark)
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
                if ((type == "vod" && mediaId != null) || (type == "series" && episodeId != null)) {
                    val dur = exoPlayer.duration
                    val pos = exoPlayer.currentPosition
                    val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
                    if (dur > 0 && remaining in 0..9999) {
                        withContext(Dispatchers.IO) {
                            val dao = db.resumeDao()
                            if (type == "vod") dao.clearVod(mediaId!!) else dao.clearEpisode(episodeId!!)
                        }
                    } else if (type != "live") {
                        val posSecs = (pos / 1000L).toInt().coerceAtLeast(0)
                        withContext(Dispatchers.IO) {
                            val dao = db.resumeDao()
                            val mark = ResumeMark(
                                type = if (type == "vod") "vod" else "series",
                                mediaId = if (type == "vod") mediaId else null,
                                episodeId = if (type == "series") episodeId else null,
                                positionSecs = posSecs,
                                updatedAt = System.currentTimeMillis()
                            )
                            dao.upsert(mark)
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

    // resume: clear on playback ended
    DisposableEffect(exoPlayer, type, mediaId, episodeId) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if ((type == "vod" && mediaId != null) || (type == "series" && episodeId != null)) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val dao = db.resumeDao()
                                if (type == "vod") dao.clearVod(mediaId!!) else dao.clearEpisode(episodeId!!)
                            } catch (_: Throwable) {}
                        }
                    }
                    // Phase 5: autoplay next for series
                    if (type == "series" && episodeId != null && autoplayNext) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val epDao = db.episodeDao()
                                val current = epDao.byEpisodeId(episodeId)
                                if (current != null) {
                                    val next = epDao.nextEpisode(current.seriesStreamId, current.season, current.episodeNum)
                                    if (next != null) {
                                        // rebuild url from Xtream config
                                        val host = store.xtHost.first()
                                        val user = store.xtUser.first()
                                        val pass = store.xtPass.first()
                                        val out  = store.xtOutput.first()
                                        val port = store.xtPort.first()
                                        if (host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                                            val cfg = com.chris.m3usuite.core.xtream.XtreamConfig(host, port, user, pass, out)
                                            val nextUrl = cfg.seriesEpisodeUrl(next.episodeId, next.containerExt)
                                            withContext(Dispatchers.Main) {
                                                exoPlayer.setMediaItem(MediaItem.fromUri(nextUrl))
                                                exoPlayer.prepare()
                                                exoPlayer.playWhenReady = true
                                            }
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

    var confirmExit by remember { mutableStateOf(false) }
    var discardResume by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }

    fun finishAndRelease() {
        if (finishing) return
        // Ask before exiting when watchtime is very short
        val dur = exoPlayer.duration
        val pos = exoPlayer.currentPosition
        if (type != "live" && ((type == "vod" && mediaId != null) || (type == "series" && episodeId != null))) {
            if (pos in 1..14999 && !discardResume) { confirmExit = true; return }
        }
        finishing = true
        scope.launch(Dispatchers.IO) {
            try {
                if (type != "live" && ((type == "vod" && mediaId != null) || (type == "series" && episodeId != null))) {
                    val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
                    val dao = db.resumeDao()
                    if (dur > 0 && remaining in 0..9999) {
                        if (type == "vod") dao.clearVod(mediaId!!) else dao.clearEpisode(episodeId!!)
                    } else {
                        val posSecs = (pos / 1000L).toInt().coerceAtLeast(0)
                        val mark = ResumeMark(
                            type = if (type == "vod") "vod" else "series",
                            mediaId = if (type == "vod") mediaId else null,
                            episodeId = if (type == "series") episodeId else null,
                            positionSecs = posSecs,
                            updatedAt = System.currentTimeMillis()
                        )
                        dao.upsert(mark)
                    }
                }
            } catch (_: Throwable) {}
            withContext(Dispatchers.Main) {
                try { exoPlayer.release() } catch (_: Throwable) {}
                onExit()
            }
        }
    }

    BackHandler { finishAndRelease() }

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

    fun refreshSubtitleOptions() {
        val tracks: Tracks = exoPlayer.currentTracks
        val opts = mutableListOf<SubOpt>()
        opts += SubOpt("Aus", null, null)
        var currentSel: SubOpt? = null
        tracks.groups.forEachIndexed { gi, g ->
            if (g.type == C.TRACK_TYPE_TEXT) {
                for (ti in 0 until g.length) {
                    val fmt = g.getTrackFormat(ti)
                    val lang = fmt.language ?: "Untertitel ${gi}-${ti}"
                    val label = if (fmt.label != null) fmt.label!! else lang
                    val opt = SubOpt(label, gi, ti)
                    opts += opt
                    if (g.isTrackSelected(ti)) currentSel = opt
                }
            }
        }
        subOptions = opts
        selectedSub = currentSel ?: opts.firstOrNull()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                refreshSubtitleOptions()
            }
        }
        exoPlayer.addListener(listener)
        refreshSubtitleOptions()
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

    // Aspect / resize controls
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    // Overlays: make container fully transparent (only buttons visible)
    val scrimAlpha = 0f
    val bottomScrimAlpha = 0f
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

    // Auto-hide controls after 3 seconds when visible and no modal open
    LaunchedEffect(controlsVisible, controlsTick, showCcMenu, showAspectMenu) {
        if (controlsVisible && !showCcMenu && !showAspectMenu) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
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
                        if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            android.view.KeyEvent.KEYCODE_SPACE,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                togglePlayPause(); true
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

        // Tap catcher to toggle controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { controlsVisible = !controlsVisible; if (controlsVisible) controlsTick++ }) }
        )

        // Controls overlay
        if (controlsVisible) Popup(
            alignment = Alignment.Center,
            properties = PopupProperties(focusable = false, dismissOnBackPress = false, usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize()) {
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
                    IconButton(onClick = { finishAndRelease() }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Schließen",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    if (canSeek) {
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
                            modifier = Modifier.weight(1f)
                        )
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
                    IconButton(onClick = {
                        val pos = exoPlayer.currentPosition
                        exoPlayer.seekTo((pos - 10_000L).coerceAtLeast(0L))
                    }) { Icon(painter = painterResource(android.R.drawable.ic_media_rew), contentDescription = "-10s", tint = Color.White.copy(alpha = 0.8f)) }
                    IconButton(onClick = { val playing = exoPlayer.playWhenReady && exoPlayer.isPlaying; exoPlayer.playWhenReady = !playing }) {
                        val icon = if (exoPlayer.playWhenReady && exoPlayer.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        Icon(painter = painterResource(icon), contentDescription = "Play/Pause", tint = Color.White.copy(alpha = 0.8f))
                    }
                    IconButton(onClick = {
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
                        iconRes = android.R.drawable.ic_menu_sort_by_size,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        contentColor = Color.White
                    ) {
                        if (!showCcMenu) {
                            localScale = effectiveScale(); localFg = effectiveFg(); localBg = effectiveBg();
                            localFgOpacity = effectiveFgOpacity(); localBgOpacity = effectiveBgOpacity();
                            refreshSubtitleOptions()
                        }
                        showCcMenu = true
                    }
                    OverlayIconButton(
                        iconRes = android.R.drawable.ic_menu_crop,
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        contentColor = Color.White,
                        onLongClick = { showAspectMenu = true }
                    ) {
                        customScaleEnabled = false
                        cycleResize()
                    }
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
                            TextButton(onClick = { finishAndRelease() }) { Text("OK") }
                        }
                    )
        }
        if (confirmExit) {
                    AlertDialog(
                        onDismissRequest = { confirmExit = false },
                        title = { Text("Wiedergabe beenden?") },
                        text = { Text("Fortschritt ist sehr gering. Resume speichern?") },
                        confirmButton = {
                            TextButton(onClick = { discardResume = true; confirmExit = false; finishAndRelease() }) { Text("Nicht speichern") }
                        },
                        dismissButton = {
                            TextButton(onClick = { discardResume = false; confirmExit = false; finishAndRelease() }) { Text("Speichern") }
                        }
                    )
        }

        if (showCcMenu && isAdult) {
                    ModalBottomSheet(onDismissRequest = { showCcMenu = false }) {
                        // Subtitle options + live style controls
                        Column(Modifier.padding(16.dp)) {
                            Text("Untertitel",)
                            Spacer(Modifier.padding(4.dp))
                            // Track selection
                            subOptions.forEach { opt ->
                                val selected = selectedSub == opt
                                Button(onClick = {
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
                                Button(onClick = { localScale = 0.05f }) { Text("Klein") }
                                Spacer(Modifier.padding(4.dp))
                                Button(onClick = { localScale = 0.06f }) { Text("Standard") }
                                Spacer(Modifier.padding(4.dp))
                                Button(onClick = { localScale = 0.08f }) { Text("Groß") }
                            }
                            Spacer(Modifier.padding(4.dp))
                            Row { Button(onClick = { localFg = 0xFFFFFFFF.toInt(); localBg = 0x66000000 }) { Text("Hell auf dunkel") }; Spacer(Modifier.padding(4.dp)); Button(onClick = { localFg = 0xFF000000.toInt(); localBg = 0x66FFFFFF }) { Text("Dunkel auf hell") } }
                            Spacer(Modifier.padding(4.dp))
                            Row { Button(onClick = { localBg = 0x66000000 }) { Text("BG Schwarz") }; Spacer(Modifier.padding(4.dp)); Button(onClick = { localBg = 0x66FFFFFF }) { Text("BG Weiß") } }

                            Spacer(Modifier.padding(8.dp))
                            Row {
                                Button(onClick = { showCcMenu = false }) { Text("Schließen") }
                                Spacer(Modifier.padding(8.dp))
                                Button(onClick = {
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
                        Text("Bildformat", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; customScaleEnabled = false }) { Text("Original") }
                            Button(onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; customScaleEnabled = false }) { Text("Vollbild") }
                            Button(onClick = { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; customScaleEnabled = false }) { Text("Stretch") }
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
                                Button(onClick = { customScaleX = 1f; customScaleY = 1f }) { Text("Reset") }
                            }
                        }
                        Row(Modifier.padding(top = 8.dp)) {
                            Button(onClick = { showAspectMenu = false }) { Text("Schließen") }
                        }
                    }
                }
            }
        }
    }
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
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val modifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else Modifier.then(Modifier.clickable(onClick = onClick))
    ElevatedCard(
        onClick = onClick,
        shape = shape,
        elevation = CardDefaults.elevatedCardElevation(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = contentColor)
        }
    }
}
