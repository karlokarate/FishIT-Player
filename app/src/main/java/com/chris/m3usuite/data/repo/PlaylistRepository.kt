package com.chris.m3usuite.data.repo

import android.content.Context
import android.net.Uri
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.core.m3u.M3UParser
import com.chris.m3usuite.core.xtream.XtreamDetect
import com.chris.m3usuite.core.xtream.ProviderCapabilityStore
import com.chris.m3usuite.core.xtream.EndpointPortStore
import com.chris.m3usuite.core.xtream.CapabilityDiscoverer
import com.chris.m3usuite.data.obx.*
import io.objectbox.kotlin.boxFor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.work.SchedulingGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.intOrNull

class PlaylistRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    /**
     * Lädt die M3U, parsed sie und ersetzt die DB-Inhalte für live/vod/series.
     * Gibt die Anzahl der importierten Items zurück (Result<Int>).
     */
    suspend fun refreshFromM3U(): Result<Int> = withContext(Dispatchers.IO) {
        val TAG = "ImportM3U"
        runCatching {
            // 1) HTTP-Client + URL
            val client = HttpClientFactory.create(context, settings)
            val url = settings.m3uUrl.first()
            require(url.isNotBlank()) { "M3U URL missing" }
            val ua = settings.userAgent.first()
            val ref = settings.referer.first()
            android.util.Log.d(TAG, "start url=${url} ua=\"${ua}\" defaultUa=\"${com.chris.m3usuite.BuildConfig.DEFAULT_UA}\" ref=\"${ref}\"")

            val uri = Uri.parse(url)
            val scheme = (uri.scheme ?: "").lowercase()
            if (scheme == "http" || scheme == "https") {
                val req = Request.Builder().url(url).build()
                val res = client.newCall(req).execute()
                try {
                if (!res.isSuccessful) {
                    android.util.Log.w(TAG, "http status=${res.code}")
                    error("HTTP ${res.code}")
                }

                val bodyLen = res.body?.contentLength() ?: -1
                val peek = kotlin.runCatching { res.peekBody(4096).string() }.getOrDefault("")
                // Accept UTF-8 BOM or leading whitespace/comments before #EXTM3U; fallback to Xtream if header missing (check only peek)
                // Xtream ALWAYS preferred: if this is a get.php URL with creds and no explicit port, try discovery first
                runCatching {
                    val qpUser = uri.getQueryParameter("username")
                    val qpPass = uri.getQueryParameter("password")
                    if (!qpUser.isNullOrBlank() && !qpPass.isNullOrBlank()) {
                        val hadExplicitPort = uri.port > 0
                        val resolved = tryResolveXtreamViaDiscovery(
                            scheme = scheme,
                            host = uri.host ?: "",
                            user = qpUser,
                            pass = qpPass,
                            basePath = null,
                            skipIfExplicitPort = hadExplicitPort
                        )
                        if (resolved) {
                            val xt = XtreamObxRepository(context, settings)
                            val r = xt.importDelta(deleteOrphans = true).map { it.first + it.second + it.third }
                            if (r.isSuccess) return@withContext r
                        }
                    }
                }
                    val hasHeader = peek.lineSequence().take(10).any { it.trimStart().startsWith("#EXTM3U") }
                if (!hasHeader) {
                    android.util.Log.w(TAG, "No #EXTM3U header – attempting Xtream detect from URL")
                    val detected = XtreamDetect.detectCreds(url)
                    if (detected != null) {
                        // If URL had no explicit port, upgrade via discovery to resolve the true port
                        val hadExplicitPort = uri.port > 0
                            val maybeResolved = if (!hadExplicitPort)
                                tryResolveXtreamViaDiscovery(scheme, uri.host ?: "", detected.username, detected.password, null, skipIfExplicitPort = false)
                            else false
                        if (!maybeResolved) settings.setXtream(detected)
                        // Switch to Xtream import path immediately
                        val xt = XtreamObxRepository(context, settings)
                        val r = xt.importDelta(deleteOrphans = true).map { it.first + it.second + it.third }
                        if (r.isSuccess) return@withContext r else error(r.exceptionOrNull()?.message ?: "Xtream import failed")
                    } else {
                        error("Invalid M3U (no #EXTM3U)")
                    }
                }

                // 3) Prefer Xtream OBX import if possible; else map parsed M3U into OBX minimal entities
                var total = 0
                var xtreamChecked = false
                val reader = res.body!!.charStream()
                M3UParser.parseAuto(
                    source = reader,
                    approxSizeBytes = if (bodyLen >= 0) bodyLen else null,
                    thresholdBytes = 4_000_000,
                    batchSize = 2000,
                    onBatch = { batch ->
                        // Optional: detect Xtream from first live url if not set
                        if (!xtreamChecked && !settings.hasXtream()) {
                            val firstLiveUrl = batch.firstOrNull { it.type == "live" }?.url
                            val creds = firstLiveUrl?.let { if (!it.isNullOrBlank()) XtreamDetect.detectCreds(it) else null }
                            if (creds != null) {
                                android.util.Log.d(TAG, "xtream.detected host=${creds.host} port=${creds.port} out=${creds.output}")
                                val uLive = runCatching { Uri.parse(firstLiveUrl) }.getOrNull()
                                val hadPort = uLive?.port ?: -1
                                val schemeLive = (uLive?.scheme ?: scheme)
                                val hostLive = uLive?.host ?: uri.host ?: creds.host
                                val resolved = tryResolveXtreamViaDiscovery(
                                    scheme = schemeLive,
                                    host = hostLive,
                                    user = creds.username,
                                    pass = creds.password,
                                    basePath = null,
                                    skipIfExplicitPort = hadPort > 0
                                )
                                if (!resolved) settings.setXtream(creds)
                            }
                            xtreamChecked = true
                        }
                        // If we already have Xtream creds, skip per-batch OBX mapping; let a single Xtream import run later
                        if (settings.hasXtream()) { total += batch.size; return@parseAuto }
                        // Minimal OBX mapping from M3U when no Xtream creds are available
                        val box = ObxStore.get(context)
                        val liveBox = box.boxFor<ObxLive>()
                        val vodBox = box.boxFor<ObxVod>()
                        val seriesBox = box.boxFor<ObxSeries>()
                        val catBox = box.boxFor<ObxCategory>()
                        fun sortKey(name: String?): String = name.orEmpty().trim().lowercase()
                        fun providerKey(cat: String?, name: String?): String? = com.chris.m3usuite.ui.util.CategoryNormalizer.normalizeKey(cat ?: name)
                        fun xtIdFromExtra(extra: String?): Int? = try {
                            if (extra.isNullOrBlank()) null else Json.parseToJsonElement(extra).jsonObject["xtream"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                        } catch (_: Throwable) { null }
                        // Categories upsert (group-title or derived language) per kind
                        val byKindCats = batch.groupBy { it.type }.mapValues { (_, list) -> list.mapNotNull { it.categoryName }.distinct() }
                        byKindCats["live"]?.forEach { c -> if (c.isNotBlank()) catBox.put(ObxCategory(kind = "live", categoryId = c, categoryName = c)) }
                        byKindCats["vod"]?.forEach { c -> if (c.isNotBlank()) catBox.put(ObxCategory(kind = "vod", categoryId = c, categoryName = c)) }
                        byKindCats["series"]?.forEach { c -> if (c.isNotBlank()) catBox.put(ObxCategory(kind = "series", categoryId = c, categoryName = c)) }
                        // Upsert entities
                        batch.forEach { mi ->
                            when (mi.type) {
                                "live" -> {
                                    val sid = mi.streamId ?: xtIdFromExtra(mi.extraJson) ?: return@forEach
                                    val existing = liveBox.query(ObxLive_.streamId.equal(sid.toLong())).build().findFirst()
                                    val ent = ObxLive(
                                        streamId = sid,
                                        nameLower = mi.name.lowercase(),
                                        sortTitleLower = sortKey(mi.name),
                                        name = mi.name,
                                        logo = mi.logo ?: mi.poster,
                                        epgChannelId = mi.epgChannelId,
                                        tvArchive = null,
                                        categoryId = mi.categoryId,
                                        providerKey = providerKey(mi.categoryName, mi.name),
                                        genreKey = null
                                    )
                                    if (existing != null) ent.id = existing.id
                                    liveBox.put(ent)
                                }
                                "vod" -> {
                                    val vid = xtIdFromExtra(mi.extraJson) ?: return@forEach
                                    val existing = vodBox.query(ObxVod_.vodId.equal(vid.toLong())).build().findFirst()
                                    val ent = ObxVod(
                                        vodId = vid,
                                        nameLower = mi.name.lowercase(),
                                        sortTitleLower = sortKey(mi.name),
                                        name = mi.name,
                                        poster = mi.poster ?: mi.logo,
                                        imagesJson = null,
                                        year = mi.year,
                                        yearKey = mi.year,
                                        rating = mi.rating,
                                        plot = mi.plot,
                                        genre = null,
                                        director = null,
                                        cast = null,
                                        country = null,
                                        releaseDate = null,
                                        imdbId = null,
                                        tmdbId = null,
                                        trailer = null,
                                        containerExt = null,
                                        categoryId = mi.categoryId,
                                        providerKey = providerKey(mi.categoryName, mi.name),
                                        genreKey = null
                                    )
                                    if (existing != null) ent.id = existing.id
                                    vodBox.put(ent)
                                }
                                "series" -> {
                                    val sid = xtIdFromExtra(mi.extraJson) ?: return@forEach
                                    val existing = seriesBox.query(ObxSeries_.seriesId.equal(sid.toLong())).build().findFirst()
                                    val ent = ObxSeries(
                                        seriesId = sid,
                                        nameLower = mi.name.lowercase(),
                                        sortTitleLower = sortKey(mi.name),
                                        name = mi.name,
                                        imagesJson = null,
                                        year = mi.year,
                                        yearKey = mi.year,
                                        rating = mi.rating,
                                        plot = mi.plot,
                                        genre = null,
                                        director = null,
                                        cast = null,
                                        imdbId = null,
                                        tmdbId = null,
                                        trailer = null,
                                        categoryId = mi.categoryId,
                                        providerKey = providerKey(mi.categoryName, mi.name),
                                        genreKey = null
                                    )
                                    if (existing != null) ent.id = existing.id
                                    seriesBox.put(ent)
                                }
                            }
                        }
                        total += batch.size
                    },
                    onProgress = { /* ignored */ },
                    cancel = { false }
                )

                // If Xtream creds are set (from detection or already configured), run an OBX Xtream import now
                if (settings.hasXtream()) {
                    val xtObx = XtreamObxRepository(context, settings)
                    runCatching { xtObx.importDelta(deleteOrphans = true) }
                }

                // 3b) Fallback EPG from #EXTM3U url-tvg if not set
                val currentEpg = settings.epgUrl.first()
                if (currentEpg.isBlank()) {
                    // Find the actual #EXTM3U header line in peek (skip BOM/whitespace/comments)
                    val headerLine = peek.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("#EXTM3U") }
                    if (!headerLine.isNullOrBlank()) {
                        val m = Regex("""url-tvg=\"([^\"]+)\"""").find(headerLine)
                        val epg = m?.groupValues?.getOrNull(1)
                        if (!epg.isNullOrBlank()) {
                            android.util.Log.d(TAG, "epg.url-tvg detected len=${epg.length}")
                            settings.setEpgUrl(epg)
                        }
                    }
                }

                // 5) Schedule workers after import
                SchedulingGateway.scheduleXtreamDeltaPeriodic(context)

                // 6) Anzahl zurückgeben
                android.util.Log.d(TAG, "parsed.count=${total}")
                total
                } finally {
                    runCatching { res.close() }
                }
            } else {
                // Non-HTTP source: content:// or file://
                var headerPeek = ""
                // Try to read a small header chunk for url-tvg detection
                runCatching {
                    when (scheme) {
                        "content" -> context.contentResolver.openInputStream(uri)?.use { s ->
                            val buf = ByteArray(4096)
                            val n = s.read(buf)
                            if (n > 0) headerPeek = String(buf, 0, n, Charsets.UTF_8)
                        }
                        "file" -> java.io.FileInputStream(java.io.File(uri.path!!)).use { s ->
                            val buf = ByteArray(4096)
                            val n = s.read(buf)
                            if (n > 0) headerPeek = String(buf, 0, n, Charsets.UTF_8)
                        }
                    }
                }

                var total = 0
                fun parseFrom(reader: java.io.Reader, approx: Long?) {
                    var xtChecked = false
                    kotlinx.coroutines.runBlocking {
                        M3UParser.parseAuto(
                        source = reader,
                        approxSizeBytes = approx,
                        thresholdBytes = 4_000_000,
                        batchSize = 2000,
                        onBatch = { batch ->
                            if (!xtChecked && !settings.hasXtream()) {
                                val firstLiveUrl = batch.firstOrNull { it.type == "live" }?.url
                                val creds = firstLiveUrl?.let { if (!it.isNullOrBlank()) com.chris.m3usuite.core.xtream.XtreamDetect.detectCreds(it) else null }
                                if (creds != null) {
                                    val uLive = runCatching { Uri.parse(firstLiveUrl) }.getOrNull()
                                    val hadPort = (uLive?.port ?: -1) > 0
                                    val schemeLive = (uLive?.scheme ?: scheme)
                                    val hostLive = uLive?.host ?: uri.host ?: creds.host
                                    val resolved = tryResolveXtreamViaDiscovery(
                                        scheme = schemeLive,
                                        host = hostLive,
                                        user = creds.username,
                                        pass = creds.password,
                                        basePath = null,
                                        skipIfExplicitPort = hadPort
                                    )
                                    if (!resolved) {
                                        settings.setXtream(creds)
                                    }
                                }
                                xtChecked = true
                            }
                            if (settings.hasXtream()) { total += batch.size; return@parseAuto }
                            val box = ObxStore.get(context)
                            val liveBox = box.boxFor<ObxLive>()
                            val vodBox = box.boxFor<ObxVod>()
                            val seriesBox = box.boxFor<ObxSeries>()
                            val catBox = box.boxFor<ObxCategory>()
                            fun sortKey(name: String?): String = name.orEmpty().trim().lowercase()
                            fun providerKey(cat: String?, name: String?): String? = com.chris.m3usuite.ui.util.CategoryNormalizer.normalizeKey(cat ?: name)
                            fun xtIdFromExtra(extra: String?): Int? = try {
                                if (extra.isNullOrBlank()) null else kotlinx.serialization.json.Json.parseToJsonElement(extra).jsonObject["xtream"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                            } catch (_: Throwable) { null }
                            val byKindCats = batch.groupBy { it.type }.mapValues { (_, list) -> list.mapNotNull { it.categoryName }.distinct() }
                            byKindCats["live"]?.forEach { c -> if (c.isNotBlank()) catBox.put(ObxCategory(kind = "live", categoryId = c, categoryName = c)) }
                            byKindCats["vod"]?.forEach { c -> if (c.isNotBlank()) catBox.put(ObxCategory(kind = "vod", categoryId = c, categoryName = c)) }
                            byKindCats["series"]?.forEach { c -> if (c.isNotBlank()) catBox.put(ObxCategory(kind = "series", categoryId = c, categoryName = c)) }
                            batch.forEach { mi ->
                                when (mi.type) {
                                    "live" -> {
                                        val sid = mi.streamId ?: xtIdFromExtra(mi.extraJson) ?: return@forEach
                                        val existing = liveBox.query(ObxLive_.streamId.equal(sid.toLong())).build().findFirst()
                                        val ent = ObxLive(
                                            streamId = sid,
                                            nameLower = mi.name.lowercase(),
                                            sortTitleLower = sortKey(mi.name),
                                            name = mi.name,
                                            logo = mi.logo ?: mi.poster,
                                            epgChannelId = mi.epgChannelId,
                                            tvArchive = null,
                                            categoryId = mi.categoryId,
                                            providerKey = providerKey(mi.categoryName, mi.name),
                                            genreKey = null
                                        )
                                        if (existing != null) ent.id = existing.id
                                        liveBox.put(ent)
                                    }
                                    "vod" -> {
                                        val vid = xtIdFromExtra(mi.extraJson) ?: return@forEach
                                        val existing = vodBox.query(ObxVod_.vodId.equal(vid.toLong())).build().findFirst()
                                        val ent = ObxVod(
                                            vodId = vid,
                                            nameLower = mi.name.lowercase(),
                                            sortTitleLower = sortKey(mi.name),
                                            name = mi.name,
                                            poster = mi.poster ?: mi.logo,
                                            imagesJson = null,
                                            year = mi.year,
                                            yearKey = mi.year,
                                            rating = mi.rating,
                                            plot = mi.plot,
                                            genre = null,
                                            director = null,
                                            cast = null,
                                            country = null,
                                            releaseDate = null,
                                            imdbId = null,
                                            tmdbId = null,
                                            trailer = null,
                                            containerExt = null,
                                            categoryId = mi.categoryId,
                                            providerKey = providerKey(mi.categoryName, mi.name),
                                            genreKey = null
                                        )
                                        if (existing != null) ent.id = existing.id
                                        vodBox.put(ent)
                                    }
                                    "series" -> {
                                        val sid = xtIdFromExtra(mi.extraJson) ?: return@forEach
                                        val existing = seriesBox.query(ObxSeries_.seriesId.equal(sid.toLong())).build().findFirst()
                                        val ent = ObxSeries(
                                            seriesId = sid,
                                            nameLower = mi.name.lowercase(),
                                            sortTitleLower = sortKey(mi.name),
                                            name = mi.name,
                                            imagesJson = null,
                                            year = mi.year,
                                            yearKey = mi.year,
                                            rating = mi.rating,
                                            plot = mi.plot,
                                            genre = null,
                                            director = null,
                                            cast = null,
                                            imdbId = null,
                                            tmdbId = null,
                                            trailer = null,
                                            categoryId = mi.categoryId,
                                            providerKey = providerKey(mi.categoryName, mi.name),
                                            genreKey = null
                                        )
                                        if (existing != null) ent.id = existing.id
                                        seriesBox.put(ent)
                                    }
                                }
                            }
                            total += batch.size
                        },
                        onProgress = { /* ignored */ },
                        cancel = { false }
                        )
                    }
                }

                when (scheme) {
                    "content" -> {
                        val afd = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r") }.getOrNull()
                        val length = afd?.length ?: -1
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "content stream null" }
                            parseFrom(InputStreamReader(input, StandardCharsets.UTF_8), if (length >= 0) length else null)
                        }
                    }
                    "file" -> {
                        val f = java.io.File(uri.path!!)
                        java.io.FileInputStream(f).use { input ->
                            parseFrom(InputStreamReader(input, StandardCharsets.UTF_8), f.length())
                        }
                    }
                    else -> {
                        // Fallback: versuchen wie HTTP zu lesen
                        val req2 = Request.Builder().url(url).build()
                        val res2 = client.newCall(req2).execute()
                        try {
                            val bodyLen2 = res2.body?.contentLength() ?: -1
                            parseFrom(res2.body!!.charStream(), if (bodyLen2 >= 0) bodyLen2 else null)
                        } finally { runCatching { res2.close() } }
                    }
                }

                if (settings.hasXtream()) {
                    val xtObx = XtreamObxRepository(context, settings)
                    runCatching { xtObx.importDelta(deleteOrphans = true) }
                }
                val currentEpg = settings.epgUrl.first()
                if (currentEpg.isBlank() && headerPeek.isNotBlank()) {
                    val headerLine = headerPeek.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("#EXTM3U") }
                    if (!headerLine.isNullOrBlank()) {
                        val m = Regex("""url-tvg=\"([^\"]+)\"""").find(headerLine)
                        val epg = m?.groupValues?.getOrNull(1)
                        if (!epg.isNullOrBlank()) settings.setEpgUrl(epg)
                    }
                }
                SchedulingGateway.scheduleXtreamDeltaPeriodic(context)
                android.util.Log.d(TAG, "parsed.count=${total}")
                total
            }
        }
    }
    private suspend fun tryResolveXtreamViaDiscovery(
        scheme: String,
        host: String,
        user: String,
        pass: String,
        basePath: String?,
        skipIfExplicitPort: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (skipIfExplicitPort) return@withContext false
        return@withContext runCatching {
            val http = HttpClientFactory.create(context, settings)
            val capStore = ProviderCapabilityStore(context)
            val portStore = EndpointPortStore(context)
            val discoverer = CapabilityDiscoverer(http = http, store = capStore, portStore = portStore)
            val caps = discoverer.discoverAuto(scheme = scheme, host = host, username = user, password = pass, basePath = basePath, forceRefresh = false)
            val u = Uri.parse(caps.baseUrl)
            val rs = (u.scheme ?: scheme).lowercase()
            val rh = u.host ?: host
            val rp = u.port.takeIf { it > 0 } ?: return@runCatching false
            settings.setXtHost(rh)
            settings.setXtPort(rp)
            settings.setXtUser(user)
            settings.setXtPass(pass)
            settings.setXtPortVerified(true)
            val currentEpg = settings.epgUrl.first()
            if (currentEpg.isBlank()) {
                val portal = "$rs://$rh:$rp"
                settings.setEpgUrl("$portal/xmltv.php?username=$user&password=$pass")
            }
            true
        }.getOrElse { false }
    }
}
