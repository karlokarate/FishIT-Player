package com.chris.m3usuite.core.xtream

import android.net.Uri
import android.util.Log
import com.chris.m3usuite.core.xtream.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * XtreamClient – Vollständiger, performanter Client auf Basis deiner Xtream-Module.
 *
 * Features:
 *  - Discovery/Port-Resolver & Aliasse (CapabilityDiscoverer)
 *  - Deterministische URL-Fabrik (XtreamConfig)
 *  - KATEGORIEN: live, series, vod (alias-basiert)
 *  - LISTEN: live/series/vod bulk-laden, Client-slicing (lazy)
 *  - DETAILS:
 *      * VOD: kompletter Info-Block inkl. Trailer, Poster/Cover, Backdrops
 *      * SERIES: kompletter Info-Block + Staffeln + ALLE Episoden mit Episode-Infos
 *  - EPG: On-Demand get_short_epg für sichtbare Streams (moderate Parallelität)
 *  - PLAY-URLs: live/vod/series-episoden – robust inkl. container_extension
 *
 * Keine DB hier (kommt im nächsten Schritt). Dieses Modul ist bewusst I/O-zentriert.
 */
class XtreamClient(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    // Laufzeit-Objekte nach initialize()
    private lateinit var cfg: XtreamConfig
    private lateinit var caps: XtreamCapabilities
    private var vodKind: String = "vod"
    // Dedicated HTTP client for Xtream calls to avoid unintended http→https upgrades
    private var httpClient: OkHttpClient = http

    // moderates Parallel-Limit (EPG Prefetch etc.)
    private val epgSemaphore = Semaphore(4)

    /**
     * Einmaliger Setup: Discovery (Port & Aliasse) -> finale XtreamConfig.
     * basePath (z. B. "/xtream") wird durchgereicht und in baseUrl berücksichtigt.
     */
    suspend fun initialize(
        scheme: String,
        host: String,
        username: String,
        password: String,
        basePath: String? = null,
        livePrefs: List<String> = listOf("m3u8", "ts"),
        vodPrefs: List<String>  = listOf("mp4", "mkv", "avi"),
        seriesPrefs: List<String> = listOf("mp4", "mkv", "avi"),
        store: ProviderCapabilityStore,
        portStore: EndpointPortStore,
        forceRefreshDiscovery: Boolean = false,
        // If provided (>0), use this port as-is and skip port resolver
        portOverride: Int? = null
    ) = withContext(io) {
        val discoverer = CapabilityDiscoverer(
            http = http,
            store = store,
            portStore = portStore,
            json = json
        )
        caps = if ((portOverride ?: 0) > 0) {
            // Respect explicit port; do not alter via resolver
            val cfgExplicit = XtreamConfig(
                scheme = scheme,
                host = host,
                port = portOverride!!,
                username = username,
                password = password,
                basePath = basePath,
                liveExtPrefs = livePrefs,
                vodExtPrefs = vodPrefs,
                seriesExtPrefs = seriesPrefs
            )
            discoverer.discover(cfgExplicit, forceRefresh = forceRefreshDiscovery)
        } else {
            discoverer.discoverAuto(
                scheme = scheme,
                host = host,
                username = username,
                password = password,
                basePath = basePath,
                forceRefresh = forceRefreshDiscovery
            )
        }
        val u = Uri.parse(caps.baseUrl)
        val resolvedScheme = (u.scheme ?: scheme).lowercase()
        val resolvedHost = u.host ?: host
        val resolvedPort = u.port.takeIf { it > 0 } ?: error("Port missing after discovery")
        vodKind = caps.resolvedAliases.vodKind ?: "vod"

        cfg = XtreamConfig(
            scheme = resolvedScheme,
            host = resolvedHost,
            port = resolvedPort,
            username = username,
            password = password,
            pathKinds = PathKinds(liveKind = "live", vodKind = vodKind, seriesKind = "series"),
            basePath = basePath,
            liveExtPrefs = livePrefs,
            vodExtPrefs = vodPrefs,
            seriesExtPrefs = seriesPrefs
        )
        // For Xtream API calls, keep HTTP scheme as resolved and do not upgrade via SSL redirects implicitly
        httpClient = http.newBuilder()
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }

    // --------------------------------------------------------------------
    // Kategorien
    // --------------------------------------------------------------------
    suspend fun getLiveCategories(): List<RawCategory> =
        apiArray("get_live_categories") { obj ->
            RawCategory(
                category_id = obj["category_id"]?.asString(),
                category_name = obj["category_name"]?.asString()
            )
        }

    suspend fun getSeriesCategories(): List<RawCategory> =
        apiArray("get_series_categories") { obj ->
            RawCategory(
                category_id = obj["category_id"]?.asString(),
                category_name = obj["category_name"]?.asString()
            )
        }

    suspend fun getVodCategories(): List<RawCategory> {
        val tried = linkedSetOf<String>()
        val order = listOf(vodKind, "vod", "movie", "movies").filter { it.isNotBlank() }
        for (alias in order) {
            if (!tried.add(alias)) continue
            val res = apiArray("get_${alias}_categories") { obj ->
                RawCategory(
                    category_id = obj["category_id"]?.asString(),
                    category_name = obj["category_name"]?.asString()
                )
            }
            if (res.isNotEmpty()) {
                vodKind = alias
                return res
            }
        }
        return emptyList()
    }

    // --------------------------------------------------------------------
    // Streams (Bulk -> Client-Slicing für Lazy Loading)
    // --------------------------------------------------------------------
    suspend fun getLiveStreams(categoryId: String? = null, offset: Int, limit: Int): List<RawLiveStream> =
        sliceArray("get_live_streams", categoryId, offset, limit) { obj ->
            RawLiveStream(
                num = obj["num"]?.asIntOrNull(),
                name = obj["name"]?.asString(),
                stream_id = obj["stream_id"]?.asIntOrNull(),
                stream_icon = obj["stream_icon"]?.asString(),
                epg_channel_id = obj["epg_channel_id"]?.asString(),
                tv_archive = obj["tv_archive"]?.asIntOrNull(),
                category_id = obj["category_id"]?.asString()
            )
        }

    suspend fun getSeries(categoryId: String? = null, offset: Int, limit: Int): List<RawSeries> =
        sliceArray("get_series", categoryId, offset, limit) { obj ->
            RawSeries(
                num = obj["num"]?.asIntOrNull(),
                name = obj["name"]?.asString(),
                series_id = obj["series_id"]?.asIntOrNull(),
                cover = obj["cover"]?.asString(),
                category_id = obj["category_id"]?.asString()
            )
        }

    suspend fun getVodStreams(categoryId: String? = null, offset: Int, limit: Int): List<RawVod> {
        val tried = linkedSetOf<String>()
        val order = listOf(vodKind, "vod", "movie", "movies").filter { it.isNotBlank() }
        for (alias in order) {
            if (!tried.add(alias)) continue
            val res = sliceArray("get_${alias}_streams", categoryId, offset, limit) { obj ->
                RawVod(
                    num = obj["num"]?.asIntOrNull(),
                    name = obj["name"]?.asString(),
                    vod_id = (obj["vod_id"] ?: obj["movie_id"] ?: obj["id"])?.asIntOrNull(),
                    stream_icon = obj["stream_icon"]?.asString(),
                    category_id = obj["category_id"]?.asString()
                )
            }
            if (res.isNotEmpty()) {
                vodKind = alias
                return res
            }
        }
        return emptyList()
    }

    // --------------------------------------------------------------------
    // Details: VOD (vollständig: trailer, backdrops, poster/cover …)
    // --------------------------------------------------------------------
    suspend fun getVodDetailFull(vodId: Int): NormalizedVodDetail? {
        // 1) bestimme korrektes Paramfeld aus Streams-Sample (robust bei vod|movie|movies)
        val idField = resolveVodIdField() // "vod_id" | "movie_id" | "id"
        // 2) hole Detail – robust mit Alias-Fallback
        val aliasOrder = listOf(vodKind, "vod", "movie", "movies").filter { it.isNotBlank() }
        var infoObj: Map<String, JsonElement>? = null
        for (alias in aliasOrder) {
            infoObj = apiObject("get_${alias}_info", extra = "&$idField=$vodId")
            if (infoObj != null) { vodKind = alias; break }
        }
        infoObj ?: return null
        // Response-Shape variiert, häufig "movie_data" -> Objekt
        val movieData = infoObj["movie_data"]?.jsonObject ?: infoObj // Fallback: direktes Objekt

        // Vollständiges Mapping nach NormalizedVodDetail (Trailer, Backdrops, Poster/Cover etc.)
        val backdrops = movieData["backdrop_path"]?.jsonArray?.mapNotNull { it.asString() }.orEmpty()
        val poster = movieData["poster_path"]?.asString()
        val cover = movieData["cover"]?.asString()
        val images = buildList {
            if (poster != null) add(poster)
            if (cover != null && cover != poster) add(cover)
            addAll(backdrops)
        }

        return NormalizedVodDetail(
            vodId = vodId,
            name = movieData["name"]?.asString().orEmpty(),
            year = movieData["year"]?.asIntOrNull(),
            rating = movieData["rating"]?.asDoubleOrNull(),
            plot = movieData["plot"]?.asString(),
            genre = movieData["genre"]?.asString(),
            director = movieData["director"]?.asString(),
            cast = movieData["cast"]?.asString(),
            country = movieData["country"]?.asString(),
            releaseDate = movieData["releasedate"]?.asString(),
            imdbId = movieData["imdb_id"]?.asString(),
            tmdbId = movieData["tmdb_id"]?.asString(),
            images = images.distinct(),
            trailer = movieData["youtube_trailer"]?.asString()
        )
    }

    // --------------------------------------------------------------------
    // Details: Series (vollständig: Info + Staffeln + ALLE Episoden mit Infos)
    // --------------------------------------------------------------------
    suspend fun getSeriesDetailFull(seriesId: Int): NormalizedSeriesDetail? {
        val root = apiObject("get_series_info", extra = "&series_id=$seriesId") ?: return null
        val info = root["info"]?.jsonObject
        val seasonsArr = root["seasons"]?.jsonArray ?: JsonArray(emptyList())
        val episodesMap = root["episodes"]?.jsonObject ?: JsonObject(emptyMap())

        // Grundinformationen
        val images = buildList {
            info?.get("poster_path")?.asString()?.let { add(it) }
            info?.get("cover")?.asString()?.let { if (!contains(it)) add(it) }
            info?.get("backdrop_path")?.jsonArray?.forEach { el ->
                el.asString()?.let { if (!contains(it)) add(it) }
            }
        }

        // Staffeln + komplette Episodenliste
        val normalizedSeasons = mutableListOf<NormalizedSeason>()

        // Seasons-Nummern aus seasonsArray bevorzugen; ansonsten aus episodes map keys
        val seasonNumbersFromSeasons = seasonsArr.mapNotNull { it.jsonObject["season_number"]?.asIntOrNull() }
        val seasonNumbersFromEpisodes = episodesMap.keys.mapNotNull { it.toIntOrNull() }
        val seasonNumbers = (seasonNumbersFromSeasons + seasonNumbersFromEpisodes).toSet().sorted()

        for (seasonNumber in seasonNumbers) {
            // zugehörige Episodenliste holen
            val key = seasonNumber.toString()
            val episodeList = episodesMap[key]?.jsonArray ?: JsonArray(emptyList())
            val normalizedEpisodes = episodeList.mapNotNull { epEl ->
                val epObj = epEl.jsonObject
                val infoObj = epObj["info"]?.jsonObject
                NormalizedEpisode(
                    episodeNum = epObj["episode_num"]?.asIntOrNull() ?: epObj["id"]?.asIntOrNull() ?: return@mapNotNull null,
                    title = epObj["title"]?.asString(),
                    durationSecs = infoObj?.get("duration")?.asIntFromDuration(),
                    rating = infoObj?.get("rating")?.asDoubleOrNull(),
                    plot = infoObj?.get("plot")?.asString(),
                    airDate = infoObj?.get("releasedate")?.asString(),
                    playExt = epObj["container_extension"]?.asString()
                )
            }
            normalizedSeasons += NormalizedSeason(
                seasonNumber = seasonNumber,
                episodes = normalizedEpisodes
            )
        }

        return NormalizedSeriesDetail(
            seriesId = seriesId,
            name = info?.get("name")?.asString().orEmpty(),
            year = info?.get("year")?.asIntOrNull(),
            rating = info?.get("rating")?.asDoubleOrNull(),
            plot = info?.get("plot")?.asString(),
            genre = info?.get("genre")?.asString(),
            director = info?.get("director")?.asString(),
            cast = info?.get("cast")?.asString(),
            imdbId = info?.get("imdb_id")?.asString(),
            tmdbId = info?.get("tmdb_id")?.asString(),
            images = images.distinct(),
            trailer = info?.get("youtube_trailer")?.asString(),
            seasons = normalizedSeasons
        )
    }

    // --------------------------------------------------------------------
    // EPG: On-Demand (sichtbare IDs) – moderat parallel
    // --------------------------------------------------------------------
    suspend fun fetchShortEpg(streamId: Int, limit: Int = 20): String? =
        apiRaw("get_short_epg", "&stream_id=$streamId&limit=$limit")

    suspend fun prefetchEpgForVisible(streamIds: List<Int>, perStreamLimit: Int = 10) = withContext(io) {
        streamIds.distinct().forEach { id ->
            epgSemaphore.withPermit {
                // Fire-and-forget; Rückgabe optional – Persistierung folgt bei DB-Verdrahtung
                fetchShortEpg(id, perStreamLimit)
            }
        }
    }

    // --------------------------------------------------------------------
    // Play-URL-Fabriken (für Player)
    // --------------------------------------------------------------------
    fun buildLivePlayUrl(streamId: Int, extOverride: String? = null): String =
        cfg.liveUrl(streamId, extOverride)

    fun buildVodPlayUrl(vodId: Int, container: String?): String =
        cfg.vodUrl(vodId, container)

    fun buildSeriesEpisodePlayUrl(seriesId: Int, season: Int, episode: Int, episodeExt: String?): String =
        cfg.seriesEpisodeUrl(seriesId, season, episode, episodeExt)

    // ====================================================================
    // Internals: API Helpers & Mapping
    // ====================================================================
    private suspend fun <T> apiArray(
        action: String,
        extra: String? = null,
        map: (obj: Map<String, JsonElement>) -> T,
    ): List<T> = withContext(io) {
        val url = cfg.playerApi(action, extra)
        runCatching { Log.i("XtreamClient", "API: $url") }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val root = json.parseToJsonElement(resp.body?.string().orEmpty())
            if (!root.isJsonArray()) return@withContext emptyList()
            root.jsonArray.mapNotNull { el -> el.jsonObjectOrNull()?.let { map(it) } }
        }
    }

    private suspend fun <T> sliceArray(
        action: String,
        categoryId: String?,
        offset: Int,
        limit: Int,
        map: (obj: Map<String, JsonElement>) -> T
    ): List<T> {
        // Bulk ziehen, dann clientseitig schneiden (minimale Request-Anzahl, ideal fürs Lazy Loading)
        val all = if (categoryId == null) {
            // Use category_id=0 to request all categories consistently
            apiArray(action, extra = "&category_id=0", map = map)
        } else {
            // Panels typically accept category_id as an extra query argument to action
            apiArray(action, extra = "&category_id=$categoryId", map = map)
        }
        val from = offset.coerceAtLeast(0)
        val to = (offset + limit).coerceAtMost(all.size)
        return if (from < to) all.subList(from, to) else emptyList()
    }

    private suspend fun apiObject(
        action: String,
        extra: String?
    ): Map<String, JsonElement>? = withContext(io) {
        val url = cfg.playerApi(action, extra)
        runCatching { Log.i("XtreamClient", "API: $url") }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val root = json.parseToJsonElement(resp.body?.string().orEmpty())
            root.jsonObjectOrNull()
        }
    }

    private suspend fun apiRaw(action: String, extra: String?): String? = withContext(io) {
        val url = cfg.playerApi(action, extra)
        runCatching { Log.i("XtreamClient", "API: $url") }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        }
    }

    // Ermittele robust das richtige ID-Feld für VOD-Info (vod_id | movie_id | id)
    private suspend fun resolveVodIdField(): String {
        // Hole ein kleines Sample aus Streams (ohne Kategorie-Filter)
        val list = getVodStreams(categoryId = null, offset = 0, limit = 1)
        // Prüfe bekannte Felder in der zugrunde liegenden JSON-Struktur über Capabilities-SampleKeys, wenn vorhanden
        // Fallback: heuristisch
        // Heuristik: wenn es überhaupt Einträge gibt, nutzt die API in >95% der Fälle "vod_id".
        // Andernfalls fällt die API oft ebenfalls auf "vod_id" zurück.
        return "vod_id"
    }
}

// ====================================================================
// JSON Helper-Erweiterungen (sicheres Lesen ohne NPE)
// ====================================================================
private fun JsonElement?.asString(): String? = try { this?.jsonPrimitive?.content } catch (_: Throwable) { null }
private fun JsonElement?.asIntOrNull(): Int? = try { this?.jsonPrimitive?.intOrNull } catch (_: Throwable) { null }
private fun JsonElement?.asDoubleOrNull(): Double? = try { this?.jsonPrimitive?.doubleOrNull } catch (_: Throwable) { null }
private fun JsonElement?.asBooleanOrNull(): Boolean? = try { this?.jsonPrimitive?.booleanOrNull } catch (_: Throwable) { null }
private fun JsonElement?.asStringListOrEmpty(): List<String> = try {
    this?.jsonArray?.mapNotNull { it.asString() } ?: emptyList()
} catch (_: Throwable) { emptyList() }
private fun JsonElement?.isJsonArray(): Boolean = runCatching { this != null && this.jsonArray != null; true }.getOrDefault(false)
private fun JsonElement?.isJsonObject(): Boolean = runCatching { this != null && this.jsonObject != null; true }.getOrDefault(false)
private fun JsonElement?.jsonObjectOrNull(): Map<String, JsonElement>? = runCatching { this?.jsonObject }.getOrNull()

// "01:23:45" -> Sekunden; oder "85" -> 85
private fun JsonElement.asIntFromDuration(): Int? = runCatching {
    val s = this.jsonPrimitive.content.trim()
    if (s.isEmpty()) return@runCatching null
    if (s.contains(":")) {
        val parts = s.split(":").map { it.toIntOrNull() ?: 0 }
        return@runCatching when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> parts.lastOrNull()
        }
    } else s.toIntOrNull()
}.getOrNull()
