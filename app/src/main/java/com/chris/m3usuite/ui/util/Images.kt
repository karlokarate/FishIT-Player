package com.chris.m3usuite.ui.util

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.core.http.RequestHeadersProvider

/**
 * Image utilities (Coil3)
 *
 * - rememberImageHeaders(): collects the current default HTTP headers (User-Agent, Referer, extras)
 * - buildImageRequest(...): builds a Coil3 ImageRequest with those headers
 * - AppAsyncImage(...): small wrapper over AsyncImage that always attaches headers
 */

data class ImageHeaders(
    val ua: String = "",
    val referer: String = "",
    val extras: Map<String, String> = emptyMap()
) {
    fun asMap(): Map<String, String> {
        val m = mutableMapOf<String, String>()
        if (ua.isNotBlank()) m["User-Agent"] = ua
        if (referer.isNotBlank()) m["Referer"] = referer
        m.putAll(extras)
        return m
    }
}

@Composable
fun rememberImageHeaders(): ImageHeaders {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    var headers by remember { mutableStateOf(ImageHeaders()) }

    LaunchedEffect(Unit) {
        // Pull a one-shot snapshot from the RequestHeadersProvider.
        // We prefer the provider to ensure parity with OkHttp/Media3.
        val map = RequestHeadersProvider.defaultHeadersBlocking(store)
        val ua = map["User-Agent"] ?: map["user-agent"] ?: ""
        val ref = map["Referer"] ?: map["referer"] ?: ""
        val extras = map.filterKeys { it.lowercase() !in setOf("user-agent", "referer") }
        headers = ImageHeaders(ua = ua, referer = ref, extras = extras)
    }
    return headers
}

/**
 * Build an ImageRequest with network headers and optional crossfade.
 */
fun buildImageRequest(
    ctx: Context,
    url: Any?,
    crossfade: Boolean = true,
    headers: ImageHeaders? = null
): ImageRequest {
    val httpHeaders = NetworkHeaders.Builder().apply {
        headers?.asMap()?.forEach { (k, v) -> set(k, v) }
    }.build()

    return ImageRequest.Builder(ctx)
        .data(url)
        .httpHeaders(httpHeaders)
        .crossfade(crossfade)
        .build()
}

/**
 * Convenience wrapper that always uses our header-aware ImageRequest.
 */
@Composable
fun AppAsyncImage(
    url: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    crossfade: Boolean = true,
    headers: ImageHeaders = rememberImageHeaders(),
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: Painter? = null,
    error: Painter? = null,
    onLoading: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val request = remember(url, headers, crossfade) {
        buildImageRequest(ctx, url, crossfade, headers)
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        placeholder = placeholder,
        error = error,
        onLoading = { onLoading?.invoke() },
        onSuccess = { onSuccess?.invoke() },
        onError = { onError?.invoke() }
    )
}
