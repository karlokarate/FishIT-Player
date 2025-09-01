package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.core.m3u.M3UParser
import com.chris.m3usuite.core.xtream.XtreamDetect
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.work.XtreamEnrichmentWorker
import com.chris.m3usuite.work.XtreamRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request

class PlaylistRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    /**
     * Lädt die M3U, parsed sie und ersetzt die DB-Inhalte für live/vod/series.
     * Gibt die Anzahl der importierten Items zurück (Result<Int>).
     */
    suspend fun refreshFromM3U(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            // 1) HTTP-Client + URL
            val client = HttpClientFactory.create(context, settings)
            val url = settings.m3uUrl.first()
            require(url.isNotBlank()) { "M3U URL missing" }

            // 2) Download
            client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")

                val body = res.body?.string().orEmpty()
                require(body.startsWith("#EXTM3U")) { "Invalid M3U" }

                // 3) Parsen → List<MediaItem>
                val parsed = M3UParser.parse(body)

                // 3a) Xtream auto-detect & persist if not set
                if (!settings.hasXtream()) {
                    val m3uUrl = url
                    val fromGet = XtreamDetect.detectFromGetPhp(m3uUrl)
                    val creds = fromGet ?: run {
                        val firstLiveUrl = parsed.firstOrNull { it.type == "live" }?.url
                        if (!firstLiveUrl.isNullOrBlank()) XtreamDetect.detectFromStreamUrl(firstLiveUrl) else null
                    }
                    if (creds != null) {
                        settings.setXtream(creds)
                    }
                }

                // 3b) Fallback EPG from #EXTM3U url-tvg if not set
                val currentEpg = settings.epgUrl.first()
                if (currentEpg.isBlank()) {
                    val firstLine = body.lineSequence().firstOrNull()?.trim().orEmpty()
                    if (firstLine.startsWith("#EXTM3U")) {
                        val m = Regex("""url-tvg=\"([^\"]+)\"""").find(firstLine)
                        val epg = m?.groupValues?.getOrNull(1)
                        if (!epg.isNullOrBlank()) {
                            settings.setEpgUrl(epg)
                        }
                    }
                }

                // 4) DB ersetzen
                val dao = DbProvider.get(context).mediaDao()
                // Preserve addedAt using url as key
                fun parseAddedAt(extra: String?): String? = try {
                    if (extra.isNullOrBlank()) null else kotlinx.serialization.json.Json.decodeFromString<Map<String,String>>(extra)["addedAt"]
                } catch (_: Throwable) { null }
                val prevLive = dao.listByType("live", 100000, 0).associateBy({ it.url }, { parseAddedAt(it.extraJson) })
                val prevVod  = dao.listByType("vod", 100000, 0).associateBy({ it.url }, { parseAddedAt(it.extraJson) })
                val prevSer  = dao.listByType("series", 100000, 0).associateBy({ it.url }, { parseAddedAt(it.extraJson) })
                val now = System.currentTimeMillis().toString()

                dao.clearType("live"); dao.clearType("vod"); dao.clearType("series")
                dao.upsertAll(parsed.map { mi ->
                    val key = mi.url
                    val addedAt = when (mi.type) {
                        "live" -> prevLive[key]
                        "vod" -> prevVod[key]
                        "series" -> prevSer[key]
                        else -> null
                    } ?: now
                    val base = mutableMapOf<String,String>()
                    base["addedAt"] = addedAt
                    val extra = kotlinx.serialization.json.Json.encodeToString(base)
                    mi.copy(extraJson = extra)
                })

                // 5) Schedule workers after import
                XtreamRefreshWorker.schedule(context)
                XtreamEnrichmentWorker.schedule(context)
                com.chris.m3usuite.work.EpgRefreshWorker.schedule(context)

                // 6) Anzahl zurückgeben
                parsed.size
            }
        }
    }
}
