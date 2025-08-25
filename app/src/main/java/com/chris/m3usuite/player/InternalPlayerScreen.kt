@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.chris.m3usuite.player

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.StyledPlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.chris.m3usuite.data.db.AppDatabase
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.ResumeMark
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

/**
 * Interner Player (Media3) mit:
 * - Untertitel-Stil aus Settings
 * - Resume-Speicherung alle 2s & beim Schließen
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

    val db: AppDatabase = remember(ctx) { DbProvider.get(ctx) }
    val store = remember(ctx) { SettingsStore(ctx) }

    // Settings (Untertitel)
    val subScale by store.subtitleScale.collectAsState(initial = 0.06f)
    val subFg by store.subtitleFg.collectAsState(initial = 0xF2FFFFFF.toInt())
    val subBg by store.subtitleBg.collectAsState(initial = 0x66000000)
    val subFgOpacity by store.subtitleFgOpacityPct.collectAsState(initial = 90)
    val subBgOpacity by store.subtitleBgOpacityPct.collectAsState(initial = 40)
    val rotationLocked by store.rotationLocked.collectAsState(initial = false)

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
                playWhenReady = true
                startPositionMs?.let { seekTo(it) }
            }
    }

    // Lifecycle: Pause/Resume/Release
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_DESTROY -> {
                    runBlocking { saveResume(db, type, mediaId, episodeId, exoPlayer.currentPosition) }
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

    // Periodisch speichern (live wird in saveResume() ignoriert)
    LaunchedEffect(url, type, mediaId, episodeId, exoPlayer) {
        while (isActive) {
            saveResume(db, type, mediaId, episodeId, exoPlayer.currentPosition)
            delay(2000)
        }
    }

    fun finishAndRelease() {
        runBlocking { saveResume(db, type, mediaId, episodeId, exoPlayer.currentPosition) }
        exoPlayer.release()
        onExit()
    }

    BackHandler { finishAndRelease() }

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
                }
            )
        },
        content = { padding: PaddingValues ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { StyledPlayerView(ctx) },
                    update = { view ->
                        view.player = exoPlayer
                        view.subtitleView?.apply {
                            setApplyEmbeddedStyles(true)
                            setApplyEmbeddedFontSizes(true)
                            setFractionalTextSize(subScale)

                            val fg = withOpacity(subFg, subFgOpacity)
                            val bg = withOpacity(subBg, subBgOpacity)
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
            }
        }
    )
}

private fun saveResume(
    db: AppDatabase,
    type: String,
    mediaId: Long?,
    episodeId: Int?,
    posMs: Long
) {
    if (type == "live") return // Live nicht persistieren
    val pos = (posMs / 1000).toInt().coerceAtLeast(0)
    when {
        type == "vod" && mediaId != null -> {
            runBlocking {
                db.resumeDao().upsert(
                    ResumeMark(
                        type = "vod",
                        mediaId = mediaId,
                        episodeId = null,
                        positionSecs = pos,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        type == "series" && episodeId != null -> {
            runBlocking {
                db.resumeDao().upsert(
                    ResumeMark(
                        type = "series",
                        mediaId = null,
                        episodeId = episodeId,
                        positionSecs = pos,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        else -> Unit
    }
}

private fun withOpacity(argb: Int, percent: Int): Int {
    val p = percent.coerceIn(0, 100)
    val a = (p / 100f * 255f).toInt().coerceIn(0, 255)
    val rgb = argb and 0x00FFFFFF
    return (a shl 24) or rgb
}
