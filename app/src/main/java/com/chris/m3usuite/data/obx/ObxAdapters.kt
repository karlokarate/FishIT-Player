package com.chris.m3usuite.data.obx

import android.content.Context
import android.net.Uri
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.core.xtream.PathKinds
import com.chris.m3usuite.core.xtream.ProviderCapabilityStore
import com.chris.m3usuite.model.Episode
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicReference

// ---------------------------------------------------------
// Utilities
// ---------------------------------------------------------

private fun normSort(s: String): String = s.lowercase().trim()

/**
 * Lazy + cached XtreamConfig-Factory (non-suspend).
 * Liest genau einmal Host/Port/User/Pass aus dem SettingsStore (runBlocking) und cached.
 * Port→Scheme: 443→https, sonst http.
 */
private object XtreamUrlFactory {
    private val ref = AtomicReference<XtreamConfig?>()

    fun getOrNull(ctx: Context): XtreamConfig? {
        ref.get()?.let { return it }
        return runBlocking {
            val store = SettingsStore(ctx)
            val host = store.xtHost.first().trim()
            val port = store.xtPort.first()
            val user = store.xtUser.first().trim()
            val pass = store.xtPass.first().trim()
            val output = store.xtOutput.first().trim().lowercase()
            if (host.isBlank() || user.isBlank() || pass.isBlank() || port <= 0) return@runBlocking null

            val schemeDefault = if (port == 443) "https" else "http"

            val capsStore = ProviderCapabilityStore(ctx)
            val caps = capsStore.findByEndpoint(schemeDefault, host, port, user)
            val resolvedUri = caps?.baseUrl?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val resolvedScheme = resolvedUri?.scheme?.takeIf { !it.isNullOrBlank() }?.lowercase()
                ?: schemeDefault
            val resolvedHost = resolvedUri?.host?.takeIf { !it.isNullOrBlank() } ?: host
            val resolvedPort = resolvedUri?.port?.takeIf { it > 0 } ?: port
            val resolvedBasePath = resolvedUri?.path?.takeIf { !it.isNullOrBlank() && it != "/" }
            val resolvedVodKind = caps?.resolvedAliases?.vodKind
                ?.trim()
                ?.takeUnless { it.isEmpty() }

            val livePrefs = when (output) {
                "hls" -> listOf("m3u8", "ts")
                "m3u8" -> listOf("m3u8", "ts")
                "ts" -> listOf("ts", "m3u8")
                else -> listOf("m3u8", "ts")
            }

            val cfg = XtreamConfig(
                scheme = resolvedScheme,
                host = resolvedHost,
                port = resolvedPort,
                username = user,
                password = pass,
                pathKinds = PathKinds(
                    liveKind = "live",
                    vodKind = resolvedVodKind ?: "movie",
                    seriesKind = "series"
                ),
                basePath = resolvedBasePath,
                liveExtPrefs = livePrefs
            )
            ref.set(cfg)
            cfg
        }
    }

    fun invalidate() { ref.set(null) }
}

/** Von außen aufrufbar, z. B. nach Login/Discovery. */
fun invalidateXtreamUrlCache() = XtreamUrlFactory.invalidate()

// ---------------------------------------------------------
// Adapters: OBX → UI-Modelle
// ---------------------------------------------------------

/**
 * Live → MediaItem
 * - Baut sofort eine abspielbare Play-URL (m3u8/ts nach Präferenz von XtreamConfig)
 */
fun ObxLive.toMediaItem(ctx: Context): MediaItem {
    val encodedId = 1_000_000_000_000L + this.streamId.toLong()
    val cfg = XtreamUrlFactory.getOrNull(ctx)
    val playUrl = cfg?.liveUrl(this.streamId)
    return MediaItem(
        id = encodedId,
        type = "live",
        streamId = this.streamId,
        name = this.name,
        sortTitle = normSort(this.name),
        categoryId = this.categoryId,
        categoryName = null,
        logo = this.logo,
        poster = this.logo,
        backdrop = null,
        epgChannelId = this.epgChannelId,
        year = null,
        rating = null,
        durationSecs = null,
        plot = null,
        url = playUrl, // entscheidend: abspielbar
        extraJson = null,
        source = "XTREAM"
    )
}

/**
 * VOD → MediaItem
 * - Nutzt vorhandene Container-Endung (z. B. "mp4") für die Play-URL
 * - Images werden aus JSON übernommen
 */
fun ObxVod.toMediaItem(ctx: Context): MediaItem {
    val encodedId = 2_000_000_000_000L + this.vodId.toLong()
    val images: List<String> = runCatching {
        this.imagesJson?.let { j ->
            Json.parseToJsonElement(j).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
    }.getOrNull() ?: emptyList()

    val cfg = XtreamUrlFactory.getOrNull(ctx)
    val playUrl = cfg?.vodUrl(this.vodId, this.containerExt)

    return MediaItem(
        id = encodedId,
        type = "vod",
        streamId = this.vodId,
        name = this.name,
        sortTitle = normSort(this.name),
        categoryId = this.categoryId,
        categoryName = null,
        logo = this.poster,
        poster = this.poster,
        backdrop = null,
        epgChannelId = null,
        year = this.year,
        rating = this.rating,
        durationSecs = this.durationSecs,
        plot = this.plot,
        url = playUrl, // entscheidend: abspielbar
        extraJson = null,
        source = "XTREAM",
        images = images,
        imdbId = this.imdbId,
        tmdbId = this.tmdbId,
        trailer = this.trailer,
        director = this.director,
        cast = this.cast,
        country = this.country,
        releaseDate = this.releaseDate,
        genre = this.genre,
        containerExt = this.containerExt
    )
}

/**
 * Series → MediaItem
 * - Serien haben keine direkte Play-URL (die hängt von Staffel/Episode ab)
 * - Poster/Backdrops aus Images-JSON (falls vorhanden)
 */
fun ObxSeries.toMediaItem(ctx: Context): MediaItem {
    val encodedId = 3_000_000_000_000L + this.seriesId.toLong()
    val images: List<String> = runCatching {
        this.imagesJson?.let { j ->
            Json.parseToJsonElement(j).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
    }.getOrNull() ?: emptyList()

    return MediaItem(
        id = encodedId,
        type = "series",
        streamId = this.seriesId,
        name = this.name,
        sortTitle = normSort(this.name),
        categoryId = this.categoryId,
        categoryName = null,
        logo = null,
        poster = images.firstOrNull(),
        backdrop = images.drop(1).firstOrNull(),
        epgChannelId = null,
        year = this.year,
        rating = this.rating,
        durationSecs = null,
        plot = this.plot,
        url = null, // bewusst: Episode-URL erst bei Auswahl
        extraJson = null,
        source = "XTREAM",
        images = images,
        imdbId = this.imdbId,
        tmdbId = this.tmdbId,
        trailer = this.trailer,
        director = this.director,
        cast = this.cast,
        genre = this.genre,
        country = this.country,
        releaseDate = this.releaseDate
    )
}

/**
 * Episode (OBX) → UI Episode
 * - Beinhaltet containerExt, damit die UI später gezielt die Episode-URL bauen kann
 * - Optionaler Helper (siehe unten) zum direkten Play-URL-Bauen
 */
fun ObxEpisode.toEpisode(): Episode = Episode(
    // id bleibt 0L – Episoden sind in OBX über (seriesId, season, episodeNum) identifizierbar
    seriesStreamId = this.seriesId,
    episodeId = this.episodeId,
    season = this.season,
    episodeNum = this.episodeNum,
    title = this.title,
    plot = this.plot,
    durationSecs = this.durationSecs,
    rating = this.rating,
    airDate = this.airDate,
    containerExt = this.playExt,
    poster = this.imageUrl
)

// ---------------------------------------------------------
// Optional Helpers: Episode-Play-URL
// ---------------------------------------------------------

/**
 * Baut eine direkte Play-URL für eine Episode (falls gewünscht).
 * Falls keine Xtream-Creds konfiguriert sind, gibt null zurück.
 */
fun buildEpisodePlayUrl(
    ctx: Context,
    seriesStreamId: Int,
    season: Int,
    episodeNum: Int,
    episodeExt: String?,
    episodeId: Int? = null
): String? {
    val cfg = XtreamUrlFactory.getOrNull(ctx) ?: return null
    val legacyEpisodeId = episodeId?.takeIf { it > 0 }
    if (legacyEpisodeId != null) {
        @Suppress("DEPRECATION")
        return cfg.seriesEpisodeUrl(legacyEpisodeId, episodeExt)
    }
    return cfg.seriesEpisodeUrl(seriesStreamId, season, episodeNum, episodeExt)
}

/** Bequemer Overload direkt aus einem Episode-Objekt. */
fun Episode.buildPlayUrl(ctx: Context): String? =
    buildEpisodePlayUrl(ctx, seriesStreamId, season, episodeNum, containerExt, episodeId.takeIf { it > 0 })
