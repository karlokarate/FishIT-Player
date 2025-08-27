package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.core.xtream.*
import com.chris.m3usuite.data.db.*
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class XtreamRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    suspend fun configureFromM3uUrl(): XtreamConfig? = withContext(Dispatchers.IO) {
        val m3u = settings.m3uUrl.first()
        val cfg = XtreamConfig.fromM3uUrl(m3u) ?: return@withContext null
        settings.set(Keys.XT_HOST, cfg.host)
        settings.setInt(Keys.XT_PORT, cfg.port)
        settings.set(Keys.XT_USER, cfg.username)
        settings.set(Keys.XT_PASS, cfg.password)
        settings.set(Keys.XT_OUTPUT, cfg.output)
        if (settings.epgUrl.first().isBlank()) {
            settings.set(Keys.EPG_URL, "${cfg.portalBase}/xmltv.php?username=${cfg.username}&password=${cfg.password}")
        }
        cfg
    }

    private suspend fun config(): XtreamConfig? {
        val host = settings.xtHost.first()
        val user = settings.xtUser.first()
        val pass = settings.xtPass.first()
        if (host.isBlank() || user.isBlank() || pass.isBlank()) return null
        val port = settings.xtPort.first()
        val out = settings.xtOutput.first()
        return XtreamConfig(host, port, user, pass, out)
    }

    suspend fun importAll(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = config() ?: error("Xtream config missing")
            val client = XtreamClient(context, settings, cfg)
            val handshake = client.handshake()
            if (handshake.userInfo?.auth != 1) error("Auth failed")

            val db = DbProvider.get(context)
            val mediaDao = db.mediaDao()
            val catDao = db.categoryDao()

            // Build previous addedAt maps per type to preserve timestamps across updates
            fun parseAddedAt(extra: String?): String? = runCatching {
                if (extra.isNullOrBlank()) return@runCatching null
                val map = kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(extra)
                map["addedAt"]
            }.getOrNull()
            val prevLive = mediaDao.listByType("live", 100000, 0).associateBy({ it.streamId }, { parseAddedAt(it.extraJson) })
            val prevVod  = mediaDao.listByType("vod", 100000, 0).associateBy({ it.streamId }, { parseAddedAt(it.extraJson) })
            val prevSer  = mediaDao.listByType("series", 100000, 0).associateBy({ it.streamId }, { parseAddedAt(it.extraJson) })

            val liveCats = client.liveCategories()
            val vodCats = client.vodCategories()
            val serCats = client.seriesCategories()
            catDao.clear("live"); catDao.clear("vod"); catDao.clear("series")
            catDao.upsertAll(liveCats.map { Category(kind="live", categoryId=it.categoryId, categoryName=it.categoryName) })
            catDao.upsertAll(vodCats.map { Category(kind="vod", categoryId=it.categoryId, categoryName=it.categoryName) })
            catDao.upsertAll(serCats.map { Category(kind="series", categoryId=it.categoryId, categoryName=it.categoryName) })

            val live = client.liveStreams()
            mediaDao.clearType("live")
            mediaDao.upsertAll(live.map {
                val addedAt = prevLive[it.streamId]?.takeIf { s -> !s.isNullOrBlank() } ?: System.currentTimeMillis().toString()
                val extra = kotlinx.serialization.json.Json.encodeToString(mapOf("addedAt" to addedAt))
                MediaItem(
                    type = "live",
                    streamId = it.streamId,
                    name = it.name,
                    sortTitle = norm(it.name),
                    categoryId = it.categoryId,
                    categoryName = liveCats.find { c -> c.categoryId == it.categoryId }?.categoryName,
                    logo = it.streamIcon,
                    poster = it.streamIcon,
                    backdrop = null,
                    epgChannelId = it.epgChannelId,
                    year = null, rating = null, durationSecs = null,
                    plot = null,
                    url = cfg.liveUrl(it.streamId),
                    extraJson = extra
                )
            })

            val vod = client.vodStreams()
            mediaDao.clearType("vod")
            mediaDao.upsertAll(vod.map {
                val addedAt = prevVod[it.streamId]?.takeIf { s -> !s.isNullOrBlank() } ?: System.currentTimeMillis().toString()
                val base = mutableMapOf("container" to (it.containerExtension ?: ""), "addedAt" to addedAt)
                val extra = Json.encodeToString(base)
                MediaItem(
                    type = "vod",
                    streamId = it.streamId,
                    name = it.name,
                    sortTitle = norm(it.name),
                    categoryId = it.categoryId,
                    categoryName = vodCats.find { c -> c.categoryId == it.categoryId }?.categoryName,
                    logo = it.streamIcon,
                    poster = it.streamIcon,
                    backdrop = null,
                    epgChannelId = null,
                    year = it.year?.toIntOrNull(),
                    rating = it.rating?.toDoubleOrNull(),
                    durationSecs = null,
                    plot = null,
                    url = cfg.vodUrl(it.streamId, it.containerExtension),
                    extraJson = extra
                )
            })

            val series = client.seriesList()
            mediaDao.clearType("series")
            mediaDao.upsertAll(series.map {
                val addedAt = prevSer[it.seriesId]?.takeIf { s -> !s.isNullOrBlank() } ?: System.currentTimeMillis().toString()
                val extra = Json.encodeToString(mapOf("addedAt" to addedAt))
                MediaItem(
                    type = "series",
                    streamId = it.seriesId,
                    name = it.name,
                    sortTitle = norm(it.name),
                    categoryId = it.categoryId,
                    categoryName = serCats.find { c -> c.categoryId == it.categoryId }?.categoryName,
                    logo = it.cover,
                    poster = it.cover,
                    backdrop = null,
                    epgChannelId = null,
                    year = null,
                    rating = null,
                    durationSecs = null,
                    plot = it.plot,
                    url = null,
                    extraJson = extra
                )
            })

            live.size + vod.size + series.size
        }
    }

    suspend fun enrichVodDetailsOnce(vodItemId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = config() ?: return@runCatching false
            val client = XtreamClient(context, settings, cfg)
            val db = DbProvider.get(context)
            val dao = db.mediaDao()
            val item = dao.byId(vodItemId) ?: return@runCatching false
            val sid = item.streamId ?: return@runCatching false
            val info = client.vodInfo(sid).info ?: return@runCatching false
            val upd = item.copy(
                poster = info.movieImage ?: item.poster,
                backdrop = info.backdropPath?.firstOrNull() ?: item.backdrop,
                plot = info.plot ?: item.plot,
                durationSecs = info.durationSecs ?: item.durationSecs,
                rating = info.rating?.toDoubleOrNull() ?: item.rating
            )
            dao.upsertAll(listOf(upd))
            true
        }
    }

    suspend fun loadSeriesInfo(seriesStreamId: Int): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = config() ?: error("Xtream config missing")
            val client = XtreamClient(context, settings, cfg)
            val info = client.seriesInfo(seriesStreamId)
            val list = mutableListOf<Episode>()
            info.episodes?.forEach { (seasonStr, eps) ->
                val season = seasonStr.toIntOrNull() ?: 0
                for (e in eps) {
                    list += Episode(
                        seriesStreamId = seriesStreamId,
                        episodeId = e.id,
                        season = e.season ?: season,
                        episodeNum = e.episodeNum ?: 0,
                        title = e.title ?: "Episode ${e.episodeNum ?: 0}",
                        plot = e.info?.plot,
                        durationSecs = e.info?.durationSecs,
                        containerExt = e.containerExtension,
                        poster = e.info?.movieImage
                    )
                }
            }
            val db = DbProvider.get(context)
            val epDao = db.episodeDao()
            epDao.clearForSeries(seriesStreamId)
            epDao.upsertAll(list)
            list.size
        }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
