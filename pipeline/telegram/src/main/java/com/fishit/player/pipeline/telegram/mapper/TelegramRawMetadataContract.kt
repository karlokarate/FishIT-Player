package com.fishit.player.pipeline.telegram.mapper

/**
 * Telegram pipeline integration with the centralized media normalization contract.
 *
 * **IMPLEMENTATION STATUS: COMPLETE (Phase 3)**
 *
 * The actual implementation is in:
 * - `TelegramMediaItem.toRawMediaMetadata()` extension in
 *   `com.fishit.player.pipeline.telegram.model.TelegramRawMetadataExtensions`
 *
 * This file is retained for contract documentation purposes.
 *
 * **CONTRACT COMPLIANCE:**
 * - ✅ Telegram provides RAW metadata ONLY (no cleaning, no normalization, no heuristics)
 * - ✅ Title extraction via simple field priority (title > episodeTitle > caption > fileName)
 * - ✅ NO title cleaning, NO tag stripping, NO TMDB lookups
 * - ✅ All normalization delegated to :core:metadata-normalizer
 *
 * **Type Definitions (from :core:model):**
 * - `RawMediaMetadata` - Input to normalization pipeline
 * - `NormalizedMediaMetadata` - Output from normalization pipeline
 * - `ExternalIds` - TMDB/IMDB/TVDB IDs
 * - `SourceType` - Pipeline identifier (TELEGRAM)
 *
 * **Normalization Behavior (from :core:metadata-normalizer):**
 * - `MediaMetadataNormalizer` - Title cleaning, scene-naming parser
 * - `TmdbMetadataResolver` - TMDB lookups and enrichment
 *
 * See: docs/v2/MEDIA_NORMALIZATION_CONTRACT.md for authoritative type definitions and rules.
 * See: docs/v2/MEDIA_NORMALIZATION_AND_UNIFICATION.md for architecture overview.
 *
 * ## Future Implementation (Phase 3+)
 *
 * When `:core:model` module has the required types, implement:
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
 * - `RawMediaMetadata` - from `:core:model`
 * - `ExternalIds` - from `:core:model`
 * - `SourceType.TELEGRAM` - enum value from `:core:model`
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
 *     // Will be enriched with actual chat names when real Telegram API integration exists
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
 * **Centralizer Responsibilities:**
 * - **Types (`:core:model`):** RawMediaMetadata, NormalizedMediaMetadata, ExternalIds, SourceType
 * - **Behavior (`:core:metadata-normalizer`):**
 *   - Title cleaning and normalization (strip tags, standardize format)
 *   - TMDB/IMDB/TVDB lookups and identity resolution
 *   - Cross-pipeline canonical identity assignment
 *   - Scene-style naming pattern parsing
 */
object TelegramRawMetadataContract {
    /**
     * Contract version for tracking changes to the metadata structure.
     */
    const val CONTRACT_VERSION = "1.0"

    /**
     * Future module dependencies:
     * - Types from `:core:model`
     * - Normalization behavior from `:core:metadata-normalizer`
     */
    const val TYPE_MODULE = ":core:model"
    const val NORMALIZER_MODULE = ":core:metadata-normalizer"
}
