package com.fishit.player.pipeline.telegram.mapper

/**
 * Telegram pipeline integration with the centralized media normalization contract.
 *
 * This file demonstrates the STRUCTURE of how Telegram will provide raw metadata
 * to the future `:core:metadata-normalizer` module.
 *
 * **IMPORTANT CONTRACT COMPLIANCE:**
 * - Telegram provides RAW metadata ONLY (no cleaning, no normalization, no heuristics)
 * - Title extraction is a simple field priority selector (title > episodeTitle > caption > fileName)
 * - NO title cleaning, NO tag stripping, NO TMDB lookups
 * - All normalization happens centrally in `:core:metadata-normalizer`
 *
 * See: v2-docs/MEDIA_NORMALIZATION_CONTRACT.md for formal rules.
 *
 * ## TODO: Future Implementation
 *
 * This will be implemented once `:core:metadata-normalizer` module exists.
 *
 * Planned signature (STRUCTURE ONLY):
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
 * Where:
 * - `RawMediaMetadata` comes from `:core:metadata-normalizer`
 * - `ExternalIds` comes from `:core:metadata-normalizer`
 * - `SourceType.TELEGRAM` is an enum value in `:core:metadata-normalizer`
 *
 * **extractRawTitle() logic (NO cleaning):**
 * ```kotlin
 * private fun TelegramMediaItem.extractRawTitle(): String {
 *     // Priority: title > episodeTitle > caption > fileName
 *     // NO cleaning of technical tags - pass raw source data
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
 * **buildTelegramSourceLabel() logic:**
 * ```kotlin
 * private fun TelegramMediaItem.buildTelegramSourceLabel(): String {
 *     // Example: "Telegram Chat: Movies HD"
 *     // Will be enriched with actual chat names when real TDLib integration exists
 *     return "Telegram Chat: $chatId"
 * }
 * ```
 *
 * **Contract Guarantees:**
 * 1. ✅ NO title cleaning (no tag stripping, no normalization)
 * 2. ✅ NO TMDB/IMDB/TVDB lookups
 * 3. ✅ Simple field priority for title selection
 * 4. ✅ Pass through year, season, episode, duration as-is
 * 5. ✅ Provide stable sourceId for resume tracking
 * 6. ✅ All normalization handled by `:core:metadata-normalizer`
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
