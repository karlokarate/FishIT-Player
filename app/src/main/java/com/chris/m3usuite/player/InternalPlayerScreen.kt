@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.chris.m3usuite.player

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import com.chris.m3usuite.data.db.AppDatabase
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.ResumeMark
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.media3.common.TrackSelectionOverride

/**
 * Interner Player (Media3) mit:
 * - Untertitel-Stil aus Settings
 * - Resume-Speicherung alle ~3s & beim Schließen
 * - optionaler Startposition
 *
 * type: "vod" | "series" | "live"  (live wird nicht persistiert)
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val httpFactory = remember(headers) {
        DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
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

    fun finishAndRelease() {
        // resume: clear if near end (<10s), otherwise save
        runBlocking {
            try {
                if (type != "live") {
                    if ((type == "vod" && mediaId != null) || (type == "series" && episodeId != null)) {
                        val dur = exoPlayer.duration
                        val pos = exoPlayer.currentPosition
                        if (pos in 1..14999 && !discardResume) {
                            confirmExit = true
                            return@runBlocking
                        }
                        val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
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
        onExit()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Wiedergabe") },
                navigationIcon = {
                    IconButton(onClick = { finishAndRelease() }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Schließen"
                        )
                    }
                },
                actions = {
                    if (isAdult) {
                        IconButton(onClick = {
                            // initialize local overrides from current effective values when opening
                            if (!showCcMenu) {
                                localScale = effectiveScale()
                                localFg = effectiveFg()
                                localBg = effectiveBg()
                                localFgOpacity = effectiveFgOpacity()
                                localBgOpacity = effectiveBgOpacity()
                                refreshSubtitleOptions()
                            }
                            showCcMenu = !showCcMenu
                        }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_sort_by_size),
                                contentDescription = "Untertitel-Menü"
                            )
                        }
                    }
                }
            )
        },
        content = { padding: PaddingValues ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { PlayerView(ctx) },
                    update = { view ->
                        view.player = exoPlayer
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
                        view.setControllerShowTimeoutMs(3000)
                    }
                )

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
                }
            }
        }
    )
}
// helper: apply opacity to ARGB color
private fun withOpacity(argb: Int, percent: Int): Int {
    val p = percent.coerceIn(0, 100)
    val a = (p / 100f * 255f).toInt().coerceIn(0, 255)
    val rgb = argb and 0x00FFFFFF
    return (a shl 24) or rgb
}
