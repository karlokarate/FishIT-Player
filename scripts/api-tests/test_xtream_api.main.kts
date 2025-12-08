#!/usr/bin/env kotlin

@file:DependsOn("com.squareup.okhttp3:okhttp:4.12.0")

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

val BASE = "http://konigtv.com:8080"
val USER = "Christoph10"
val PASS = "JQ2rKsQ744"

val http = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

fun fetch(url: String): Pair<Int, String?> {
    val req = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .build()
    return try {
        http.newCall(req).execute().use { resp ->
            resp.code to resp.body?.string()
        }
    } catch (e: Exception) {
        -1 to e.message
    }
}

fun test(name: String, action: String? = null, extra: Map<String, String> = emptyMap()) {
    val params = mutableMapOf("username" to USER, "password" to PASS)
    action?.let { params["action"] = it }
    params.putAll(extra)
    val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
    val url = "$BASE/player_api.php?$query"
    
    print(">>> $name... ")
    val (code, body) = fetch(url)
    
    if (code == 200 && body != null) {
        val len = body.length
        val preview = if (len > 200) body.take(200) + "..." else body
        println("✅ $len bytes")
        println("    $preview")
        println()
    } else {
        println("❌ HTTP $code: ${body?.take(100)}")
        println()
    }
}

println("=" .repeat(60))
println("XTREAM API CLIENT TEST - KönigTV")
println("=" .repeat(60))
println("Host: $BASE")
println("User: $USER")
println()

test("Server Info")
test("Live Categories", "get_live_categories")
test("VOD Categories", "get_vod_categories")
test("Series Categories", "get_series_categories")
test("Live Streams (sample)", "get_live_streams", mapOf("category_id" to "0"))
test("VOD Streams (sample)", "get_vod_streams", mapOf("category_id" to "0"))
test("Series (sample)", "get_series", mapOf("category_id" to "0"))

println("=" .repeat(60))
println("PLAYBACK URL EXAMPLES")
println("=" .repeat(60))
println("Live: $BASE/live/$USER/$PASS/1.m3u8")
println("VOD:  $BASE/vod/$USER/$PASS/1.mp4")
println("Series: $BASE/series/$USER/$PASS/1.mkv")
println()
println("TEST COMPLETE")
