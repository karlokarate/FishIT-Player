package com.fishit.player.core.metadata.parser

/**
 * Regex-based scene name parser using PTN (parse-torrent-name) inspired "peel the onion" approach.
 *
 * Algorithm:
 * 1. Apply each pattern in order, extract match, remove from working string
 * 2. Title = what remains after all patterns are applied
 * 3. Special handling for numeric titles (1917, 300, 2001) to not confuse with years
 *
 * Patterns are ordered by priority - most specific first.
 *
 * Inspired by: https://github.com/divijbindlish/parse-torrent-name
 */
class RegexSceneNameParser : SceneNameParser {

        // ==========================================================================
        // PATTERN REGISTRY (ordered by extraction priority)
        // ==========================================================================

        /**
         * Pattern definition with name, regex, and optional type. Patterns are applied in order -
         * earlier patterns have higher priority.
         */
        private data class Pattern(
                val name: String,
                val regex: Regex,
                val type: PatternType = PatternType.STRING,
                val boundary: Boolean = true, // wrap in \b...\b
        )

        private enum class PatternType {
                STRING,
                INTEGER,
                BOOLEAN
        }

        companion object {
                // Separators that surround valid matches
                private const val SEPS = """[\s._\-\[\](){}]"""

                // ==========================================================================
                // IPTV PROVIDER TAGS (must be first - highest priority for IPTV streams)
                // ==========================================================================

                /** IPTV provider prefixes like "N|", "DE|FHD|", etc. */
                private val PROVIDER_PREFIX =
                        Pattern(
                                "provider",
                                Regex(
                                        "^$SEPS*(?:" +
                                                "(?:N|NF|D|DE|EN|ES|IT|FR|PT|RU|PL|TR|AR|" +
                                                "FHD|4K|UHD|HD\\+?|SD|HDR|" +
                                                "VOD|MOVIE|FILM|SERIE|SERIES|TV|LIVE|XXX|ADULT|" +
                                                "HEVC|H265|X265|H264|X264|" +
                                                "DV|DOLBY|ATMOS|DTS|DD|AAC|" +
                                                "MULTI|DUAL|GER|ENG|GERMAN|ENGLISH|FRENCH|SPANISH|ITALIAN|" +
                                                "LATAM|BR|MX|CO|VE|CL|KIDS)" +
                                                "[|]$SEPS*)+",
                                        RegexOption.IGNORE_CASE
                                ),
                                boundary = false
                        )

                /** Bracketed provider tags: [N], [4K], (DE), etc. */
                private val PROVIDER_BRACKET =
                        Pattern(
                                "provider_bracket",
                                Regex(
                                        "[\\[\\(](N|NF|D|DE|EN|ES|IT|FR|FHD|4K|UHD|HD|SD|HDR|" +
                                                "VOD|MOVIE|FILM|SERIE|SERIES|TV|LIVE|" +
                                                "HEVC|H265|X265|H264|X264|DV|DOLBY|ATMOS|" +
                                                "MULTI|DUAL|GER|ENG|GERMAN|ENGLISH)[\\]\\)]",
                                        RegexOption.IGNORE_CASE
                                ),
                                boundary = false
                        )

                // ==========================================================================
                // SEASON/EPISODE PATTERNS (PTN-style, high priority)
                // ==========================================================================

                /** S01E02, S1E2, s01e02 - standard pattern */
                private val SEASON_EPISODE =
                        Pattern(
                                "season_episode",
                                Regex("([Ss](\\d{1,2}))[\\s._-]*[Ee](\\d{1,4})"),
                                boundary = false
                        )

                /** 1x02, 01x02 - alternative pattern */
                private val SEASON_X_EPISODE =
                        Pattern(
                                "season_x_episode",
                                Regex("(\\d{1,2})x(\\d{1,4})", RegexOption.IGNORE_CASE),
                                boundary = false
                        )

                /** Episode XX, Ep XX, Ep.XX - word pattern */
                private val EPISODE_WORD =
                        Pattern(
                                "episode_word",
                                Regex(
                                        "(?:Episode|Folge|Ep\\.?)\\s*(\\d{1,4})",
                                        RegexOption.IGNORE_CASE
                                ),
                                boundary = false
                        )

                /** Anime style: " - 1089 " (episode after dash, 2-4 digits) */
                private val ANIME_EPISODE =
                        Pattern(
                                "anime_episode",
                                Regex("\\s+-\\s*(\\d{2,4})(?:\\s|\\(|\\[|$)"),
                                boundary = false
                        )

                /** Season only: S01, Season 1 */
                private val SEASON_ONLY =
                        Pattern(
                                "season_only",
                                Regex("(?:[Ss]|Season\\s*)(\\d{1,2})(?![Ee\\dx])"),
                                boundary = false
                        )

                // ==========================================================================
                // YEAR PATTERN (must come after season/episode to avoid conflicts)
                // ==========================================================================

                /** Year in parentheses/brackets: (2024), [2019] - highest confidence */
                private val YEAR_PAREN =
                        Pattern(
                                "year_paren",
                                Regex("[\\[\\(](19\\d{2}|20\\d{2})[\\]\\)]"),
                                PatternType.INTEGER,
                                boundary = false
                        )

                /** Standalone year: 2024, 1999 - with separator boundaries */
                private val YEAR_STANDALONE =
                        Pattern(
                                "year",
                                Regex(
                                        """(?:^|[\s._\-\[\](){}])(19\d{2}|20\d{2})(?=[\s._\-\[\](){}]|$)"""
                                ),
                                PatternType.INTEGER,
                                boundary = false
                        )

                // ==========================================================================
                // QUALITY PATTERNS (PTN patterns)
                // ==========================================================================

                private val RESOLUTION =
                        Pattern(
                                "resolution",
                                // Word boundaries to avoid matching inside words
                                Regex("\\b([0-9]{3,4}p|4K|8K|UHD)\\b", RegexOption.IGNORE_CASE)
                        )

                private val QUALITY =
                        Pattern(
                                "quality",
                                Regex(
                                        // All quality tags require word boundaries to avoid matching inside words
                                        // e.g., "TS" should not match in "Jujutsu" or "Tschick"
                                        "\\b((?:PPV\\.)?[HP]DTV|(?:HD)?CAM|B[DR]Rip|(?:HD-?)?TS|" +
                                                "(?:PPV\\s)?WEB-?DL(?:\\sDVDRip)?|HDRip|DVDRip|DVDRIP|" +
                                                "CamRip|W[EB]BRip|BluRay|Blu-Ray|BDRip|DvDScr|HDTV|Telesync|PDTV|DVDSCR)\\b",
                                        RegexOption.IGNORE_CASE
                                )
                        )

                private val CODEC =
                        Pattern(
                                "codec",
                                Regex(
                                        // Word boundaries to avoid matching inside words
                                        "\\b(xvid|[hx]\\.?26[45]|HEVC|AVC|AV1|DivX|VC-?1)\\b",
                                        RegexOption.IGNORE_CASE
                                )
                        )

                private val AUDIO =
                        Pattern(
                                "audio",
                                Regex(
                                        // Word boundaries to avoid matching inside words
                                        "\\b(MP3|DD5\\.?1|Dual[\\-\\s]Audio|LiNE|DTS(?:-HD)?(?:\\sMA)?|" +
                                                "AAC[.-]?LC|AAC(?:\\.?2\\.0)?|AC3(?:\\.5\\.1)?|TrueHD|FLAC|" +
                                                "Dolby[\\s]?Atmos|Atmos|DD\\+?(?:\\d\\.\\d)?|DDP?\\d?\\.?\\d?|Opus)\\b",
                                        RegexOption.IGNORE_CASE
                                )
                        )

                private val HDR =
                        Pattern(
                                "hdr",
                                Regex(
                                        // Word boundaries to avoid matching inside words
                                        "\\b(HDR10\\+?|HDR|Dolby\\s*Vision|DV)\\b",
                                        RegexOption.IGNORE_CASE
                                )
                        )

                // ==========================================================================
                // EDITION/FLAGS (PTN patterns)
                // ==========================================================================

                private val EXTENDED =
                        Pattern(
                                "extended",
                                Regex("EXTENDED(?:[._-]?CUT)?", RegexOption.IGNORE_CASE),
                                PatternType.BOOLEAN
                        )
                private val DIRECTORS =
                        Pattern(
                                "directors",
                                Regex("Director'?s?[\\s._-]*Cut", RegexOption.IGNORE_CASE),
                                PatternType.BOOLEAN
                        )
                private val UNRATED =
                        Pattern(
                                "unrated",
                                Regex("UNRATED", RegexOption.IGNORE_CASE),
                                PatternType.BOOLEAN
                        )
                private val THEATRICAL =
                        Pattern(
                                "theatrical",
                                Regex("THEATRICAL", RegexOption.IGNORE_CASE),
                                PatternType.BOOLEAN
                        )
                private val REMASTERED =
                        Pattern(
                                "remastered",
                                Regex("REMASTERED", RegexOption.IGNORE_CASE),
                                PatternType.BOOLEAN
                        )
                private val PROPER =
                        Pattern(
                                "proper",
                                Regex("PROPER", RegexOption.IGNORE_CASE),
                                PatternType.BOOLEAN
                        )
                private val REPACK =
                        Pattern(
                                "repack",
                                Regex("REPACK", RegexOption.IGNORE_CASE),
                                PatternType.BOOLEAN
                        )
                private val IMAX =
                        Pattern("imax", Regex("IMAX", RegexOption.IGNORE_CASE), PatternType.BOOLEAN)
                private val THREE_D =
                        Pattern("3d", Regex("3D", RegexOption.IGNORE_CASE), PatternType.BOOLEAN)

                // ==========================================================================
                // OTHER PATTERNS
                // ==========================================================================

                private val CONTAINER =
                        Pattern(
                                "container",
                                Regex(
                                        "\\.(mkv|avi|mp4|m4v|mov|wmv|flv|webm|mpg|mpeg|m2ts|ts|vob|iso|3gp)\$",
                                        RegexOption.IGNORE_CASE
                                ),
                                boundary = false
                        )

                private val LANGUAGE =
                        Pattern(
                                "language",
                                Regex(
                                        "\\b(German|English|French|Spanish|Italian|Portuguese|Russian|Polish|Turkish|Arabic|" +
                                                "Deutsch|Englisch|Multi|Dual|" +
                                                "GER|ENG|FRE|SPA|ITA|POR|RUS|POL|TUR|ARA|DL)\\b",
                                        RegexOption.IGNORE_CASE
                                )
                        )

                private val RELEASE_GROUP =
                        Pattern(
                                "group",
                                // Must contain at least one letter to avoid matching years like "1994"
                                Regex("-\\s*([A-Za-z][A-Za-z0-9]*|[A-Za-z0-9]*[A-Za-z][A-Za-z0-9]*)\\s*\$"),
                                boundary = false
                        )

                private val WEBSITE =
                        Pattern("website", Regex("^\\[\\s*([^\\]]+?)\\s*\\]"), boundary = false)

                private val CHANNEL_TAG =
                        Pattern("channel", Regex("@[A-Za-z0-9_]+"), boundary = false)

                private val EXTERNAL_ID =
                        Pattern(
                                "external_id",
                                Regex(
                                        "(?:tmdb[:\\-_]?\\d+|imdb[:\\-_]?tt\\d+|tt\\d{7,}|" +
                                                "\\[tmdb[:\\-_]?\\d+\\]|\\(tt\\d+\\))",
                                        RegexOption.IGNORE_CASE
                                ),
                                boundary = false
                        )

                private val SIZE =
                        Pattern(
                                "size",
                                Regex("(\\d+(?:\\.\\d+)?(?:GB|MB))", RegexOption.IGNORE_CASE)
                        )

                // ==========================================================================
                // PATTERN ORDER (peel the onion - extract in this order)
                // ==========================================================================

                private val PATTERNS =
                        listOf(
                                // 1. Remove container extension first
                                CONTAINER,
                                // 2. Remove IPTV provider tags
                                PROVIDER_PREFIX,
                                PROVIDER_BRACKET,
                                // 3. Remove external IDs and website tags
                                EXTERNAL_ID,
                                WEBSITE,
                                CHANNEL_TAG,
                                // 4. Extract season/episode (before year to handle conflicts)
                                SEASON_EPISODE,
                                SEASON_X_EPISODE,
                                EPISODE_WORD,
                                ANIME_EPISODE,
                                SEASON_ONLY,
                                // 5. Extract quality tags (before year)
                                RESOLUTION,
                                QUALITY,
                                CODEC,
                                AUDIO,
                                HDR,
                                // 6. Extract edition flags
                                EXTENDED,
                                DIRECTORS,
                                UNRATED,
                                THEATRICAL,
                                REMASTERED,
                                PROPER,
                                REPACK,
                                IMAX,
                                THREE_D,
                                // 7. Extract language
                                LANGUAGE,
                                // 8. Extract size
                                SIZE,
                                // 9. Extract release group (at end)
                                RELEASE_GROUP,
                                // 10. Extract year LAST (to handle numeric titles like "1917",
                                // "300")
                                YEAR_PAREN,
                                YEAR_STANDALONE,
                        )

                // Known numeric movie titles that should NOT be treated as years
                private val NUMERIC_TITLES =
                        setOf(
                                "1917",
                                "1984",
                                "1776",
                                "1492",
                                "1941",
                                "1922",
                                "1918",
                                "1883",
                                "1923",
                                "300",
                                "2001",
                                "2010",
                                "2012",
                                "2036",
                                "2046",
                                "2067",
                                "9",
                                "8",
                                "7",
                                "6",
                                "5",
                                "4",
                                "3",
                                "2",
                                "1",
                                "12",
                                "13",
                                "21",
                                "22",
                                "23",
                                "24",
                                "25",
                                "42",
                                "69",
                                "71",
                                "77",
                                "84",
                                "88",
                                "96",
                                "99"
                        )

                // File extensions for cleanup
                private val FILE_EXTENSION_REGEX =
                        Regex(
                                "\\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|m2ts|ts|vob|iso|3gp|zip|rar|7z)\$",
                                RegexOption.IGNORE_CASE
                        )
        }

        // ==========================================================================
        // PARSING IMPLEMENTATION
        // ==========================================================================

        override fun parse(filename: String): ParsedSceneInfo {
                var working = filename.trim()

                // Early exit for empty/garbage
                if (working.isBlank()) {
                        return ParsedSceneInfo(
                                title = filename.ifBlank { "Unknown" },
                                year = null,
                                isEpisode = false,
                                season = null,
                                episode = null,
                                quality = null,
                                edition = null,
                                extraTags = emptyList()
                        )
                }

                // Track extracted values
                val extracted = mutableMapOf<String, Any>()
                var season: Int? = null
                var episode: Int? = null
                var year: Int? = null

                // ==========================================================================
                // STEP 1: Remove file extension
                // ==========================================================================
                working = working.replace(FILE_EXTENSION_REGEX, "")

                // ==========================================================================
                // STEP 2: Check for numeric title at start (before year extraction can claim it)
                // ==========================================================================
                val numericTitleMatch = Regex("^(\\d{1,4})(?:[\\s._\\-]|$)").find(working)
                val potentialNumericTitle = numericTitleMatch?.groupValues?.get(1)
                val hasNumericTitle =
                        potentialNumericTitle != null &&
                                (NUMERIC_TITLES.contains(potentialNumericTitle) ||
                                        // Also protect 3-4 digit numbers at start that look like
                                        // titles, not years
                                        (potentialNumericTitle.length <= 3 &&
                                                potentialNumericTitle.toIntOrNull() != null))

                // ==========================================================================
                // STEP 3: Apply patterns in order (peel the onion)
                // ==========================================================================
                for (pattern in PATTERNS) {
                        // Skip year extraction if we detected a numeric title at the start
                        if (hasNumericTitle &&
                                        potentialNumericTitle != null &&
                                        (pattern.name == "year" || pattern.name == "year_paren")
                        ) {
                                // Check if the year pattern would match our numeric title
                                val yearMatch = pattern.regex.find(working)
                                if (yearMatch != null &&
                                                yearMatch.value.contains(potentialNumericTitle)
                                ) {
                                        // Skip this year match - it's our title
                                        continue
                                }
                        }

                        val match = pattern.regex.find(working)
                        if (match != null) {
                                // Extract the value
                                when (pattern.name) {
                                        "season_episode" -> {
                                                season = match.groupValues[2].toIntOrNull()
                                                episode = match.groupValues[3].toIntOrNull()
                                        }
                                        "season_x_episode" -> {
                                                season = match.groupValues[1].toIntOrNull()
                                                episode = match.groupValues[2].toIntOrNull()
                                        }
                                        "episode_word", "anime_episode" -> {
                                                episode = match.groupValues[1].toIntOrNull()
                                        }
                                        "season_only" -> {
                                                season = match.groupValues[1].toIntOrNull()
                                        }
                                        "year_paren", "year" -> {
                                                val yearCandidate =
                                                        match.groupValues[1].toIntOrNull()
                                                if (yearCandidate != null &&
                                                                yearCandidate in 1900..2099
                                                ) {
                                                        // Check if year is part of a timestamp
                                                        // (e.g., 20231205)
                                                        if (!isPartOfTimestamp(match, working)) {
                                                                year = yearCandidate
                                                        } else {
                                                                continue // Don't remove timestamp
                                                                // from working string
                                                        }
                                                }
                                        }
                                        else -> {
                                                when (pattern.type) {
                                                        PatternType.BOOLEAN ->
                                                                extracted[pattern.name] = true
                                                        PatternType.INTEGER ->
                                                                extracted[pattern.name] =
                                                                        match.groupValues
                                                                                .getOrNull(1)
                                                                                ?.toIntOrNull()
                                                                                ?: 0
                                                        PatternType.STRING ->
                                                                extracted[pattern.name] =
                                                                        match.groupValues.getOrNull(
                                                                                1
                                                                        )
                                                                                ?: match.value
                                                }
                                        }
                                }

                                // Remove matched portion from working string (peel the onion)
                                working = working.replace(match.value, " ")
                        }
                }

                // ==========================================================================
                // STEP 4: Clean up remaining string to get title
                // ==========================================================================
                var title =
                        working.replace(Regex("[._]"), " ") // Dots and underscores to spaces
                                .replace(
                                        Regex("[\\[\\](){}|*#]+"),
                                        " "
                                ) // Remove brackets and symbols
                                .replace(
                                        Regex("^[\\-\\s:]+|[\\-\\s:]+$"),
                                        ""
                                ) // Trim leading/trailing
                                .replace(Regex("\\s*-\\s*-\\s*"), " - ") // Normalize double dashes
                                .replace(Regex("\\s+"), " ") // Collapse multiple spaces
                                .trim()

                // Remove any remaining non-alphanumeric prefix/suffix
                title =
                        title.replace(Regex("^[^a-zA-Z0-9äöüÄÖÜßéèêàâùûôîçñÀ-ÿ]+"), "")
                                .replace(Regex("[^a-zA-Z0-9äöüÄÖÜßéèêàâùûôîçñÀ-ÿ!?]+$"), "")
                                .trim()

                // ==========================================================================
                // STEP 5: Fallback if title is empty
                // ==========================================================================
                if (title.isBlank()) {
                        title =
                                filename.replace(FILE_EXTENSION_REGEX, "")
                                        .replace(Regex("[\\[\\](){}|*#_.]"), " ")
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                }

                if (title.isBlank()) {
                        title = "Unknown"
                }

                // ==========================================================================
                // STEP 6: Build result
                // ==========================================================================
                val edition =
                        if (extracted.containsKey("extended") ||
                                        extracted.containsKey("directors") ||
                                        extracted.containsKey("unrated") ||
                                        extracted.containsKey("theatrical") ||
                                        extracted.containsKey("remastered") ||
                                        extracted.containsKey("proper") ||
                                        extracted.containsKey("repack") ||
                                        extracted.containsKey("imax") ||
                                        extracted.containsKey("3d")
                        ) {
                                EditionInfo(
                                        extended = extracted["extended"] as? Boolean ?: false,
                                        directors = extracted["directors"] as? Boolean ?: false,
                                        unrated = extracted["unrated"] as? Boolean ?: false,
                                        theatrical = extracted["theatrical"] as? Boolean ?: false,
                                        threeD = extracted["3d"] as? Boolean ?: false,
                                        imax = extracted["imax"] as? Boolean ?: false,
                                        remastered = extracted["remastered"] as? Boolean ?: false,
                                        proper = extracted["proper"] as? Boolean ?: false,
                                        repack = extracted["repack"] as? Boolean ?: false,
                                )
                        } else null

                val quality =
                        if (extracted.containsKey("resolution") ||
                                        extracted.containsKey("quality") ||
                                        extracted.containsKey("codec") ||
                                        extracted.containsKey("audio") ||
                                        extracted.containsKey("hdr") ||
                                        extracted.containsKey("group")
                        ) {
                                QualityInfo(
                                        resolution = extracted["resolution"] as? String,
                                        source = extracted["quality"] as? String,
                                        codec = extracted["codec"] as? String,
                                        audio = extracted["audio"] as? String,
                                        hdr = extracted["hdr"] as? String,
                                        group = extracted["group"] as? String,
                                )
                        } else null

                return ParsedSceneInfo(
                        title = title,
                        year = year,
                        isEpisode = season != null || episode != null,
                        season = season,
                        episode = episode,
                        quality = quality,
                        edition = edition,
                        extraTags = emptyList(),
                )
        }

        /**
         * Check if a year match is part of a timestamp (e.g., 20231205). Returns true if the year
         * is followed by 4+ more digits.
         */
        private fun isPartOfTimestamp(match: MatchResult, source: String): Boolean {
                val endPos = match.range.last + 1
                if (endPos < source.length) {
                        val after = source.substring(endPos, minOf(endPos + 4, source.length))
                        if (after.matches(Regex("\\d{4}.*"))) {
                                return true
                        }
                }
                // Also check if preceded by digits
                val startPos = match.range.first
                if (startPos > 0 && source[startPos - 1].isDigit()) {
                        return true
                }
                return false
        }
}
