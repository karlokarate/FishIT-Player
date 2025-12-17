/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Gold-Standard Scene Release Parser.
 * Complete port of @ctrl/video-filename-parser with RE2J optimization.
 *
 * Design Goals:
 * - O(n) guaranteed parsing time via RE2J where possible
 * - Non-blocking, thread-safe operation
 * - Pre-compiled patterns for maximum performance
 * - Comprehensive metadata extraction
 */
package com.fishit.player.core.metadata.parser

import com.fishit.player.core.metadata.parser.model.AudioChannels
import com.fishit.player.core.metadata.parser.model.AudioCodec
import com.fishit.player.core.metadata.parser.model.EditionInfo
import com.fishit.player.core.metadata.parser.model.QualityModifier
import com.fishit.player.core.metadata.parser.model.Revision
import com.fishit.player.core.metadata.parser.model.SceneReleaseInfo
import com.fishit.player.core.metadata.parser.model.VideoCodec
import com.fishit.player.core.metadata.parser.model.VideoResolution
import com.fishit.player.core.metadata.parser.model.VideoSource
import com.fishit.player.core.metadata.parser.patterns.ScenePatterns
import com.google.re2j.Matcher
import com.google.re2j.Pattern as Re2Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gold-standard scene release parser.
 * Thread-safe, non-blocking, O(n) guaranteed parsing.
 */
@Singleton
class GoldSceneReleaseParser
    @Inject
    constructor() {
        /**
         * Parse a filename or release name into comprehensive metadata.
         *
         * @param input The filename or release name to parse
         * @param isTv Hint: treat as TV show (enables season/episode extraction)
         * @return Parsed scene release information
         */
        fun parse(
            input: String,
            isTv: Boolean = false,
        ): SceneReleaseInfo {
            if (input.isBlank()) return SceneReleaseInfo.EMPTY

            // Step 1: Simplify title (remove technical tags for cleaner parsing)
            val simplified = simplifyTitle(input)

            // Step 2: Extract external IDs early (before they get stripped)
            val tmdbId = extractTmdbId(input)
            val imdbId = extractImdbId(input)

            // Step 3: Parse title and year
            val (title, year) = parseTitleAndYear(simplified, input)

            // Step 4: Parse quality info
            val resolution = parseResolution(input)
            val sources = parseSources(input)
            val modifier = parseQualityModifier(input, sources)
            val revision = parseRevision(input)

            // Step 5: Parse codecs
            val videoCodec = parseVideoCodec(input)
            val audioCodec = parseAudioCodec(input)
            val audioChannels = parseAudioChannels(input)

            // Step 6: Parse edition info
            val edition = parseEdition(input)

            // Step 7: Parse season/episode (if TV or auto-detected)
            val seasonInfo = parseSeasonEpisode(simplified, isTv || containsSeasonEpisodeMarkers(input))

            // Step 8: Parse additional metadata
            val releaseGroup = parseReleaseGroup(input, title)
            val languages = parseLanguages(input, title)
            val isMulti = ScenePatterns.LANG_MULTI_RE2.matches(input)
            val isComplete = ScenePatterns.COMPLETE_RE2.matches(input)

            return SceneReleaseInfo(
                title = title.ifBlank { extractFallbackTitle(input) },
                year = year,
                seasons = seasonInfo.seasons,
                episodes = seasonInfo.episodes,
                absoluteEpisode = seasonInfo.absoluteEpisode,
                airDate = seasonInfo.airDate,
                isFullSeason = seasonInfo.isFullSeason,
                isMultiSeason = seasonInfo.isMultiSeason,
                isSpecial = seasonInfo.isSpecial,
                resolution = resolution ?: guessResolutionFromSource(sources, modifier),
                sources = sources,
                modifier = modifier,
                revision = revision,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                audioChannels = audioChannels,
                edition = edition,
                releaseGroup = releaseGroup,
                languages = languages,
                isMultiLanguage = isMulti,
                isComplete = isComplete,
                tmdbId = tmdbId,
                imdbId = imdbId,
                originalInput = input,
            )
        }

        // =========================================================================
        // SIMPLIFY TITLE
        // =========================================================================

        /**
         * Simplify title by removing technical tags.
         * This makes title extraction more reliable.
         */
        private fun simplifyTitle(input: String): String {
            var result = input

            // Remove file extension
            result = ScenePatterns.FILE_EXTENSION_RE2.matcher(result).replaceAll("")

            // Remove website prefix
            result = ScenePatterns.WEBSITE_PREFIX_RE2.matcher(result).replaceAll("")

            // Remove torrent tags
            result = ScenePatterns.TORRENT_SUFFIX_RE2.matcher(result).replaceAll("")

            // Remove request tags
            result = ScenePatterns.REQUEST_PREFIX_RE2.matcher(result).replaceAll("")

            // Remove hash suffix
            result = ScenePatterns.HASH_SUFFIX_RE2.matcher(result).replaceAll("")

            // Remove common source tags
            result = removeCommonSourceTags(result)

            // Remove video codec (up to 2 passes)
            result = removeVideoCodec(result)
            result = removeVideoCodec(result)

            return result.trim()
        }

        private fun removeCommonSourceTags(input: String): String {
            // Common sources regex
            val pattern = Re2Pattern.compile(
                "(?i)\\b(Bluray|(?:dvdr?|BD)rip|HDTV|HDRip|TS|R5|CAM|SCR|(?:WEB|DVD)?[.]?SCREENER|DiVX|xvid|web-?dl)\\b",
            )
            return pattern.matcher(input).replaceAll("")
        }

        private fun removeVideoCodec(input: String): String {
            val codecPatterns = listOf(
                ScenePatterns.CODEC_X265_RE2,
                ScenePatterns.CODEC_X264_RE2,
                ScenePatterns.CODEC_H265_RE2,
                ScenePatterns.CODEC_H264_RE2,
                ScenePatterns.CODEC_XVID_RE2,
            )
            var result = input
            for (pattern in codecPatterns) {
                val matcher = pattern.matcher(result)
                if (matcher.find()) {
                    result = matcher.replaceFirst("")
                    break
                }
            }
            return result
        }

        // =========================================================================
        // TITLE AND YEAR EXTRACTION
        // =========================================================================

        /**
         * Parse title and year using cascading patterns.
         */
        private fun parseTitleAndYear(
            simplified: String,
            original: String,
        ): Pair<String, Int?> {
            // Remove potential release group from end for cleaner matching
            val groupless = simplified.replace(Regex("-[a-z0-9]+$", RegexOption.IGNORE_CASE), "")

            // Try each title/year pattern
            for (pattern in ScenePatterns.TITLE_YEAR_PATTERNS) {
                val matcher = pattern.matcher(groupless)
                if (matcher.find()) {
                    val rawTitle = matcher.group("title") ?: ""
                    val yearStr = matcher.group("year")?.replace("[\\[\\]]".toRegex(), "")

                    val cleanedTitle = cleanReleaseTitle(rawTitle)
                    if (cleanedTitle.isNullOrBlank()) continue

                    val year = yearStr?.toIntOrNull()?.takeIf { it in 1880..2099 }

                    return cleanedTitle to year
                }
            }

            // Fallback: try to find first artifact (codec, resolution) and take everything before
            return fallbackTitleExtraction(simplified, original)
        }

        /**
         * Fallback title extraction using position of first technical tag.
         */
        private fun fallbackTitleExtraction(
            simplified: String,
            original: String,
        ): Pair<String, Int?> {
            // Find positions of various artifacts
            val positions = mutableListOf<Int>()

            findFirstMatchPosition(ScenePatterns.RES_1080P_RE2, original)?.let { positions.add(it) }
            findFirstMatchPosition(ScenePatterns.RES_720P_RE2, original)?.let { positions.add(it) }
            findFirstMatchPosition(ScenePatterns.RES_2160P_RE2, original)?.let { positions.add(it) }
            findFirstMatchPosition(ScenePatterns.CODEC_X264_RE2, original)?.let { positions.add(it) }
            findFirstMatchPosition(ScenePatterns.CODEC_X265_RE2, original)?.let { positions.add(it) }
            findFirstMatchPosition(ScenePatterns.CHANNELS_51_RE2, original)?.let { positions.add(it) }

            if (positions.isNotEmpty()) {
                val firstPos = positions.filter { it > 0 }.minOrNull() ?: return simplified.trim() to null
                val title = cleanReleaseTitle(original.substring(0, firstPos))
                return (title ?: simplified.trim()) to null
            }

            return simplified.trim() to null
        }

        private fun findFirstMatchPosition(
            pattern: Re2Pattern,
            input: String,
        ): Int? {
            val matcher = pattern.matcher(input)
            return if (matcher.find()) matcher.start() else null
        }

        /**
         * Clean a release title: handle acronyms, convert separators to spaces.
         * Ported from releaseTitleCleaner.
         */
        private fun cleanReleaseTitle(title: String): String? {
            if (title.isBlank() || title == "(") return null

            var cleaned = title
                .replace('_', ' ')
                .replace(Regex("\\[.+?\\]"), "") // Remove [tags]
                .trim()

            // Remove common source/edition tags
            cleaned = removeCommonSourceTags(cleaned).trim()
            cleaned = ScenePatterns.LANG_MULTI_RE2.matcher(cleaned).replaceAll("").trim()

            // Remove scene garbage
            cleaned = cleaned.replace(Regex("(?i)\\b(PROPER|REAL|READ[.]?NFO)\\b"), "").trim()

            // Look for gap formed by removing items
            cleaned = cleaned.split("  ")[0]
            cleaned = cleaned.split("..")[0]

            // Convert dots to spaces, preserving acronyms
            cleaned = convertDotsPreservingAcronyms(cleaned)

            return cleaned.trim().takeIf { it.isNotBlank() }
        }

        /**
         * Convert dots to spaces while preserving acronyms like S.H.I.E.L.D.
         */
        private fun convertDotsPreservingAcronyms(input: String): String {
            val parts = input.split('.')
            val result = StringBuilder()
            var previousWasAcronym = false

            for (i in parts.indices) {
                val part = parts[i]
                val nextPart = parts.getOrNull(i + 1) ?: ""

                when {
                    // Single letter (except 'a' in certain contexts)
                    part.length == 1 && part.lowercase() != "a" && part.toIntOrNull() == null -> {
                        result.append(part).append('.')
                        previousWasAcronym = true
                    }
                    // 'a' in acronym context
                    part.lowercase() == "a" && (previousWasAcronym || nextPart.length == 1) -> {
                        result.append(part).append('.')
                        previousWasAcronym = true
                    }
                    else -> {
                        if (previousWasAcronym) {
                            result.append(' ')
                            previousWasAcronym = false
                        }
                        result.append(part).append(' ')
                    }
                }
            }

            return result.toString().trim()
        }

        private fun extractFallbackTitle(input: String): String {
            // Remove extension and basic cleanup
            var title = ScenePatterns.FILE_EXTENSION_RE2.matcher(input).replaceAll("")
            title = title.replace("[._-]+".toRegex(), " ").trim()
            // Take first segment before any numbers that look like year
            val yearMatch = Regex("\\b(19|20)\\d{2}\\b").find(title)
            if (yearMatch != null) {
                title = title.substring(0, yearMatch.range.first).trim()
            }
            return title.ifBlank { input }
        }

        // =========================================================================
        // RESOLUTION PARSING
        // =========================================================================

        private fun parseResolution(input: String): VideoResolution? =
            when {
                ScenePatterns.RES_2160P_RE2.matches(input) -> VideoResolution.R2160P
                ScenePatterns.RES_1080P_RE2.matches(input) -> VideoResolution.R1080P
                ScenePatterns.RES_720P_RE2.matches(input) -> VideoResolution.R720P
                ScenePatterns.RES_576P_RE2.matches(input) -> VideoResolution.R576P
                ScenePatterns.RES_540P_RE2.matches(input) -> VideoResolution.R540P
                ScenePatterns.RES_480P_RE2.matches(input) -> VideoResolution.R480P
                else -> null
            }

        private fun guessResolutionFromSource(
            sources: List<VideoSource>,
            modifier: QualityModifier?,
        ): VideoResolution? {
            // BluRay defaults
            if (sources.contains(VideoSource.BLURAY)) {
                return when (modifier) {
                    QualityModifier.REMUX -> VideoResolution.R2160P
                    QualityModifier.BRDISK -> VideoResolution.R1080P
                    else -> VideoResolution.R720P
                }
            }
            // DVD defaults to 480p
            if (sources.contains(VideoSource.DVD)) {
                return VideoResolution.R480P
            }
            // WEB sources default to 480p if unknown
            if (sources.any { it == VideoSource.WEBDL || it == VideoSource.WEBRIP }) {
                return VideoResolution.R480P
            }
            return null
        }

        // =========================================================================
        // SOURCE PARSING
        // =========================================================================

        private fun parseSources(input: String): List<VideoSource> {
            val sources = mutableListOf<VideoSource>()

            if (ScenePatterns.SOURCE_BLURAY_RE2.matches(input) ||
                ScenePatterns.SOURCE_BDRIP_RE2.matches(input)
            ) {
                sources.add(VideoSource.BLURAY)
            }

            if (ScenePatterns.SOURCE_WEBRIP_RE2.matches(input)) {
                sources.add(VideoSource.WEBRIP)
            } else if (ScenePatterns.SOURCE_WEBDL_RE2.matches(input)) {
                sources.add(VideoSource.WEBDL)
            }

            if (ScenePatterns.SOURCE_DVDR_RE2.matches(input) ||
                (ScenePatterns.SOURCE_DVD_RE2.matches(input) && !ScenePatterns.SOURCE_SCR_RE2.matches(input))
            ) {
                sources.add(VideoSource.DVD)
            }

            if (ScenePatterns.SOURCE_HDTV_RE2.matches(input)) {
                sources.add(VideoSource.HDTV)
            }

            if (ScenePatterns.SOURCE_SDTV_RE2.matches(input)) {
                sources.add(VideoSource.TV)
            }

            if (ScenePatterns.SOURCE_CAM_RE2.matches(input)) {
                sources.add(VideoSource.CAM)
            }

            if (ScenePatterns.SOURCE_TS_RE2.matches(input)) {
                sources.add(VideoSource.TELESYNC)
            }

            if (ScenePatterns.SOURCE_TC_RE2.matches(input)) {
                sources.add(VideoSource.TELECINE)
            }

            if (ScenePatterns.SOURCE_SCR_RE2.matches(input)) {
                sources.add(VideoSource.SCREENER)
            }

            if (ScenePatterns.SOURCE_WORKPRINT_RE2.matches(input)) {
                sources.add(VideoSource.WORKPRINT)
            }

            return sources.distinct()
        }

        private fun parseQualityModifier(
            input: String,
            sources: List<VideoSource>,
        ): QualityModifier? {
            if (ScenePatterns.BRDISK_RE2.matches(input) && sources.contains(VideoSource.BLURAY)) {
                return QualityModifier.BRDISK
            }
            if (ScenePatterns.REMUX_RE2.matches(input) &&
                !ScenePatterns.SOURCE_WEBDL_RE2.matches(input) &&
                !ScenePatterns.SOURCE_HDTV_RE2.matches(input)
            ) {
                return QualityModifier.REMUX
            }
            if (ScenePatterns.RAWHD_RE2.matches(input)) {
                return QualityModifier.RAWHD
            }
            return null
        }

        private fun parseRevision(input: String): Revision {
            var version = 1
            var real = 0

            if (ScenePatterns.PROPER_RE2.matches(input)) {
                version = 2
            }

            val versionMatcher = ScenePatterns.VERSION_RE2.matcher(input)
            if (versionMatcher.find()) {
                val versionStr = versionMatcher.group()
                val digit = versionStr.filter { it.isDigit() }.toIntOrNull()
                if (digit != null) version = digit
            }

            // Count REAL occurrences (case-sensitive)
            val realMatcher = ScenePatterns.REAL_JVM.matcher(input)
            while (realMatcher.find()) {
                real++
            }

            return Revision(version, real)
        }

        // =========================================================================
        // CODEC PARSING
        // =========================================================================

        private fun parseVideoCodec(input: String): VideoCodec? =
            when {
                ScenePatterns.CODEC_H265_RE2.matches(input) -> VideoCodec.H265
                ScenePatterns.CODEC_H264_RE2.matches(input) -> VideoCodec.H264
                ScenePatterns.CODEC_X265_RE2.matches(input) -> VideoCodec.X265
                ScenePatterns.CODEC_X264_RE2.matches(input) -> VideoCodec.X264
                ScenePatterns.CODEC_AV1_RE2.matches(input) -> VideoCodec.AV1
                ScenePatterns.CODEC_VP9_RE2.matches(input) -> VideoCodec.VP9
                ScenePatterns.CODEC_XVID_RE2.matches(input) -> VideoCodec.XVID
                ScenePatterns.CODEC_WMV_RE2.matches(input) -> VideoCodec.WMV
                else -> null
            }

        private fun parseAudioCodec(input: String): AudioCodec? =
            when {
                ScenePatterns.AUDIO_TRUEHD_RE2.matches(input) -> AudioCodec.DOLBY_TRUEHD
                ScenePatterns.AUDIO_ATMOS_RE2.matches(input) -> AudioCodec.DOLBY_ATMOS
                ScenePatterns.AUDIO_EAC3_RE2.matches(input) -> AudioCodec.DOLBY_DIGITAL_PLUS
                ScenePatterns.AUDIO_DTSHD_RE2.matches(input) -> AudioCodec.DTS_HD
                ScenePatterns.AUDIO_DTS_RE2.matches(input) -> AudioCodec.DTS
                ScenePatterns.AUDIO_AC3_RE2.matches(input) -> AudioCodec.DOLBY_DIGITAL
                ScenePatterns.AUDIO_AAC_RE2.matches(input) -> AudioCodec.AAC
                ScenePatterns.AUDIO_FLAC_RE2.matches(input) -> AudioCodec.FLAC
                ScenePatterns.AUDIO_MP3_RE2.matches(input) -> AudioCodec.MP3
                ScenePatterns.AUDIO_OPUS_RE2.matches(input) -> AudioCodec.OPUS
                else -> null
            }

        private fun parseAudioChannels(input: String): AudioChannels? =
            when {
                ScenePatterns.CHANNELS_71_RE2.matches(input) -> AudioChannels.SURROUND_71
                ScenePatterns.CHANNELS_51_RE2.matches(input) -> AudioChannels.SURROUND_51
                ScenePatterns.CHANNELS_STEREO_RE2.matches(input) -> AudioChannels.STEREO
                ScenePatterns.CHANNELS_MONO_RE2.matches(input) -> AudioChannels.MONO
                else -> null
            }

        // =========================================================================
        // EDITION PARSING
        // =========================================================================

        private fun parseEdition(input: String): EditionInfo =
            EditionInfo(
                isExtended = ScenePatterns.EDITION_EXTENDED_RE2.matches(input),
                isDirectorsCut = ScenePatterns.EDITION_DIRECTORS_RE2.matches(input),
                isTheatrical = ScenePatterns.EDITION_THEATRICAL_RE2.matches(input),
                isUnrated = ScenePatterns.EDITION_UNRATED_RE2.matches(input),
                isRemastered = ScenePatterns.EDITION_REMASTERED_RE2.matches(input),
                isImax = ScenePatterns.EDITION_IMAX_RE2.matches(input),
                isFanEdit = ScenePatterns.EDITION_FANEDIT_RE2.matches(input),
                isHdr = ScenePatterns.EDITION_HDR_RE2.matches(input),
                isDolbyVision = ScenePatterns.EDITION_DV_RE2.matches(input),
                is3D = ScenePatterns.EDITION_3D_RE2.matches(input),
                isUhd = ScenePatterns.RES_2160P_RE2.matches(input),
            )

        // =========================================================================
        // SEASON/EPISODE PARSING
        // =========================================================================

        private data class SeasonEpisodeInfo(
            val seasons: List<Int> = emptyList(),
            val episodes: List<Int> = emptyList(),
            val absoluteEpisode: Int? = null,
            val airDate: String? = null,
            val isFullSeason: Boolean = false,
            val isMultiSeason: Boolean = false,
            val isSpecial: Boolean = false,
        )

        private fun containsSeasonEpisodeMarkers(input: String): Boolean =
            ScenePatterns.SEASON_SxE_RE2.matches(input) ||
                ScenePatterns.SEASON_1X01_RE2.matches(input) ||
                ScenePatterns.GERMAN_FOLGE_RE2.matches(input) ||
                ScenePatterns.ANIME_SUBGROUP_RE2.matches(input)

        private fun parseSeasonEpisode(
            simplified: String,
            isTv: Boolean,
        ): SeasonEpisodeInfo {
            if (!isTv) return SeasonEpisodeInfo()

            // Try S01E01 format first
            val sxeMatcher = ScenePatterns.SEASON_SxE_RE2.matcher(simplified)
            if (sxeMatcher.find()) {
                val season = sxeMatcher.group(1).toIntOrNull() ?: 0
                val episode = sxeMatcher.group(2).toIntOrNull() ?: 0

                // Check for multi-episode
                val multiMatcher = ScenePatterns.SEASON_MULTI_EP_RE2.matcher(simplified)
                if (multiMatcher.find()) {
                    val ep1 = multiMatcher.group(2).toIntOrNull() ?: episode
                    val ep2 = multiMatcher.group(3).toIntOrNull() ?: episode
                    val episodes = (ep1..ep2).toList()
                    return SeasonEpisodeInfo(
                        seasons = listOf(season),
                        episodes = episodes,
                    )
                }

                return SeasonEpisodeInfo(
                    seasons = listOf(season),
                    episodes = listOf(episode),
                )
            }

            // Try 1x01 format
            val xMatcher = ScenePatterns.SEASON_1X01_RE2.matcher(simplified)
            if (xMatcher.find()) {
                val season = xMatcher.group(1).toIntOrNull() ?: 0
                val episode = xMatcher.group(2).toIntOrNull() ?: 0
                return SeasonEpisodeInfo(
                    seasons = listOf(season),
                    episodes = listOf(episode),
                )
            }

            // Try German "Folge" format
            val folgeMatcher = ScenePatterns.GERMAN_FOLGE_RE2.matcher(simplified)
            if (folgeMatcher.find()) {
                val episode = folgeMatcher.group(1).toIntOrNull() ?: 0
                return SeasonEpisodeInfo(
                    seasons = listOf(1),
                    episodes = listOf(episode),
                )
            }

            // Try anime absolute episode
            val animeMatcher = ScenePatterns.ANIME_ABSOLUTE_EP_RE2.matcher(simplified)
            if (animeMatcher.find()) {
                val absEp = animeMatcher.group(1).toIntOrNull()
                return SeasonEpisodeInfo(absoluteEpisode = absEp)
            }

            // Try air date
            val airDateMatcher = ScenePatterns.AIR_DATE_RE2.matcher(simplified)
            if (airDateMatcher.find()) {
                val year = airDateMatcher.group(1)
                val month = airDateMatcher.group(2)
                val day = airDateMatcher.group(3)
                return SeasonEpisodeInfo(airDate = "$year-$month-$day")
            }

            // Try season only
            val seasonOnlyMatcher = ScenePatterns.SEASON_ONLY_RE2.matcher(simplified)
            if (seasonOnlyMatcher.find()) {
                val season = seasonOnlyMatcher.group(1).toIntOrNull() ?: 0
                return SeasonEpisodeInfo(
                    seasons = listOf(season),
                    isFullSeason = true,
                )
            }

            // Try compact format (103 = S01E03)
            val compactMatcher = ScenePatterns.COMPACT_SxE_RE2.matcher(simplified)
            if (compactMatcher.find()) {
                val season = compactMatcher.group(1).toIntOrNull() ?: 0
                val episode = compactMatcher.group(2).toIntOrNull() ?: 0
                return SeasonEpisodeInfo(
                    seasons = listOf(season),
                    episodes = listOf(episode),
                )
            }

            return SeasonEpisodeInfo()
        }

        // =========================================================================
        // RELEASE GROUP PARSING
        // =========================================================================

        private fun parseReleaseGroup(
            input: String,
            title: String,
        ): String? {
            // Check for anime subgroup first
            val animeMatcher = ScenePatterns.ANIME_SUBGROUP_RE2.matcher(input)
            if (animeMatcher.find()) {
                return animeMatcher.group(1)
            }

            // Remove title and extension to find group at end
            var searchArea = input
                .replace(title, "", ignoreCase = true)
                .let { ScenePatterns.FILE_EXTENSION_RE2.matcher(it).replaceAll("") }
                .trim()

            // Standard -GROUP at end
            val groupMatcher = ScenePatterns.GROUP_SUFFIX_RE2.matcher(searchArea)
            if (groupMatcher.find()) {
                val group = groupMatcher.group(1).uppercase()
                // Validate it's not a resolution/codec
                if (group !in listOf("DL", "HD", "SD", "720P", "1080P", "2160P", "X264", "X265", "HEVC")) {
                    return groupMatcher.group(1)
                }
            }

            return null
        }

        // =========================================================================
        // LANGUAGE PARSING
        // =========================================================================

        private fun parseLanguages(
            input: String,
            title: String,
        ): List<String> {
            // Search area is input minus title
            val searchArea = input.replace(title, "", ignoreCase = true).replace(".", " ")
            val languages = mutableListOf<String>()

            if (ScenePatterns.LANG_GERMAN_RE2.matches(searchArea)) languages.add("German")
            if (ScenePatterns.LANG_FRENCH_RE2.matches(searchArea)) languages.add("French")
            if (ScenePatterns.LANG_SPANISH_RE2.matches(searchArea)) languages.add("Spanish")
            if (ScenePatterns.LANG_ITALIAN_RE2.matches(searchArea)) languages.add("Italian")
            if (ScenePatterns.LANG_RUSSIAN_RE2.matches(searchArea)) languages.add("Russian")
            if (ScenePatterns.LANG_JAPANESE_RE2.matches(searchArea)) languages.add("Japanese")
            if (ScenePatterns.LANG_KOREAN_RE2.matches(searchArea)) languages.add("Korean")
            if (ScenePatterns.LANG_CHINESE_RE2.matches(searchArea)) languages.add("Chinese")
            if (ScenePatterns.LANG_ENGLISH_RE2.matches(searchArea)) languages.add("English")

            // Default to English if nothing found
            if (languages.isEmpty()) languages.add("English")

            return languages.distinct()
        }

        // =========================================================================
        // EXTERNAL ID EXTRACTION
        // =========================================================================

        private fun extractTmdbId(input: String): Int? {
            val matcher = ScenePatterns.TMDB_ID_RE2.matcher(input)
            return if (matcher.find()) matcher.group(1).toIntOrNull() else null
        }

        private fun extractImdbId(input: String): String? {
            val matcher = ScenePatterns.IMDB_ID_RE2.matcher(input)
            return if (matcher.find()) matcher.group(1) else null
        }

        // =========================================================================
        // HELPER EXTENSIONS
        // =========================================================================

        private fun Re2Pattern.matches(input: String): Boolean = matcher(input).find()

        private fun Matcher.matches(input: String): Boolean = find()
    }
