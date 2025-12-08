#!/usr/bin/env kotlin
@file:DependsOn("com.squareup.okhttp3:okhttp:4.12.0")

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * KönigTV API Test Script - Direct API calls ohne Client-Logik
 */
val BASE = "http://konigtv.com:8080"
val USER = "Christoph10"
val PASS = "JQ2rKsQ744"

val http = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

fun fetch(url: String): String? {
    println("\n--- Fetching: $url")
    return try {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                println("HTTP ${resp.code}: ${resp.message}")
                null
            } else {
                resp.body?.string()
            }
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
        null
    }
}

fun main() {
    println("=== KönigTV API Test ===")
    
    // 1. Server Info
    println("\n\n========== 1. SERVER INFO ==========")
    val serverInfo = fetch("$BASE/player_api.php?username=$USER&password=$PASS")
    if (serverInfo != null) {
        println("Response (${serverInfo.length} chars):")
        println(serverInfo.take(2000))
    }
    
    // 2. Live Categories
    println("\n\n========== 2. LIVE CATEGORIES ==========")
    val liveCats = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_live_categories")
    if (liveCats != null) {
        println("Response (${liveCats.length} chars):")
        println(liveCats.take(2000))
    }
    
    // 3. VOD Categories
    println("\n\n========== 3. VOD CATEGORIES ==========")
    val vodCats = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_vod_categories")
    if (vodCats != null) {
        println("Response (${vodCats.length} chars):")
        println(vodCats.take(2000))
    }
    
    // 4. Series Categories
    println("\n\n========== 4. SERIES CATEGORIES ==========")
    val seriesCats = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_series_categories")
    if (seriesCats != null) {
        println("Response (${seriesCats.length} chars):")
        println(seriesCats.take(2000))
    }
    
    // 5. Live Streams (first 3000 chars)
    println("\n\n========== 5. LIVE STREAMS (sample) ==========")
    val live = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_live_streams")
    if (live != null) {
        println("Response (${live.length} chars, showing first 3000):")
        println(live.take(3000))
    }
    
    // 6. VOD Streams (first 3000 chars)
    println("\n\n========== 6. VOD STREAMS (sample) ==========")
    val vod = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_vod_streams")
    if (vod != null) {
        println("Response (${vod.length} chars, showing first 3000):")
        println(vod.take(3000))
    }
    
    // 7. Series (first 3000 chars)
    println("\n\n========== 7. SERIES (sample) ==========")
    val series = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_series")
    if (series != null) {
        println("Response (${series.length} chars, showing first 3000):")
        println(series.take(3000))
    }
    
    // 8. Extract IDs and test detail endpoints
    println("\n\n========== 8. VOD INFO ==========")
    // Try to extract first vod_id or stream_id
    val vodIdMatch = vod?.let { Regex(""""(?:vod_id|stream_id)"[:\s]*(\d+)""").find(it)?.groupValues?.get(1) }
    if (vodIdMatch != null) {
        println("Testing VOD ID: $vodIdMatch")
        val vodInfo = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_vod_info&vod_id=$vodIdMatch")
        if (vodInfo != null) {
            println("Response (${vodInfo.length} chars):")
            println(vodInfo.take(5000))
        }
    } else {
        println("Could not extract VOD ID from streams")
    }
    
    // 9. Series Info
    println("\n\n========== 9. SERIES INFO ==========")
    val seriesIdMatch = series?.let { Regex(""""series_id"[:\s]*(\d+)""").find(it)?.groupValues?.get(1) }
    if (seriesIdMatch != null) {
        println("Testing Series ID: $seriesIdMatch")
        val seriesInfo = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_series_info&series_id=$seriesIdMatch")
        if (seriesInfo != null) {
            println("Response (${seriesInfo.length} chars, showing first 5000):")
            println(seriesInfo.take(5000))
        }
    } else {
        println("Could not extract Series ID from list")
    }
    
    // 10. Short EPG
    println("\n\n========== 10. SHORT EPG ==========")
    val liveIdMatch = live?.let { Regex(""""stream_id"[:\s]*(\d+)""").find(it)?.groupValues?.get(1) }
    if (liveIdMatch != null) {
        println("Testing Stream ID: $liveIdMatch")
        val epg = fetch("$BASE/player_api.php?username=$USER&password=$PASS&action=get_short_epg&stream_id=$liveIdMatch")
        if (epg != null) {
            println("Response (${epg.length} chars):")
            println(epg.take(3000))
        }
    } else {
        println("Could not extract Live Stream ID")
    }
    
    println("\n\n=== Test Complete ===")
}

main()
