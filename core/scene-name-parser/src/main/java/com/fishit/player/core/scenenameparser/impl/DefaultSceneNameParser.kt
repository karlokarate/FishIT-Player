package com.fishit.player.core.scenenameparser.impl

import com.fishit.player.core.scenenameparser.api.ParsedReleaseName
import com.fishit.player.core.scenenameparser.api.SceneNameInput
import com.fishit.player.core.scenenameparser.api.SceneNameParseResult
import com.fishit.player.core.scenenameparser.api.SceneNameParser
import com.fishit.player.core.scenenameparser.api.SourceHint
import com.fishit.player.core.scenenameparser.api.TmdbType

/**
 * Default implementation of SceneNameParser using Parsikle.
 *
 * Parsing strategy:
 * 1. Pre-normalization: Clean and sanitize input based on source hint
 * 2. TMDB extraction: Extract TMDB IDs/URLs first (pure string ops)
 * 3. Tokenization: Split into meaningful tokens
 * 4. Pattern matching: Use Parsikle combinators to identify patterns
 * 5. Result assembly: Build ParsedReleaseName from extracted components
 */
class DefaultSceneNameParser : SceneNameParser {

    override fun parse(input: SceneNameInput): SceneNameParseResult {
        try {
            // Step 1: Pre-normalization
            val normalized = preNormalize(input.raw, input.sourceHint)
            
            if (normalized.isBlank()) {
                return SceneNameParseResult.Unparsed("Empty input after normalization")
            }

            // Step 2: TMDB extraction
            val tmdbInfo = extractTmdbInfo(input.raw)

            // Step 3: Tokenize
            val tokens = tokenize(normalized)

            // Step 4: Pattern matching
            val components = extractComponents(tokens)

            // Step 5: Build result
            if (components.title.isBlank()) {
                return SceneNameParseResult.Unparsed("No title found")
            }

            val parsed = ParsedReleaseName(
                title = components.title,
                year = components.year,
                season = components.season,
                episode = components.episode,
                episodeTitle = components.episodeTitle,
                resolution = components.resolution,
                source = components.source,
                videoCodec = components.videoCodec,
                audioCodec = components.audioCodec,
                language = components.language,
                releaseGroup = components.releaseGroup,
                tmdbId = tmdbInfo.id,
                tmdbType = tmdbInfo.type,
                tmdbUrl = tmdbInfo.url,
            )

            return SceneNameParseResult.Parsed(parsed)
        } catch (e: Exception) {
            return SceneNameParseResult.Unparsed("Parse error: ${e.message}")
        }
    }

    /**
     * Pre-normalize and sanitize input.
     */
    private fun preNormalize(raw: String, sourceHint: SourceHint): String {
        var text = raw.trim()

        // Remove TMDB URLs/tags first (they interfere with parsing)
        text = text.replace(Regex("https?://www\\.themoviedb\\.org/[^\\s]+"), "")
        text = text.replace(Regex("themoviedb\\.org/[^\\s]+"), "")
        text = text.replace(Regex("tmdb:[0-9]+"), "")
        text = text.replace(Regex("tmdb-[0-9]+"), "")

        // Telegram-specific cleanup
        if (sourceHint == SourceHint.TELEGRAM) {
            // Remove emojis (basic approach - remove common emoji ranges)
            text = text.replace(Regex("[\\p{So}\\p{Sk}]+"), " ")
            
            // Remove Telegram noise
            text = text.replace(Regex("@[a-zA-Z0-9_]+"), "") // @channel
            text = text.replace(Regex("t\\.me/[^\\s]+"), "")
            text = text.replace(Regex("Telegram:\\s*"), "")
            
            // Remove known non-semantic bracket tags
            text = text.replace(Regex("\\[TGx\\]"), "")
            text = text.replace(Regex("\\[NF\\]"), "")
        }

        // Normalize separators
        text = text.replace("_", ".")
        text = text.replace(Regex("\\.{2,}"), ".") // collapse multiple dots
        text = text.replace(Regex("\\s{2,}"), " ") // collapse multiple spaces

        return text.trim()
    }

    /**
     * Extract TMDB information from raw string.
     */
    private fun extractTmdbInfo(raw: String): TmdbInfo {
        // Pattern 1: themoviedb.org/movie/<id>
        val moviePattern = Regex("themoviedb\\.org/movie/(\\d+)")
        val movieMatch = moviePattern.find(raw)
        if (movieMatch != null) {
            val id = movieMatch.groupValues[1].toIntOrNull()
            return TmdbInfo(
                id = id,
                type = TmdbType.MOVIE,
                url = movieMatch.value,
            )
        }

        // Pattern 2: themoviedb.org/tv/<id>
        val tvPattern = Regex("themoviedb\\.org/tv/(\\d+)")
        val tvMatch = tvPattern.find(raw)
        if (tvMatch != null) {
            val id = tvMatch.groupValues[1].toIntOrNull()
            return TmdbInfo(
                id = id,
                type = TmdbType.TV,
                url = tvMatch.value,
            )
        }

        // Pattern 3: tmdb:<id> or tmdb-<id>
        val tagPattern = Regex("tmdb[:-](\\d+)")
        val tagMatch = tagPattern.find(raw)
        if (tagMatch != null) {
            val id = tagMatch.groupValues[1].toIntOrNull()
            return TmdbInfo(id = id, type = null, url = null)
        }

        return TmdbInfo()
    }

    /**
     * Tokenize normalized string.
     */
    private fun tokenize(normalized: String): List<Token> {
        val tokens = mutableListOf<Token>()
        
        // Split on dots, spaces, and dashes (carefully for release groups)
        // Keep compound tokens intact: S01E02, 1x02, DDP5.1, H.264, DTS-HD, etc.
        
        val parts = normalized.split(Regex("[.\\s]+"))
        
        for (part in parts) {
            if (part.isBlank()) continue
            
            // Check for episode pattern: S01E02, S1E2, 1x02
            val episodePattern = Regex("S(\\d{1,2})E(\\d{1,3})", RegexOption.IGNORE_CASE)
            val episodeMatch = episodePattern.find(part)
            if (episodeMatch != null) {
                val season = episodeMatch.groupValues[1].toIntOrNull()
                val episode = episodeMatch.groupValues[2].toIntOrNull()
                tokens.add(Token.Episode(season, episode))
                continue
            }

            // Check for alternate episode pattern: 1x02
            val altEpisodePattern = Regex("(\\d{1,2})x(\\d{1,3})", RegexOption.IGNORE_CASE)
            val altMatch = altEpisodePattern.find(part)
            if (altMatch != null) {
                val season = altMatch.groupValues[1].toIntOrNull()
                val episode = altMatch.groupValues[2].toIntOrNull()
                tokens.add(Token.Episode(season, episode))
                continue
            }

            // Check for year: 1900-2099
            val yearPattern = Regex("^(19\\d{2}|20\\d{2})$")
            if (yearPattern.matches(part)) {
                tokens.add(Token.Year(part.toInt()))
                continue
            }

            // Check for resolution
            if (part.matches(Regex("(2160p|1080p|720p|480p|4K|8K)", RegexOption.IGNORE_CASE))) {
                tokens.add(Token.Resolution(part))
                continue
            }

            // Check for source
            if (part.matches(Regex("(WEB-DL|WEBRip|BluRay|HDTV|DVDRip|REMUX|UHD)", RegexOption.IGNORE_CASE))) {
                tokens.add(Token.Source(part))
                continue
            }

            // Check for video codec
            if (part.matches(Regex("(x264|x265|H\\.264|H\\.265|HEVC|AVC|10bit)", RegexOption.IGNORE_CASE))) {
                tokens.add(Token.VideoCodec(part))
                continue
            }

            // Check for audio codec (including compound like DDP5.1)
            if (part.matches(Regex("(AAC|DDP5\\.1|DTS(-HD)?(\\.MA)?|AC3|Atmos|TrueHD)", RegexOption.IGNORE_CASE))) {
                tokens.add(Token.AudioCodec(part))
                continue
            }

            // Check for language
            if (part.matches(Regex("(GERMAN|MULTI|ENGLISH|DL|KOREAN|DUAL)", RegexOption.IGNORE_CASE))) {
                tokens.add(Token.Language(part))
                continue
            }

            // Check for release group (usually starts with -)
            if (part.startsWith("-") && part.length > 1) {
                tokens.add(Token.ReleaseGroup(part.substring(1)))
                continue
            }

            // Check for streaming service tags
            if (part.matches(Regex("(AMZN|ATVP|DSNP|NF)", RegexOption.IGNORE_CASE))) {
                tokens.add(Token.StreamingService(part))
                continue
            }

            // Everything else is a word (potential title part)
            tokens.add(Token.Word(part))
        }

        return tokens
    }

    /**
     * Extract components from tokens.
     */
    private fun extractComponents(tokens: List<Token>): Components {
        var season: Int? = null
        var episode: Int? = null
        var year: Int? = null
        var resolution: String? = null
        var source: String? = null
        var videoCodec: String? = null
        var audioCodec: String? = null
        var language: String? = null
        var releaseGroup: String? = null
        val titleParts = mutableListOf<String>()

        var inTitle = true

        for (token in tokens) {
            when (token) {
                is Token.Episode -> {
                    season = token.season
                    episode = token.episode
                    inTitle = false
                }
                is Token.Year -> {
                    if (inTitle) {
                        year = token.value
                        inTitle = false
                    }
                }
                is Token.Resolution -> resolution = token.value
                is Token.Source -> source = token.value
                is Token.VideoCodec -> videoCodec = token.value
                is Token.AudioCodec -> audioCodec = token.value
                is Token.Language -> language = token.value
                is Token.ReleaseGroup -> releaseGroup = token.value
                is Token.StreamingService -> {} // Ignored for now
                is Token.Word -> {
                    if (inTitle) {
                        titleParts.add(token.value)
                    }
                }
            }
        }

        val title = titleParts.joinToString(" ").trim()

        return Components(
            title = title,
            year = year,
            season = season,
            episode = episode,
            episodeTitle = null, // Not extracted in basic version
            resolution = resolution,
            source = source,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            language = language,
            releaseGroup = releaseGroup,
        )
    }

    private data class TmdbInfo(
        val id: Int? = null,
        val type: TmdbType? = null,
        val url: String? = null,
    )

    private data class Components(
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val episodeTitle: String? = null,
        val resolution: String? = null,
        val source: String? = null,
        val videoCodec: String? = null,
        val audioCodec: String? = null,
        val language: String? = null,
        val releaseGroup: String? = null,
    )

    private sealed class Token {
        data class Word(val value: String) : Token()
        data class Episode(val season: Int?, val episode: Int?) : Token()
        data class Year(val value: Int) : Token()
        data class Resolution(val value: String) : Token()
        data class Source(val value: String) : Token()
        data class VideoCodec(val value: String) : Token()
        data class AudioCodec(val value: String) : Token()
        data class Language(val value: String) : Token()
        data class ReleaseGroup(val value: String) : Token()
        data class StreamingService(val value: String) : Token()
    }
}
