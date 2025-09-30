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
import coil3.request.allowHardware
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.request.crossfade
import coil3.request.allowRgb565
import com.chris.m3usuite.R
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.core.http.RequestHeadersProvider
import com.chris.m3usuite.ui.debug.safePainter
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
    // Compute synchronously once to avoid a second image request that can cause flicker.
    return remember(store) {
        val map = RequestHeadersProvider.defaultHeadersBlocking(store)
        val ua = map["User-Agent"] ?: map["user-agent"] ?: ""
        val ref = map["Referer"] ?: map["referer"] ?: ""
        val extras = map.filterKeys { it.lowercase() !in setOf("user-agent", "referer") }
        ImageHeaders(ua = ua, referer = ref, extras = extras)
    }
}
/**
 * Build an ImageRequest with network headers and optional crossfade.
 */
fun buildImageRequest(
    ctx: Context,
    url: Any?,
    crossfade: Boolean = true,
    headers: ImageHeaders? = null,
    widthPx: Int? = null,
    heightPx: Int? = null,
    preferRgb565: Boolean = true,
    cacheKeySuffix: String? = null
): ImageRequest {
    val httpHeaders = NetworkHeaders.Builder().apply {
        headers?.asMap()?.forEach { (k, v) -> set(k, v) }
    }.build()
    val sizedUrl: Any? = when (url) {
        is String -> rewriteTmdbUrlForSize(url, widthPx, heightPx)
        else -> url
    }
    val b = ImageRequest.Builder(ctx)
        .data(sizedUrl)
        .httpHeaders(httpHeaders)
        // TV low-spec: avoid crossfade to reduce GPU work
        .crossfade(if (com.chris.m3usuite.core.device.DeviceProfile.isTvLowSpec(ctx)) false else crossfade)
        .allowHardware(true)
        .allowRgb565(preferRgb565)
    if (widthPx != null && heightPx != null && widthPx > 0 && heightPx > 0) {
        // Prefer explicit pixel size if available; Compose will otherwise supply a resolver.
        b.size(widthPx, heightPx)
    }
    val cacheKey = when (sizedUrl) {
        is String -> sizedUrl
        null -> null
        else -> sizedUrl.toString()
    }?.let { dataKey ->
        if (cacheKeySuffix.isNullOrBlank()) dataKey else "$dataKey@$cacheKeySuffix"
    }
    if (!cacheKey.isNullOrEmpty()) {
        b.memoryCacheKey(cacheKey)
        b.diskCacheKey(cacheKey)
    }
    return b.build()
}
/**
 * Convenience wrapper that always uses our header-aware ImageRequest.
 */
@Composable
fun AppAsyncImage(
    url: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    crossfade: Boolean = false,
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
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val request = remember(url, headers, crossfade, measured) {
        val suffix = if (measured.width > 0 && measured.height > 0) "${measured.width}x${measured.height}" else null
        buildImageRequest(
            ctx = ctx,
            url = url,
            crossfade = crossfade,
            headers = headers,
            widthPx = measured.width.takeIf { it > 0 },
            heightPx = measured.height.takeIf { it > 0 },
            preferRgb565 = true,
            cacheKeySuffix = suffix
        )
    }
    val resolvedPlaceholder = placeholder ?: safePainter(R.drawable.fisch_bg, label = "Images/placeholder")
    val resolvedError = error ?: safePainter(R.drawable.fisch_header, label = "Images/error")
    AsyncImage(
        imageLoader = AppImageLoader.get(ctx),
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.onSizeChanged { newSize -> if (measured != newSize) measured = newSize },
        contentScale = contentScale,
        alignment = alignment,
        placeholder = resolvedPlaceholder,
        error = resolvedError,
        onLoading = { onLoading?.invoke() },
        onSuccess = { onSuccess?.invoke() },
        onError = { onError?.invoke() }
    )
}
@Composable
fun AppPosterImage(
    url: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    crossfade: Boolean = false,
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
    // Try TMDb size fallbacks: w342 → w185 → w154 → original
    val posterCandidates = remember { listOf("w342", "w185", "w154", "original") }
    var attempt by remember(url) { mutableStateOf(0) }
    val sized = remember(url, attempt) {
        when (url) {
            is String -> forceTmdbSizeOrOriginal(url, posterCandidates.getOrElse(attempt) { "original" })
            else -> url
        }
    }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val request = remember(sized, headers, crossfade, measured) {
        val suffix = if (measured.width > 0 && measured.height > 0) "${measured.width}x${measured.height}" else null
        buildImageRequest(
            ctx = ctx,
            url = sized,
            crossfade = crossfade,
            headers = headers,
            widthPx = measured.width.takeIf { it > 0 },
            heightPx = measured.height.takeIf { it > 0 },
            preferRgb565 = true,
            cacheKeySuffix = suffix
        )
    }
    val resolvedPlaceholder = placeholder ?: safePainter(R.drawable.fisch_bg, label = "Images/placeholder")
    val resolvedError = error ?: safePainter(R.drawable.fisch_header, label = "Images/error")
    AsyncImage(
        imageLoader = AppImageLoader.get(ctx),
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.onSizeChanged { measured = it },
        contentScale = contentScale,
        alignment = alignment,
        placeholder = resolvedPlaceholder,
        error = resolvedError,
        onLoading = { onLoading?.invoke() },
        onSuccess = { onSuccess?.invoke() },
        onError = {
            // Try next candidate size if available; only surface error when exhausted
            val next = attempt + 1
            if (url is String && next < posterCandidates.size) {
                attempt = next
            } else {
                onError?.invoke()
            }
        }
    )
}
@Composable
fun AppHeroImage(
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
    // Try TMDb size fallbacks for hero: w780 → w500 → w342 → original
    val heroCandidates = remember { listOf("w780", "w500", "w342", "original") }
    var attempt by remember(url) { mutableStateOf(0) }
    val sized = remember(url, attempt) {
        when (url) {
            is String -> forceTmdbSizeOrOriginal(url, heroCandidates.getOrElse(attempt) { "original" })
            else -> url
        }
    }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val request = remember(sized, headers, crossfade, measured) {
        val suffix = if (measured.width > 0 && measured.height > 0) "${measured.width}x${measured.height}" else null
        buildImageRequest(
            ctx = ctx,
            url = sized,
            crossfade = crossfade,
            headers = headers,
            widthPx = measured.width.takeIf { it > 0 },
            heightPx = measured.height.takeIf { it > 0 },
            preferRgb565 = true,
            cacheKeySuffix = suffix
        )
    }
    val resolvedPlaceholder = placeholder ?: safePainter(R.drawable.fisch_bg, label = "Images/placeholder")
    val resolvedError = error ?: safePainter(R.drawable.fisch_header, label = "Images/error")
    AsyncImage(
        imageLoader = AppImageLoader.get(ctx),
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.onSizeChanged { measured = it },
        contentScale = contentScale,
        alignment = alignment,
        placeholder = resolvedPlaceholder,
        error = resolvedError,
        onLoading = { onLoading?.invoke() },
        onSuccess = { onSuccess?.invoke() },
        onError = {
            val next = attempt + 1
            if (url is String && next < heroCandidates.size) {
                attempt = next
            } else {
                onError?.invoke()
            }
        }
    )
}
private fun forceTmdbSize(url: String, sizeLabel: String): String {
    val m = TMDB_REGEX.matchEntire(url) ?: return url
    val current = m.groupValues.getOrNull(1) ?: return url
    val file = m.groupValues.getOrNull(2) ?: return url
    if (current.equals(sizeLabel, ignoreCase = true)) return url
    return "https://image.tmdb.org/t/p/$sizeLabel/$file"
}
private fun forceTmdbSizeOrOriginal(url: String, sizeLabel: String): String {
    val m = TMDB_REGEX.matchEntire(url) ?: return url
    val file = m.groupValues.getOrNull(2) ?: return url
    val lbl = sizeLabel.lowercase()
    return if (lbl == "original") "https://image.tmdb.org/t/p/original/$file" else forceTmdbSize(url, sizeLabel)
}
// ---------------------------------------------------------
// TMDb utilities: rewrite image.tmdb.org URLs to best-fit sizes
// ---------------------------------------------------------
private fun tmdbSizeForWidth(px: Int): String {
    // Choose the nearest lower/equal TMDb size bucket for posters/backdrops
    return when {
        px <= 154 -> "w154"
        px <= 185 -> "w185"
        px <= 342 -> "w342"
        px <= 500 -> "w500"
        px <= 780 -> "w780"
        else -> "w780" // safe upper bound; 'original' is also possible but larger
    }
}
private val TMDB_REGEX = Regex("^https?://image\\.tmdb\\.org/t/p/([^/]+)/(.+)$", RegexOption.IGNORE_CASE)
private fun rewriteTmdbUrlForSize(url: String, widthPx: Int?, heightPx: Int?): String {
    val m = TMDB_REGEX.matchEntire(url) ?: return url
    val sizeLabel = m.groupValues.getOrNull(1) ?: return url
    val file = m.groupValues.getOrNull(2) ?: return url
    val target = (widthPx ?: 0).coerceAtLeast(0)
    if (target <= 0) return url
    val desired = tmdbSizeForWidth(target)
    // If already the same size, keep as-is
    if (sizeLabel.equals(desired, ignoreCase = true)) return url
    return "https://image.tmdb.org/t/p/$desired/$file"
}
