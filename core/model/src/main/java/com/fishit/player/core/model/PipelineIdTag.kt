package com.fishit.player.core.model

/**
 * Identifies which pipeline produced a media item.
 *
 * Used for:
 * - Tracking media origin across the system
 * - Building global IDs with pipeline prefix
 * - Variant source identification
 * - Cross-pipeline deduplication
 *
 * @property code Short string identifier used in serialization and global IDs
 */
enum class PipelineIdTag(
    val code: String,
) {
    /** Telegram media via TDLib */
    TELEGRAM("tg"),

    /** Xtream Codes API (IPTV, VOD, Series) */
    XTREAM("xc"),

    /** Local/IO file system sources */
    IO("io"),

    /** Audiobook-specific pipeline */
    AUDIOBOOK("ab"),

    /** Unknown or unidentified pipeline */
    UNKNOWN("unk"),
    ;

    companion object {
        /**
         * Parse a code string to PipelineIdTag.
         *
         * @param code The code string (e.g., "tg", "xc")
         * @return Matching PipelineIdTag or UNKNOWN if not found
         */
        fun fromCode(code: String): PipelineIdTag = entries.find { it.code == code } ?: UNKNOWN
    }
}
