package com.fishit.player.core.model.util

/**
 * SSOT for rating scale normalization.
 *
 * Xtream API provides ratings in two fields:
 * - `rating`: String on 0–10 scale (from TMDB/IMDB scraping)
 * - `rating_5based`: Double on 0–5 scale (provider-computed)
 *
 * This utility centralizes the normalization to a consistent 0–10 scale,
 * eliminating duplicate `rating5Based * 2.0` expressions scattered across
 * the pipeline adapter and raw metadata mapper.
 */
object RatingNormalizer {
    /**
     * Normalize a 5-point rating to 10-point scale.
     *
     * A value of 0.0 (or negative) is treated as "no rating" since Xtream API
     * uses `rating_5based=0` to indicate unrated content.
     *
     * @param rating5Based Rating on 0–5 scale
     * @return Rating on 0–10 scale, or null if input is null/zero/negative
     */
    fun normalize5to10(rating5Based: Double?): Double? = rating5Based?.takeIf { it > 0.0 }?.let { it * 2.0 }

    /**
     * Resolve rating from Xtream API fields.
     *
     * Priority:
     * 1. `rating` string parsed as Double (already 0–10 scale)
     * 2. `rating5Based` normalized to 0–10 scale
     *
     * Values of 0.0 are treated as "no rating" since Xtream API uses 0 to
     * indicate unrated content, not a genuine zero-star rating.
     *
     * @param ratingRaw Raw rating string from API (0–10 scale)
     * @param rating5Based Rating from API (0–5 scale)
     * @return Resolved rating on 0–10 scale, or null if neither is available
     */
    fun resolve(
        ratingRaw: String?,
        rating5Based: Double?,
    ): Double? = ratingRaw?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: normalize5to10(rating5Based)
}
