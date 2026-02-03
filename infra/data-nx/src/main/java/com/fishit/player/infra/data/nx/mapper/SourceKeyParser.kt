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
 * - SourceKey format: `src:{sourceType}:{accountKey}:{itemKind}:{itemKey}`
 *   - Example: `src:xtream:myserver:vod:12345`
 *   - Example: `src:telegram:myaccount:file:123456789:987654321`
 * - SourceId format (legacy): `{sourceType}:{itemKind}:{itemKey}`
 *   - Example: `xtream:vod:12345`
 *   - Example: `telegram:file:123456789:987654321`
 * - Both formats are parsed by this utility
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
 *
 * @see com.fishit.player.core.model.ids.XtreamIdCodec for sourceId formatting/parsing
 */
package com.fishit.player.infra.data.nx.mapper

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
 * 3. Support for both sourceKey (with src: prefix) and legacy sourceId formats
 * 4. Null-safe extraction with clear fallback behavior
 */
object SourceKeyParser {
    /**
     * Parse a sourceKey into structured components.
     *
     * Supports two formats:
     * - Full sourceKey: `src:{sourceType}:{accountKey}:{itemKind}:{itemKey...}`
     * - Legacy sourceId: `{sourceType}:{itemKind}:{itemKey...}`
     *
     * @param sourceKey The source key string to parse
     * @return Parsed components or null if format is invalid
     */
    fun parse(sourceKey: String?): ParsedSourceKey? {
        if (sourceKey.isNullOrBlank()) return null

        val parts = sourceKey.split(":")
        
        // Handle full sourceKey format: src:{sourceType}:{accountKey}:{itemKind}:{itemKey...}
        if (parts[0] == "src" && parts.size >= 5) {
            return ParsedSourceKey(
                sourceType = parts[1],
                accountKey = parts[2],
                itemKind = parts[3],
                itemKey = parts.drop(4).joinToString(":"),
            )
        }
        
        // Handle legacy sourceId format: {sourceType}:{itemKind}:{itemKey...}
        if (parts.size >= 3) {
            return ParsedSourceKey(
                sourceType = parts[0],
                accountKey = "unknown",
                itemKind = parts[1],
                itemKey = parts.drop(2).joinToString(":"),
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
     * Examples:
     * - `src:xtream:myserver:vod:123` → "myserver"
     * - `xtream:vod:123` → "unknown" (legacy format has no account)
     *
     * @param sourceKey The source key string
     * @return Account key or "unknown" if not present
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
     * - Xtream Episode: "series:100:s1:e5" or "100_1_5" (composite ID)
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
     * - Full sourceKey: `src:xtream:account:vod:12345` → 12345
     * - Legacy sourceId: `xtream:vod:12345` → 12345
     * - Composite IDs: `src:xtream:account:episode:series:100:s1:e5` → null (use extractItemKey)
     * - Already numeric: `12345` → 12345
     *
     * This replaces NxCatalogWriter.extractNumericId().
     *
     * @param sourceKey The source key string
     * @return Numeric ID or null if not a simple numeric key
     */
    fun extractNumericItemKey(sourceKey: String?): Long? {
        // If input is already just a number, return it
        sourceKey?.toLongOrNull()?.let { return it }

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
        val parsed = parse(sourceKey) ?: return null
        
        // For simple numeric IDs (VOD, Live, Series)
        if (parsed.itemKey.toLongOrNull() != null) {
            return parsed.itemKey
        }
        
        // For composite episode IDs, extract the first numeric component
        // Format: "series:100:s1:e5" → "100"
        if (parsed.itemKind == "episode" && parsed.itemKey.startsWith("series:")) {
            return parsed.itemKey.split(":").getOrNull(1)
        }
        
        return null
    }

    /**
     * Extract Xtream episode ID from sourceKey.
     *
     * Handles two formats:
     * 1. Direct episode ID: `src:xtream:account:episode:12345` → 12345
     * 2. Composite format: `src:xtream:account:episode:100_1_5` → 5 (episode number)
     *
     * This replaces NxXtreamSeriesIndexRepository.extractEpisodeIdFromSourceKey().
     *
     * @param sourceKey The source key string
     * @return Episode ID (stream ID or episode number) or null
     */
    fun extractXtreamEpisodeId(sourceKey: String?): Int? {
        val itemKey = extractItemKey(sourceKey) ?: return null
        
        // Try direct numeric ID first
        itemKey.toIntOrNull()?.let { return it }
        
        // Try composite format: seriesId_season_episode
        val parts = itemKey.split("_")
        if (parts.size >= 3) {
            return parts[2].toIntOrNull()
        }
        
        return null
    }

    /**
     * Extract Xtream series ID from episode sourceKey.
     *
     * For composite episode format: `src:xtream:account:episode:100_1_5` → 100
     *
     * @param sourceKey The source key string
     * @return Series ID or null
     */
    fun extractXtreamSeriesIdFromEpisode(sourceKey: String?): Int? {
        val itemKey = extractItemKey(sourceKey) ?: return null
        
        // Composite format: seriesId_season_episode
        val parts = itemKey.split("_")
        if (parts.size >= 3) {
            return parts[0].toIntOrNull()
        }
        
        return null
    }

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
