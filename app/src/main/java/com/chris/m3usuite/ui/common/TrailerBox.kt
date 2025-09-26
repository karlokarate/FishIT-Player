package com.chris.m3usuite.ui.common

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DefaultHttpDataSource
import com.chris.m3usuite.ui.common.AppIcon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.media3.common.util.UnstableApi
import com.chris.m3usuite.R
import com.chris.m3usuite.ui.debug.safePainter

private fun isYoutubeUrl(url: String): Boolean =
    url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)

private fun youtubeIdFromUrl(url: String): String? {
    return runCatching {
        val u = Uri.parse(url)
        when {
            u.host?.contains("youtu.be", true) == true -> u.lastPathSegment
            u.host?.contains("youtube.com", true) == true -> u.getQueryParameter("v")
            else -> null
        }
    }.getOrNull()
}

private fun youtubeEmbedUrl(url: String): String? {
    val id = youtubeIdFromUrl(url) ?: return null
    return "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&modestbranding=1"
}

@OptIn(UnstableApi::class)
@Composable
fun TrailerBox(
    url: String,
    headers: Map<String, String> = emptyMap(),
    expanded: MutableState<Boolean>,
    modifier: Modifier = Modifier.height(220.dp)
) {
    val isYt = remember(url) { isYoutubeUrl(url) }

    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
            .clip(shape)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
        ) {
            if (isYt) {
                val embed = remember(url) { youtubeEmbedUrl(url) }
                if (embed != null) {
                    YoutubeWebView(embed)
                } else {
                    Text("Trailer nicht abspielbar", color = Color.White)
                }
            } else {
                SimpleVideoBox(url = url, headers = headers)
            }
            IconButton(onClick = { expanded.value = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                val requested = AppIcon.PlayCircle.resId()
                val resolved = if (requested != 0) {
                    requested
                } else {
                    Log.w("TrailerBox", "Missing drawable for AppIcon.PlayCircle – using fallback icon")
                    R.drawable.ic_play_circle_primary
                }
                Icon(painter = safePainter(resolved, label = "TrailerBox"), contentDescription = "Vollbild", tint = Color.White)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YoutubeWebView(embedUrl: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = WebViewClient()
                loadUrl(embedUrl)
            }
        },
        update = { it.loadUrl(embedUrl) }
    )
}

@UnstableApi
@Composable
private fun SimpleVideoBox(url: String, headers: Map<String, String>) {
    val context = LocalContext.current
    val player = remember {
        // Media3 official pattern: Build a player once per composition, release in DisposableEffect
        val trackSelector = DefaultTrackSelector(context)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        if (headers.isNotEmpty()) httpFactory.setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory)
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    DisposableEffect(url) {
        val item = MediaItem.fromUri(url)
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                this.player = player
            }
        },
        update = { view -> view.player = player }
    )
}
