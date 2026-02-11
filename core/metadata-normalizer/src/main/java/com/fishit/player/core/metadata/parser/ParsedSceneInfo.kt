package com.fishit.player.core.metadata.parser

/**
 * Parsed metadata from a scene-style filename.
 *
 * Represents structured information extracted from media filenames
 * using scene release naming conventions.
 *
 * @property title Cleaned title with technical tags removed
 * @property year Release year if detected (1900-2099)
 * @property isEpisode True if filename contains episode markers (SxxEyy)
 * @property season Season number for episodes (1-99)
 * @property episode Episode number for episodes (1-999)
 * @property rating Rating extracted from structured title formats (e.g. Xtream pipe format "Title | Year | 7.4")
 * @property quality Quality and technical metadata
 * @property edition Edition flags (Extended, Director's Cut, Unrated, etc.)
 * @property extraTags Unrecognized tags for debugging/logging
 */
data class ParsedSceneInfo(
    val title: String,
    val year: Int? = null,
    val isEpisode: Boolean = false,
    val season: Int? = null,
    val episode: Int? = null,
    val rating: Double? = null,
    val quality: QualityInfo? = null,
    val edition: EditionInfo? = null,
    val extraTags: List<String> = emptyList(),
)

/**
 * Quality and technical metadata from filename.
 *
 * @property resolution Resolution: 480p, 720p, 1080p, 2160p, 4K, 8K, UHD
 * @property source Source: WEB-DL, WEBRip, BluRay, DVDRip, HDTV, BDRip, etc.
 * @property codec Video codec: x264, x265, H264, H265, HEVC, AV1, XviD
 * @property audio Audio codec: AAC, AC3, DD, DD+, DTS, Dolby Atmos
 * @property hdr HDR format: HDR, HDR10, HDR10+, Dolby Vision
 * @property group Release group name (after final hyphen or in brackets)
 */
data class QualityInfo(
    val resolution: String? = null,
    val source: String? = null,
    val codec: String? = null,
    val audio: String? = null,
    val hdr: String? = null,
    val group: String? = null,
)

/**
 * Edition and release flags from filename.
 *
 * @property extended Extended cut/edition
 * @property directors Director's cut
 * @property unrated Unrated version
 * @property theatrical Theatrical cut
 * @property threeD 3D release
 * @property imax IMAX release
 * @property remastered Remastered version
 * @property proper PROPER flag (scene re-release)
 * @property repack REPACK flag (scene re-release)
 */
data class EditionInfo(
    val extended: Boolean = false,
    val directors: Boolean = false,
    val unrated: Boolean = false,
    val theatrical: Boolean = false,
    val threeD: Boolean = false,
    val imax: Boolean = false,
    val remastered: Boolean = false,
    val proper: Boolean = false,
    val repack: Boolean = false,
) {
    /**
     * Returns true if any edition flag is set.
     */
    fun hasAnyFlag(): Boolean =
        extended ||
            directors ||
            unrated ||
            theatrical ||
            threeD ||
            imax ||
            remastered ||
            proper ||
            repack
}
