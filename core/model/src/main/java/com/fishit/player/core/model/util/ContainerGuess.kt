package com.fishit.player.core.model.util

/**
 * SSOT for guessing container format from MIME type.
 *
 * Consolidates 2 duplicate implementations (NxDetailMediaRepositoryImpl + WorkDetailMapper).
 * Includes all supported formats from both implementations.
 */
object ContainerGuess {

    /**
     * Guesses container format from MIME type string.
     *
     * @param mimeType MIME type (e.g., "video/mp4", "video/x-matroska")
     * @return Container extension (e.g., "mp4", "mkv"), or null if unrecognized
     */
    fun fromMimeType(mimeType: String): String? = when {
        "mp4" in mimeType -> "mp4"
        "mkv" in mimeType || "matroska" in mimeType -> "mkv"
        "avi" in mimeType -> "avi"
        "webm" in mimeType -> "webm"
        "mpegts" in mimeType -> "ts"
        // "ts" alone is too broad (matches "fonts", "bits", etc.) — only match mpegts
        mimeType == "video/mp2t" -> "ts"
        "ogg" in mimeType -> "ogg"
        "flv" in mimeType -> "flv"
        else -> null
    }
}
