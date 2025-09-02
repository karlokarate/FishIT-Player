package com.chris.m3usuite.ui.util

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import com.chris.m3usuite.core.http.RequestHeadersProvider

/**
 * Liest User-Agent und Referer aus dem SettingsStore einmalig ein
 * und hält sie im Compose-State, damit alle ImageRequests darauf zugreifen können.
 */
@Composable
fun rememberImageHeaders(): ImageHeaders {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }

    var ua by remember { mutableStateOf("IBOPlayer/1.4 (Android)") }
    var ref by remember { mutableStateOf("") }
    var extras by remember { mutableStateOf<Map<String,String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        runCatching {
            ua = store.userAgent.first()
            ref = store.referer.first()
            val extraJson = store.extraHeadersJson.first()
            extras = RequestHeadersProvider.parseExtraHeaders(extraJson)
        }
    }
    return ImageHeaders(ua = ua, referer = ref, extras = extras)
}

data class ImageHeaders(val ua: String, val referer: String, val extras: Map<String,String>)

/**
 * Baut einen Coil-3 ImageRequest inkl. HTTP-Headern (User-Agent/Referer).
 * Gibt null zurück, wenn die URL leer ist – AsyncImage kommt damit klar.
 */
fun buildImageRequest(
    ctx: Context,
    url: String?,
    headers: ImageHeaders,
    crossfade: Boolean = true
): ImageRequest? {
    if (url.isNullOrBlank()) return null

    val httpHeaders = NetworkHeaders.Builder()
        .set("User-Agent", headers.ua.ifBlank { "IBOPlayer/1.4 (Android)" })
        .apply {
            if (headers.referer.isNotBlank()) set("Referer", headers.referer)
            headers.extras.forEach { (k,v) -> set(k, v) }
        }
        .build()

    return ImageRequest.Builder(ctx)
        .data(url)
        .httpHeaders(httpHeaders)
        .crossfade(crossfade)
        .build()
}
