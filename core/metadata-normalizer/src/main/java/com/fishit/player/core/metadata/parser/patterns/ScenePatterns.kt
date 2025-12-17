/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Compiled Pattern Registry for Scene Release Parsing.
 * All patterns are compiled once at class load time for maximum performance.
 *
 * Ported from @ctrl/video-filename-parser TypeScript reference.
 * Uses RE2J where possible for O(n) guaranteed parsing.
 */
package com.fishit.player.core.metadata.parser.patterns

import com.google.re2j.Pattern as Re2Pattern
import java.util.regex.Pattern as JvmPattern

/**
 * Compiled patterns for scene release parsing.
 * Singleton object - patterns are compiled once at class load.
 *
 * Pattern naming convention:
 * - *_RE2: RE2J-compatible patterns (preferred, O(n) guaranteed)
 * - *_JVM: JVM patterns for complex features (backreferences, lookahead)
 */
object ScenePatterns {
    // =========================================================================
    // FILE EXTENSION PATTERNS
    // =========================================================================

    /** Common video file extensions */
    val FILE_EXTENSION_RE2: Re2Pattern =
        Re2Pattern.compile(
            "\\.(mkv|mp4|avi|m4v|mov|wmv|flv|webm|ts|m2ts|vob|iso|img|divx|xvid|3gp|mpg|mpeg)$",
            Re2Pattern.CASE_INSENSITIVE,
        )

    // =========================================================================
    // RESOLUTION PATTERNS
    // =========================================================================

    /** 2160p/4K detection */
    val RES_2160P_RE2: Re2Pattern =
        Re2Pattern.compile(
            "(?i)\\b(2160p|4k[-_. ]?(?:UHD|HEVC|BD)?|(?:UHD|HEVC|BD)[-_. ]?4k|COMPLETE[.]?UHD|UHD[.]?COMPLETE)\\b",
        )

    /** 1080p detection */
    val RES_1080P_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(1080[ip]|1920x1080)(10bit)?\\b")

    /** 720p detection */
    val RES_720P_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(720[ip]|1280x720|960p)(10bit)?\\b")

    /** 576p detection */
    val RES_576P_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b576[ip]\\b")

    /** 540p detection */
    val RES_540P_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b540[ip]\\b")

    /** 480p detection */
    val RES_480P_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(480[ip]|640x480|848x480)\\b")

    // =========================================================================
    // VIDEO SOURCE PATTERNS
    // =========================================================================

    /** BluRay source */
    val SOURCE_BLURAY_RE2: Re2Pattern =
        Re2Pattern.compile(
            "(?i)\\b(M?Blu-?Ray|HDDVD|BD(?:ISO|Mux|25|50)?|UHDBD|BR[.-]?DISK|Bluray(?:1080|720)p?|BD(?:1080|720)p?)\\b",
        )

    /** WEB-DL source (includes streaming services) */
    val SOURCE_WEBDL_RE2: Re2Pattern =
        Re2Pattern.compile(
            "(?i)\\b(WEB[-_. ]?DL|HDRIP|WEBDL|WEB-DLMux|NF|APTV|NETFLIX|NetflixU?HD|DSNY|DSNP|HMAX|AMZN|AmazonHD|iTunesHD|MaxdomeHD|WebHD)\\b",
        )

    /** WEBRip source */
    val SOURCE_WEBRIP_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(WebRip|Web-Rip|WEBCap|WEBMux)\\b")

    /** HDTV source */
    val SOURCE_HDTV_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bHDTV\\b")

    /** DVD source */
    val SOURCE_DVD_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(DVD9?|DVDRip|NTSC|PAL|xvidvd|DvDivX)\\b")

    /** DVD-R source */
    val SOURCE_DVDR_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(DVD-R|DVDR)\\b")

    /** BDRip/BRRip source */
    val SOURCE_BDRIP_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(BDRip|UHDBDRip|HD[-_. ]?DVDRip|BRRip)\\b")

    /** CAM source */
    val SOURCE_CAM_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(CAMRIP|CAM|HDCAM|HD-CAM)\\b")

    /** Telesync source */
    val SOURCE_TS_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(TS|TELESYNC|HD-TS|HDTS|PDVD|TSRip|HDTSRip)\\b")

    /** Telecine source */
    val SOURCE_TC_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(TC|TELECINE|HD-TC|HDTC)\\b")

    /** Screener source */
    val SOURCE_SCR_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(SCR|SCREENER|DVDSCR|(?:DVD|WEB)[.]?SCREENER)\\b")

    /** Workprint source */
    val SOURCE_WORKPRINT_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(WORKPRINT|WP)\\b")

    /** PDTV/SDTV/DSR/TVRip source */
    val SOURCE_SDTV_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(PDTV|SDTV|WS[-_. ]DSR|DSR|TVRip)\\b")

    // =========================================================================
    // QUALITY MODIFIER PATTERNS
    // =========================================================================

    /** REMUX detection */
    val REMUX_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b((?:BD|UHD)?Remux)\\b")

    /** BluRay Disk detection */
    val BRDISK_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(COMPLETE|ISO|BDISO|BDMux|BD25|BD50|BR[.]?DISK)\\b")

    /** Raw HD detection */
    val RAWHD_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(RawHD|1080i[-_. ]HDTV|Raw[-_. ]HD|MPEG[-_. ]?2)\\b")

    // =========================================================================
    // VIDEO CODEC PATTERNS
    // =========================================================================

    /** x265 codec */
    val CODEC_X265_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(x265|HEVC)\\b")

    /** x264 codec */
    val CODEC_X264_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bx264\\b")

    /** H.265 codec */
    val CODEC_H265_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bh[.]?265\\b")

    /** H.264 codec */
    val CODEC_H264_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bh[.]?264\\b")

    /** AV1 codec */
    val CODEC_AV1_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bAV1\\b")

    /** VP9 codec */
    val CODEC_VP9_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bVP9\\b")

    /** XviD codec */
    val CODEC_XVID_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(X-?vid(?:HD)?|divx)\\b")

    /** WMV codec */
    val CODEC_WMV_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bWMV\\b")

    // =========================================================================
    // AUDIO CODEC PATTERNS
    // =========================================================================

    /** Dolby TrueHD */
    val AUDIO_TRUEHD_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(True-?HD)\\b")

    /** Dolby Atmos */
    val AUDIO_ATMOS_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(Dolby[.-]?Atmos|Atmos)\\b")

    /** Dolby Digital Plus (EAC3) */
    val AUDIO_EAC3_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(EAC3|E-AC-?3|DDP|DD[+]|DD[.]?Plus)\\b")

    /** Dolby Digital (AC3) */
    val AUDIO_AC3_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(Dolby(?:[.-]?Digital)?|DD|AC3D?)\\b")

    /** DTS-HD Master Audio */
    val AUDIO_DTSHD_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(DTS-?HD|DTS-?MA|DTS[:-]X)\\b")

    /** DTS */
    val AUDIO_DTS_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bDTS\\b")

    /** AAC */
    val AUDIO_AAC_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bAAC\\d?[.]?\\d?(?:ch)?\\b")

    /** FLAC */
    val AUDIO_FLAC_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bFLAC\\b")

    /** MP3 */
    val AUDIO_MP3_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(LAME\\d+-?\\d+|mp3)\\b")

    /** Opus */
    val AUDIO_OPUS_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bOpus\\b")

    // =========================================================================
    // AUDIO CHANNEL PATTERNS
    // =========================================================================

    /** 7.1 channels */
    val CHANNELS_71_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b7[.]?[01](?:ch)?\\b")

    /** 5.1 channels */
    val CHANNELS_51_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b([56][.]?[01](?:ch)?|5ch|6ch)\\b")

    /** Stereo */
    val CHANNELS_STEREO_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(2[.]?0(?:ch)?|stereo)\\b")

    /** Mono */
    val CHANNELS_MONO_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(1[.]?0(?:ch)?|mono|1ch)\\b")

    // =========================================================================
    // EDITION PATTERNS
    // =========================================================================

    /** Extended edition */
    val EDITION_EXTENDED_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(Extended|Uncut|Ultimate|Rogue|Collector)\\b")

    /** Director's Cut */
    val EDITION_DIRECTORS_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(Director'?s?|DC)\\b")

    /** Theatrical */
    val EDITION_THEATRICAL_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bTheatrical\\b")

    /** Unrated */
    val EDITION_UNRATED_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(Uncensored|Unrated)\\b")

    /** Remastered */
    val EDITION_REMASTERED_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(Remastered|Anniversary|Restored)\\b")

    /** IMAX */
    val EDITION_IMAX_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bIMAX\\b")

    /** Fan Edit */
    val EDITION_FANEDIT_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(Despecialized|Fan[.]?Edit)\\b")

    /** HDR */
    val EDITION_HDR_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\bHDR(?:10[+]?)?\\b")

    /** Dolby Vision */
    val EDITION_DV_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(DV|Dolby[.]?Vision)\\b")

    /** 3D */
    val EDITION_3D_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b3D\\b")

    // =========================================================================
    // REVISION/QUALITY PATTERNS
    // =========================================================================

    /** PROPER/REPACK detection */
    val PROPER_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(PROPER|REPACK|RERIP)\\b")

    /** Version detection (v2, [v3], etc.) */
    val VERSION_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)(v\\d|\\[v\\d\\])")

    /** REAL detection (case-sensitive!) */
    val REAL_JVM: JvmPattern =
        JvmPattern.compile("\\bREAL\\b")

    // =========================================================================
    // SEASON/EPISODE PATTERNS (Essential subset)
    // =========================================================================

    /** Standard S01E01 format */
    val SEASON_SxE_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)S(\\d{1,4})[Ee](\\d{1,4})")

    /** Multi-episode S01E01E02 or S01E01-02 */
    val SEASON_MULTI_EP_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)S(\\d{1,2})[Ee](\\d{1,3})(?:[-Ee](\\d{1,3}))+")

    /** 1x01 format */
    val SEASON_1X01_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)(\\d{1,2})x(\\d{2,3})")

    /** Season only (S01, Season 1) */
    val SEASON_ONLY_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)(?:S|Season[.]?)\\s*(\\d{1,2})(?!\\d|[Ee])")

    /** Episode only (E01, Ep01) */
    val EPISODE_ONLY_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)(?:E|Ep|Episode)[.]?\\s*(\\d{1,4})(?!\\d)")

    /** German "Folge" format */
    val GERMAN_FOLGE_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)Folge[.]?\\s*(\\d{1,4})")

    /** Anime subgroup tag [SubsPlease] */
    val ANIME_SUBGROUP_RE2: Re2Pattern =
        Re2Pattern.compile("^\\[([^\\]]+)\\]")

    /** Anime absolute episode: Title - 01 */
    val ANIME_ABSOLUTE_EP_RE2: Re2Pattern =
        Re2Pattern.compile("\\s+-\\s+(\\d{1,4})(?:\\s|$|\\[)")

    /** Air date: 2024.01.15 or 2024-01-15 */
    val AIR_DATE_RE2: Re2Pattern =
        Re2Pattern.compile("(\\d{4})[-._](\\d{2})[-._](\\d{2})")

    /** 103/113 format (S1E03) */
    val COMPACT_SxE_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)(?:^|[._])([1-9])(\\d{2})(?:[._]|$)")

    // =========================================================================
    // RELEASE GROUP PATTERNS
    // =========================================================================

    /** Standard release group: -GROUP at end */
    val GROUP_SUFFIX_RE2: Re2Pattern =
        Re2Pattern.compile("-([A-Za-z0-9]+)$")

    /** Known release groups (for exception handling) */
    val KNOWN_GROUPS: Set<String> =
        setOf(
            "YIFY", "YTS", "RARBG", "SPARKS", "GECKOS", "FGT", "FRAMESTOR",
            "FLUX", "TIGOLE", "QXR", "PSA", "ION10", "NIMA4K",
            "SUBSPLEASE", "ERAI-RAWS", "JUDAS", "ASW", "EMBER",
            "JOY", "VH-PROD", "FTW-HS", "DX-TV", "MONOLITH",
            "D-Z0N3", "VYNDROS", "HDO", "CTRLHD", "SEV",
        )

    // =========================================================================
    // SIMPLIFY/CLEANUP PATTERNS
    // =========================================================================

    /** Website prefix [www.site.com] */
    val WEBSITE_PREFIX_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)^\\[\\s*[a-z]+(\\.[a-z]+)+\\s*\\][-. ]*|^www\\.[a-z]+\\.(?:com|net)[ -]*")

    /** Torrent tags [ettv], [rarbg] etc. */
    val TORRENT_SUFFIX_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\[(?:ettv|rartv|rarbg|cttv)\\]$")

    /** Request tags [REQ] */
    val REQUEST_PREFIX_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)^\\[(?:REQ)\\]")

    /** Hash suffix [E1A86634] */
    val HASH_SUFFIX_RE2: Re2Pattern =
        Re2Pattern.compile("\\[[A-Fa-f0-9]{8}\\]")

    // =========================================================================
    // EXTERNAL ID PATTERNS
    // =========================================================================

    /** TMDb ID pattern: tmdb-550 or tmdb:550 */
    val TMDB_ID_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)tmdb[-:](\\d+)")

    /** IMDb ID pattern: imdb-tt0111161 or tt0111161 */
    val IMDB_ID_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)(?:imdb-)?(tt\\d{7,})")

    // =========================================================================
    // TITLE/YEAR PATTERNS (JVM - need lookahead/behind)
    // =========================================================================

    /**
     * Movie title with year patterns.
     * Order matters - more specific patterns first.
     * Uses JVM Pattern due to lookahead requirements.
     */
    val TITLE_YEAR_PATTERNS: List<JvmPattern> =
        listOf(
            // Special Edition movies: Mission.Impossible.3.Special.Edition.2011
            JvmPattern.compile(
                "^(?<title>(?![\\[(]).+?)?(?:(?:[-_\\W](?<![)\\[!]))*\\(?\\b(?<edition>(?:(?:Extended\\.|Ultimate\\.)?(?:Director\\.?s|Collector\\.?s|Theatrical|Anniversary|The\\.Uncut|Ultimate|Final|Extended|Rogue|Special|Despecialized|\\d{2,3}(?:th)?\\.Anniversary)(?:\\.(Cut|Edition|Version))?(?:\\.(Extended|Uncensored|Remastered|Unrated|Uncut|IMAX|Fan\\.?Edit))?|(?:Uncensored|Remastered|Unrated|Uncut|IMAX|Fan\\.?Edit|Edition|Restored|(?:2|3|4)in1))))\\b\\)?.{1,3}(?<year>(?:1[89]|20)\\d{2}(?!p|i|\\d+|\\]|\\W\\d+)))+(?:\\W+|_|$)(?!\\\\)",
                JvmPattern.CASE_INSENSITIVE,
            ),
            // Folder format: Blade Runner 2049 (2017)
            JvmPattern.compile(
                "^(?<title>(?![\\[(]).+?)?(?:(?:[-_\\W](?<![)\\[!]))*\\((?<year>(?:1[89]|20)\\d{2}(?!p|i|(?:1[89]|20)\\d{2}|\\]|\\W(?:1[89]|20)\\d{2})))+",
                JvmPattern.CASE_INSENSITIVE,
            ),
            // Normal format: Mission.Impossible.3.2011
            JvmPattern.compile(
                "^(?<title>(?![\\[(]).+?)?(?:(?:[-_\\W](?<![)\\[!]))*(?<year>(?:1[89]|20)\\d{2}(?!p|i|(?:1[89]|20)\\d{2}|\\]|\\W(?:1[89]|20)\\d{2})))+(?:\\W+|_|$)(?!\\\\)",
                JvmPattern.CASE_INSENSITIVE,
            ),
            // Brackets for years (unusual): Star.Wars[2015]
            JvmPattern.compile(
                "^(?<title>.+?)?(?:(?:[-_\\W](?<![\\(\\[!]))*(?<year>\\[\\w *\\]))+(?:\\W+|_|$)(?!\\\\)",
                JvmPattern.CASE_INSENSITIVE,
            ),
            // Last resort with brackets in title
            JvmPattern.compile(
                "^(?<title>.+?)?(?:(?:[-_\\W](?<![)\\[!]))*(?<year>(?:1[89]|20)\\d{2}(?!p|i|\\d+|\\]|\\W\\d+)))+(?:\\W+|_|$)(?!\\\\)",
                JvmPattern.CASE_INSENSITIVE,
            ),
        )

    // =========================================================================
    // LANGUAGE PATTERNS
    // =========================================================================

    val LANG_ENGLISH_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(english|eng|EN)\\b")

    val LANG_GERMAN_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(german|ger|DE|videomann)\\b")

    val LANG_FRENCH_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(FR|FRENCH|VOSTFR|VO|VFF|VFQ|VF2|TRUEFRENCH|SUBFRENCH)\\b")

    val LANG_SPANISH_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(spanish|spa|ES)\\b")

    val LANG_ITALIAN_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(ita|italian)\\b")

    val LANG_RUSSIAN_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(russian|rus|RU)\\b")

    val LANG_JAPANESE_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(japanese|jpn|JP)\\b")

    val LANG_KOREAN_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(korean|kor|KR)\\b")

    val LANG_CHINESE_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(chinese|chi|CN|mandarin|cantonese)\\b")

    val LANG_MULTI_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)(?<!WEB-)\\b(MULTi|DUAL|DL)\\b")

    // =========================================================================
    // COMPLETE/SEASON PACK PATTERNS
    // =========================================================================

    val COMPLETE_RE2: Re2Pattern =
        Re2Pattern.compile("(?i)\\b(COMPLETE|NTSC|PAL)[.]?DVDR?\\b")

    // =========================================================================
    // COMMON TECHNICAL TAGS TO STRIP (for simplifyTitle)
    // =========================================================================

    /**
     * Tags that should be removed before title extraction.
     * Used for simplifying the filename before parsing.
     */
    val SIMPLIFY_TAGS: Set<String> =
        setOf(
            // Resolution
            "480P", "576P", "720P", "1080P", "1080I", "2160P", "4K", "UHD", "8K",
            // Bit depth
            "10BIT", "8BIT",
            // Video codecs
            "X264", "X265", "H264", "H265", "HEVC", "AVC", "XVID", "DIVX", "AV1", "VP9",
            // Audio
            "AAC", "AC3", "DTS", "DD51", "DD5", "DDP", "EAC3", "TRUEHD", "FLAC", "MP3",
            "ATMOS", "DTSHD", "LPCM",
            // Channels
            "51", "71", "20",
            // Source tags
            "BLURAY", "BDRIP", "BRRIP", "DVDRIP", "HDRIP", "HDTV", "WEBDL", "WEBRIP",
            "WEB", "PDTV", "SDTV", "CAM", "TS", "TC", "SCR", "SCREENER",
            // Quality modifiers
            "REMUX", "PROPER", "REPACK", "REAL", "INTERNAL", "LIMITED",
            // HDR variants
            "HDR", "HDR10", "HDR10PLUS", "DV", "DOLBYVISION", "HLG", "SDR",
            // 3D
            "3D", "SBS", "HSBS", "HOU",
            // Edition
            "EXTENDED", "UNCUT", "UNRATED", "DIRECTORS", "THEATRICAL", "REMASTERED",
            "IMAX", "SPECIAL", "ANNIVERSARY", "COLLECTORS",
            // Scene
            "NFOFIX", "DIRFIX", "SAMPLEFIX", "READNFO", "NFO",
            // Other
            "COMPLETE", "FINAL", "MULTi", "DUAL",
        )
}
