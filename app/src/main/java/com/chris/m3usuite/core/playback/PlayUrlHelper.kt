package com.chris.m3usuite.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.core.xtream.EndpointPortStore
import com.chris.m3usuite.core.xtream.ProviderCapabilityStore
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxVod
import com.chris.m3usuite.data.obx.ObxVod_
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Gemeinsamer URL-/Header-Aufbau für Live & VOD.
 * - Nutzt vorhandene URL oder baut Xtream-URLs (inkl. Port/Scheme)
 * - Liefert optionale Header (User-Agent, Referer)
 */
object PlayUrlHelper {
    data class PlayRequest(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val mimeType: String? = null,
    )

    suspend fun forVod(
        context: Context,
        store: SettingsStore,
        item: MediaItem,
    ): PlayRequest? = build(context, store, item, Type.VOD)

    suspend fun forLive(
        context: Context,
        store: SettingsStore,
        item: MediaItem,
    ): PlayRequest? = build(context, store, item, Type.LIVE)

    suspend fun defaultHeaders(store: SettingsStore): Map<String, String> {
        val ua = store.userAgent.first()
        val ref = store.referer.first()
        return buildMap {
            if (ua.isNotBlank()) put("User-Agent", ua)
            if (ref.isNotBlank()) put("Referer", ref)
        }
    }

    fun encodeUrl(url: String): String = URLEncoder.encode(url, StandardCharsets.UTF_8.name())

    @OptIn(UnstableApi::class)
    fun guessMimeType(
        url: String?,
        containerExt: String?,
    ): String? {
        val normalizedUrl =
            url
                ?.substringBefore('?')
                ?.lowercase()
                ?.trim()
                .orEmpty()
        val ext = containerExt?.lowercase()?.trim().orEmpty()
        val fromUrlExt = normalizedUrl.substringAfterLast('.', missingDelimiterValue = "").lowercase()

        return when {
            normalizedUrl.contains(".m3u8") || ext == "m3u8" -> MimeTypes.APPLICATION_M3U8
            normalizedUrl.contains(".mpd") || ext == "mpd" -> MimeTypes.APPLICATION_MPD
            normalizedUrl.contains(".ism/manifest") -> MimeTypes.APPLICATION_SS
            ext == "ts" || ext == "mpegts" || ext == "m2ts" || fromUrlExt == "ts" -> "video/mp2t"
            ext == "mp4" || fromUrlExt == "mp4" -> MimeTypes.VIDEO_MP4
            ext == "mkv" || fromUrlExt == "mkv" -> MimeTypes.VIDEO_MATROSKA
            ext == "webm" || fromUrlExt == "webm" -> MimeTypes.VIDEO_WEBM
            ext == "flv" || fromUrlExt == "flv" -> "video/x-flv"
            ext == "avi" || fromUrlExt == "avi" -> "video/x-msvideo"
            ext == "mov" || fromUrlExt == "mov" -> "video/quicktime"
            ext == "wmv" || fromUrlExt == "wmv" -> "video/x-ms-wmv"
            ext == "mp3" || fromUrlExt == "mp3" -> MimeTypes.AUDIO_MPEG
            ext == "aac" || fromUrlExt == "aac" -> MimeTypes.AUDIO_AAC
            ext == "ogg" || fromUrlExt == "ogg" || fromUrlExt == "ogv" -> "video/ogg"
            ext == "wav" || fromUrlExt == "wav" -> "audio/wav"
            normalizedUrl.startsWith("rtmp://") -> "application/x-rtmp"
            normalizedUrl.startsWith("rtsp://") -> "application/rtsp"
            else -> null
        }
    }

    // --- intern ---

    private enum class Type { LIVE, VOD }

    private suspend fun build(
        context: Context,
        store: SettingsStore,
        item: MediaItem,
        type: Type,
    ): PlayRequest? {
        var resolvedContainerExt = item.containerExt
        val url =
            when {
                // VOD with pre-set URL (e.g., from list/detail). If it's an Xtream movie URL, adjust extension to match known container.
                item.url != null -> {
                    val raw = item.url!!
                    if (type == Type.VOD && raw.contains("/movie/")) {
                        // Resolve containerExt: item → OBX → detail
                        var chosenExt = resolvedContainerExt?.lowercase()?.trim()
                        if (chosenExt.isNullOrBlank()) {
                            val obxExt =
                                runCatching {
                                    val box = ObxStore.get(context).boxFor<ObxVod>()
                                    val q = box.query(ObxVod_.vodId.equal((item.streamId ?: -1).toLong())).build()
                                    try {
                                        q.findFirst()?.containerExt
                                    } finally {
                                        q.close()
                                    }
                                }.getOrNull()
                            if (!obxExt.isNullOrBlank()) chosenExt = obxExt
                        }
                        if (chosenExt.isNullOrBlank() && item.streamId != null) {
                            runCatching {
                                val port = store.xtPort.first()
                                val scheme = if (port == 443) "https" else "http"
                                val http = HttpClientFactory.create(context, store)
                                val client = XtreamClient(http)
                                val caps = ProviderCapabilityStore(context)
                                val ports = EndpointPortStore(context)
                                client.initialize(
                                    scheme = scheme,
                                    host = store.xtHost.first(),
                                    username = store.xtUser.first(),
                                    password = store.xtPass.first(),
                                    basePath = null,
                                    livePrefs = listOf("m3u8", "ts"),
                                    store = caps,
                                    portStore = ports,
                                    portOverride = port,
                                )
                                val detailExt = client.getVodDetailFull(item.streamId!!)?.containerExt
                                if (!detailExt.isNullOrBlank()) {
                                    chosenExt = detailExt
                                    cacheVodContainerExt(context, item.streamId!!, detailExt!!)
                                }
                            }
                        }
                        if (!chosenExt.isNullOrBlank()) {
                            resolvedContainerExt = chosenExt
                            // Replace last extension with chosenExt
                            val base = raw.substringBeforeLast('.')
                            "$base.$chosenExt"
                        } else {
                            raw
                        }
                    } else {
                        raw
                    }
                }

                else -> {
                    val streamId = item.streamId ?: return null
                    val port = store.xtPort.first()
                    val scheme = if (port == 443) "https" else "http"
                    val output =
                        store.xtOutput
                            .first()
                            .trim()
                            .lowercase()
                    val livePrefs =
                        when (output) {
                            "hls" -> listOf("m3u8", "ts")
                            "m3u8" -> listOf("m3u8", "ts")
                            "ts" -> listOf("ts", "m3u8")
                            else -> listOf("m3u8", "ts")
                        }

                    val http = HttpClientFactory.create(context, store)
                    val client = XtreamClient(http)
                    val caps = ProviderCapabilityStore(context)
                    val ports = EndpointPortStore(context)
                    client.initialize(
                        scheme = scheme,
                        host = store.xtHost.first(),
                        username = store.xtUser.first(),
                        password = store.xtPass.first(),
                        basePath = null,
                        livePrefs = livePrefs,
                        store = caps,
                        portStore = ports,
                        portOverride = port,
                    )

                    when (type) {
                        Type.LIVE -> client.buildLivePlayUrl(streamId)
                        Type.VOD -> {
                            // 1) Prefer extension already on the item
                            var chosenExt = resolvedContainerExt?.lowercase()?.trim()

                            // 2) If missing, try ObjectBox cached VOD row
                            if (chosenExt.isNullOrBlank()) {
                                val obxExt =
                                    runCatching {
                                        val box = ObxStore.get(context).boxFor<ObxVod>()
                                        val q = box.query(ObxVod_.vodId.equal(streamId.toLong())).build()
                                        try {
                                            q.findFirst()?.containerExt
                                        } finally {
                                            q.close()
                                        }
                                    }.getOrNull()
                                if (!obxExt.isNullOrBlank()) chosenExt = obxExt
                            }

                            // 3) If still missing, try provider detail API
                            if (chosenExt.isNullOrBlank()) {
                                val detailExt = runCatching { client.getVodDetailFull(streamId)?.containerExt }.getOrNull()
                                if (!detailExt.isNullOrBlank()) {
                                    chosenExt = detailExt
                                    cacheVodContainerExt(context, streamId, detailExt!!)
                                }
                            }

                            resolvedContainerExt = chosenExt

                            client.buildVodPlayUrl(streamId, resolvedContainerExt)
                        }
                    }
                }
            } ?: return null

        // Standard: Redirects OkHttp/Exo überlassen; keine separate HEAD/Range-Auflösung
        val finalUrl = url

        val mimeType =
            when (type) {
                Type.VOD -> guessMimeType(finalUrl, resolvedContainerExt)
                Type.LIVE -> guessMimeType(finalUrl, null)
            }

        // Safe diagnostics: log host/port/kind/id + chosen ext source (no creds)
        if (com.chris.m3usuite.BuildConfig.DEBUG) {
            kotlin.runCatching {
                val host = store.xtHost.first()
                val port = store.xtPort.first()
                val kind = if (type == Type.LIVE) "live" else "vod"
                val extInfo = (resolvedContainerExt ?: "").ifBlank { "unknown" }
                com.chris.m3usuite.core.logging.UnifiedLog.log(
                    category = "player",
                    level = com.chris.m3usuite.core.logging.UnifiedLog.Level.DEBUG,
                    message = "resolved url for kind=$kind id=${item.streamId} host=$host port=$port ext=$extInfo from=${if (item.url != null) "item.url" else "builder"}",
                )
            }
        }

        return PlayRequest(url = finalUrl, headers = defaultHeaders(store), mimeType = mimeType)
    }

    suspend fun resolveFinalUrl(
        context: Context,
        store: SettingsStore,
        initialUrl: String,
    ): String {
        val withContext =
            withContext(Dispatchers.IO) {
                val client = HttpClientFactory.create(context, store)
                val headers = defaultHeaders(store)
                val builder =
                    okhttp3.Request
                        .Builder()
                        .url(initialUrl)
                        .header("Accept", "*/*")
                headers.forEach { (k, v) -> builder.header(k, v) }

                fun execute(
                    method: String,
                    range: Boolean,
                ): okhttp3.Response {
                    val reqBuilder = builder.method(method, null)
                    if (range) reqBuilder.header("Range", "bytes=0-0")
                    return client.newCall(reqBuilder.build()).execute()
                }

                val response =
                    try {
                        execute("HEAD", false)
                    } catch (_: Throwable) {
                        runCatching { execute("GET", true) }.getOrNull()
                    }
                response?.use { return@withContext it.request.url.toString() }
                initialUrl
            }
        return withContext
    }

    private suspend fun cacheVodContainerExt(
        context: Context,
        vodId: Int,
        containerExt: String,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val box = ObxStore.get(context).boxFor<ObxVod>()
                val query = box.query(ObxVod_.vodId.equal(vodId.toLong())).build()
                try {
                    query.findFirst()?.let { existing ->
                        if (existing.containerExt != containerExt) {
                            existing.containerExt = containerExt
                            box.put(existing)
                        }
                    }
                } finally {
                    query.close()
                }
            }
        }
    }
}
