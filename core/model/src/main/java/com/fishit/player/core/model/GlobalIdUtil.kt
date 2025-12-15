package com.fishit.player.core.model

/**
 * @deprecated GlobalIdUtil has been moved to CanonicalIdUtil in core:metadata-normalizer.
 *
 * DO NOT USE IN PIPELINES. Pipelines must not generate canonical IDs.
 * Only the metadata normalizer may generate canonical IDs.
 *
 * Migration path:
 * - If you are in pipeline code: REMOVE calls to GlobalIdUtil. Pipelines should not compute IDs.
 * - If you are in normalizer code: Use CanonicalIdUtil.canonicalHashId() instead.
 *
 * This stub exists only to provide clear compiler errors with actionable messages.
 *
 * Reason for move:
 * - core:model must remain a pure contract/types module
 * - GlobalIdUtil embedded normalization logic (scene tag stripping)
 * - This violated the MEDIA_NORMALIZATION_CONTRACT requirement that only the normalizer performs normalization
 * - Pipelines were able to import GlobalIdUtil and bypass the normalizer
 *
 * See: contracts/MEDIA_NORMALIZATION_CONTRACT.md
 */
@Deprecated(
    message = "Do not use GlobalIdUtil. Use CanonicalIdUtil in core:metadata-normalizer instead. Pipelines must NOT generate canonical IDs.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "CanonicalIdUtil.canonicalHashId(canonicalTitle, year, season, episode, tmdbId)",
        "com.fishit.player.core.metadata.CanonicalIdUtil"
    )
)
object GlobalIdUtil {
    @Deprecated(
        message = "Moved to CanonicalIdUtil.canonicalHashId() in core:metadata-normalizer",
        level = DeprecationLevel.ERROR
    )
    fun generateCanonicalId(originalTitle: String, year: Int?): String {
        throw UnsupportedOperationException(
            "GlobalIdUtil has been removed. Use CanonicalIdUtil in core:metadata-normalizer instead."
        )
    }

    @Deprecated(
        message = "Normalization logic moved to core:metadata-normalizer",
        level = DeprecationLevel.ERROR
    )
    internal fun normalizeTitle(title: String): String {
        throw UnsupportedOperationException(
            "Title normalization must only happen in core:metadata-normalizer"
        )
    }
}
