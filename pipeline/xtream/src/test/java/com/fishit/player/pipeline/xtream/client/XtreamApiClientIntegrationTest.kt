package com.fishit.player.pipeline.xtream.client

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * Integration Test für XtreamApiClient mit echten Credentials.
 *
 * Führe aus mit: ./gradlew :pipeline:xtream:test --tests "*XtreamApiClientIntegrationTest*"
 *
 * ACHTUNG: Dies ist ein manueller Test - nicht für CI geeignet.
 */
fun main() {
    val config =
            XtreamApiConfig(
                    host = "konigtv.com",
                    port = 8080,
                    scheme = "HTTP",
                    username = "Christoph10",
                    password = "JQ2rKsQ744",
            )

    val http =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

    val client = DefaultXtreamApiClient(http)

    runBlocking {
        println("=".repeat(60))
        println("XTREAM API CLIENT INTEGRATION TEST")
        println("=".repeat(60))
        println("Host: ${config.host}:${config.port}")
        println("User: ${config.username}")
        println()

        // 1. Initialize
        println(">>> Initializing client...")
        val initResult = client.initialize(config)
        if (initResult.isFailure) {
            println("❌ Init failed: ${initResult.exceptionOrNull()?.message}")
            return@runBlocking
        }
        println("✅ Initialized successfully")
        println("   Capabilities: ${client.capabilities}")
        println()

        // 2. Server Info
        println(">>> Getting server info...")
        val serverInfo = client.getServerInfo()
        serverInfo
                .onSuccess { info ->
                    println("✅ Server Info:")
                    println("   User: ${info.userInfo?.username}")
                    println("   Status: ${info.userInfo?.status}")
                    println("   Exp: ${info.userInfo?.expDate}")
                    println("   Max Conn: ${info.userInfo?.maxConnections}")
                    println("   Active Conn: ${info.userInfo?.activeCons}")
                }
                .onFailure { e -> println("❌ Server info failed: ${e.message}") }
        println()

        // 3. Live Categories
        println(">>> Getting live categories...")
        val liveCats = client.getLiveCategories()
        println("✅ Live Categories: ${liveCats.size}")
        liveCats.take(5).forEach { cat -> println("   - [${cat.categoryId}] ${cat.categoryName}") }
        if (liveCats.size > 5) println("   ... and ${liveCats.size - 5} more")
        println()

        // 4. VOD Categories
        println(">>> Getting VOD categories...")
        val vodCats = client.getVodCategories()
        println("✅ VOD Categories: ${vodCats.size}")
        vodCats.take(5).forEach { cat -> println("   - [${cat.categoryId}] ${cat.categoryName}") }
        if (vodCats.size > 5) println("   ... and ${vodCats.size - 5} more")
        println()

        // 5. Series Categories
        println(">>> Getting series categories...")
        val seriesCats = client.getSeriesCategories()
        println("✅ Series Categories: ${seriesCats.size}")
        seriesCats.take(5).forEach { cat ->
            println("   - [${cat.categoryId}] ${cat.categoryName}")
        }
        if (seriesCats.size > 5) println("   ... and ${seriesCats.size - 5} more")
        println()

        // 6. Live Streams (sample)
        println(">>> Getting live streams (first 10)...")
        val liveStreams = client.getLiveStreams(limit = 10)
        println("✅ Live Streams (sample): ${liveStreams.size}")
        liveStreams.take(5).forEach { stream ->
            println(
                    "   - [${stream.streamId ?: stream.id}] ${stream.name} (EPG: ${stream.epgChannelId})"
            )
        }
        println()

        // 7. VOD Streams (sample)
        println(">>> Getting VOD streams (first 10)...")
        val vodStreams = client.getVodStreams(limit = 10)
        println("✅ VOD Streams (sample): ${vodStreams.size}")
        vodStreams.take(5).forEach { vod ->
            val id = vod.vodId ?: vod.movieId ?: vod.streamId ?: vod.id
            println(
                    "   - [$id] ${vod.name} (${vod.year ?: "?"}) - ${vod.containerExtension ?: "?"}"
            )
        }
        println()

        // 8. Series (sample)
        println(">>> Getting series (first 10)...")
        val series = client.getSeries(limit = 10)
        println("✅ Series (sample): ${series.size}")
        series.take(5).forEach { s ->
            println("   - [${s.seriesId ?: s.id}] ${s.name} (${s.year ?: "?"})")
        }
        println()

        // 9. VOD Info (if we have VOD)
        if (vodStreams.isNotEmpty()) {
            val firstVod = vodStreams.first()
            val vodId = firstVod.vodId ?: firstVod.movieId ?: firstVod.streamId ?: firstVod.id ?: 0
            println(">>> Getting VOD info for ID $vodId...")
            val vodInfo = client.getVodInfo(vodId)
            if (vodInfo != null) {
                println("✅ VOD Info:")
                println("   Title: ${vodInfo.movieData?.name ?: vodInfo.info?.name}")
                println("   Plot: ${vodInfo.info?.plot?.take(100)}...")
                println("   Duration: ${vodInfo.info?.duration}")
                println("   Rating: ${vodInfo.info?.rating}")
                println("   Container: ${vodInfo.movieData?.containerExtension}")
            } else {
                println("⚠️ No VOD info returned")
            }
            println()
        }

        // 10. Series Info (if we have series)
        if (series.isNotEmpty()) {
            val firstSeries = series.first()
            val seriesId = firstSeries.seriesId ?: firstSeries.id ?: 0
            println(">>> Getting series info for ID $seriesId...")
            val seriesInfo = client.getSeriesInfo(seriesId)
            if (seriesInfo != null) {
                println("✅ Series Info:")
                println("   Title: ${seriesInfo.info?.name}")
                println("   Plot: ${seriesInfo.info?.plot?.take(100)}...")
                println("   Seasons: ${seriesInfo.seasons?.size ?: 0}")
                println("   Episodes: ${seriesInfo.episodes?.values?.flatten()?.size ?: 0}")
                seriesInfo.episodes?.entries?.take(2)?.forEach { (season, eps) ->
                    println("   Season $season: ${eps.size} episodes")
                    eps.take(2).forEach { ep ->
                        println("      - E${ep.episodeNum}: ${ep.title} (${ep.containerExtension})")
                    }
                }
            } else {
                println("⚠️ No series info returned")
            }
            println()
        }

        // 11. EPG (if we have live streams with EPG)
        val streamWithEpg = liveStreams.firstOrNull { !it.epgChannelId.isNullOrBlank() }
        if (streamWithEpg != null) {
            val streamId = streamWithEpg.streamId ?: streamWithEpg.id ?: 0
            println(">>> Getting EPG for stream $streamId (${streamWithEpg.name})...")
            val epg = client.getShortEpg(streamId, 5)
            println("✅ EPG entries: ${epg.size}")
            epg.take(3).forEach { prog -> println("   - ${prog.start}: ${prog.title}") }
            println()
        }

        // 12. Playback URLs
        println(">>> Generating playback URLs...")
        if (liveStreams.isNotEmpty()) {
            val liveId = liveStreams.first().streamId ?: liveStreams.first().id ?: 0
            val liveUrl = client.buildLiveUrl(liveId, "m3u8")
            println("✅ Live URL: ${liveUrl.take(80)}...")
        }
        if (vodStreams.isNotEmpty()) {
            val vodId =
                    vodStreams.first().vodId
                            ?: vodStreams.first().movieId ?: vodStreams.first().id ?: 0
            val ext = vodStreams.first().containerExtension ?: "mp4"
            val vodUrl = client.buildVodUrl(vodId, ext)
            println("✅ VOD URL: ${vodUrl.take(80)}...")
        }
        println()

        // 13. Search
        println(">>> Searching for 'action'...")
        val searchResults =
                client.search(
                        "action",
                        setOf(XtreamContentType.VOD, XtreamContentType.SERIES),
                        limit = 5
                )
        println("✅ Search Results:")
        println("   VOD: ${searchResults.vod.size}")
        searchResults.vod.take(3).forEach { println("      - ${it.name}") }
        println("   Series: ${searchResults.series.size}")
        searchResults.series.take(3).forEach { println("      - ${it.name}") }
        println()

        println("=".repeat(60))
        println("TEST COMPLETE")
        println("=".repeat(60))

        client.close()
    }
}
