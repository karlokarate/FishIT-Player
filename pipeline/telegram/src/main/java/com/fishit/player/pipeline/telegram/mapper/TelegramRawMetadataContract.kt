package com.fishit.player.pipeline.telegram.mapper

/**
 * Telegram pipeline integration with the centralized media normalization contract.
 *
 * This file documents the STRUCTURE ONLY of how Telegram will provide raw metadata
 * to the future `:core:metadata-normalizer` module in Phase 3.
 *
 * **CRITICAL: This is documentation only - NOT an implementation.**
 *
 * **CONTRACT COMPLIANCE:**
 * - Telegram provides RAW metadata ONLY (no cleaning, no normalization, no heuristics)
 * - Title extraction is a simple field priority selector (title > episodeTitle > caption > fileName)
 * - NO title cleaning, NO tag stripping, NO TMDB lookups
 * - All normalization happens centrally in `:core:metadata-normalizer`
 *
 * **Type Definitions:**
 * - `RawMediaMetadata` - Defined in `:core:metadata-normalizer` (NOT in this module)
 * - `ExternalIds` - Defined in `:core:metadata-normalizer` (NOT in this module)
 * - `SourceType` - Defined in `:core:metadata-normalizer` (NOT in this module)
 *
 * This module does NOT and MUST NOT define these types locally.
 *
 * See: v2-docs/MEDIA_NORMALIZATION_CONTRACT.md for authoritative type definitions and rules.
 * See: v2-docs/MEDIA_NORMALIZATION_AND_UNIFICATION.md for architecture overview.
 *
 * ## Future Implementation (Phase 3+)
 *
 * When `:core:metadata-normalizer` module exists with the required types, implement:
 *
 * ```kotlin
 * fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
 *     return RawMediaMetadata(
 *         // Simple field selector - NO cleaning, NO normalization
 *         originalTitle = extractRawTitle(),
 *         year = this.year,
 *         season = this.seasonNumber,
 *         episode = this.episodeNumber,
 *         durationMinutes = this.durationSecs?.let { it / 60 },
 *         externalIds = ExternalIds(), // Telegram doesn't provide external IDs
 *         sourceType = SourceType.TELEGRAM,
 *         sourceLabel = buildTelegramSourceLabel(),
 *         sourceId = this.remoteId ?: "msg:${chatId}:${messageId}"
 *     )
 * }
 * ```
 *
 * **Type sources (Phase 3):**
 * - `RawMediaMetadata` - from `:core:metadata-normalizer`
 * - `ExternalIds` - from `:core:metadata-normalizer`
 * - `SourceType.TELEGRAM` - enum value from `:core:metadata-normalizer`
 *
 * **extractRawTitle() helper (simple field priority selector):**
 * ```kotlin
 * private fun TelegramMediaItem.extractRawTitle(): String {
 *     // Priority: title > episodeTitle > caption > fileName
 *     // CRITICAL: NO cleaning of technical tags - pass raw source data AS-IS
 *     // Examples of what stays unchanged:
 *     //   "Movie.2020.1080p.BluRay.x264-GROUP" -> returned AS-IS (no stripping)
 *     //   "Series.S01E05.HDTV.x264" -> returned AS-IS (no normalization)
 *     return when {
 *         title.isNotBlank() -> title
 *         episodeTitle?.isNotBlank() == true -> episodeTitle!!
 *         caption?.isNotBlank() == true -> caption!!
 *         fileName?.isNotBlank() == true -> fileName!!
 *         else -> "Untitled Media $messageId"
 *     }
 * }
 * ```
 *
 * **buildTelegramSourceLabel() helper:**
 * ```kotlin
 * private fun TelegramMediaItem.buildTelegramSourceLabel(): String {
 *     // Example: "Telegram Chat: Movies HD"
 *     // Will be enriched with actual chat names when real TDLib integration exists
 *     return "Telegram Chat: $chatId"
 * }
 * ```
 *
 * **Pipeline Responsibilities (per contract):**
 * 1. ✅ Provide raw title via simple field priority (NO cleaning, NO normalization)
 * 2. ✅ Pass through year, season, episode, duration as-is from source
 * 3. ✅ Provide stable sourceId for tracking (remoteId or "msg:chatId:messageId")
 * 4. ✅ Leave externalIds empty (Telegram doesn't provide TMDB/IMDB/TVDB)
 * 5. ✅ NO TMDB lookups, NO cross-pipeline matching, NO canonical identity computation
 *
 * **Centralizer Responsibilities (`:core:metadata-normalizer`):**
 * - Title cleaning and normalization (strip tags, standardize format)
 * - TMDB/IMDB/TVDB lookups and identity resolution
 * - Cross-pipeline canonical identity assignment
 * - Scene-style naming pattern parsing
 */
object TelegramRawMetadataContract {
    /**
     * Contract version for tracking changes to the metadata structure.
     */
    const val CONTRACT_VERSION = "1.0"

    /**
     * Future module dependency that will provide RawMediaMetadata types.
     */
    const val MODULE_DEPENDENCY = ":core:metadata-normalizer"
}
