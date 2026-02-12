/**
 * Centralized sourceKey parsing utilities for NX entity mapping.
 *
 * **SSOT for ALL sourceKey parsing to eliminate duplication across repository files.**
 *
 * This file centralizes the parsing logic that was previously duplicated in:
 * - NxCatalogWriter.kt (extractNumericId)
 * - NxXtreamLiveRepositoryImpl.kt (extractStreamIdFromSourceKey)
 * - NxXtreamSeriesIndexRepository.kt (extractEpisodeIdFromSourceKey, extractAccountKeyFromSourceKey)
 * - NxTelegramMediaRepositoryImpl.kt (extractChatIdFromSourceKey, extractMessageIdFromSourceKey)
 * - NxCanonicalMediaRepositoryImpl.kt (inline split logic)
 * - NxWorkSourceRefRepositoryImpl.kt (inline split logic)
 * - NxWorkSourceRefDiagnosticsImpl.kt (inline split logic)
 *
 * **Architecture:**
 * - **ONLY** SourceKey format supported: `src:{sourceType}:{accountKey}:{itemKind}:{itemKey}`
 *   - Example: `src:xtream:myserver:vod:12345`
 *   - Example: `src:telegram:myaccount:file:123456789:987654321`
 * - Legacy sourceId format is **NOT** supported (removed to avoid conflicts and confusion)
 *
 * **Usage:**
 * ```kotlin
 * // Parse full sourceKey
 * val parsed = SourceKeyParser.parse("src:xtream:myserver:vod:12345")
 * val vodId = parsed?.itemKey  // "12345"
 * val account = parsed?.accountKey  // "myserver"
 *
 * // Extract specific components
 * val streamId = SourceKeyParser.extractNumericItemKey("src:xtream:myserver:live:456")
 * val chatId = SourceKeyParser.extractTelegramChatId("src:telegram:account:file:123:456")
 * ```
 *
 * **Expected Impact (Issue #669 - Following PR #671 Pattern):**
 * - Eliminates ~100-150 lines of duplicated split/parsing logic
 * - Reduces CC in 7+ mapper/repository files
 * - Creates single source of truth for sourceKey parsing
 * - Improves testability (test once, use everywhere)
 * - Removes legacy format support to avoid conflicts and false expectations
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.SourceType

/**
 * Parsed representation of a sourceKey.
 *
 * SourceKey format: `src:{sourceType}:{accountKey}:{itemKind}:{itemKey}`
 *
 * @property sourceType The source type (e.g., "xtream", "telegram", "io")
 * @property accountKey The account identifier within the source
 * @property itemKind The type of item (e.g., "vod", "series", "episode", "live", "file")
 * @property itemKey The item-specific identifier (format varies by source/kind)
 */
data class ParsedSourceKey(
    val sourceType: String,
    val accountKey: String,
    val itemKind: String,
    val itemKey: String,
)

/**
 * Centralized sourceKey parsing utilities.
 *
 * **Eliminates ~100-150 lines of duplicated parsing logic across 7+ files.**
 *
 * This object provides:
 * 1. Full sourceKey parsing into structured components
 * 2. Targeted extraction helpers for common patterns
 * 3. **ONLY** supports sourceKey format (legacy sourceId removed)
 * 4. Null-safe extraction with clear fallback behavior
 */
object SourceKeyParser {

    /**
     * Pattern for XtreamIdCodec composite episode itemKey: `series:{seriesId}:s{season}:e{episode}`
     * Captures: group(1)=seriesId (may be negative), group(2)=season, group(3)=episode
     */
    private val EPISODE_CODEC_PATTERN = Regex("""^series:(-?\d+):s(\d+):e(\d+)$""")

    // =========================================================================
    // Builder Functions
    // =========================================================================

    /**
     * Build a sourceKey from components.
     *
     * SourceKey format: `src:{sourceType}:{accountKey}:{sourceId}`
     * where sourceId contains `{itemKind}:{itemKey}`
     *
     * Examples:
     * - `src:xtream:user@iptv.server:vod:12345`
     * - `src:telegram:+491234567890:file:123456789:987654321`
     *
     * @param sourceType The source type enum
     * @param accountKey The account identifier (readable format per NX_SSOT_CONTRACT)
     * @param sourceId The item-specific identifier containing kind and key
     * @return Complete sourceKey string
     */
    fun buildSourceKey(sourceType: SourceType, accountKey: String, sourceId: String): String {
        return "src:${sourceType.name.lowercase()}:$accountKey:$sourceId"
    }

    // =========================================================================
    // Parser Functions
    // =========================================================================

    /**
     * Parse a sourceKey into structured components.
     *
     * **ONLY** supports sourceKey format: `src:{sourceType}:{accountKey}:{itemKind}:{itemKey...}`
     *
     * Legacy sourceId format is **NOT** supported (removed to avoid conflicts).
     *
     * @param sourceKey The source key string to parse (must start with "src:")
     * @return Parsed components or null if format is invalid or not sourceKey format
     */
    fun parse(sourceKey: String?): ParsedSourceKey? {
        if (sourceKey.isNullOrBlank()) return null

        val parts = sourceKey.split(":")
        
        // ONLY handle sourceKey format: src:{sourceType}:{accountKey}:{itemKind}:{itemKey...}
        if (parts[0] == "src" && parts.size >= 5) {
            return ParsedSourceKey(
                sourceType = parts[1],
                accountKey = parts[2],
                itemKind = parts[3],
                itemKey = parts.drop(4).joinToString(":"),
            )
        }
        
        return null
    }

    // =========================================================================
    // Generic Extraction Helpers
    // =========================================================================

    /**
     * Extract sourceType from sourceKey.
     *
     * Examples:
     * - `src:xtream:account:vod:123` → "xtream"
     * - `xtream:vod:123` → "xtream"
     *
     * @param sourceKey The source key string
     * @return Source type or null if invalid
     */
    fun extractSourceType(sourceKey: String?): String? = parse(sourceKey)?.sourceType

    /**
     * Extract accountKey from sourceKey.
     *
     * Example:
     * - `src:xtream:myserver:vod:123` → "myserver"
     *
     * @param sourceKey The source key string (must be sourceKey format)
     * @return Account key or "unknown" if not valid sourceKey format
     */
    fun extractAccountKey(sourceKey: String?): String = parse(sourceKey)?.accountKey ?: "unknown"

    /**
     * Extract itemKind from sourceKey.
     *
     * Examples:
     * - `src:xtream:account:vod:123` → "vod"
     * - `src:telegram:account:file:123:456` → "file"
     *
     * @param sourceKey The source key string
     * @return Item kind or null if invalid
     */
    fun extractItemKind(sourceKey: String?): String? = parse(sourceKey)?.itemKind

    /**
     * Extract raw itemKey from sourceKey.
     *
     * The itemKey format varies by source and kind:
     * - Xtream VOD: "12345" (numeric stream ID)
     * - Xtream Episode: "100_1_5" (composite ID: seriesId_season_episode)
     * - Telegram: "123456789:987654321" (chatId:messageId)
     *
     * @param sourceKey The source key string
     * @return Raw item key string or null if invalid
     */
    fun extractItemKey(sourceKey: String?): String? = parse(sourceKey)?.itemKey

    /**
     * Extract numeric item key (common for Xtream VOD/Live/Series).
     *
     * Handles these formats:
     * - SourceKey: `src:xtream:account:vod:12345` → 12345
     * - Already numeric: `12345` → 12345
     *
     * This replaces NxCatalogWriter.extractNumericId().
     *
     * @param sourceKey The source key string
     * @return Numeric ID or null if not a simple numeric key
     */
    fun extractNumericItemKey(sourceKey: String?): Long? {
        if (sourceKey.isNullOrBlank()) return null
        
        // If input is already just a number, return it
        sourceKey.toLongOrNull()?.let { return it }

        // Extract from sourceKey format
        val itemKey = extractItemKey(sourceKey) ?: return null
        return itemKey.toLongOrNull()
    }

    // =========================================================================
    // Xtream-Specific Helpers
    // =========================================================================

    /**
     * Extract Xtream stream ID from sourceKey (for VOD/Live/Series).
     *
     * This replaces NxXtreamLiveRepositoryImpl.extractStreamIdFromSourceKey().
     *
     * @param sourceKey The source key string
     * @return Stream ID string or null
     */
    fun extractXtreamStreamId(sourceKey: String?): String? {
        if (sourceKey.isNullOrBlank()) return null
        
        // Parse as sourceKey
        val parsed = parse(sourceKey) ?: return null
        
        // For simple numeric IDs (VOD, Live, Series)
        if (parsed.itemKey.toLongOrNull() != null) {
            return parsed.itemKey
        }
        
        // For composite episode IDs, extract the first numeric component
        // Format: "100_1_5" → "100"
        if (parsed.itemKind == "episode") {
            return parsed.itemKey.split("_").getOrNull(0)
        }
        
        return null
    }

    /**
     * Extract Xtream episode ID from sourceKey.
     *
     * Handles:
     * 1. Direct episode ID: `src:xtream:account:episode:12345` → 12345
     * 2. Underscore composite: `src:xtream:account:episode:100_1_5` → 5 (episode number)
     * 3. XtreamIdCodec composite: `src:xtream:account:episode:series:100:s1:e5` → 5 (episode number)
     *
     * This replaces NxXtreamSeriesIndexRepository.extractEpisodeIdFromSourceKey().
     *
     * @param sourceKey The source key string
     * @return Episode ID (stream ID or episode number) or null
     */
    fun extractXtreamEpisodeId(sourceKey: String?): Int? {
        if (sourceKey.isNullOrBlank()) return null
        
        // Parse as sourceKey
        val itemKey = extractItemKey(sourceKey) ?: return null
        
        // Try direct numeric ID first
        itemKey.toIntOrNull()?.let { return it }
        
        // Try underscore composite format: seriesId_season_episode
        val underscoreParts = itemKey.split("_")
        if (underscoreParts.size >= 3) {
            return underscoreParts[2].toIntOrNull()
        }
        
        // Try XtreamIdCodec composite format: series:{seriesId}:s{season}:e{episode}
        val codecMatch = EPISODE_CODEC_PATTERN.find(itemKey)
        if (codecMatch != null) {
            return codecMatch.groupValues[3].toIntOrNull()
        }
        
        return null
    }

    /**
     * Extract Xtream series ID from episode sourceKey.
     *
     * Handles:
     * - Underscore composite: `src:xtream:account:episode:100_1_5` → 100
     * - XtreamIdCodec composite: `src:xtream:account:episode:series:100:s1:e5` → 100
     *
     * @param sourceKey The source key string
     * @return Series ID or null
     */
    fun extractXtreamSeriesIdFromEpisode(sourceKey: String?): Int? {
        if (sourceKey.isNullOrBlank()) return null
        
        // Parse as sourceKey
        val itemKey = extractItemKey(sourceKey) ?: return null
        
        // Underscore composite format: seriesId_season_episode
        val underscoreParts = itemKey.split("_")
        if (underscoreParts.size >= 3) {
            return underscoreParts[0].toIntOrNull()
        }
        
        // XtreamIdCodec composite format: series:{seriesId}:s{season}:e{episode}
        val codecMatch = EPISODE_CODEC_PATTERN.find(itemKey)
        if (codecMatch != null) {
            return codecMatch.groupValues[1].toIntOrNull()
        }
        
        return null
    }

    /**
     * Extract full episode info (seriesId, season, episodeNumber) from episode sourceKey.
     *
     * Handles:
     * - Underscore composite: `src:xtream:account:episode:100_1_5` → (100, 1, 5)
     * - XtreamIdCodec composite: `src:xtream:account:episode:series:100:s1:e5` → (100, 1, 5)
     *
     * @param sourceKey The source key string
     * @return Triple of (seriesId, season, episode) or null
     */
    fun extractXtreamEpisodeInfo(sourceKey: String?): EpisodeInfo? {
        if (sourceKey.isNullOrBlank()) return null
        
        val itemKey = extractItemKey(sourceKey) ?: return null
        
        // Underscore composite: seriesId_season_episode
        val underscoreParts = itemKey.split("_")
        if (underscoreParts.size >= 3) {
            val seriesId = underscoreParts[0].toIntOrNull() ?: return null
            val season = underscoreParts[1].toIntOrNull() ?: return null
            val episode = underscoreParts[2].toIntOrNull() ?: return null
            return EpisodeInfo(seriesId, season, episode)
        }
        
        // XtreamIdCodec composite: series:{seriesId}:s{season}:e{episode}
        val codecMatch = EPISODE_CODEC_PATTERN.find(itemKey)
        if (codecMatch != null) {
            val seriesId = codecMatch.groupValues[1].toIntOrNull() ?: return null
            val season = codecMatch.groupValues[2].toIntOrNull() ?: return null
            val episode = codecMatch.groupValues[3].toIntOrNull() ?: return null
            return EpisodeInfo(seriesId, season, episode)
        }
        
        return null
    }

    /**
     * Parsed episode identity components.
     */
    data class EpisodeInfo(
        val seriesId: Int,
        val season: Int,
        val episodeNumber: Int,
    )

    // =========================================================================
    // Telegram-Specific Helpers
    // =========================================================================

    /**
     * Extract Telegram chatId from sourceKey.
     *
     * Format: `src:telegram:account:file:{chatId}:{messageId}`
     *
     * This replaces NxTelegramMediaRepositoryImpl.extractChatIdFromSourceKey().
     *
     * @param sourceKey The source key string
     * @return Chat ID or null
     */
    fun extractTelegramChatId(sourceKey: String?): Long? {
        val itemKey = extractItemKey(sourceKey) ?: return null
        val parts = itemKey.split(":")
        return parts.getOrNull(0)?.toLongOrNull()
    }

    /**
     * Extract Telegram messageId from sourceKey.
     *
     * Format: `src:telegram:account:file:{chatId}:{messageId}`
     *
     * This replaces NxTelegramMediaRepositoryImpl.extractMessageIdFromSourceKey().
     *
     * @param sourceKey The source key string
     * @return Message ID or null
     */
    fun extractTelegramMessageId(sourceKey: String?): Long? {
        val itemKey = extractItemKey(sourceKey) ?: return null
        val parts = itemKey.split(":")
        return parts.getOrNull(1)?.toLongOrNull()
    }

    /**
     * Extract both chatId and messageId from sourceKey.
     *
     * @param sourceKey The source key string
     * @return Pair of (chatId, messageId) or null if invalid
     */
    fun extractTelegramIds(sourceKey: String?): Pair<Long, Long>? {
        val chatId = extractTelegramChatId(sourceKey) ?: return null
        val messageId = extractTelegramMessageId(sourceKey) ?: return null
        return Pair(chatId, messageId)
    }
}
