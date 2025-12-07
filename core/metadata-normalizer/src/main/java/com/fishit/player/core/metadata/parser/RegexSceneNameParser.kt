package com.fishit.player.core.metadata.parser

/**
 * Regex-based scene name parser.
 *
 * Extracts structured metadata from media filenames using curated regex patterns
 * inspired by video-filename-parser, guessit, and scene-release-parser-php.
 *
 * Parsing algorithm:
 * 1. Preprocessing: Remove extension, normalize separators
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
                "\\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|m2ts|ts|vob|iso|3gp)$",
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
                "\\b(AAC(?:\\d\\.\\d)?|AC3|DD(?:\\+)?(?:\\d\\.\\d)?|DTS(?:-HD)?|Dolby\\s+Atmos|TrueHD|FLAC|Opus)\\b",
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
        private val DIRECTORS_REGEX = Regex("\\b(Director'?s?\\s*Cut|DIRECTORS?\\s*CUT)\\b", RegexOption.IGNORE_CASE)
        private val UNRATED_REGEX = Regex("\\b(Unrated|UNRATED)\\b")
        private val THEATRICAL_REGEX = Regex("\\b(Theatrical|THEATRICAL)\\b")
        private val THREE_D_REGEX = Regex("\\b(3D)\\b", RegexOption.IGNORE_CASE)
        private val IMAX_REGEX = Regex("\\b(IMAX)\\b", RegexOption.IGNORE_CASE)
        private val REMASTERED_REGEX = Regex("\\b(Remastered|REMASTERED)\\b")
        private val PROPER_REGEX = Regex("\\b(PROPER)\\b", RegexOption.IGNORE_CASE)
        private val REPACK_REGEX = Regex("\\b(REPACK)\\b", RegexOption.IGNORE_CASE)

        // Season/Episode patterns (multiple formats)
        private val SEASON_EPISODE_REGEX =
            Regex(
                "[Ss](\\d{1,2})[\\s_]?[Ee](\\d{1,3})",
            )
        private val SEASON_EPISODE_COMPACT =
            Regex(
                "(\\d{1,2})x(\\d{1,3})",
                RegexOption.IGNORE_CASE,
            )

        // Year patterns (with boundaries to avoid false positives)
        // Matches years with word boundaries or underscores as delimiters
        private val YEAR_REGEX =
            Regex(
                "(?:^|[\\s._-])(19\\d{2}|20\\d{2})(?=[\\s._-]|$)",
            )

        // Year in parentheses (higher confidence)
        // Requires actual parentheses to avoid matching years inside timestamps/IDs
        private val YEAR_PAREN_REGEX =
            Regex(
                "\\((19\\d{2}|20\\d{2})\\)",
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
    }

    override fun parse(filename: String): ParsedSceneInfo {
        var workingString = filename.trim()

        // Step 1: Remove file extension
        workingString = workingString.replace(FILE_EXTENSIONS, "")

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

        // Step 6: Extract year (prefer parenthesized, then standalone)
        val yearParenMatch = YEAR_PAREN_REGEX.findAll(workingString).lastOrNull()
        if (yearParenMatch != null) {
            val yearStr = yearParenMatch.groupValues[1]
            val yearCandidate = yearStr.toIntOrNull()
            if (yearCandidate != null && yearCandidate in 1900..2099) {
                year = yearCandidate
                workingString = workingString.replace(yearParenMatch.value, " ")
            }
        }

        // If no year yet, try standalone (prefer last occurrence)
        if (year == null) {
            val yearMatches = YEAR_REGEX.findAll(workingString).toList()
            if (yearMatches.isNotEmpty()) {
                // Prefer year in last 40% of string (more likely release year)
                val lastYearMatch =
                    yearMatches.lastOrNull { match ->
                        val position = match.range.first.toDouble() / workingString.length
                        position > 0.6
                    } ?: yearMatches.last()

                val yearCandidate = lastYearMatch.groupValues[1].toIntOrNull()
                if (yearCandidate != null && yearCandidate in 1900..2099) {
                    year = yearCandidate
                    // Replace the full match (including delimiters) with space
                    workingString = workingString.replace(lastYearMatch.value, " ")
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
                .replace(Regex("\\s+"), " ") // Collapse multiple spaces
                .replace(Regex("^[-\\s]+|[-\\s]+$"), "") // Trim hyphens and spaces
                .trim()

        // If title is empty, use original filename without extension
        if (title.isBlank()) {
            title = filename.replace(FILE_EXTENSIONS, "").trim()
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
