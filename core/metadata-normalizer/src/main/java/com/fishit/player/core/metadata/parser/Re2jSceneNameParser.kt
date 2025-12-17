/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Two-Stage Scene Name Parser
 *
 * Architecture:
 * 1. CLASSIFY FIRST - Determine if Series, Movie, or Unknown (cheap)
 * 2. PARSE SECOND - Run only the relevant parse path
 *
 * Rules:
 * - For UNKNOWN (non-Xtream): classify first, then parse
 * - For Xtream: mediaType comes from endpoint, only do title cleaning
 * - Never infer mediaType from parsing for Xtream sources
 *
 * Uses tokenization + small RE2J patterns for O(n) guaranteed safety.
 * No lookahead/lookbehind - all validation done via code.
 */
package com.fishit.player.core.metadata.parser

import com.google.re2j.Pattern as Re2Pattern

/**
 * Content candidate type determined by classification step.
 */
enum class ContentCandidate {
    SERIES,  // SxxEyy, 1x02, Folge detected
    MOVIE,   // Valid year token detected
    UNKNOWN, // Neither series markers nor year found
}

/**
 * RE2J-based scene name parser with two-stage processing:
 * 1. Classify (series vs movie vs unknown)
 * 2. Parse (only relevant patterns)
 *
 * All patterns are small, RE2J-safe, with O(n) guaranteed time.
 */
class Re2jSceneNameParser : SceneNameParser {

    // =========================================================================
    // CLASSIFICATION PATTERNS (Stage 1 - cheap checks)
    // =========================================================================

    // Series markers - if ANY of these match, it's a SeriesCandidate
    // Note: These patterns allow separators (space, dot, underscore) between S and E
    private val sxeClassifyPattern = Re2Pattern.compile("(?i)S\\d{1,2}[._ ]?E\\d{1,4}")
    private val xFormatClassifyPattern = Re2Pattern.compile("(?i)\\d{1,2}x\\d{2,3}")
    private val folgeClassifyPattern = Re2Pattern.compile("(?i)Folge[._ ]?\\d{1,4}")
    private val episodeClassifyPattern = Re2Pattern.compile("(?i)(?:^|[._ ])E\\d{1,4}(?:[._ ]|$)")

    // Year pattern for classification (will be validated in code)
    private val yearClassifyPattern = Re2Pattern.compile("(?:^|[._ \\-\\(\\[])(?:19|20)\\d{2}(?:[._ \\-\\)\\]]|$)")

    // Timestamp pattern - years that are actually timestamps (20231205, etc.)
    private val timestampPattern = Re2Pattern.compile("(?:19|20)\\d{6}")

    // =========================================================================
    // EXTRACTION PATTERNS (Stage 2 - used only in specific paths)
    // =========================================================================

    // Season/Episode extraction - allow separators between S and E
    private val sxeExtractPattern = Re2Pattern.compile("(?i)S(\\d{1,2})[._ ]?E(\\d{1,4})")
    private val xFormatExtractPattern = Re2Pattern.compile("(?i)(\\d{1,2})x(\\d{2,3})")
    private val folgeExtractPattern = Re2Pattern.compile("(?i)Folge[._ ]?(\\d{1,4})")
    private val seasonOnlyPattern = Re2Pattern.compile("(?i)(?:^|[._ ])S(\\d{1,2})(?:[._ ]|$)")
    private val episodeOnlyPattern = Re2Pattern.compile("(?i)(?:^|[._ ])E(\\d{1,4})(?:[._ ]|$)")

    // Year extraction (for movie path)
    private val yearExtractPattern = Re2Pattern.compile("(?:^|[._ \\-\\(\\[])((19|20)\\d{2})(?:[._ \\-\\)\\]]|$)")

    // Resolution patterns
    private val res2160pPattern = Re2Pattern.compile("(?i)(?:2160p|4k|UHD)")
    private val res1080pPattern = Re2Pattern.compile("(?i)1080[ip]")
    private val res720pPattern = Re2Pattern.compile("(?i)(?:720[ip]|960p)")
    private val res480pPattern = Re2Pattern.compile("(?i)480[ip]")

    // Source patterns
    private val blurayPattern = Re2Pattern.compile("(?i)(?:Blu-?Ray|BluRay|BDRIP|BRRip|BD(?:25|50)?|HDDVD)")
    private val webdlPattern = Re2Pattern.compile("(?i)(?:WEB-?DL|WEBDL|WEBRip|WEB|AMZN|NF|NETFLIX|DSNP|HMAX|ATVP)")
    private val hdtvPattern = Re2Pattern.compile("(?i)(?:HDTV|PDTV|SDTV|TVRip|DSR)")
    private val dvdPattern = Re2Pattern.compile("(?i)(?:DVD-?R?|DVDRip|DVDSCR)")

    // Video codec patterns
    private val x265Pattern = Re2Pattern.compile("(?i)(?:x265|HEVC|H\\.?265)")
    private val x264Pattern = Re2Pattern.compile("(?i)(?:x264|H\\.?264|AVC)")

    // Audio codec patterns
    private val dtsPattern = Re2Pattern.compile("(?i)(?:DTS-?HD|DTS-?MA|DTS)")
    private val ac3Pattern = Re2Pattern.compile("(?i)(?:AC3|DD|EAC3|DDP)")
    private val aacPattern = Re2Pattern.compile("(?i)AAC")

    // Edition patterns
    private val extendedPattern = Re2Pattern.compile("(?i)(?:Extended|Uncut|Directors?|DC|Theatrical|Unrated|Remastered)")
    private val imaxPattern = Re2Pattern.compile("(?i)IMAX")
    private val threeDPattern = Re2Pattern.compile("(?i)3D")
    private val properPattern = Re2Pattern.compile("(?i)(?:PROPER|REPACK)")

    // Language patterns
    private val germanPattern = Re2Pattern.compile("(?i)(?:German|GER)(?:[._ ]|$)")
    private val multiPattern = Re2Pattern.compile("(?i)(?:MULTi|DUAL)(?:[._ ]|$)")

    // Cleanup patterns
    private val noisePattern = Re2Pattern.compile("(?i)(?:PROPER|REAL|READNFO|READ\\.?NFO|INTERNAL)")
    private val subgroupPattern = Re2Pattern.compile("^\\[([^\\]]+)\\]")
    // Group suffix: only match after separator (space, dot, underscore), not after letter
    private val groupSuffixPattern = Re2Pattern.compile("[._ ]-([A-Za-z0-9]{2,12})$")
    private val hashPattern = Re2Pattern.compile("\\[[A-Fa-f0-9]{8}\\]")
    private val providerPrefixPattern = Re2Pattern.compile("^[A-Z]\\|")
    private val channelTagPattern = Re2Pattern.compile("@[A-Za-z0-9_]+")

    // File extension
    private val extensionPattern = Re2Pattern.compile("(?i)\\.(?:mkv|mp4|avi|m4v|mov|wmv|flv|webm|ts|m2ts|vob|iso)$")

    // Technical tag boundary - where tech info starts
    private val techBoundaryPattern = Re2Pattern.compile(
        "(?i)(?:^|[._ ])(2160p|1080[ip]|720[ip]|480[ip]|4k|UHD|" +
            "Blu-?Ray|BluRay|BDRIP|WEB-?DL|WEBDL|WEBRip|HDTV|DVD|" +
            "x264|x265|HEVC|H\\.?264|H\\.?265|AVC|" +
            "German|GER|MULTi|DUAL|DL|" +
            "DTS|AC3|AAC|DD|" +
            "Extended|Directors|Remastered|PROPER)(?:[._ ]|$)",
    )

    // =========================================================================
    // MAIN PARSE METHOD
    // =========================================================================

    override fun parse(filename: String): ParsedSceneInfo {
        if (filename.isBlank()) {
            return ParsedSceneInfo(title = "")
        }

        // Step 1: Pre-clean input
        val cleaned = preclean(filename)

        // Step 2: CLASSIFY (cheap) - determine content type
        val candidate = classify(cleaned)

        // Step 3: PARSE (targeted) - only run relevant patterns
        return when (candidate) {
            ContentCandidate.SERIES -> parseAsSeries(cleaned, filename)
            ContentCandidate.MOVIE -> parseAsMovie(cleaned, filename)
            ContentCandidate.UNKNOWN -> parseAsUnknown(cleaned, filename)
        }
    }

    // =========================================================================
    // STAGE 1: CLASSIFICATION (cheap, determines parse path)
    // =========================================================================

    /**
     * Classify input as Series, Movie, or Unknown.
     * This is a CHEAP operation - just check for markers, don't extract.
     */
    private fun classify(input: String): ContentCandidate {
        // Check for series markers FIRST (most specific)
        if (hasSeriesMarkers(input)) {
            return ContentCandidate.SERIES
        }

        // Check for valid year (not timestamp)
        if (hasValidYear(input)) {
            return ContentCandidate.MOVIE
        }

        return ContentCandidate.UNKNOWN
    }

    /**
     * Check if input has series markers: SxxEyy, 1x02, Folge, etc.
     */
    private fun hasSeriesMarkers(input: String): Boolean {
        return sxeClassifyPattern.matcher(input).find() ||
            xFormatClassifyPattern.matcher(input).find() ||
            folgeClassifyPattern.matcher(input).find() ||
            episodeClassifyPattern.matcher(input).find()
    }

    /**
     * Check if input has a valid year (1900-2099) that's not a timestamp.
     */
    private fun hasValidYear(input: String): Boolean {
        // First check if there's a timestamp pattern - if so, it's not a year
        if (timestampPattern.matcher(input).find()) {
            return false
        }

        // Check for year pattern
        val matcher = yearClassifyPattern.matcher(input)
        while (matcher.find()) {
            val match = matcher.group()
            // Extract just the 4 digits
            val yearStr = match.replace(Regex("[^0-9]"), "")
            if (yearStr.length == 4) {
                val year = yearStr.toIntOrNull()
                if (year != null && year in 1900..2099) {
                    return true
                }
            }
        }
        return false
    }

    // =========================================================================
    // STAGE 2: TARGETED PARSING
    // =========================================================================

    /**
     * Parse as Series: extract season, episode, title.
     */
    private fun parseAsSeries(input: String, original: String): ParsedSceneInfo {
        var season: Int? = null
        var episode: Int? = null
        var title = input

        // Extract SxxEyy
        val sxeMatcher = sxeExtractPattern.matcher(input)
        if (sxeMatcher.find()) {
            season = sxeMatcher.group(1)?.toIntOrNull()
            episode = sxeMatcher.group(2)?.toIntOrNull()
            // Title is everything before SxxEyy
            val pos = sxeMatcher.start()
            if (pos > 0) {
                title = input.substring(0, pos)
            }
        } else {
            // Try 1x02 format
            val xMatcher = xFormatExtractPattern.matcher(input)
            if (xMatcher.find()) {
                season = xMatcher.group(1)?.toIntOrNull()
                episode = xMatcher.group(2)?.toIntOrNull()
                val pos = xMatcher.start()
                if (pos > 0) {
                    title = input.substring(0, pos)
                }
            } else {
                // Try Folge format
                val folgeMatcher = folgeExtractPattern.matcher(input)
                if (folgeMatcher.find()) {
                    season = 1 // Folge implies season 1
                    episode = folgeMatcher.group(1)?.toIntOrNull()
                    val pos = folgeMatcher.start()
                    if (pos > 0) {
                        title = input.substring(0, pos)
                    }
                } else {
                    // Try standalone episode (Exx)
                    val epMatcher = episodeOnlyPattern.matcher(input)
                    if (epMatcher.find()) {
                        episode = epMatcher.group(1)?.toIntOrNull()
                        val pos = epMatcher.start()
                        if (pos > 0) {
                            title = input.substring(0, pos)
                        }
                    }
                }
            }
        }

        // Clean the title
        val cleanedTitle = cleanTitle(title)

        // Extract quality info
        val quality = extractQuality(input)

        // Extract edition info
        val edition = extractEdition(input)

        return ParsedSceneInfo(
            title = cleanedTitle,
            year = null, // Series don't have years in the same way
            isEpisode = true,
            season = season,
            episode = episode,
            quality = quality,
            edition = edition,
        )
    }

    /**
     * Parse as Movie: extract year, title.
     */
    private fun parseAsMovie(input: String, original: String): ParsedSceneInfo {
        var year: Int? = null
        var title = input

        // Find the tech boundary first
        val techPos = findTechBoundary(input)

        // Find year - prefer the LAST valid year before tech boundary
        val yearMatcher = yearExtractPattern.matcher(input)
        var lastYear: Int? = null
        var lastYearPos = -1

        while (yearMatcher.find()) {
            val yearStr = yearMatcher.group(1)
            val pos = yearMatcher.start()

            // Only consider years before tech boundary (or if no tech boundary)
            if (techPos < 0 || pos < techPos) {
                val y = yearStr?.toIntOrNull()
                if (y != null && y in 1900..2099) {
                    lastYear = y
                    lastYearPos = pos
                }
            }
        }

        year = lastYear

        // Title is everything before the year (or before tech boundary if no year)
        if (lastYearPos > 0) {
            title = input.substring(0, lastYearPos)
        } else if (techPos > 0) {
            title = input.substring(0, techPos)
        }

        // Clean the title
        val cleanedTitle = cleanTitle(title)

        // Extract quality info
        val quality = extractQuality(input)

        // Extract edition info
        val edition = extractEdition(input)

        return ParsedSceneInfo(
            title = cleanedTitle,
            year = year,
            isEpisode = false,
            season = null,
            episode = null,
            quality = quality,
            edition = edition,
        )
    }

    /**
     * Parse as Unknown: only clean title, extract tech info.
     * Do NOT guess mediaType.
     */
    private fun parseAsUnknown(input: String, original: String): ParsedSceneInfo {
        var title = input

        // Find tech boundary
        val techPos = findTechBoundary(input)
        if (techPos > 0) {
            title = input.substring(0, techPos)
        }

        // Clean the title
        val cleanedTitle = cleanTitle(title)

        // Extract quality info (for display purposes)
        val quality = extractQuality(input)

        return ParsedSceneInfo(
            title = cleanedTitle,
            year = null,
            isEpisode = false,
            season = null,
            episode = null,
            quality = quality,
            edition = EditionInfo(),
        )
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Pre-clean input: remove extension, provider prefix, channel tags, hash.
     */
    private fun preclean(input: String): String {
        var result = input

        // Remove file extension
        result = extensionPattern.matcher(result).replaceAll("")

        // Remove provider prefix (e.g., "N|")
        result = providerPrefixPattern.matcher(result).replaceAll("")

        // Remove channel tags (e.g., "@ArcheMovie")
        result = channelTagPattern.matcher(result).replaceAll("")

        // Remove hash suffixes (e.g., "[ABCD1234]")
        result = hashPattern.matcher(result).replaceAll("")

        // Collapse multiple spaces/separators
        result = result.replace(Regex("[._ ]+"), " ").trim()

        return result
    }

    /**
     * Find position of first technical tag (resolution, source, codec, etc.)
     * Returns -1 if not found.
     */
    private fun findTechBoundary(input: String): Int {
        val matcher = techBoundaryPattern.matcher(input)
        return if (matcher.find()) matcher.start() else -1
    }

    /**
     * Clean a title: remove noise, convert separators, handle hyphens.
     */
    private fun cleanTitle(input: String): String {
        var title = input.trim()

        // Remove subgroup prefix [SubsPlease]
        title = subgroupPattern.matcher(title).replaceAll("").trim()

        // Remove noise patterns
        title = noisePattern.matcher(title).replaceAll("").trim()

        // Remove release group suffix -GROUP
        title = groupSuffixPattern.matcher(title).replaceAll("").trim()

        // Convert separators to spaces, preserving hyphens in words
        title = convertSeparators(title)

        // Collapse multiple spaces
        title = title.replace(Regex("\\s+"), " ").trim()

        // Remove trailing separators
        title = title.trimEnd('.', '_', '-', ' ')

        return title.ifBlank { "Unknown" }
    }

    /**
     * Convert dots and underscores to spaces, but preserve hyphens between letters.
     * X-Men stays X-Men, Spider-Man stays Spider-Man
     */
    private fun convertSeparators(input: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < input.length) {
            val char = input[i]

            when {
                // Hyphen between letters: keep it
                char == '-' && i > 0 && i < input.length - 1 &&
                    input[i - 1].isLetter() && input[i + 1].isLetter() -> {
                    result.append(char)
                }
                // Dot or underscore: convert to space
                char == '.' || char == '_' -> {
                    result.append(' ')
                }
                // Hyphen not between letters: convert to space
                char == '-' -> {
                    result.append(' ')
                }
                // Everything else: keep
                else -> {
                    result.append(char)
                }
            }
            i++
        }

        return result.toString()
    }

    /**
     * Extract quality information from input.
     */
    private fun extractQuality(input: String): QualityInfo {
        val resolution = when {
            res2160pPattern.matcher(input).find() -> "2160p"
            res1080pPattern.matcher(input).find() -> "1080p"
            res720pPattern.matcher(input).find() -> "720p"
            res480pPattern.matcher(input).find() -> "480p"
            else -> null
        }

        val source = when {
            blurayPattern.matcher(input).find() -> "BluRay"
            webdlPattern.matcher(input).find() -> "WEB-DL"
            hdtvPattern.matcher(input).find() -> "HDTV"
            dvdPattern.matcher(input).find() -> "DVD"
            else -> null
        }

        val codec = when {
            x265Pattern.matcher(input).find() -> "x265"
            x264Pattern.matcher(input).find() -> "x264"
            else -> null
        }

        val audio = when {
            dtsPattern.matcher(input).find() -> "DTS"
            ac3Pattern.matcher(input).find() -> "DD"
            aacPattern.matcher(input).find() -> "AAC"
            else -> null
        }

        // Extract release group
        val group = extractReleaseGroup(input)

        return QualityInfo(
            resolution = resolution,
            source = source,
            codec = codec,
            audio = audio,
            hdr = null,
            group = group,
        )
    }

    /**
     * Extract release group from input.
     */
    private fun extractReleaseGroup(input: String): String? {
        // Check for anime subgroup first
        val subMatcher = subgroupPattern.matcher(input)
        if (subMatcher.find()) {
            return subMatcher.group(1)
        }

        // Check for -GROUP suffix
        val groupMatcher = groupSuffixPattern.matcher(input)
        if (groupMatcher.find()) {
            val group = groupMatcher.group(1)
            // Validate it's not a codec/resolution
            val invalid = setOf(
                "DL", "HD", "SD", "720P", "1080P", "2160P", "4K",
                "X264", "X265", "HEVC", "H264", "H265", "AVC",
                "AAC", "AC3", "DTS", "FLAC",
                "WEB", "WEBDL", "WEBRIP", "BLURAY", "BDRIP",
            )
            if (group?.uppercase() !in invalid) {
                return group
            }
        }

        return null
    }

    /**
     * Extract edition information from input.
     */
    private fun extractEdition(input: String): EditionInfo {
        return EditionInfo(
            extended = extendedPattern.matcher(input).find() &&
                input.contains(Regex("(?i)Extended|Uncut|Collector")),
            directors = input.contains(Regex("(?i)Director|DC")),
            unrated = input.contains(Regex("(?i)Unrated|Uncensored")),
            theatrical = input.contains(Regex("(?i)Theatrical")),
            threeD = threeDPattern.matcher(input).find(),
            imax = imaxPattern.matcher(input).find(),
            remastered = input.contains(Regex("(?i)Remastered|Anniversary|Restored")),
            proper = properPattern.matcher(input).find(),
            repack = input.contains(Regex("(?i)REPACK|RERIP")),
        )
    }
}
