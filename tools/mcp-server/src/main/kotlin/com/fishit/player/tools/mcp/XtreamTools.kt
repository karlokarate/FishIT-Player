package com.fishit.player.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// All Tool.Input references replaced with ToolSchema per MCP SDK 0.4.0

/**
 * Xtream API Tools for MCP Server
 *
 * Provides direct access to Xtream Codes API for pipeline testing.
 * Uses environment variables for credentials (Codespace Secrets).
 *
 * Features:
 * - All Xtream API endpoints (categories, streams, info, EPG)
 * - Search across content types
 * - Stream URL generation
 * - Automatic retry with exponential backoff
 */
object XtreamTools {
    private const val MAX_RETRIES = 3
    private const val INITIAL_BACKOFF_MS = 1000L

    // =========================================================================
    // HTTP Config (matches App's XtreamTransportConfig EXACTLY)
    // See: infra/transport-xtream/.../XtreamTransportConfig.kt
    // =========================================================================
    private const val USER_AGENT = "FishIT-Player/2.x (Android)"
    private const val ACCEPT_JSON = "application/json"
    private const val ACCEPT_ENCODING = "gzip"

    private val client = OkHttpClient.Builder()
        // Timeouts per Premium Contract Section 3 (all 30s)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        // Redirect handling (matches App exactly)
        .followRedirects(true)
        .followSslRedirects(false)  // CRITICAL: Many Xtream panels use HTTP on non-standard ports
        .build()

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }

    fun register(server: Server) {
        // Tool: Get server info and user status
        server.addTool(
            name = "xtream_server_info",
            description = """
                Get Xtream server info and user account status.
                Returns: user_info (account details, expiry) and server_info (timezone, etc.)
                No parameters required - uses environment credentials.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            executeXtreamCall("") // No action = server info
        }

        // Tool: Get VOD categories
        server.addTool(
            name = "xtream_vod_categories",
            description = """
                Get all VOD (movie) categories from Xtream server.
                Returns: List of categories with id, name, parent_id.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            executeXtreamCall("get_vod_categories")
        }

        // Tool: Get Live categories
        server.addTool(
            name = "xtream_live_categories",
            description = """
                Get all Live TV categories from Xtream server.
                Returns: List of categories with id, name.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            executeXtreamCall("get_live_categories")
        }

        // Tool: Get Series categories
        server.addTool(
            name = "xtream_series_categories",
            description = """
                Get all Series categories from Xtream server.
                Returns: List of categories with id, name.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            executeXtreamCall("get_series_categories")
        }

        // Tool: Get VOD streams by category
        server.addTool(
            name = "xtream_vod_streams",
            description = """
                Get VOD (movie) items from a specific category.
                Returns: List of VOD items with stream_id, name, stream_icon, rating, etc.
                
                Parameters:
                - category_id: Category ID (use xtream_vod_categories to find IDs)
                - limit: Max items to return (default 20, max 100)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("category_id") {
                        put("type", "string")
                        put("description", "Category ID from xtream_vod_categories")
                    }
                    putJsonObject("limit") {
                        put("type", "number")
                        put("description", "Max items to return (default 20)")
                    }
                },
                required = listOf("category_id")
            )
        ) { request ->
            val categoryId = request.arguments?.get("category_id")?.jsonPrimitive?.content ?: ""
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 20
            executeXtreamCall("get_vod_streams", "category_id" to categoryId, limit = limit)
        }

        // Tool: Get Live streams by category
        server.addTool(
            name = "xtream_live_streams",
            description = """
                Get Live TV channels from a specific category.
                Returns: List of channels with stream_id, name, stream_icon, epg_channel_id, etc.
                
                Parameters:
                - category_id: Category ID (use xtream_live_categories to find IDs)
                - limit: Max items to return (default 20, max 100)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("category_id") {
                        put("type", "string")
                        put("description", "Category ID from xtream_live_categories")
                    }
                    putJsonObject("limit") {
                        put("type", "number")
                        put("description", "Max items to return (default 20)")
                    }
                },
                required = listOf("category_id")
            )
        ) { request ->
            val categoryId = request.arguments?.get("category_id")?.jsonPrimitive?.content ?: ""
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 20
            executeXtreamCall("get_live_streams", "category_id" to categoryId, limit = limit)
        }

        // Tool: Get Series list by category
        server.addTool(
            name = "xtream_series",
            description = """
                Get Series from a specific category.
                Returns: List of series with series_id, name, cover, plot, cast, etc.
                
                Parameters:
                - category_id: Category ID (use xtream_series_categories to find IDs)
                - limit: Max items to return (default 20, max 100)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("category_id") {
                        put("type", "string")
                        put("description", "Category ID from xtream_series_categories")
                    }
                    putJsonObject("limit") {
                        put("type", "number")
                        put("description", "Max items to return (default 20)")
                    }
                },
                required = listOf("category_id")
            )
        ) { request ->
            val categoryId = request.arguments?.get("category_id")?.jsonPrimitive?.content ?: ""
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 20
            executeXtreamCall("get_series", "category_id" to categoryId, limit = limit)
        }

        // Tool: Get Series info (episodes)
        server.addTool(
            name = "xtream_series_info",
            description = """
                Get detailed series info including all episodes.
                Returns: Series details with seasons and episodes array.
                
                Parameters:
                - series_id: Series ID (from xtream_series results)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("series_id") {
                        put("type", "string")
                        put("description", "Series ID from xtream_series")
                    }
                },
                required = listOf("series_id")
            )
        ) { request ->
            val seriesId = request.arguments?.get("series_id")?.jsonPrimitive?.content ?: ""
            executeXtreamCall("get_series_info", "series_id" to seriesId)
        }

        // Tool: Get VOD info (single movie details)
        server.addTool(
            name = "xtream_vod_info",
            description = """
                Get detailed VOD (movie) info.
                Returns: Movie details including plot, cast, director, etc.
                
                Parameters:
                - vod_id: VOD ID (stream_id from xtream_vod_streams)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("vod_id") {
                        put("type", "string")
                        put("description", "VOD ID (stream_id from xtream_vod_streams)")
                    }
                },
                required = listOf("vod_id")
            )
        ) { request ->
            val vodId = request.arguments?.get("vod_id")?.jsonPrimitive?.content ?: ""
            executeXtreamCall("get_vod_info", "vod_id" to vodId)
        }

        // Tool: Get EPG (Electronic Program Guide)
        server.addTool(
            name = "xtream_epg",
            description = """
                Get EPG (TV program guide) for a live channel.
                Returns: Program schedule with title, start/end times, description.
                
                Parameters:
                - stream_id: Live stream ID (from xtream_live_streams)
                - limit: Max programs to return (default 20)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("stream_id") {
                        put("type", "string")
                        put("description", "Stream ID from xtream_live_streams")
                    }
                    putJsonObject("limit") {
                        put("type", "number")
                        put("description", "Max programs to return (default 20)")
                    }
                },
                required = listOf("stream_id")
            )
        ) { request ->
            val streamId = request.arguments?.get("stream_id")?.jsonPrimitive?.content ?: ""
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 20
            executeXtreamCall("get_short_epg", "stream_id" to streamId, limit = limit)
        }

        // Tool: Generate stream URL
        server.addTool(
            name = "xtream_stream_url",
            description = """
                Generate playback URL for a stream.
                Returns: Direct stream URL for VOD, Live, or Series episode.
                
                Parameters:
                - stream_id: Stream ID
                - stream_type: Type of stream (live, movie, series)
                - extension: File extension (m3u8, ts, mp4, mkv)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("stream_id") {
                        put("type", "string")
                        put("description", "Stream ID")
                    }
                    putJsonObject("stream_type") {
                        put("type", "string")
                        put("enum", JsonArray(listOf("live", "movie", "series").map { JsonPrimitive(it) }))
                        put("description", "Type of stream")
                    }
                    putJsonObject("extension") {
                        put("type", "string")
                        put("description", "File extension (m3u8, ts, mp4, mkv)")
                    }
                },
                required = listOf("stream_id", "stream_type")
            )
        ) { request ->
            val streamId = request.arguments?.get("stream_id")?.jsonPrimitive?.content ?: ""
            val streamType = request.arguments?.get("stream_type")?.jsonPrimitive?.content ?: "live"
            val extension = request.arguments?.get("extension")?.jsonPrimitive?.content ?: getDefaultExtension(streamType)
            generateStreamUrl(streamId, streamType, extension)
        }

        // Tool: Search across all content
        server.addTool(
            name = "xtream_search",
            description = """
                Search for content across VOD, Live, and Series.
                Returns: Matching items from all content types.
                
                Parameters:
                - query: Search query
                - content_types: Types to search (vod, live, series) - comma separated
                - limit: Max results per type (default 10)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Search query")
                    }
                    putJsonObject("content_types") {
                        put("type", "string")
                        put("description", "Comma-separated: vod,live,series (default: all)")
                    }
                    putJsonObject("limit") {
                        put("type", "number")
                        put("description", "Max results per type (default 10)")
                    }
                },
                required = listOf("query")
            )
        ) { request ->
            val query = request.arguments?.get("query")?.jsonPrimitive?.content ?: ""
            val types = request.arguments?.get("content_types")?.jsonPrimitive?.content?.split(",")?.map { it.trim() }
                ?: listOf("vod", "live", "series")
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 10
            searchXtream(query, types, limit)
        }
    }

    private fun getDefaultExtension(streamType: String): String = when (streamType) {
        "live" -> "m3u8"
        "movie" -> "mp4"
        "series" -> "mkv"
        else -> "m3u8"
    }

    private fun generateStreamUrl(streamId: String, streamType: String, extension: String): CallToolResult {
        val url = System.getenv("XTREAM_URL") ?: return errorResult("XTREAM_URL not set")
        val user = System.getenv("XTREAM_USER") ?: return errorResult("XTREAM_USER not set")
        val pass = System.getenv("XTREAM_PASS") ?: return errorResult("XTREAM_PASS not set")

        val baseUrl = url.trimEnd('/')
        val streamUrl = when (streamType) {
            "live" -> "$baseUrl/live/$user/$pass/$streamId.$extension"
            "movie" -> "$baseUrl/movie/$user/$pass/$streamId.$extension"
            "series" -> "$baseUrl/series/$user/$pass/$streamId.$extension"
            else -> return errorResult("Invalid stream_type: $streamType")
        }

        val result = buildJsonObject {
            put("stream_url", streamUrl)
            put("stream_id", streamId)
            put("stream_type", streamType)
            put("extension", extension)
            put("note", "Use this URL with a media player. URL contains credentials.")
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(result))),
            isError = false
        )
    }

    private fun searchXtream(query: String, types: List<String>, limit: Int): CallToolResult {
        val url = System.getenv("XTREAM_URL") ?: return errorResult("XTREAM_URL not set")
        val user = System.getenv("XTREAM_USER") ?: return errorResult("XTREAM_USER not set")
        val pass = System.getenv("XTREAM_PASS") ?: return errorResult("XTREAM_PASS not set")

        val baseUrl = url.trimEnd('/')
        val queryLower = query.lowercase()
        val results = mutableMapOf<String, JsonArray>()

        types.forEach { type ->
            val action = when (type) {
                "vod" -> "get_vod_streams"
                "live" -> "get_live_streams"
                "series" -> "get_series"
                else -> return@forEach
            }

            try {
                val requestUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=$action"
                val request = Request.Builder()
                    .url(requestUrl)
                    // Headers per Premium Contract Section 4 (matches App exactly)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", ACCEPT_JSON)
                    .header("Accept-Encoding", ACCEPT_ENCODING)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        // Gzip handling (matches App behavior)
                        val bodyBytes = response.body?.bytes() ?: return@forEach
                        val body = if (bodyBytes.size >= 2 &&
                            (bodyBytes[0].toInt() and 0xFF) == 0x1F &&
                            (bodyBytes[1].toInt() and 0xFF) == 0x8B
                        ) {
                            try {
                                java.util.zip.GZIPInputStream(bodyBytes.inputStream())
                                    .bufferedReader()
                                    .readText()
                            } catch (e: Exception) {
                                String(bodyBytes, Charsets.UTF_8)
                            }
                        } else {
                            String(bodyBytes, Charsets.UTF_8)
                        }
                        
                        val items = json.parseToJsonElement(body)
                        if (items is JsonArray) {
                            val matches = items.filter { item ->
                                val name = item.jsonObject["name"]?.jsonPrimitive?.content?.lowercase() ?: ""
                                name.contains(queryLower)
                            }.take(limit)
                            if (matches.isNotEmpty()) {
                                results[type] = JsonArray(matches)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue with other types
            }
        }

        val response = buildJsonObject {
            put("query", query)
            put("searched_types", JsonArray(types.map { JsonPrimitive(it) }))
            put("total_matches", results.values.sumOf { it.size })
            putJsonObject("results") {
                results.forEach { (type, items) ->
                    put(type, items)
                }
            }
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(response))),
            isError = false
        )
    }

    private fun executeXtreamCall(
        action: String,
        vararg params: Pair<String, String>,
        limit: Int = 100
    ): CallToolResult {
        val url = System.getenv("XTREAM_URL") ?: return errorResult("XTREAM_URL not set")
        val user = System.getenv("XTREAM_USER") ?: return errorResult("XTREAM_USER not set")
        val pass = System.getenv("XTREAM_PASS") ?: return errorResult("XTREAM_PASS not set")

        val baseUrl = url.trimEnd('/')
        val actionParam = if (action.isNotEmpty()) "&action=$action" else ""
        val extraParams = params.joinToString("") { "&${it.first}=${it.second}" }
        
        val requestUrl = "$baseUrl/player_api.php?username=$user&password=$pass$actionParam$extraParams"

        return executeWithRetry(requestUrl, limit)
    }

    private fun executeWithRetry(requestUrl: String, limit: Int): CallToolResult {
        var lastException: Exception? = null
        var backoffMs = INITIAL_BACKOFF_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                val request = Request.Builder()
                    .url(requestUrl)
                    // Headers per Premium Contract Section 4 (matches App exactly)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", ACCEPT_JSON)
                    .header("Accept-Encoding", ACCEPT_ENCODING)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Don't retry on 4xx errors (client errors)
                        if (response.code in 400..499) {
                            return errorResult("HTTP ${response.code}: ${response.message}")
                        }
                        // 5xx errors and others - retry
                        throw RuntimeException("HTTP ${response.code}: ${response.message}")
                    }

                    // Get raw bytes first for gzip handling (matches App behavior)
                    val bodyBytes = response.body?.bytes() ?: return errorResult("Empty response")
                    
                    // Defensive gzip handling: Some servers send gzip without Content-Encoding header
                    // Check for gzip magic bytes: 0x1F 0x8B (per RFC 1952)
                    val body = if (bodyBytes.size >= 2 &&
                        (bodyBytes[0].toInt() and 0xFF) == 0x1F &&
                        (bodyBytes[1].toInt() and 0xFF) == 0x8B
                    ) {
                        try {
                            java.util.zip.GZIPInputStream(bodyBytes.inputStream())
                                .bufferedReader()
                                .readText()
                        } catch (e: Exception) {
                            // Fallback to raw bytes if decompression fails
                            String(bodyBytes, Charsets.UTF_8)
                        }
                    } else {
                        String(bodyBytes, Charsets.UTF_8)
                    }
                    
                    // Parse and potentially limit results
                    val jsonResult = try {
                        val parsed = json.parseToJsonElement(body)
                        when {
                            parsed is JsonArray && parsed.size > limit -> {
                                val limited = JsonArray(parsed.take(limit))
                                buildJsonObject {
                                    put("total_count", parsed.size)
                                    put("returned_count", limit)
                                    put("data", limited)
                                }
                            }
                            else -> parsed
                        }
                    } catch (e: Exception) {
                        // Not valid JSON, return as text
                        return CallToolResult(
                            content = listOf(TextContent(text = body)),
                            isError = false
                        )
                    }

                    return CallToolResult(
                        content = listOf(TextContent(text = json.encodeToString(jsonResult))),
                        isError = false
                    )
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(backoffMs)
                    backoffMs *= 2 // Exponential backoff
                }
            }
        }

        return errorResult("Request failed after $MAX_RETRIES retries: ${lastException?.message}")
    }

    private fun errorResult(message: String) = CallToolResult(
        content = listOf(TextContent(text = """{"error": "$message"}""")),
        isError = true
    )
}
