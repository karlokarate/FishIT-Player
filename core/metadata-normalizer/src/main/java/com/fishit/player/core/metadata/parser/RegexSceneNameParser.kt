package com.fishit.player.core.metadata.parser

/**
 * Regex-based scene name parser.
 *
 * Extracts structured metadata from media filenames using curated regex patterns inspired by
 * video-filename-parser, guessit, and scene-release-parser-php.
 *
 * Parsing algorithm:
 * 1. Preprocessing: Remove extension, provider tags, normalize separators
 * 2. Tag extraction: Edition, quality, group, season/episode, year
 * 3. Title extraction: Remove all tags, clean up separators
 * 4. Validation: Ensure title non-empty, validate ranges
 *
 * Deterministic: Same input always produces same output.
 */
class RegexSceneNameParser : SceneNameParser {
        companion object {
                // File extensions to remove
                private val FILE_EXTENSIONS =
                        Regex(
                                "\\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|m2ts|ts|vob|iso|3gp|zip|rar|7z)$",
                                RegexOption.IGNORE_CASE,
                        )

                // ========== IPTV/XTREAM PROVIDER TAGS ==========
                // Common provider prefixes: N|, NF|, D|, DE|, EN|, FHD|, 4K|, UHD|, HD|, SD|, VOD|,
                // etc.
                // These appear at the start of IPTV stream titles and should be stripped

                // Provider tag prefixes (pipe-separated ONLY)
                // Pattern: strips leading sequences like "N|", "NF|", "D|DE|FHD|", etc.
                // IMPORTANT: Tags MUST be followed by | or other IPTV separators to avoid false
                // positives
                // e.g., "Die Maske" should NOT match "D" as provider tag
                // Single-letter tags (D, N, etc.) REQUIRE pipe separator
                // Multi-letter tags (MOVIE, FILM, etc.) also require separator to avoid matching
                // word starts
                private val PROVIDER_PREFIX_REGEX =
                        Regex(
                                "^" + // Must be at start
                                "[\\s\\[\\]()]*" + // Optional leading whitespace/brackets only
                                        "(?:" +
                                        "(?:N|NF|D|DE|EN|ES|IT|FR|PT|RU|PL|TR|AR|" + // Language/provider codes
                                        "FHD|4K|UHD|HD\\+?|SD|HDR|" + // Quality prefixes
                                        "VOD|MOVIE|FILM|SERIE|SERIES|TV|LIVE|XXX|ADULT|" + // Content type
                                        "HEVC|H265|X265|H264|X264|" + // Codec prefixes
                                        "DV|DOLBY|ATMOS|DTS|DD|AAC|" + // Audio prefixes
                                        "MULTI|DUAL|GER|ENG|GERMAN|ENGLISH|FRENCH|SPANISH|ITALIAN|" + // Language prefixes
                                        "LATAM|BR|MX|CO|VE|CL" + // Latin American country codes
                                        ")" +
                                        "[|]" + // REQUIRED: pipe separator after tag
                                        "[\\s]*" + // Optional trailing space
                                        ")+", // One or more occurrences
                                RegexOption.IGNORE_CASE,
                        )

                // Bracketed provider tags anywhere in string: [N], [NF], [4K], [HD], (DE), etc.
                private val PROVIDER_BRACKET_REGEX =
                        Regex(
                                "[\\[\\(]+" +
                                        "(N|NF|D|DE|EN|ES|IT|FR|FHD|4K|UHD|HD|SD|HDR|" +
                                        "VOD|MOVIE|FILM|SERIE|SERIES|TV|LIVE|" +
                                        "HEVC|H265|X265|H264|X264|DV|DOLBY|ATMOS|" +
                                        "MULTI|DUAL|GER|ENG|GERMAN|ENGLISH)" +
                                        "[\\]\\)]+",
                                RegexOption.IGNORE_CASE,
                        )

                // TMDb/IMDb suffixes: tmdb-12345, [tmdb:603], (tt0137523), imdb-tt0172495
                private val EXTERNAL_ID_SUFFIX_REGEX =
                        Regex(
                                "[\\s\\-_]*" +
                                        "(?:" +
                                        "tmdb[:\\-_]?\\d+" + // tmdb-12345, tmdb:12345
                                        "|" +
                                        "imdb[:\\-_]?tt\\d+" + // imdb-tt12345
                                        "|" +
                                        "tt\\d{7,}" + // tt1234567 (IMDb ID standalone)
                                        "|" +
                                        "\\[tmdb[:\\-_]?\\d+\\]" + // [tmdb:12345]
                                        "|" +
                                        "\\(tt\\d+\\)" + // (tt1234567)
                                        ")",
                                RegexOption.IGNORE_CASE,
                        )

                // Resolution patterns
                private val RESOLUTION_REGEX =
                        Regex(
                                "\\b(480p|576p|720p|1080p|2160p|4320p|8K|4K|UHD)\\b",
                                RegexOption.IGNORE_CASE,
                        )

                // Video codec patterns
                private val CODEC_REGEX =
                        Regex(
                                "\\b(x264|x265|H\\.?264|H\\.?265|HEVC|AV1|XviD|DivX|VC-?1)\\b",
                                RegexOption.IGNORE_CASE,
                        )

                // Source patterns
                private val SOURCE_REGEX =
                        Regex(
                                "\\b(WEB-?DL|WEB-?Rip|WEBRip|BluRay|Blu-Ray|BDRip|DVDRip|DVD-Rip|HDTV|PDTV|DVDSCR|BRRip)\\b",
                                RegexOption.IGNORE_CASE,
                        )

                // Audio codec patterns
                private val AUDIO_REGEX =
                        Regex(
                                "\\b(AAC(?:\\d\\.\\d)?|AC3|DD(?:\\+)?(?:\\d\\.\\d)?|DTS(?:-HD)?(?:\\s+MA)?|Dolby\\s+Atmos|TrueHD|FLAC|Opus|DDP?\\d?\\.?\\d?)\\b",
                                RegexOption.IGNORE_CASE,
                        )

                // HDR patterns
                private val HDR_REGEX =
                        Regex(
                                "\\b(HDR10\\+|HDR10|HDR|Dolby\\s+Vision|DV)\\b",
                                RegexOption.IGNORE_CASE,
                        )

                // Edition flags
                private val EXTENDED_REGEX = Regex("\\b(Extended|EXTENDED)\\b")
                private val DIRECTORS_REGEX =
                        Regex(
                                "\\b(Director'?s?\\s*Cut|DIRECTORS?\\s*CUT)\\b",
                                RegexOption.IGNORE_CASE
                        )
                private val UNRATED_REGEX = Regex("\\b(Unrated|UNRATED)\\b")
                private val THEATRICAL_REGEX = Regex("\\b(Theatrical|THEATRICAL)\\b")
                private val THREE_D_REGEX = Regex("\\b(3D)\\b", RegexOption.IGNORE_CASE)
                private val IMAX_REGEX = Regex("\\b(IMAX)\\b", RegexOption.IGNORE_CASE)
                private val REMASTERED_REGEX = Regex("\\b(Remastered|REMASTERED)\\b")
                private val PROPER_REGEX = Regex("\\b(PROPER)\\b", RegexOption.IGNORE_CASE)
                private val REPACK_REGEX = Regex("\\b(REPACK)\\b", RegexOption.IGNORE_CASE)

                // Language tags that should be removed from title
                private val LANGUAGE_TAG_REGEX =
                        Regex(
                                "\\b(German|English|French|Spanish|Italian|Portuguese|Russian|Polish|Turkish|Arabic|" +
                                        "Deutsch|Englisch|Multi|Dual|" +
                                        "GER|ENG|FRE|SPA|ITA|POR|RUS|POL|TUR|ARA)\\b",
                                RegexOption.IGNORE_CASE,
                        )

                // Note: Timestamp detection (e.g., 20231205) is handled inline in year extraction
                // to avoid matching years that are part of timestamps

                // Season/Episode patterns (multiple formats)
                // Allow multiple spaces between S and E: "S05 E16", "S05  E16", "S05E16"
                private val SEASON_EPISODE_REGEX =
                        Regex(
                                "[Ss](\\d{1,2})[\\s_]*[Ee](\\d{1,3})",
                        )
                private val SEASON_EPISODE_COMPACT =
                        Regex(
                                "(\\d{1,2})x(\\d{1,3})",
                                RegexOption.IGNORE_CASE,
                        )

                // Anime-style episode: " - 1089 " or " - 23 " (episode number after dash)
                private val ANIME_EPISODE_REGEX =
                        Regex(
                                "\\s+-\\s*(\\d{1,4})\\s*(?:\\(|\\[|$)",
                        )

                // Season-only pattern: "S01" without episode (for season packs)
                private val SEASON_ONLY_REGEX =
                        Regex(
                                "[Ss](\\d{1,2})(?:\\s+|\\.|$)(?![Ee])",
                        )

                // German episode format: "Folge XX" or "Episode XX"
                private val FOLGE_EPISODE_REGEX =
                        Regex(
                                "(?:Folge|Episode|Ep\\.?)\\s*(\\d{1,3})",
                                RegexOption.IGNORE_CASE,
                        )

                // Year patterns (with boundaries to avoid false positives)
                // Matches years with word boundaries or common delimiters
                // Must be followed by non-digit to avoid matching timestamps like 20231205
                private val YEAR_REGEX =
                        Regex(
                                "(?:^|[\\s._-])(19\\d{2}|20\\d{2})(?=[\\s._-]|$)(?!\\d)",
                        )

                // Year in parentheses (higher confidence)
                // Requires actual parentheses to avoid matching years inside timestamps/IDs
                private val YEAR_PAREN_REGEX =
                        Regex(
                                "\\((19\\d{2}|20\\d{2})\\)",
                        )

                // Year in brackets
                private val YEAR_BRACKET_REGEX =
                        Regex(
                                "\\[(19\\d{2}|20\\d{2})\\]",
                        )

                // Release group patterns
                private val GROUP_HYPHEN_REGEX =
                        Regex(
                                "-\\s*([A-Za-z0-9]+)\\s*$",
                        )
                private val GROUP_BRACKET_REGEX =
                        Regex(
                                "\\[([A-Za-z0-9]+)]",
                        )

                // Channel/user tags (Telegram specific)
                private val CHANNEL_TAG_REGEX =
                        Regex(
                                "@[A-Za-z0-9_]+",
                        )

                // Garbage-only patterns (for cleanup)
                private val GARBAGE_ONLY_REGEX =
                        Regex(
                                "^[\\s|_\\-*#\\[\\]().,;:!?]+$",
                        )
        }

        override fun parse(filename: String): ParsedSceneInfo {
                var workingString = filename.trim()

                // Step 0: Early exit for empty/garbage input
                if (workingString.isBlank() || GARBAGE_ONLY_REGEX.matches(workingString)) {
                        return ParsedSceneInfo(
                                title = filename.ifBlank { "Unknown" },
                                year = null,
                                isEpisode = false,
                                season = null,
                                episode = null,
                                quality = null,
                                edition = null,
                                extraTags = emptyList(),
                        )
                }

                // Step 1: Remove file extension
                workingString = workingString.replace(FILE_EXTENSIONS, "")

                // Step 1.5: Remove IPTV/Xtream provider prefixes
                workingString = workingString.replace(PROVIDER_PREFIX_REGEX, "")

                // Step 1.6: Remove bracketed provider tags
                workingString = workingString.replace(PROVIDER_BRACKET_REGEX, " ")

                // Step 1.7: Remove external ID suffixes (TMDb, IMDb)
                workingString = workingString.replace(EXTERNAL_ID_SUFFIX_REGEX, "")

                // Track what we extract
                var year: Int? = null
                var season: Int? = null
                var episode: Int? = null
                var resolution: String? = null
                var source: String? = null
                var codec: String? = null
                var audio: String? = null
                var hdr: String? = null
                var group: String? = null
                val editionFlags = mutableMapOf<String, Boolean>()

                // Step 2: Extract edition flags
                if (EXTENDED_REGEX.containsMatchIn(workingString)) {
                        editionFlags["extended"] = true
                        workingString = workingString.replace(EXTENDED_REGEX, " ")
                }
                if (DIRECTORS_REGEX.containsMatchIn(workingString)) {
                        editionFlags["directors"] = true
                        workingString = workingString.replace(DIRECTORS_REGEX, " ")
                }
                if (UNRATED_REGEX.containsMatchIn(workingString)) {
                        editionFlags["unrated"] = true
                        workingString = workingString.replace(UNRATED_REGEX, " ")
                }
                if (THEATRICAL_REGEX.containsMatchIn(workingString)) {
                        editionFlags["theatrical"] = true
                        workingString = workingString.replace(THEATRICAL_REGEX, " ")
                }
                if (THREE_D_REGEX.containsMatchIn(workingString)) {
                        editionFlags["threeD"] = true
                        workingString = workingString.replace(THREE_D_REGEX, " ")
                }
                if (IMAX_REGEX.containsMatchIn(workingString)) {
                        editionFlags["imax"] = true
                        workingString = workingString.replace(IMAX_REGEX, " ")
                }
                if (REMASTERED_REGEX.containsMatchIn(workingString)) {
                        editionFlags["remastered"] = true
                        workingString = workingString.replace(REMASTERED_REGEX, " ")
                }
                if (PROPER_REGEX.containsMatchIn(workingString)) {
                        editionFlags["proper"] = true
                        workingString = workingString.replace(PROPER_REGEX, " ")
                }
                if (REPACK_REGEX.containsMatchIn(workingString)) {
                        editionFlags["repack"] = true
                        workingString = workingString.replace(REPACK_REGEX, " ")
                }

                // Step 3: Extract quality tags
                resolution = RESOLUTION_REGEX.find(workingString)?.value
                if (resolution != null) {
                        workingString = workingString.replace(RESOLUTION_REGEX, " ")
                }

                codec = CODEC_REGEX.find(workingString)?.value
                if (codec != null) {
                        workingString = workingString.replace(CODEC_REGEX, " ")
                }

                source = SOURCE_REGEX.find(workingString)?.value
                if (source != null) {
                        workingString = workingString.replace(SOURCE_REGEX, " ")
                }

                audio = AUDIO_REGEX.find(workingString)?.value
                if (audio != null) {
                        workingString = workingString.replace(AUDIO_REGEX, " ")
                }

                hdr = HDR_REGEX.find(workingString)?.value
                if (hdr != null) {
                        workingString = workingString.replace(HDR_REGEX, " ")
                }

                // Step 4: Extract channel tags (Telegram)
                workingString = workingString.replace(CHANNEL_TAG_REGEX, " ")

                // Step 5: Extract season/episode
                val seasonEpisodeMatch = SEASON_EPISODE_REGEX.find(workingString)
                if (seasonEpisodeMatch != null) {
                        season = seasonEpisodeMatch.groupValues[1].toIntOrNull()
                        episode = seasonEpisodeMatch.groupValues[2].toIntOrNull()
                        workingString = workingString.replace(SEASON_EPISODE_REGEX, " ")
                } else {
                        val compactMatch = SEASON_EPISODE_COMPACT.find(workingString)
                        if (compactMatch != null) {
                                season = compactMatch.groupValues[1].toIntOrNull()
                                episode = compactMatch.groupValues[2].toIntOrNull()
                                workingString = workingString.replace(SEASON_EPISODE_COMPACT, " ")
                        }
                }

                // Step 5.5: Extract German-style episode ("Folge XX", "Episode XX")
                if (episode == null) {
                        val folgeMatch = FOLGE_EPISODE_REGEX.find(workingString)
                        if (folgeMatch != null) {
                                episode = folgeMatch.groupValues[1].toIntOrNull()
                                workingString = workingString.replace(FOLGE_EPISODE_REGEX, " ")
                        }
                }

                // Step 5.6: Extract anime-style episode (" - 1089 " pattern)
                if (episode == null) {
                        val animeMatch = ANIME_EPISODE_REGEX.find(workingString)
                        if (animeMatch != null) {
                                episode = animeMatch.groupValues[1].toIntOrNull()
                                // Don't remove the match, just extract the episode number
                        }
                }

                // Step 5.7: Extract season-only pattern ("S01" without episode)
                if (season == null) {
                        val seasonOnlyMatch = SEASON_ONLY_REGEX.find(workingString)
                        if (seasonOnlyMatch != null) {
                                season = seasonOnlyMatch.groupValues[1].toIntOrNull()
                                workingString = workingString.replace(SEASON_ONLY_REGEX, " ")
                        }
                }

                // Step 5.8: Remove remaining language tags
                workingString = workingString.replace(LANGUAGE_TAG_REGEX, " ")

                // Note: Timestamps are NOT removed from workingString to preserve them in title
                // Instead, we check for timestamp patterns during year extraction below

                // Step 6: Extract year (prefer parenthesized, then bracketed, then standalone)
                // Helper function to check if a year is part of a timestamp (e.g., 20231205)
                fun isPartOfTimestamp(yearMatch: MatchResult, source: String): Boolean {
                        val yearStr = yearMatch.groupValues[1]
                        val startPos = yearMatch.range.first
                        val endPos = yearMatch.range.last + 1

                        // Check if there are 4 more digits immediately after the year (YYYYMMDD
                        // pattern)
                        if (endPos < source.length) {
                                val after =
                                        source.substring(endPos, minOf(endPos + 4, source.length))
                                if (after.matches(Regex("\\d{4}.*"))) {
                                        return true // Year followed by 4+ digits = timestamp
                                }
                        }
                        // Check if year is preceded by digits (within a larger number)
                        if (startPos > 0) {
                                val charBefore = source[startPos - 1]
                                if (charBefore.isDigit()) {
                                        return true // Part of larger number
                                }
                        }
                        return false
                }

                val yearParenMatch = YEAR_PAREN_REGEX.findAll(workingString).lastOrNull()
                if (yearParenMatch != null && !isPartOfTimestamp(yearParenMatch, workingString)) {
                        val yearStr = yearParenMatch.groupValues[1]
                        val yearCandidate = yearStr.toIntOrNull()
                        if (yearCandidate != null && yearCandidate in 1900..2099) {
                                year = yearCandidate
                                workingString = workingString.replace(yearParenMatch.value, " ")
                        }
                }

                // Try bracketed year if no year yet
                if (year == null) {
                        val yearBracketMatch =
                                YEAR_BRACKET_REGEX.findAll(workingString).lastOrNull()
                        if (yearBracketMatch != null &&
                                        !isPartOfTimestamp(yearBracketMatch, workingString)
                        ) {
                                val yearCandidate = yearBracketMatch.groupValues[1].toIntOrNull()
                                if (yearCandidate != null && yearCandidate in 1900..2099) {
                                        year = yearCandidate
                                        workingString =
                                                workingString.replace(yearBracketMatch.value, " ")
                                }
                        }
                }

                // If no year yet, try standalone (prefer last occurrence)
                if (year == null) {
                        val yearMatches =
                                YEAR_REGEX.findAll(workingString).toList().filter {
                                        !isPartOfTimestamp(it, workingString)
                                } // Exclude timestamps
                        if (yearMatches.isNotEmpty()) {
                                // Prefer year in last 40% of string (more likely release year)
                                val lastYearMatch =
                                        yearMatches.lastOrNull { match ->
                                                val position =
                                                        match.range.first.toDouble() /
                                                                workingString.length
                                                position > 0.6
                                        }
                                                ?: yearMatches.last()

                                val yearCandidate = lastYearMatch.groupValues[1].toIntOrNull()
                                if (yearCandidate != null && yearCandidate in 1900..2099) {
                                        year = yearCandidate
                                        // Replace the full match (including delimiters) with space
                                        workingString =
                                                workingString.replace(lastYearMatch.value, " ")
                                }
                        }
                }

                // Step 7: Extract release group (after year to avoid conflicts)
                val groupMatch = GROUP_HYPHEN_REGEX.find(workingString)
                if (groupMatch != null) {
                        group = groupMatch.groupValues[1]
                        workingString = workingString.replace(GROUP_HYPHEN_REGEX, " ")
                }
                if (group == null) {
                        val bracketMatch = GROUP_BRACKET_REGEX.find(workingString)
                        if (bracketMatch != null) {
                                group = bracketMatch.groupValues[1]
                                workingString = workingString.replace(GROUP_BRACKET_REGEX, " ")
                        }
                }

                // Step 8: Clean up title - replace common separators with spaces
                var title =
                        workingString
                                .replace(Regex("[._]"), " ") // Dots and underscores to spaces
                                .replace(
                                        Regex("[\\[\\](){}|]"),
                                        " "
                                ) // Remove remaining brackets and pipes
                                .replace(Regex("[*#]+"), " ") // Remove stars and hashes
                                .replace(
                                        Regex("^[\\-\\s:]+|[\\-\\s:]+$"),
                                        ""
                                ) // Trim leading/trailing hyphens, spaces, colons
                                .replace(Regex("\\s*-\\s*-\\s*"), " - ") // Normalize double dashes
                                .replace(Regex("\\s+"), " ") // Collapse multiple spaces
                                .trim()

                // Step 8.5: If title still has leading/trailing garbage, clean again
                title =
                        title.replace(
                                        Regex("^[^a-zA-Z0-9äöüÄÖÜßéèêàâùûôîçñÀ-ÿ]+"),
                                        ""
                                ) // Strip leading non-alphanum
                                .replace(
                                        Regex("[^a-zA-Z0-9äöüÄÖÜßéèêàâùûôîçñÀ-ÿ!?]+$"),
                                        ""
                                ) // Strip trailing non-alphanum
                                .trim()

                // If title is empty, use original filename without extension
                if (title.isBlank()) {
                        title =
                                filename.replace(FILE_EXTENSIONS, "")
                                        .replace(Regex("[\\[\\](){}|*#_.]"), " ")
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                }

                // Ultimate fallback: if still blank, use "Unknown"
                if (title.isBlank()) {
                        title = "Unknown"
                }

                // Build edition info
                val edition =
                        if (editionFlags.isNotEmpty()) {
                                EditionInfo(
                                        extended = editionFlags["extended"] ?: false,
                                        directors = editionFlags["directors"] ?: false,
                                        unrated = editionFlags["unrated"] ?: false,
                                        theatrical = editionFlags["theatrical"] ?: false,
                                        threeD = editionFlags["threeD"] ?: false,
                                        imax = editionFlags["imax"] ?: false,
                                        remastered = editionFlags["remastered"] ?: false,
                                        proper = editionFlags["proper"] ?: false,
                                        repack = editionFlags["repack"] ?: false,
                                )
                        } else {
                                null
                        }

                // Build quality info
                val quality =
                        if (resolution != null ||
                                        source != null ||
                                        codec != null ||
                                        audio != null ||
                                        hdr != null ||
                                        group != null
                        ) {
                                QualityInfo(
                                        resolution = resolution,
                                        source = source,
                                        codec = codec,
                                        audio = audio,
                                        hdr = hdr,
                                        group = group,
                                )
                        } else {
                                null
                        }

                return ParsedSceneInfo(
                        title = title,
                        year = year,
                        isEpisode = season != null && episode != null,
                        season = season,
                        episode = episode,
                        quality = quality,
                        edition = edition,
                        extraTags = emptyList(),
                )
        }
}
