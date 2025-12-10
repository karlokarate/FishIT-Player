package com.fishit.player.core.metadata

import com.fishit.player.core.model.GlobalIdUtil
import com.fishit.player.core.model.MediaVariant
import com.fishit.player.core.model.NormalizedMedia
import com.fishit.player.core.model.QualityTags
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceKey
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.VariantPreferences
import com.fishit.player.core.model.VariantSelector
import java.util.Locale

/**
 * Cross-pipeline normalizer that merges RawMediaMetadata from multiple sources into deduplicated
 * NormalizedMedia with multi-variant support.
 *
 * **Algorithm:**
 * 1. Group raw items by [globalId] (canonical ID based on normalized title + year)
 * 2. For each group, create a [NormalizedMedia] with all variants
 * 3. Sort variants by user preferences using [VariantSelector]
 * 4. Set primary source to the best variant
 *
 * **Example:** Input:
 * - "Breaking.Bad.S01E01.1080p.BluRay" from Telegram
 * - "Breaking Bad - S01E01" from Xtream
 *
 * Output:
 * - Single NormalizedMedia "Breaking Bad S01E01" with 2 variants
 */
object Normalizer {

    /**
     * Normalize and merge raw metadata from multiple pipelines.
     *
     * @param rawItems Raw metadata from one or more pipelines
     * @param prefs User preferences for variant sorting
     * @return Deduplicated normalized media with sorted variants
     */
    fun normalize(
            rawItems: List<RawMediaMetadata>,
            prefs: VariantPreferences = VariantPreferences.default(),
    ): List<NormalizedMedia> {
        // Group by globalId for cross-pipeline deduplication
        val groupedByGlobalId =
                rawItems.groupBy { raw ->
                    raw.globalId.ifEmpty {
                        // Fallback if globalId wasn't set by pipeline
                        GlobalIdUtil.generateCanonicalId(raw.originalTitle, raw.year)
                    }
                }

        return groupedByGlobalId.mapNotNull { (globalId, group) ->
            if (group.isEmpty()) return@mapNotNull null

            // Use first item as reference for title/year/mediaType
            val reference = group.first()

            // Create variants from all raw items
            val variants = group.map { raw -> raw.toMediaVariant() }.toMutableList()

            // Sort by preference
            val sortedVariants = VariantSelector.sortByPreference(variants, prefs)
            variants.clear()
            variants.addAll(sortedVariants)

            // Best variant becomes primary
            val bestVariant = variants.first()

            NormalizedMedia(
                    globalId = globalId,
                    title = reference.originalTitle, // Later: apply title cleaning here
                    year = reference.year,
                    mediaType = reference.mediaType,
                    primaryPipelineIdTag = bestVariant.sourceKey.pipeline,
                    primarySourceId = bestVariant.sourceKey.sourceId,
                    variants = variants,
            )
        }
    }

    /** Convert a RawMediaMetadata to a MediaVariant. */
    private fun RawMediaMetadata.toMediaVariant(): MediaVariant {
        return MediaVariant(
                sourceKey = SourceKey(pipelineIdTag, sourceId),
                qualityTag = deriveQualityTag(),
                resolutionHeight = deriveResolutionHeight(),
                language = deriveLanguage(),
                isOmu = deriveIsOmu(),
                sourceUrl = deriveSourceUrl(),
                available = true,
        )
    }

    /** Derive quality tag from title and other metadata. */
    private fun RawMediaMetadata.deriveQualityTag(): String {
        val titleUpper = originalTitle.uppercase()

        return when {
            titleUpper.contains("2160P") ||
                    titleUpper.contains("4K") ||
                    titleUpper.contains("UHD") -> QualityTags.UHD
            titleUpper.contains("1080P") || titleUpper.contains("FHD") -> QualityTags.FHD
            titleUpper.contains("720P") -> QualityTags.HD
            titleUpper.contains("CAM") || titleUpper.contains("HDCAM") -> QualityTags.CAM
            titleUpper.contains("WEB-DL") || titleUpper.contains("WEBRIP") -> QualityTags.WEB
            titleUpper.contains("BLURAY") || titleUpper.contains("BDRIP") -> QualityTags.BLURAY
            else -> QualityTags.SD
        }
    }

    /** Derive resolution height from quality tag or title. */
    private fun RawMediaMetadata.deriveResolutionHeight(): Int? {
        val titleUpper = originalTitle.uppercase()

        return when {
            titleUpper.contains("2160P") || titleUpper.contains("4K") -> 2160
            titleUpper.contains("1080P") -> 1080
            titleUpper.contains("720P") -> 720
            titleUpper.contains("480P") -> 480
            else -> null
        }
    }

    /**
     * Derive language from title (basic heuristics).
     *
     * Full language detection can be enhanced later.
     */
    private fun RawMediaMetadata.deriveLanguage(): String {
        val titleUpper = originalTitle.uppercase()
        val labelUpper = sourceLabel.uppercase()

        return when {
            titleUpper.contains("[GER]") ||
                    titleUpper.contains("GERMAN") ||
                    labelUpper.contains("GERMAN") ||
                    labelUpper.contains("DE") -> "de"
            titleUpper.contains("[ENG]") || titleUpper.contains("ENGLISH") -> "en"
            titleUpper.contains("[FR]") || titleUpper.contains("FRENCH") -> "fr"
            titleUpper.contains("[ES]") || titleUpper.contains("SPANISH") -> "es"
            titleUpper.contains("[IT]") || titleUpper.contains("ITALIAN") -> "it"
            else -> Locale.getDefault().language // Fallback to system language
        }
    }

    /** Check if this is Original with subtitles (OmU/OV). */
    private fun RawMediaMetadata.deriveIsOmu(): Boolean {
        val titleLower = originalTitle.lowercase()
        return titleLower.contains("omu") ||
                titleLower.contains("[omu]") ||
                titleLower.contains("ov ") ||
                titleLower.contains("[ov]") ||
                titleLower.contains("subbed")
    }

    /**
     * Derive source URL (only for URL-based sources like Xtream).
     *
     * For Telegram, this is null (file-based resolution at playback).
     */
    private fun RawMediaMetadata.deriveSourceUrl(): String? {
        // For Xtream, sourceId format is "xtream:vod:123" - extract stream URL
        // This is a placeholder; actual URL construction happens in transport layer
        return when (sourceType) {
            SourceType.XTREAM -> {
                // Xtream URLs are constructed at playback time from provider credentials
                null // Will be resolved by XtreamPlaybackSourceResolver
            }
            else -> null
        }
    }
}
