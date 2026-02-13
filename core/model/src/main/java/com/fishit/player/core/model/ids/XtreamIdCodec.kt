package com.fishit.player.core.model.ids

/**
 * XtreamIdCodec - Single Source of Truth for Xtream Source ID Formatting and Parsing.
 *
 * **Contract:**
 * - ONE format per content type (no variations, no whitespace)
 * - All Xtream source IDs MUST be created via this codec
 * - All Xtream source ID parsing MUST go through this codec
 *
 * **Formats (CANONICAL - NO EXCEPTIONS):**
 * - VOD:     `xtream:vod:{vodId}`
 * - Series:  `xtream:series:{seriesId}`
 * - Episode: `xtream:episode:{episodeId}` (preferred when ID available)
 * - Episode: `xtream:episode:series:{seriesId}:s{season}:e{episode}` (fallback)
 * - Live:    `xtream:live:{channelId}`
 *
 * **Why This Exists:**
 * 1. Prevents "xtream:vod: 123" whitespace bugs
 * 2. Enables type-safe ID handling across modules
 * 3. Provides parser for playback/detail navigation
 * 4. Foundation for generator-based source addition
 *
 * **Location:**
 * This codec lives in `core/model` because source IDs are part of the core data model
 * (used by `RawMediaMetadata.sourceId` and `NX_Work` entities). All modules can safely
 * depend on `core/model` to use this codec.
 *
 * **Migration Note:**
 * Existing IDs in database are already in correct format.
 * This codec enforces the format going forward.
 *
 * @see XtreamParsedSourceId for the parsed type hierarchy
 * @see MEDIA_NORMALIZATION_CONTRACT.md Section 2.1.1
 */
object XtreamIdCodec {
    /** Xtream source type prefix */
    const val PREFIX = "xtream"

    /** Legacy series-episode format: S{season}E{episode} (case-insensitive) */
    private val LEGACY_SERIES_EPISODE_REGEX = Regex("""[Ss](\d+)[Ee](\d+)""")

    // =========================================================================
    // Format Functions (String Generation)
    // =========================================================================

    /**
     * Format VOD source ID.
     *
     * @param vodId The Xtream VOD stream ID (non-zero Long, may be negative for test content)
     * @return Canonical source ID: `xtream:vod:{vodId}`
     */
    fun vod(vodId: Long): String {
        require(vodId != 0L) { "VOD ID must not be zero, got: $vodId" }
        return "$PREFIX:vod:$vodId"
    }

    /**
     * Format VOD source ID from typed wrapper.
     */
    fun vod(id: XtreamVodId): String = vod(id.id)

    /**
     * Format VOD source ID from Int (common in Xtream DTOs).
     */
    fun vod(vodId: Int): String = vod(vodId.toLong())

    /**
     * Format Series source ID.
     *
     * @param seriesId The Xtream series ID (non-zero Long, may be negative for test content)
     * @return Canonical source ID: `xtream:series:{seriesId}`
     */
    fun series(seriesId: Long): String {
        require(seriesId != 0L) { "Series ID must not be zero, got: $seriesId" }
        return "$PREFIX:series:$seriesId"
    }

    /**
     * Format Series source ID from typed wrapper.
     */
    fun series(id: XtreamSeriesId): String = series(id.id)

    /**
     * Format Series source ID from Int (common in Xtream DTOs).
     */
    fun series(seriesId: Int): String = series(seriesId.toLong())

    /**
     * Format Episode source ID with stable episode ID (PREFERRED).
     *
     * Use this when the Xtream API provides a unique episode stream ID.
     *
     * @param episodeId The Xtream episode stream ID (non-zero Long, may be negative for test content)
     * @return Canonical source ID: `xtream:episode:{episodeId}`
     */
    fun episode(episodeId: Long): String {
        require(episodeId != 0L) { "Episode ID must not be zero, got: $episodeId" }
        return "$PREFIX:episode:$episodeId"
    }

    /**
     * Format Episode source ID from typed wrapper.
     */
    fun episode(id: XtreamEpisodeId): String = episode(id.id)

    /**
     * Format Episode source ID from Int (common in Xtream DTOs).
     */
    fun episode(episodeId: Int): String = episode(episodeId.toLong())

    /**
     * Format Episode source ID with composite identity (FALLBACK).
     *
     * Use this only when no unique episode stream ID is available.
     * The stable [episode] format is preferred.
     *
     * @param seriesId The parent series ID (non-zero, may be negative)
     * @param season Season number (0-based or 1-based)
     * @param episodeNum Episode number within season (0-based or 1-based)
     * @return Canonical source ID: `xtream:episode:series:{seriesId}:s{season}:e{episode}`
     */
    fun episodeComposite(seriesId: Long, season: Int, episodeNum: Int): String {
        require(seriesId != 0L) { "Series ID must not be zero, got: $seriesId" }
        require(season >= 0) { "Season must be non-negative, got: $season" }
        require(episodeNum >= 0) { "Episode must be non-negative, got: $episodeNum" }
        return "$PREFIX:episode:series:$seriesId:s$season:e$episodeNum"
    }

    /**
     * Format Episode source ID with composite identity from Int seriesId.
     */
    fun episodeComposite(seriesId: Int, season: Int, episodeNum: Int): String =
        episodeComposite(seriesId.toLong(), season, episodeNum)

    /**
     * Format Live channel source ID.
     *
     * @param channelId The Xtream live stream ID (non-zero Long, may be negative for test content)
     * @return Canonical source ID: `xtream:live:{channelId}`
     */
    fun live(channelId: Long): String {
        require(channelId != 0L) { "Channel ID must not be zero, got: $channelId" }
        return "$PREFIX:live:$channelId"
    }

    /**
     * Format Live channel source ID from typed wrapper.
     */
    fun live(id: XtreamChannelId): String = live(id.id)

    /**
     * Format Live channel source ID from Int (common in Xtream DTOs).
     */
    fun live(channelId: Int): String = live(channelId.toLong())

    // =========================================================================
    // Nullable String Overloads (for NX Repository Mapping)
    // =========================================================================

    /**
     * Format VOD source ID from nullable String ID (extracted from sourceKey).
     *
     * This overload exists for NX repository mapping where IDs are extracted
     * from sourceKey strings and may be null or invalid. Uses "unknown" fallback
     * for invalid inputs to maintain backward compatibility.
     *
     * **Note:** Prefer the typed overloads (Long, Int, XtreamVodId) when possible.
     *
     * @param vodIdStr Nullable string representation of VOD ID
     * @return Canonical source ID, or fallback `xtream:vod:unknown` if invalid
     */
    fun vodOrUnknown(vodIdStr: String?): String {
        val vodId = vodIdStr?.toLongOrNull()
        return if (vodId != null && vodId != 0L) vod(vodId) else "$PREFIX:vod:unknown"
    }

    /**
     * Format Series source ID from nullable String ID (extracted from sourceKey).
     *
     * @param seriesIdStr Nullable string representation of Series ID
     * @return Canonical source ID, or fallback `xtream:series:unknown` if invalid
     */
    fun seriesOrUnknown(seriesIdStr: String?): String {
        val seriesId = seriesIdStr?.toLongOrNull()
        return if (seriesId != null && seriesId != 0L) series(seriesId) else "$PREFIX:series:unknown"
    }

    /**
     * Format Episode source ID from nullable String ID (extracted from sourceKey).
     *
     * @param episodeIdStr Nullable string representation of Episode ID
     * @return Canonical source ID, or fallback `xtream:episode:unknown` if invalid
     */
    fun episodeOrUnknown(episodeIdStr: String?): String {
        val episodeId = episodeIdStr?.toLongOrNull()
        return if (episodeId != null && episodeId != 0L) episode(episodeId) else "$PREFIX:episode:unknown"
    }

    /**
     * Format Episode composite source ID from nullable String seriesId.
     *
     * Handles the legacy pattern where episode info is extracted from sourceKey
     * and may contain invalid values.
     *
     * @param seriesIdStr Nullable string representation of Series ID
     * @param season Season number (defaults to 0 if null)
     * @param episodeNum Episode number (defaults to 0 if null)
     * @return Canonical source ID, or fallback with "unknown" seriesId if invalid
     */
    fun episodeCompositeOrUnknown(seriesIdStr: String?, season: Int?, episodeNum: Int?): String {
        val seriesId = seriesIdStr?.toLongOrNull()
        val s = season ?: 0
        val e = episodeNum ?: 0
        return if (seriesId != null && seriesId != 0L) {
            episodeComposite(seriesId, s, e)
        } else {
            // Fallback format matching legacy behavior
            "$PREFIX:episode:unknown:$s:$e"
        }
    }

    /**
     * Format Live channel source ID from nullable String ID (extracted from sourceKey).
     *
     * @param channelIdStr Nullable string representation of Channel ID
     * @return Canonical source ID, or fallback `xtream:live:unknown` if invalid
     */
    fun liveOrUnknown(channelIdStr: String?): String {
        val channelId = channelIdStr?.toLongOrNull()
        return if (channelId != null && channelId != 0L) live(channelId) else "$PREFIX:live:unknown"
    }

    // =========================================================================
    // Format from Parsed Type
    // =========================================================================

    /**
     * Format any parsed source ID back to canonical string form.
     *
     * Useful for round-trip testing and persistence.
     *
     * @param parsed The parsed source ID
     * @return Canonical source ID string
     */
    fun format(parsed: XtreamParsedSourceId): String = when (parsed) {
        is XtreamParsedSourceId.Vod -> vod(parsed.vodId)
        is XtreamParsedSourceId.Series -> series(parsed.seriesId)
        is XtreamParsedSourceId.Episode -> episode(parsed.episodeId)
        is XtreamParsedSourceId.EpisodeComposite -> episodeComposite(
            parsed.seriesId,
            parsed.season,
            parsed.episode,
        )
        is XtreamParsedSourceId.Live -> live(parsed.channelId)
    }

    // =========================================================================
    // Parse Function (String → Typed)
    // =========================================================================

    /**
     * Parse a source ID string to typed representation.
     *
     * Handles ALL Xtream identity formats:
     * - Legacy: `xtream:vod:123`, `xtream:series:456`, `xtream:episode:789`
     * - Legacy composite: `xtream:episode:series:{seriesId}:s{season}:e{episode}`
     * - Legacy composite (numeric): `xtream:episode:{seriesId}:{season}:{episode}`
     * - NX sourceKey: `src:xtream:{account}:{kind}:{...}` (strips prefix, re-parses)
     *
     * Returns null if:
     * - String is not a recognized Xtream format
     * - Format is invalid
     * - IDs are zero (not valid)
     *
     * @param sourceId Raw source ID or NX sourceKey string
     * @return Parsed source ID or null if invalid
     */
    fun parse(sourceId: String): XtreamParsedSourceId? {
        // NX format: src:xtream:{account}:{kind}:{...}
        // Strip prefix and re-parse as xtream:{kind}:{...}
        // IMPORTANT: accountKey may contain colons (e.g., user:pass@host:8080),
        // so we can't split by ":" naively. Instead, search for known content-type markers.
        if (sourceId.startsWith("src:$PREFIX:") || sourceId.startsWith("src:xc:")) {
            val contentMarkers = listOf(":vod:", ":live:", ":series:", ":episode:")
            for (marker in contentMarkers) {
                val markerIdx = sourceId.indexOf(marker)
                if (markerIdx >= 0) {
                    // Re-parse as xtream:{kind}:{...} from the marker position
                    return parse("$PREFIX${sourceId.substring(markerIdx)}")
                }
            }
            return null
        }

        if (!sourceId.startsWith("$PREFIX:")) return null

        val parts = sourceId.split(':')
        if (parts.size < 3) return null

        return when (parts[1]) {
            "vod" -> parts.getOrNull(2)?.toLongOrNull()
                ?.takeIf { it != 0L }
                ?.let { XtreamParsedSourceId.Vod(it) }

            "series" -> parseSeries(parts)

            "live" -> parts.getOrNull(2)?.toLongOrNull()
                ?.takeIf { it != 0L }
                ?.let { XtreamParsedSourceId.Live(it) }

            "episode" -> parseEpisode(parts)

            else -> null
        }
    }

    /**
     * Parse episode source ID.
     *
     * Handles all formats:
     * - `xtream:episode:{episodeId}` (stable, direct ID)
     * - `xtream:episode:series:{seriesId}:s{season}:e{episode}` (XtreamIdCodec composite)
     * - `xtream:episode:{seriesId}:{season}:{episode}` (legacy composite)
     * - `xtream:episode:{seriesId}_{season}_{episode}` (NX underscore legacy)
     */
    private fun parseEpisode(parts: List<String>): XtreamParsedSourceId? {
        // Format A: xtream:episode:{episodeId}
        if (parts.size == 3) {
            // Check for underscore legacy format first: {seriesId}_{season}_{episode}
            if (parts[2].contains('_')) {
                val subParts = parts[2].split('_')
                if (subParts.size == 3) {
                    val seriesId = subParts[0].toLongOrNull()?.takeIf { it != 0L } ?: return null
                    val season = subParts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                    val episode = subParts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                    return XtreamParsedSourceId.EpisodeComposite(seriesId, season, episode)
                }
            }
            return parts[2].toLongOrNull()
                ?.takeIf { it != 0L }
                ?.let { XtreamParsedSourceId.Episode(it) }
        }

        // Format B: xtream:episode:series:{seriesId}:s{season}:e{episode}
        if (parts.size == 6 && parts[2] == "series") {
            val seriesId = parts[3].toLongOrNull()?.takeIf { it != 0L } ?: return null
            val season = parts[4].removePrefix("s").toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val episode = parts[5].removePrefix("e").toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return XtreamParsedSourceId.EpisodeComposite(seriesId, season, episode)
        }

        // Format C (legacy): xtream:episode:{seriesId}:{season}:{episode}
        if (parts.size == 5) {
            val seriesId = parts[2].toLongOrNull()?.takeIf { it != 0L } ?: return null
            val season = parts[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val episode = parts[4].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return XtreamParsedSourceId.EpisodeComposite(seriesId, season, episode)
        }

        return null
    }

    /**
     * Parse series source ID.
     *
     * Handles:
     * - `xtream:series:{seriesId}` (standard)
     * - `xtream:series:{seriesId}:S{season}E{episode}` (legacy episode-on-series)
     *
     * The legacy format encodes an episode reference on a series sourceId.
     * Returns [XtreamParsedSourceId.EpisodeComposite] for the legacy format.
     */
    private fun parseSeries(parts: List<String>): XtreamParsedSourceId? {
        val seriesId = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it != 0L } ?: return null

        // Legacy format: xtream:series:{seriesId}:S{season}E{episode}
        if (parts.size == 4) {
            val sxex = LEGACY_SERIES_EPISODE_REGEX.matchEntire(parts[3])
            if (sxex != null) {
                val season = sxex.groupValues[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                val episode = sxex.groupValues[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                return XtreamParsedSourceId.EpisodeComposite(seriesId, season, episode)
            }
        }

        return XtreamParsedSourceId.Series(seriesId)
    }

    // =========================================================================
    // Validation Helpers
    // =========================================================================

    /**
     * Check if a source ID string is a valid Xtream source ID.
     */
    fun isValid(sourceId: String): Boolean = parse(sourceId) != null

    /**
     * Check if a source ID is for VOD content.
     */
    fun isVod(sourceId: String): Boolean = parse(sourceId) is XtreamParsedSourceId.Vod

    /**
     * Check if a source ID is for Series content.
     */
    fun isSeries(sourceId: String): Boolean = parse(sourceId) is XtreamParsedSourceId.Series

    /**
     * Check if a source ID is for Episode content.
     */
    fun isEpisode(sourceId: String): Boolean {
        val parsed = parse(sourceId)
        return parsed is XtreamParsedSourceId.Episode || parsed is XtreamParsedSourceId.EpisodeComposite
    }

    /**
     * Check if a source ID is for Live content.
     */
    fun isLive(sourceId: String): Boolean = parse(sourceId) is XtreamParsedSourceId.Live

    // =========================================================================
    // Extraction Helpers (for Playback/UI)
    // =========================================================================

    /**
     * Extract VOD ID from source ID string.
     *
     * @return VOD ID or null if not a valid VOD source ID
     */
    fun extractVodId(sourceId: String): Long? =
        (parse(sourceId) as? XtreamParsedSourceId.Vod)?.vodId

    /**
     * Extract Series ID from source ID string.
     *
     * @return Series ID or null if not a valid Series source ID
     */
    fun extractSeriesId(sourceId: String): Long? =
        (parse(sourceId) as? XtreamParsedSourceId.Series)?.seriesId

    /**
     * Extract Episode stream ID from source ID string.
     *
     * Returns the direct episode ID if available (preferred format).
     * Returns null for composite format (use [extractEpisodeComposite] instead).
     *
     * @return Episode stream ID or null
     */
    fun extractEpisodeId(sourceId: String): Long? =
        (parse(sourceId) as? XtreamParsedSourceId.Episode)?.episodeId

    /**
     * Extract composite episode info from source ID string.
     *
     * @return Triple of (seriesId, season, episode) or null
     */
    fun extractEpisodeComposite(sourceId: String): Triple<Long, Int, Int>? {
        val parsed = parse(sourceId) as? XtreamParsedSourceId.EpisodeComposite ?: return null
        return Triple(parsed.seriesId, parsed.season, parsed.episode)
    }

    /**
     * Extract Channel ID from source ID string.
     *
     * @return Channel ID or null if not a valid Live source ID
     */
    fun extractChannelId(sourceId: String): Long? =
        (parse(sourceId) as? XtreamParsedSourceId.Live)?.channelId

    // =========================================================================
    // Content Type Detection (SSOT — used by SourceSelection, PlayerNavViewModel)
    // =========================================================================

    /**
     * Xtream content types for lightweight sourceKey pattern matching.
     *
     * This is the SSOT enum for content type detection from sourceKey strings.
     * Use [detectContentType] to resolve from a sourceKey.
     */
    enum class ContentType {
        VOD, LIVE, SERIES, EPISODE, UNKNOWN
    }

    /**
     * Lightweight content type detection from any sourceKey format.
     *
     * Unlike [parse], this does NOT validate IDs or parse composite formats.
     * It simply detects the content type segment in the sourceKey string.
     *
     * Supports both formats:
     * - Legacy: `xtream:vod:123`, `xtream:live:456`
     * - NX: `src:xtream:account:vod:123`, `src:xtream:account:live:456`
     *
     * @param sourceKey The full source ID or sourceKey string
     * @return Detected content type, or [ContentType.UNKNOWN] if not recognized
     */
    fun detectContentType(sourceKey: String): ContentType = when {
        sourceKey.contains(":vod:") -> ContentType.VOD
        sourceKey.contains(":live:") -> ContentType.LIVE
        sourceKey.contains(":episode:") -> ContentType.EPISODE
        sourceKey.contains(":series:") -> ContentType.SERIES
        else -> ContentType.UNKNOWN
    }

    /**
     * Extract the simple numeric ID segment after the content type marker.
     *
     * Returns the FIRST segment after the marker, stopping at the next colon.
     * For composite episode formats, returns only the first ID part.
     *
     * Works for both formats:
     * - Legacy `xtream:vod:123` → `"123"`
     * - NX `src:xtream:account:vod:123` → `"123"`
     * - Composite `xtream:episode:series:456:s1:e3` → `"series"` (use [parse] for composites)
     *
     * For full parsing with type safety, prefer [parse] and the specific extract* methods.
     *
     * @param sourceKey The full source ID or sourceKey string
     * @param contentType The content type to extract ID for
     * @return The extracted first ID segment, or null if marker not found
     */
    fun extractSimpleId(sourceKey: String, contentType: ContentType): String? {
        val marker = when (contentType) {
            ContentType.VOD -> ":vod:"
            ContentType.LIVE -> ":live:"
            ContentType.SERIES -> ":series:"
            ContentType.EPISODE -> ":episode:"
            ContentType.UNKNOWN -> return null
        }
        val idx = sourceKey.indexOf(marker)
        if (idx < 0) return null
        return sourceKey.substring(idx + marker.length).takeWhile { it != ':' }.ifEmpty { null }
    }
}
