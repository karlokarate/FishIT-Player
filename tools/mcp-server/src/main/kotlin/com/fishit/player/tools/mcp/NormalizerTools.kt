package com.fishit.player.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.*

/**
 * Normalizer Tools for MCP Server
 *
 * Tools for testing the pipeline normalization chain:
 * Source DTO → RawMediaMetadata → NormalizedMedia → UI
 */
object NormalizerTools {
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }

    fun register(server: Server) {
        // Tool: Show RawMediaMetadata schema
        server.addTool(
            name = "normalize_raw_schema",
            description = """
                Get the schema of RawMediaMetadata.
                This is the canonical type produced by all pipelines.
                
                Returns: JSON schema with all fields and their purposes.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            getRawMediaMetadataSchema()
        }

        // Tool: Convert Xtream VOD to RawMediaMetadata
        server.addTool(
            name = "normalize_xtream_vod",
            description = """
                Convert an Xtream VOD item to RawMediaMetadata.
                Shows the exact mapping from Xtream fields to canonical format.
                
                Parameters:
                - vod_json: VOD item JSON from xtream_vod_streams
                
                Returns: RawMediaMetadata with field mapping annotations.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("vod_json") {
                        put("type", "string")
                        put("description", "VOD item JSON from xtream_vod_streams")
                    }
                },
                required = listOf("vod_json")
            )
        ) { request ->
            val vodJson = request.arguments?.get("vod_json")?.jsonPrimitive?.content ?: ""
            convertXtreamVodToRaw(vodJson)
        }

        // Tool: Convert Xtream Channel to RawMediaMetadata
        server.addTool(
            name = "normalize_xtream_channel",
            description = """
                Convert an Xtream Live channel to RawMediaMetadata.
                
                Parameters:
                - channel_json: Channel item JSON from xtream_live_streams
                
                Returns: RawMediaMetadata for live content.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("channel_json") {
                        put("type", "string")
                        put("description", "Channel JSON from xtream_live_streams")
                    }
                },
                required = listOf("channel_json")
            )
        ) { request ->
            val channelJson = request.arguments?.get("channel_json")?.jsonPrimitive?.content ?: ""
            convertXtreamChannelToRaw(channelJson)
        }

        // Tool: Convert TgMessage to RawMediaMetadata
        server.addTool(
            name = "normalize_telegram_message",
            description = """
                Convert a TgMessage to RawMediaMetadata.
                Shows the Telegram pipeline mapping.
                
                Parameters:
                - message_json: TgMessage JSON (use telegram_mock_message to generate)
                
                Returns: RawMediaMetadata with Telegram-specific field mapping.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("message_json") {
                        put("type", "string")
                        put("description", "TgMessage JSON")
                    }
                },
                required = listOf("message_json")
            )
        ) { request ->
            val messageJson = request.arguments?.get("message_json")?.jsonPrimitive?.content ?: ""
            convertTelegramToRaw(messageJson)
        }

        // Tool: Simulate title parsing
        server.addTool(
            name = "normalize_parse_title",
            description = """
                Parse a media title to extract metadata.
                Shows what the normalizer extracts from filenames.
                
                Parameters:
                - title: Media title or filename
                
                Returns: Extracted year, resolution, quality, etc.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Media title or filename to parse")
                    }
                },
                required = listOf("title")
            )
        ) { request ->
            val title = request.arguments?.get("title")?.jsonPrimitive?.content ?: ""
            parseTitle(title)
        }

        // Tool: MediaType detection
        server.addTool(
            name = "normalize_detect_type",
            description = """
                Detect MediaType from available metadata.
                
                Parameters:
                - has_season: Whether season info is present
                - has_episode: Whether episode info is present
                - duration_ms: Duration in milliseconds (optional)
                - source: Source type (xtream_vod, xtream_live, telegram, etc.)
                
                Returns: Detected MediaType with reasoning.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("has_season") {
                        put("type", "boolean")
                        put("description", "Whether season info is present")
                    }
                    putJsonObject("has_episode") {
                        put("type", "boolean")
                        put("description", "Whether episode info is present")
                    }
                    putJsonObject("duration_ms") {
                        put("type", "number")
                        put("description", "Duration in milliseconds")
                    }
                    putJsonObject("source") {
                        put("type", "string")
                        put("description", "Source type")
                    }
                },
                required = emptyList()
            )
        ) { request ->
            val hasSeason = request.arguments?.get("has_season")?.jsonPrimitive?.booleanOrNull ?: false
            val hasEpisode = request.arguments?.get("has_episode")?.jsonPrimitive?.booleanOrNull ?: false
            val durationMs = request.arguments?.get("duration_ms")?.jsonPrimitive?.longOrNull
            val source = request.arguments?.get("source")?.jsonPrimitive?.content ?: "unknown"
            detectMediaType(hasSeason, hasEpisode, durationMs, source)
        }
    }

    private fun getRawMediaMetadataSchema(): CallToolResult {
        val schema = buildJsonObject {
            put("type", "RawMediaMetadata")
            put("description", "Canonical media type produced by all pipelines")
            putJsonObject("fields") {
                putJsonObject("originalTitle") {
                    put("type", "String")
                    put("description", "Raw title from source (NO cleaning)")
                    put("required", true)
                }
                putJsonObject("mediaType") {
                    put("type", "MediaType")
                    put("enum", JsonArray(listOf("MOVIE", "SERIES_EPISODE", "LIVE", "CLIP", "AUDIOBOOK_CHAPTER", "UNKNOWN").map { JsonPrimitive(it) }))
                    put("default", "UNKNOWN")
                }
                putJsonObject("year") {
                    put("type", "Int?")
                    put("description", "Release year if available")
                }
                putJsonObject("season") {
                    put("type", "Int?")
                    put("description", "Season number for episodes")
                }
                putJsonObject("episode") {
                    put("type", "Int?")
                    put("description", "Episode number for episodes")
                }
                putJsonObject("durationMs") {
                    put("type", "Long?")
                    put("description", "Duration in MILLISECONDS (v2 standard)")
                }
                putJsonObject("sourceType") {
                    put("type", "SourceType")
                    put("enum", JsonArray(listOf("XTREAM", "TELEGRAM", "IO", "AUDIOBOOK").map { JsonPrimitive(it) }))
                }
                putJsonObject("sourceId") {
                    put("type", "String")
                    put("description", "Stable unique ID within pipeline")
                }
                putJsonObject("sourceLabel") {
                    put("type", "String")
                    put("description", "Human-readable source label for UI")
                }
                putJsonObject("poster") {
                    put("type", "ImageRef?")
                    put("description", "Portrait poster image (~2:3)")
                }
                putJsonObject("backdrop") {
                    put("type", "ImageRef?")
                    put("description", "Landscape backdrop (~16:9)")
                }
                putJsonObject("thumbnail") {
                    put("type", "ImageRef?")
                    put("description", "Small preview thumbnail")
                }
                putJsonObject("externalIds") {
                    put("type", "Map<String, String>")
                    put("description", "External IDs (e.g., tmdb=12345)")
                }
            }
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(schema))),
            isError = false
        )
    }

    private fun convertXtreamVodToRaw(vodJson: String): CallToolResult {
        return try {
            val vod = json.parseToJsonElement(vodJson).jsonObject
            
            val streamId = vod["stream_id"]?.jsonPrimitive?.content 
                ?: vod["vod_id"]?.jsonPrimitive?.content 
                ?: vod["id"]?.jsonPrimitive?.content 
                ?: "unknown"
            
            val name = vod["name"]?.jsonPrimitive?.content 
                ?: vod["title"]?.jsonPrimitive?.content 
                ?: "Untitled"
            
            val streamIcon = vod["stream_icon"]?.jsonPrimitive?.content
                ?: vod["cover"]?.jsonPrimitive?.content
            
            val rating = vod["rating"]?.jsonPrimitive?.content
            val tmdbId = vod["tmdb"]?.jsonPrimitive?.content
            
            val rawMedia = buildJsonObject {
                put("originalTitle", name)
                put("mediaType", "MOVIE")
                put("year", JsonNull) // Extracted by normalizer from title
                put("season", JsonNull)
                put("episode", JsonNull)
                put("durationMs", JsonNull) // Not in list API
                put("sourceType", "XTREAM")
                put("sourceId", "xtream_vod_$streamId")
                put("sourceLabel", "Xtream VOD")
                if (streamIcon != null) {
                    putJsonObject("poster") {
                        put("type", "URL")
                        put("url", streamIcon)
                    }
                }
                putJsonObject("externalIds") {
                    if (tmdbId != null) put("tmdb", tmdbId)
                }
                putJsonObject("_mapping") {
                    put("stream_id → sourceId", "xtream_vod_$streamId")
                    put("name → originalTitle", name)
                    put("stream_icon → poster", streamIcon ?: "null")
                    put("tmdb → externalIds.tmdb", tmdbId ?: "null")
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(rawMedia))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "Parse failed: ${e.message}"}""")),
                isError = true
            )
        }
    }

    private fun convertXtreamChannelToRaw(channelJson: String): CallToolResult {
        return try {
            val channel = json.parseToJsonElement(channelJson).jsonObject
            
            val streamId = channel["stream_id"]?.jsonPrimitive?.content 
                ?: channel["id"]?.jsonPrimitive?.content 
                ?: "unknown"
            
            val name = channel["name"]?.jsonPrimitive?.content ?: "Untitled"
            val streamIcon = channel["stream_icon"]?.jsonPrimitive?.content
            val epgChannelId = channel["epg_channel_id"]?.jsonPrimitive?.content
            
            val rawMedia = buildJsonObject {
                put("originalTitle", name)
                put("mediaType", "LIVE")
                put("year", JsonNull)
                put("season", JsonNull)
                put("episode", JsonNull)
                put("durationMs", JsonNull) // Live = no duration
                put("sourceType", "XTREAM")
                put("sourceId", "xtream_live_$streamId")
                put("sourceLabel", "Xtream Live")
                if (streamIcon != null) {
                    putJsonObject("thumbnail") {
                        put("type", "URL")
                        put("url", streamIcon)
                    }
                }
                putJsonObject("externalIds") {
                    if (epgChannelId != null) put("epg_channel", epgChannelId)
                }
                putJsonObject("_mapping") {
                    put("stream_id → sourceId", "xtream_live_$streamId")
                    put("name → originalTitle", name)
                    put("stream_icon → thumbnail", streamIcon ?: "null")
                    put("epg_channel_id → externalIds.epg_channel", epgChannelId ?: "null")
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(rawMedia))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "Parse failed: ${e.message}"}""")),
                isError = true
            )
        }
    }

    private fun convertTelegramToRaw(messageJson: String): CallToolResult {
        return try {
            val msg = json.parseToJsonElement(messageJson).jsonObject
            val content = msg["content"]?.jsonObject
            val contentType = content?.get("type")?.jsonPrimitive?.content ?: "Unknown"
            
            val messageId = msg["id"]?.jsonPrimitive?.long ?: 0
            val chatId = msg["chatId"]?.jsonPrimitive?.long ?: 0
            val caption = msg["caption"]?.jsonPrimitive?.content
            
            // Determine media type from content
            val mediaType = when (contentType) {
                "Video" -> {
                    val duration = content?.get("duration")?.jsonPrimitive?.int ?: 0
                    if (duration > 3600) "MOVIE" else "CLIP"
                }
                "Audio" -> "AUDIOBOOK_CHAPTER"
                else -> "UNKNOWN"
            }
            
            // Duration in ms
            val durationMs = content?.get("duration")?.jsonPrimitive?.int?.let { it * 1000L }
            
            // Filename from content
            val fileName = content?.get("fileName")?.jsonPrimitive?.content
            
            val rawMedia = buildJsonObject {
                put("originalTitle", fileName ?: caption ?: "Telegram Media $messageId")
                put("mediaType", mediaType)
                put("year", JsonNull) // Extracted by normalizer
                put("season", JsonNull)
                put("episode", JsonNull)
                if (durationMs != null) put("durationMs", durationMs)
                put("sourceType", "TELEGRAM")
                put("sourceId", "tg_${chatId}_$messageId")
                put("sourceLabel", "Telegram")
                putJsonObject("externalIds") {
                    put("telegram_msg", messageId.toString())
                    put("telegram_chat", chatId.toString())
                }
                putJsonObject("_mapping") {
                    put("chatId + id → sourceId", "tg_${chatId}_$messageId")
                    put("fileName/caption → originalTitle", fileName ?: caption ?: "fallback")
                    put("content.duration * 1000 → durationMs", durationMs?.toString() ?: "null")
                    put("content.type → mediaType", "$contentType → $mediaType")
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(rawMedia))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "Parse failed: ${e.message}"}""")),
                isError = true
            )
        }
    }

    private fun parseTitle(title: String): CallToolResult {
        // Simple regex patterns for common formats
        val yearPattern = """[.\s\-_\(]*((?:19|20)\d{2})[.\s\-_\)]*""".toRegex()
        val seasonEpisodePattern = """[Ss](\d{1,2})[Ee](\d{1,2})""".toRegex()
        val resolutionPattern = """(720p|1080p|2160p|4K|UHD)""".toRegex(RegexOption.IGNORE_CASE)
        val qualityPattern = """(WEB-DL|WEBRip|BluRay|BDRip|HDRip|HDTV|DVDRip)""".toRegex(RegexOption.IGNORE_CASE)
        val codecPattern = """(x264|x265|HEVC|H\.?264|H\.?265|AVC)""".toRegex(RegexOption.IGNORE_CASE)
        
        val yearMatch = yearPattern.find(title)
        val seMatch = seasonEpisodePattern.find(title)
        val resMatch = resolutionPattern.find(title)
        val qualityMatch = qualityPattern.find(title)
        val codecMatch = codecPattern.find(title)
        
        val result = buildJsonObject {
            put("input", title)
            putJsonObject("extracted") {
                put("year", yearMatch?.groupValues?.get(1))
                if (seMatch != null) {
                    put("season", seMatch.groupValues[1].toIntOrNull())
                    put("episode", seMatch.groupValues[2].toIntOrNull())
                }
                put("resolution", resMatch?.value)
                put("quality", qualityMatch?.value)
                put("codec", codecMatch?.value)
            }
            putJsonObject("cleaned_title") {
                // Remove extracted parts for clean title
                var clean = title
                    .replace(yearPattern, " ")
                    .replace(seasonEpisodePattern, " ")
                    .replace(resolutionPattern, " ")
                    .replace(qualityPattern, " ")
                    .replace(codecPattern, " ")
                    .replace("""[.\-_]""".toRegex(), " ")
                    .replace("""\s+""".toRegex(), " ")
                    .trim()
                put("value", clean)
            }
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(result))),
            isError = false
        )
    }

    private fun detectMediaType(
        hasSeason: Boolean,
        hasEpisode: Boolean,
        durationMs: Long?,
        source: String
    ): CallToolResult {
        val (mediaType, reasoning) = when {
            source.contains("live", ignoreCase = true) -> "LIVE" to "Source indicates live stream"
            hasSeason && hasEpisode -> "SERIES_EPISODE" to "Has both season and episode info"
            hasSeason || hasEpisode -> "SERIES_EPISODE" to "Has partial season/episode info"
            durationMs != null && durationMs < 600_000 -> "CLIP" to "Duration < 10 minutes"
            durationMs != null && durationMs > 3_600_000 -> "MOVIE" to "Duration > 1 hour"
            source.contains("audiobook", ignoreCase = true) -> "AUDIOBOOK_CHAPTER" to "Source indicates audiobook"
            else -> "UNKNOWN" to "Insufficient data to determine type"
        }

        val result = buildJsonObject {
            put("mediaType", mediaType)
            put("reasoning", reasoning)
            putJsonObject("input") {
                put("hasSeason", hasSeason)
                put("hasEpisode", hasEpisode)
                put("durationMs", durationMs)
                put("source", source)
            }
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(result))),
            isError = false
        )
    }
}
