package com.chris.m3usuite.core.xtream

import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import com.chris.m3usuite.core.debug.GlobalDebug
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * XtreamClient – API‑first Client für Xtream Codes Panels.
 *
 * Unterschied zu älteren Ständen:
 *  - Wenn **keine Kategorie** übergeben wird, wird bei Listen-Calls zunächst **category_id=*** gefragt
 *    und **nur bei leerem Ergebnis oder Fehler** auf **category_id=0** gefallen (deckt beide Panel‑Varianten ab).
 *  - JSON‑Helper werden **aktiv genutzt** (isJsonArray/isJsonObject), keine „immer true“-Heuristik.
 *  - EPG‑Prefetch im Client bleibt erhalten, ist aber **@Deprecated** – nutze bitte
 *    `EpgRepository.prefetchNowNext(...)`, da dort Cache & Persist bereits integriert sind.
 */
class XtreamClient(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private val rateMutex = Mutex()
        private var lastCallAt = 0L
        private const val MIN_INTERVAL_MS = 120L

        private data class CacheEntry(
            val at: Long,
            val body: String,
        )

        private val cacheLock = Mutex()
        private val cache =
            object : LinkedHashMap<String, CacheEntry>(512, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > 512
            }
        private const val CACHE_TTL_MS = 60_000L
        private const val EPG_CACHE_TTL_MS = 15_000L

        private suspend fun takeRateSlot() {
            rateMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastCallAt
                if (delta in 0 until MIN_INTERVAL_MS) {
                    delay(MIN_INTERVAL_MS - delta)
                }
                lastCallAt = SystemClock.elapsedRealtime()
            }
        }

        private suspend fun readCache(
            url: String,
            isEpg: Boolean,
        ): String? {
            val ttl = if (isEpg) EPG_CACHE_TTL_MS else CACHE_TTL_MS
            return cacheLock.withLock {
                val e = cache[url] ?: return@withLock null
                if ((SystemClock.elapsedRealtime() - e.at) <= ttl) e.body else null
            }
        }

        private suspend fun writeCache(
            url: String,
            body: String,
        ) {
            cacheLock.withLock { cache[url] = CacheEntry(SystemClock.elapsedRealtime(), body) }
        }
    }

    private lateinit var cfg: XtreamConfig
    private lateinit var caps: XtreamCapabilities
    private var vodKind: String = "vod"

    // Eigener Client für Xtream-API (keine erzwungenen SSL‑Redirects)
    private var httpClient: OkHttpClient = http

    // Moderates Parallel‑Limit (nur für fire‑and‑forget EPG‑Fetch)
    private val epgSemaphore = Semaphore(4)

    private fun redact(url: String): String =
        url
            .replace(Regex("(?i)(password)=([^&]*)"), "${'$'}1=***")
            .replace(Regex("(?i)(username)=([^&]*)"), "${'$'}1=***")

    private fun snippet(
        s: String,
        n: Int = 200,
    ): String = s.replace("\n", " ").replace("\r", " ").take(n)

    /**
     * Discovery (Port/Aliasse) → finale XtreamConfig erzeugen.
     */
    suspend fun initialize(
        scheme: String,
        host: String,
        username: String,
        password: String,
        basePath: String? = null,
        livePrefs: List<String> = listOf("m3u8", "ts"),
        vodPrefs: List<String> = listOf("mp4", "mkv", "avi"),
        seriesPrefs: List<String> = listOf("mp4", "mkv", "avi"),
        store: ProviderCapabilityStore,
        portStore: EndpointPortStore,
        forceRefreshDiscovery: Boolean = false,
        portOverride: Int? = null,
    ) = withContext(io) {
        val discoverer =
            CapabilityDiscoverer(
                http = http,
                store = store,
                portStore = portStore,
                json = json,
            )
        caps =
            if ((portOverride ?: 0) > 0) {
                val cfgExplicit =
                    XtreamConfig(
                        scheme = scheme,
                        host = host,
                        port = portOverride!!,
                        username = username,
                        password = password,
                        basePath = basePath,
                        liveExtPrefs = livePrefs,
                        vodExtPrefs = vodPrefs,
                        seriesExtPrefs = seriesPrefs,
                    )
                discoverer.discover(cfgExplicit, forceRefresh = forceRefreshDiscovery)
            } else {
                discoverer.discoverAuto(
                    scheme = scheme,
                    host = host,
                    username = username,
                    password = password,
                    basePath = basePath,
                    forceRefresh = forceRefreshDiscovery,
                )
            }

        val u = caps.baseUrl.toUri()
        val resolvedScheme = (u.scheme ?: scheme).lowercase()
        val resolvedHost = u.host ?: host
        val resolvedPort = u.port.takeIf { it > 0 } ?: if (resolvedScheme == "https") 443 else 80
        val resolvedBasePath = u.path?.takeIf { it.isNotBlank() && it != "/" }
        vodKind = caps.resolvedAliases.vodKind ?: "vod"

        cfg =
            XtreamConfig(
                scheme = resolvedScheme,
                host = resolvedHost,
                port = resolvedPort,
                username = username,
                password = password,
                pathKinds = PathKinds(liveKind = "live", vodKind = vodKind, seriesKind = "series"),
                basePath = resolvedBasePath ?: basePath,
                liveExtPrefs = livePrefs,
                vodExtPrefs = vodPrefs,
                seriesExtPrefs = seriesPrefs,
            )

        httpClient =
            http
                .newBuilder()
                .followRedirects(true)
                .followSslRedirects(false)
                .build()
    }

    // ------------------------------------------------------------
    // Kategorien
    // ------------------------------------------------------------
    suspend fun getLiveCategories(): List<RawCategory> =
        apiArray("get_live_categories") { obj ->
            RawCategory(
                category_id = obj["category_id"]?.asString(),
                category_name = obj["category_name"]?.asString(),
            )
        }

    suspend fun getSeriesCategories(): List<RawCategory> =
        apiArray("get_series_categories") { obj ->
            RawCategory(
                category_id = obj["category_id"]?.asString(),
                category_name = obj["category_name"]?.asString(),
            )
        }

    suspend fun getVodCategories(): List<RawCategory> {
        val tried = linkedSetOf<String>()
        val order = listOf(vodKind, "vod", "movie", "movies").filter { it.isNotBlank() }
        for (alias in order) {
            if (!tried.add(alias)) continue
            val res =
                apiArray("get_${alias}_categories") { obj ->
                    RawCategory(
                        category_id = obj["category_id"]?.asString(),
                        category_name = obj["category_name"]?.asString(),
                    )
                }
            if (res.isNotEmpty()) {
                vodKind = alias
                return res
            }
        }
        return emptyList()
    }

    // ------------------------------------------------------------
    // Streams (Bulk → Client‑Slicing)
    // ------------------------------------------------------------
    suspend fun getLiveStreams(
        categoryId: String? = null,
        offset: Int,
        limit: Int,
    ): List<RawLiveStream> =
        sliceArray("get_live_streams", categoryId, offset, limit) { obj ->
            RawLiveStream(
                num = obj["num"]?.asIntOrNull(),
                name = obj["name"]?.asString(),
                stream_id = obj["stream_id"]?.asIntOrNull(),
                stream_icon = obj["stream_icon"]?.asString(),
                epg_channel_id = obj["epg_channel_id"]?.asString(),
                tv_archive = obj["tv_archive"]?.asIntOrNull(),
                category_id = obj["category_id"]?.asString(),
            )
        }

    suspend fun getSeries(
        categoryId: String? = null,
        offset: Int,
        limit: Int,
    ): List<RawSeries> =
        sliceArray("get_series", categoryId, offset, limit) { obj ->
            RawSeries(
                num = obj["num"]?.asIntOrNull(),
                name = obj["name"]?.asString(),
                series_id = obj["series_id"]?.asIntOrNull(),
                cover = obj["cover"]?.asString(),
                category_id = obj["category_id"]?.asString(),
            )
        }

    suspend fun getVodStreams(
        categoryId: String? = null,
        offset: Int,
        limit: Int,
    ): List<RawVod> {
        val tried = linkedSetOf<String>()
        val order = listOf(vodKind, "vod", "movie", "movies").filter { it.isNotBlank() }
        for (alias in order) {
            if (!tried.add(alias)) continue
            val res =
                sliceArray("get_${alias}_streams", categoryId, offset, limit) { obj ->
                    val resolvedId =
                        (
                            obj["vod_id"]
                                ?: obj["movie_id"]
                                ?: obj["id"]
                                ?: obj["stream_id"]
                        )?.asIntOrNull()
                    RawVod(
                        num = obj["num"]?.asIntOrNull(),
                        name = obj["name"]?.asString(),
                        vod_id = resolvedId,
                        stream_icon = obj["stream_icon"]?.asString(),
                        category_id = obj["category_id"]?.asString(),
                        container_extension = obj["container_extension"]?.asString(),
                    )
                }
            if (res.isNotEmpty()) {
                vodKind = alias
                return res
            }
        }
        return emptyList()
    }

    // ------------------------------------------------------------
    // Details: VOD / Series (für Drilldown)
    // ------------------------------------------------------------
    suspend fun getVodDetailFull(vodId: Int): NormalizedVodDetail? {
        // Some panels expect different ID field names for VOD info.
        // Try common variants in order to maximize compatibility.
        val aliasOrder = listOf(vodKind, "vod", "movie", "movies").filter { it.isNotBlank() }
        val idFields = listOf("vod_id", "movie_id", "id", "stream_id")
        var infoObj: Map<String, JsonElement>? = null
        outer@ for (alias in aliasOrder) {
            for (idField in idFields) {
                infoObj = apiObject("get_${alias}_info", extra = "&$idField=$vodId")
                if (infoObj != null) {
                    vodKind = alias
                    break@outer
                }
            }
        }
        infoObj ?: return null
        // Prefer info{} for rich metadata; movie_data{} holds stream/container bits
        val info = infoObj["info"]?.jsonObject
        val movieData = infoObj["movie_data"]?.jsonObject

        fun pickStr(key: String): String? = info?.get(key)?.asString() ?: movieData?.get(key)?.asString() ?: infoObj[key]?.asString()

        fun pickArray(key: String): List<String> {
            val el = info?.get(key) ?: movieData?.get(key) ?: infoObj[key]
            return when {
                el?.isJsonArray() == true -> el.jsonArray.mapNotNull { it.asString() }
                else -> el?.asString()?.let { listOf(it) } ?: emptyList()
            }
        }

        // TMDb-style: images = [poster, cover?, backdrops...]
        val poster = pickStr("movie_image") ?: pickStr("poster_path") ?: pickStr("cover") ?: pickStr("cover_big")
        val cover = pickStr("cover")?.takeUnless { it == poster } ?: pickStr("cover_big")?.takeUnless { it == poster }
        val backdrops = pickArray("backdrop_path")
        val container = sanitizeContainerExt(movieData?.get("container_extension")?.asString() ?: pickStr("container_extension"))
        val durationSecs = parseDurationSeconds(pickStr("duration")) ?: pickStr("duration_secs")?.toIntOrNull()
        val images =
            buildList {
                if (poster != null) add(poster)
                if (cover != null) add(cover)
                backdrops.forEach { b -> if (!contains(b)) add(b) }
            }

        val plotText =
            listOf("plot", "description", "plot_outline", "overview").firstNotNullOfOrNull { k -> pickStr(k) }?.trim()?.takeIf {
                it.isNotEmpty()
            }
        val trailerText =
            listOf("youtube_trailer", "trailer", "trailer_url", "youtube", "yt_trailer").firstNotNullOfOrNull { k ->
                pickStr(k)
            }

        val detail =
            NormalizedVodDetail(
                vodId = vodId,
                name = pickStr("name").orEmpty(),
                year = pickStr("year")?.toIntOrNull(),
                rating = pickStr("rating")?.toDoubleOrNull(),
                plot = plotText,
                genre = (pickStr("genre") ?: pickStr("genres"))?.takeIf { it.isNotBlank() },
                director = pickStr("director"),
                cast = pickStr("cast") ?: pickStr("actors"),
                country = pickStr("country"),
                releaseDate = pickStr("releasedate") ?: pickStr("releaseDate"),
                imdbId = pickStr("imdb_id"),
                tmdbId = pickStr("tmdb_id"),
                images = images.distinct(),
                trailer = trailerText,
                containerExt = container,
                durationSecs = durationSecs,
                audio = pickStr("audio"),
                video = pickStr("video"),
                bitrate = pickStr("bitrate"),
                mpaaRating = pickStr("mpaa_rating"),
                age = pickStr("age"),
                tmdbUrl = pickStr("tmdb_url"),
                oName = pickStr("o_name"),
                coverBig = pickStr("cover_big"),
            )
        if (GlobalDebug.isEnabled()) {
            logVodDebug(vodId, infoObj, info, movieData, detail)
        }
        return detail
    }

    private fun logVodDebug(
        vodId: Int,
        root: Map<String, JsonElement>,
        info: JsonObject?,
        movieData: JsonObject?,
        detail: NormalizedVodDetail,
    ) {
        val tag = "XtreamDetail"

        fun sanitised(value: String?): String? {
            if (value.isNullOrEmpty()) return value
            var out = value.replace('\n', ' ').replace('\r', ' ')
            cfg.username.takeIf { it.isNotBlank() }?.let { out = out.replace(it, "***", ignoreCase = true) }
            cfg.password.takeIf { it.isNotBlank() }?.let { out = out.replace(it, "***", ignoreCase = true) }
            out = out.replace(Regex("""(?i)username=[^&"']+"""), "username=***")
            out = out.replace(Regex("""(?i)password=[^&"']+"""), "password=***")
            return out
        }

        val imagesStr =
            detail.images
                .mapNotNull { sanitised(it)?.takeIf { s -> s.isNotBlank() } }
                .joinToString(prefix = "[", postfix = "]")

        val detailParts =
            buildList {
                sanitised(detail.name)?.takeIf { it.isNotBlank() }?.let { add("name=$it") }
                detail.year?.toString()?.let { add("year=$it") }
                detail.rating?.toString()?.let { add("rating=$it") }
                sanitised(detail.plot)?.takeIf { it.isNotBlank() }?.let { add("plot=$it") }
                sanitised(detail.genre)?.takeIf { it.isNotBlank() }?.let { add("genre=$it") }
                sanitised(detail.director)?.takeIf { it.isNotBlank() }?.let { add("director=$it") }
                sanitised(detail.cast)?.takeIf { it.isNotBlank() }?.let { add("cast=$it") }
                sanitised(detail.country)?.takeIf { it.isNotBlank() }?.let { add("country=$it") }
                sanitised(detail.releaseDate)?.takeIf { it.isNotBlank() }?.let { add("releaseDate=$it") }
                sanitised(detail.imdbId)?.takeIf { it.isNotBlank() }?.let { add("imdbId=$it") }
                sanitised(detail.tmdbId)?.takeIf { it.isNotBlank() }?.let { add("tmdbId=$it") }
                sanitised(detail.trailer)?.takeIf { it.isNotBlank() }?.let { add("trailer=$it") }
                sanitised(detail.containerExt)?.takeIf { it.isNotBlank() }?.let { add("containerExt=$it") }
                detail.durationSecs?.let { add("durationSecs=$it") }
                sanitised(detail.audio)?.takeIf { it.isNotBlank() }?.let { add("audio=$it") }
                sanitised(detail.video)?.takeIf { it.isNotBlank() }?.let { add("video=$it") }
                sanitised(detail.bitrate)?.takeIf { it.isNotBlank() }?.let { add("bitrate=$it") }
                sanitised(detail.mpaaRating)?.takeIf { it.isNotBlank() }?.let { add("mpaaRating=$it") }
                sanitised(detail.age)?.takeIf { it.isNotBlank() }?.let { add("age=$it") }
                sanitised(detail.tmdbUrl)?.takeIf { it.isNotBlank() }?.let { add("tmdbUrl=$it") }
                sanitised(detail.oName)?.takeIf { it.isNotBlank() }?.let { add("oName=$it") }
                sanitised(detail.coverBig)?.takeIf { it.isNotBlank() }?.let { add("coverBig=$it") }
                if (imagesStr.isNotBlank()) add("images=$imagesStr")
            }.joinToString(", ")

        Log.i(tag, "vod:$vodId detail { $detailParts }")

        val infoStr = sanitised(info?.toString()) ?: "null"
        Log.i(tag, "vod:$vodId info=$infoStr")

        val movieStr = sanitised(movieData?.toString()) ?: "null"
        Log.i(tag, "vod:$vodId movie_data=$movieStr")

        val rootStr = sanitised(JsonObject(root).toString()) ?: "null"
        Log.i(tag, "vod:$vodId raw=$rootStr")
    }

    suspend fun getSeriesDetailFull(seriesId: Int): NormalizedSeriesDetail? {
        val root = apiObject("get_series_info", extra = "&series_id=$seriesId") ?: return null
        val info = root["info"]?.jsonObject
        val seasonsArr = root["seasons"]?.jsonArray ?: JsonArray(emptyList())
        val episodesMap = root["episodes"]?.jsonObject ?: JsonObject(emptyMap())

        val images =
            buildList {
                info?.get("poster_path")?.asString()?.let { add(it) }
                info?.get("cover")?.asString()?.let { if (!contains(it)) add(it) }
                val b = info?.get("backdrop_path")
                if (b?.isJsonArray() == true) {
                    b.jsonArray.forEach { el -> el.asString()?.let { if (!contains(it)) add(it) } }
                } else {
                    b?.asString()?.let { if (!contains(it)) add(it) }
                }
            }

        val normalizedSeasons = mutableListOf<NormalizedSeason>()
        val seasonNumbersFromSeasons = seasonsArr.mapNotNull { it.jsonObject["season_number"]?.asIntOrNull() }
        val seasonNumbersFromEpisodes = episodesMap.keys.mapNotNull { it.toIntOrNull() }
        val seasonNumbers = (seasonNumbersFromSeasons + seasonNumbersFromEpisodes).toSet().sorted()

        for (seasonNumber in seasonNumbers) {
            val key = seasonNumber.toString()
            val episodeList = episodesMap[key]?.jsonArray ?: JsonArray(emptyList())
            val normalizedEpisodes =
                episodeList.mapNotNull { epEl ->
                    val epObj = epEl.jsonObject
                    val infoObj = epObj["info"]?.jsonObject
                    val poster =
                        runCatching {
                            infoObj?.get("movie_image")?.asString()
                                ?: infoObj?.get("cover")?.asString()
                                ?: infoObj?.get("poster_path")?.asString()
                                ?: infoObj?.get("thumbnail")?.asString()
                                ?: infoObj?.get("img")?.asString()
                                ?: infoObj?.get("still_path")?.asString()
                        }.getOrNull()
                    NormalizedEpisode(
                        episodeId = epObj["id"]?.asIntOrNull(),
                        episodeNum = epObj["episode_num"]?.asIntOrNull() ?: epObj["id"]?.asIntOrNull() ?: return@mapNotNull null,
                        title = epObj["title"]?.asString(),
                        durationSecs = infoObj?.get("duration")?.asIntFromDuration(),
                        rating = infoObj?.get("rating")?.asDoubleOrNull(),
                        plot = infoObj?.get("plot")?.asString(),
                        airDate = infoObj?.get("releasedate")?.asString(),
                        playExt = sanitizeContainerExt(epObj["container_extension"]?.asString()),
                        posterUrl = poster,
                    )
                }
            normalizedSeasons +=
                NormalizedSeason(
                    seasonNumber = seasonNumber,
                    episodes = normalizedEpisodes,
                )
        }

        // Trailer synonyms for series
        val trailerTextSer =
            listOf("youtube_trailer", "trailer", "trailer_url", "youtube", "yt_trailer")
                .firstNotNullOfOrNull { key -> info?.get(key)?.asString() }

        return NormalizedSeriesDetail(
            seriesId = seriesId,
            name = info?.get("name")?.asString().orEmpty(),
            year = info?.get("year")?.asIntOrNull(),
            rating = info?.get("rating")?.asDoubleOrNull(),
            plot = (listOf("plot", "description", "overview").firstNotNullOfOrNull { k -> info?.get(k)?.asString() }),
            genre = (info?.get("genre")?.asString() ?: info?.get("genres")?.asString()),
            director = info?.get("director")?.asString(),
            cast = info?.get("cast")?.asString(),
            imdbId = info?.get("imdb_id")?.asString(),
            tmdbId = info?.get("tmdb_id")?.asString(),
            images = images.distinct(),
            trailer = trailerTextSer,
            country = info?.get("country")?.asString(),
            releaseDate = info?.get("releasedate")?.asString() ?: info?.get("releaseDate")?.asString(),
            seasons = normalizedSeasons,
        )
    }

    // ------------------------------------------------------------
    // EPG – On‑Demand (sichtbare IDs)
    // ------------------------------------------------------------
    suspend fun fetchShortEpg(
        streamId: Int,
        limit: Int = 20,
    ): String? = apiRaw("get_short_epg", "&stream_id=$streamId&limit=$limit")

    /**
     * Veraltet: nutze stattdessen EpgRepository.prefetchNowNext(...),
     * damit Cache & Persist gepflegt werden.
     */
    @Deprecated("Use EpgRepository.prefetchNowNext(...) for caching/persistence")
    suspend fun prefetchEpgForVisible(
        streamIds: List<Int>,
        perStreamLimit: Int = 10,
    ) = withContext(io) {
        streamIds.distinct().forEach { id ->
            epgSemaphore.withPermit {
                fetchShortEpg(id, perStreamLimit) // fire‑and‑forget (kein Persist)
            }
        }
    }

    // ------------------------------------------------------------
    // Play‑URLs
    // ------------------------------------------------------------
    fun buildLivePlayUrl(
        streamId: Int,
        extOverride: String? = null,
    ): String = cfg.liveUrl(streamId, extOverride)

    fun buildVodPlayUrl(
        vodId: Int,
        container: String?,
    ): String = cfg.vodUrl(vodId, container)

    fun buildSeriesEpisodePlayUrl(
        seriesId: Int,
        season: Int,
        episode: Int,
        episodeExt: String?,
        episodeId: Int? = null,
    ): String =
        episodeId?.takeIf { it > 0 }?.let {
            @Suppress("DEPRECATION")
            cfg.seriesEpisodeUrl(it, episodeExt)
        }
            ?: cfg.seriesEpisodeUrl(seriesId, season, episode, episodeExt)

    // ============================================================
    // Internals: API Helpers
    // ============================================================
    private suspend fun <T> apiArray(
        action: String,
        extra: String? = null,
        map: (obj: Map<String, JsonElement>) -> T,
    ): List<T> =
        withContext(io) {
            val url = cfg.playerApi(action, extra)
            val isEpg = action == "get_short_epg"
            val cached = readCache(url, isEpg)
            val body: String =
                if (cached != null) {
                    if (com.chris.m3usuite.BuildConfig.DEBUG) Log.i("XtreamClient", "CACHE: ${redact(url)}")
                    cached
                } else {
                    if (com.chris.m3usuite.BuildConfig.DEBUG) Log.i("XtreamClient", "API: ${redact(url)}")
                    takeRateSlot()
                    val req =
                        Request
                            .Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .get()
                            .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val code = resp.code
                        val ct = resp.header("Content-Type").orEmpty()
                        if (!resp.isSuccessful) {
                            Log.w("XtreamClient", "HTTP $code action=$action url=${redact(url)} ct=$ct")
                            return@withContext emptyList()
                        }
                        val b = resp.body?.string().orEmpty()
                        writeCache(url, b)
                        b
                    }
                }
            val root =
                runCatching { json.parseToJsonElement(body) }.getOrElse { err ->
                    Log.w(
                        "XtreamClient",
                        "parse_error action=$action len=${body.length} head='${snippet(body)}' err=${err.javaClass.simpleName}",
                    )
                    null
                }
            if (root != null && root.isJsonArray()) {
                root.jsonArray.mapNotNull { el -> el.jsonObjectOrNull()?.let { map(it) } }
            } else {
                Log.w("XtreamClient", "unexpected_body action=$action len=${body.length} head='${snippet(body)}'")
                emptyList()
            }
        }

    private suspend fun <T> sliceArray(
        action: String,
        categoryId: String?,
        offset: Int,
        limit: Int,
        map: (obj: Map<String, JsonElement>) -> T,
    ): List<T> {
        val all: List<T> =
            if (categoryId == null) {
                // Erst * probieren; bei leerem Resultat ODER Fehler → 0 → ohne category_id als letzte Eskalation
                val star = runCatching { apiArray(action, extra = "&category_id=*", map = map) }.getOrElse { emptyList() }
                if (star.isNotEmpty()) {
                    star
                } else {
                    if (com.chris.m3usuite.BuildConfig.DEBUG) {
                        Log.i(
                            "XtreamClient",
                            "Fallback category_id=0 for action=$action (star empty)",
                        )
                    }
                    val zero = runCatching { apiArray(action, extra = "&category_id=0", map = map) }.getOrElse { emptyList() }
                    if (zero.isNotEmpty()) {
                        zero
                    } else {
                        if (com.chris.m3usuite.BuildConfig.DEBUG) {
                            Log.i(
                                "XtreamClient",
                                "Fallback without category_id for action=$action (0 empty)",
                            )
                        }
                        apiArray(action, extra = null, map = map)
                    }
                }
            } else {
                apiArray(action, extra = "&category_id=$categoryId", map = map)
            }
        val from = offset.coerceAtLeast(0)
        val to = (offset + limit).coerceAtMost(all.size)
        return if (from < to) all.subList(from, to) else emptyList()
    }

    private suspend fun apiObject(
        action: String,
        extra: String?,
    ): Map<String, JsonElement>? =
        withContext(io) {
            val url = cfg.playerApi(action, extra)
            val isEpg = action == "get_short_epg"
            val cached = readCache(url, isEpg)
            val body: String =
                if (cached != null) {
                    if (com.chris.m3usuite.BuildConfig.DEBUG) Log.i("XtreamClient", "CACHE: ${redact(url)}")
                    cached
                } else {
                    if (com.chris.m3usuite.BuildConfig.DEBUG) Log.i("XtreamClient", "API: ${redact(url)}")
                    takeRateSlot()
                    val req =
                        Request
                            .Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .get()
                            .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val code = resp.code
                        val ct = resp.header("Content-Type").orEmpty()
                        if (!resp.isSuccessful) {
                            Log.w("XtreamClient", "HTTP $code action=$action url=${redact(url)} ct=$ct")
                            return@withContext null
                        }
                        val b = resp.body?.string().orEmpty()
                        writeCache(url, b)
                        b
                    }
                }
            val root =
                runCatching { json.parseToJsonElement(body) }.getOrElse { err ->
                    Log.w(
                        "XtreamClient",
                        "parse_error action=$action len=${body.length} head='${snippet(body)}' err=${err.javaClass.simpleName}",
                    )
                    null
                }
            if (root != null && root.isJsonObject()) {
                root.jsonObject
            } else {
                Log.w("XtreamClient", "unexpected_body action=$action len=${body.length} head='${snippet(body)}'")
                null
            }
        }

    private suspend fun apiRaw(
        action: String,
        extra: String?,
    ): String? =
        withContext(io) {
            val url = cfg.playerApi(action, extra)
            val isEpg = action == "get_short_epg"
            val cached = readCache(url, isEpg)
            if (cached != null) {
                if (com.chris.m3usuite.BuildConfig.DEBUG) Log.i("XtreamClient", "CACHE: ${redact(url)}")
                return@withContext cached
            }
            if (com.chris.m3usuite.BuildConfig.DEBUG) Log.i("XtreamClient", "API: ${redact(url)}")
            takeRateSlot()
            val req =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()
            httpClient.newCall(req).execute().use { resp ->
                val code = resp.code
                val ct = resp.header("Content-Type").orEmpty()
                if (!resp.isSuccessful) {
                    Log.w("XtreamClient", "HTTP $code action=$action url=${redact(url)} ct=$ct")
                    return@withContext null
                }
                val body = resp.body?.string()
                if (body != null) writeCache(url, body)
                body
            }
        }

    private fun resolveVodIdField(): String = "vod_id" // die meisten Panels erwarten dieses Feld
}

// ============================================================
// JSON‑Helper (sicheres Lesen ohne NPEs)
// ============================================================
private fun JsonElement?.asString(): String? = runCatching { this?.jsonPrimitive?.content }.getOrNull()

private fun JsonElement?.asIntOrNull(): Int? = runCatching { this?.jsonPrimitive?.intOrNull }.getOrNull()

private fun JsonElement?.asDoubleOrNull(): Double? = runCatching { this?.jsonPrimitive?.doubleOrNull }.getOrNull()

private fun JsonElement?.jsonObjectOrNull(): Map<String, JsonElement>? = runCatching { this?.jsonObject }.getOrNull()

private fun JsonElement?.isJsonArray(): Boolean = this is JsonArray

private fun JsonElement?.isJsonObject(): Boolean = this is JsonObject

private fun parseDurationSeconds(raw: String?): Int? =
    runCatching {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return@runCatching null
        if (":" in value) {
            val parts = value.split(":").map { it.toIntOrNull() ?: 0 }
            return@runCatching when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> parts.lastOrNull()
            }
        }
        val digits = value.takeWhile { it.isDigit() }
        val number = digits.toIntOrNull() ?: return@runCatching null
        val lower = value.lowercase()
        when {
            lower.contains("hour") || lower.contains("hr") -> number * 3600
            lower.contains("min") || lower.contains("m") -> number * 60
            else -> if (number <= 600) number * 60 else number
        }
    }.getOrNull()

// "01:23:45" → Sekunden; „85“ → 85
private fun JsonElement.asIntFromDuration(): Int? =
    runCatching {
        val s = this.jsonPrimitive.content.trim()
        if (":" in s) {
            val p = s.split(":").map { it.toIntOrNull() ?: 0 }
            when (p.size) {
                3 -> p[0] * 3600 + p[1] * 60 + p[2]
                2 -> p[0] * 60 + p[1]
                else -> p.lastOrNull()
            }
        } else {
            s.toIntOrNull()
        }
    }.getOrNull()

private val EXT_REGEX = Regex("^[a-z0-9]{2,5}$")

private fun sanitizeContainerExt(raw: String?): String? {
    val value = raw?.lowercase()?.trim().orEmpty()
    return value.takeIf { it.isNotBlank() && EXT_REGEX.matches(it) }
}
