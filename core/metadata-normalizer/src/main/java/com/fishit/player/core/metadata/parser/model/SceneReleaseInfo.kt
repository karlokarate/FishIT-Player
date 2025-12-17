/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scene Release Information - Comprehensive parsed metadata from release names.
 * Ported from @ctrl/video-filename-parser TypeScript reference implementation.
 */
package com.fishit.player.core.metadata.parser.model

/**
 * Video resolution enum with standard values.
 * Order matters for fallback resolution guessing.
 */
enum class VideoResolution(val label: String, val pixels: Int) {
    R2160P("2160p", 2160),
    R1080P("1080p", 1080),
    R720P("720p", 720),
    R576P("576p", 576),
    R540P("540p", 540),
    R480P("480p", 480),
    ;

    companion object {
        fun fromHeight(height: Int): VideoResolution? =
            entries.find { it.pixels == height }
    }
}

/**
 * Video source/origin enum.
 */
enum class VideoSource(val label: String) {
    BLURAY("BluRay"),
    WEBDL("WEB-DL"),
    WEBRIP("WEBRip"),
    DVD("DVD"),
    HDTV("HDTV"),
    TV("TV"),
    CAM("CAM"),
    SCREENER("Screener"),
    TELESYNC("Telesync"),
    TELECINE("Telecine"),
    WORKPRINT("Workprint"),
    PPV("PPV"),
    ;
}

/**
 * Quality modifier for special releases.
 */
enum class QualityModifier {
    REMUX,
    BRDISK,
    RAWHD,
}

/**
 * Video codec enum.
 */
enum class VideoCodec(val label: String) {
    X265("x265"),
    X264("x264"),
    H265("H.265"),
    H264("H.264"),
    HEVC("HEVC"),
    AV1("AV1"),
    VP9("VP9"),
    XVID("XviD"),
    DIVX("DivX"),
    WMV("WMV"),
    MPEG2("MPEG-2"),
    ;
}

/**
 * Audio codec enum.
 */
enum class AudioCodec(val label: String) {
    DOLBY_DIGITAL("Dolby Digital"),
    DOLBY_DIGITAL_PLUS("Dolby Digital Plus"),
    DOLBY_ATMOS("Dolby Atmos"),
    DOLBY_TRUEHD("Dolby TrueHD"),
    DTS("DTS"),
    DTS_HD("DTS-HD"),
    DTS_X("DTS:X"),
    AAC("AAC"),
    FLAC("FLAC"),
    MP3("MP3"),
    OPUS("Opus"),
    VORBIS("Vorbis"),
    PCM("PCM"),
    LPCM("LPCM"),
    ;
}

/**
 * Audio channel configuration.
 */
enum class AudioChannels(val label: String, val channels: Float) {
    MONO("Mono", 1.0f),
    STEREO("Stereo", 2.0f),
    SURROUND_51("5.1", 5.1f),
    SURROUND_71("7.1", 7.1f),
    ;
}

/**
 * Special edition types.
 */
data class EditionInfo(
    val isExtended: Boolean = false,
    val isDirectorsCut: Boolean = false,
    val isTheatrical: Boolean = false,
    val isUnrated: Boolean = false,
    val isRemastered: Boolean = false,
    val isImax: Boolean = false,
    val isFanEdit: Boolean = false,
    val isHdr: Boolean = false,
    val isDolbyVision: Boolean = false,
    val is3D: Boolean = false,
    val isUhd: Boolean = false,
) {
    val hasEdition: Boolean
        get() =
            isExtended ||
                isDirectorsCut ||
                isTheatrical ||
                isUnrated ||
                isRemastered ||
                isImax ||
                isFanEdit

    companion object {
        val NONE = EditionInfo()
    }
}

/**
 * Release version/quality info.
 */
data class Revision(
    /** Version number (1 = original, 2+ = PROPER/REPACK) */
    val version: Int = 1,
    /** Number of REAL tags found */
    val real: Int = 0,
)

/**
 * Comprehensive scene release information.
 * Contains all parsed metadata from a release filename.
 */
data class SceneReleaseInfo(
    // === Core Title Info ===
    /** Cleaned title without technical tags */
    val title: String,
    /** Release year (null if not found) */
    val year: Int? = null,

    // === TV Series Info ===
    /** Season numbers (empty for movies) */
    val seasons: List<Int> = emptyList(),
    /** Episode numbers (empty for movies) */
    val episodes: List<Int> = emptyList(),
    /** Absolute episode number for anime */
    val absoluteEpisode: Int? = null,
    /** Air date for daily shows */
    val airDate: String? = null,
    /** True if this is a full season pack */
    val isFullSeason: Boolean = false,
    /** True if this is a multi-season pack */
    val isMultiSeason: Boolean = false,
    /** True if this is a special episode */
    val isSpecial: Boolean = false,

    // === Quality Info ===
    val resolution: VideoResolution? = null,
    val sources: List<VideoSource> = emptyList(),
    val modifier: QualityModifier? = null,
    val revision: Revision = Revision(),

    // === Codec Info ===
    val videoCodec: VideoCodec? = null,
    val audioCodec: AudioCodec? = null,
    val audioChannels: AudioChannels? = null,

    // === Additional Metadata ===
    val edition: EditionInfo = EditionInfo.NONE,
    val releaseGroup: String? = null,
    val languages: List<String> = emptyList(),
    val isMultiLanguage: Boolean = false,
    val isComplete: Boolean = false,

    // === External IDs (if found in filename) ===
    val tmdbId: Int? = null,
    val imdbId: String? = null,

    // === Debug/Source Info ===
    val originalInput: String = "",
) {
    /** True if this appears to be a TV show (has season/episode info) */
    val isTvShow: Boolean
        get() = seasons.isNotEmpty() || episodes.isNotEmpty() || absoluteEpisode != null

    /** True if this appears to be a movie */
    val isMovie: Boolean
        get() = !isTvShow

    /** First season number or null */
    val season: Int?
        get() = seasons.firstOrNull()

    /** First episode number or null */
    val episode: Int?
        get() = episodes.firstOrNull() ?: absoluteEpisode

    /** Primary source or null */
    val source: VideoSource?
        get() = sources.firstOrNull()

    companion object {
        /** Empty result for parse failures */
        val EMPTY = SceneReleaseInfo(title = "")
    }
}
