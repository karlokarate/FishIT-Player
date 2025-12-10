package com.fishit.player.core.model

/**
 * User preferences for variant selection.
 *
 * These preferences drive the automatic selection of the best playback variant when multiple
 * sources are available for the same media.
 *
 * @property preferredLanguage ISO-639-1 language code (e.g., "de", "en")
 * @property preferOmu True to prefer Original with subtitles over dubbed
 * @property preferXtream True to prefer Xtream sources (usually more reliable) over Telegram
 */
data class VariantPreferences(
        val preferredLanguage: String = "de",
        val preferOmu: Boolean = false,
        val preferXtream: Boolean = true,
) {
    companion object {
        /** Default preferences using system language. */
        fun default(systemLanguage: String = "de"): VariantPreferences =
                VariantPreferences(preferredLanguage = systemLanguage)
    }
}

/**
 * Selects and sorts media variants based on user preferences and quality.
 *
 * **Scoring Algorithm (applied in strict order):**
 *
 * 1. **Availability** – Unavailable variants sorted to end
 * 2. **Language Priority:**
 * ```
 *    - +40 if language matches preferred
 *    - +20 if OmU and user prefers OmU
 *    - +10 if dubbed and user doesn't prefer OmU
 * ```
 * 3. **Quality Priority:**
 * ```
 *    - Add resolutionHeight directly (1080 > 720 > 480)
 *    - +20 for WEB/BluRay quality tags
 *    - -100 for CAM quality
 * ```
 * 4. **Pipeline Priority:**
 * ```
 *    - +15 if Xtream and user prefers Xtream
 * ```
 */
object VariantSelector {

    /**
     * Sort variants by preference, returning a new list with best variants first.
     *
     * @param variants List of variants to sort
     * @param prefs User preferences for selection
     * @return New list sorted by descending score (stable sort)
     */
    fun sortByPreference(
            variants: List<MediaVariant>,
            prefs: VariantPreferences,
    ): List<MediaVariant> {
        return variants.sortedByDescending { variant -> calculateScore(variant, prefs) }
    }

    /**
     * Get the best available variant.
     *
     * @param variants List of variants to choose from
     * @param prefs User preferences for selection
     * @return Best variant, or null if all unavailable
     */
    fun selectBest(
            variants: List<MediaVariant>,
            prefs: VariantPreferences,
    ): MediaVariant? {
        return sortByPreference(variants, prefs).firstOrNull { it.available }
    }

    /**
     * Calculate the preference score for a variant.
     *
     * Higher score = better match.
     */
    internal fun calculateScore(
            variant: MediaVariant,
            prefs: VariantPreferences,
    ): Int {
        var score = 0

        // 1. Availability penalty (unavailable variants get massive penalty)
        if (!variant.available) {
            score -= 100_000
        }

        // 2. Language priority
        if (variant.language?.equals(prefs.preferredLanguage, ignoreCase = true) == true) {
            score += 40
        }
        if (variant.isOmu && prefs.preferOmu) {
            score += 20
        }
        if (!variant.isOmu && !prefs.preferOmu) {
            score += 10 // Dubbed when OmU not preferred
        }

        // 3. Quality priority
        score += variant.resolutionHeight ?: 0

        val qualityUpper = variant.qualityTag.uppercase()
        if (qualityUpper.contains("WEB") || qualityUpper.contains("BLURAY")) {
            score += 20
        }
        if (qualityUpper.contains("CAM")) {
            score -= 100
        }

        // 4. Pipeline priority
        if (variant.sourceKey.pipeline == PipelineIdTag.XTREAM && prefs.preferXtream) {
            score += 15
        }

        return score
    }
}
