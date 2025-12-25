/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Release group detection rules.
 * Ported from TypeScript video-filename-parser reference.
 * NO Kotlin Regex - uses token matching only.
 */
package com.fishit.player.core.metadata.parser.rules

/**
 * Detected release group information.
 */
data class GroupResult(
    val group: String?, // Release group name
    val tokenIndex: Int = -1,
    val isAnimeSubgroup: Boolean = false, // True if detected from [SubGroup] format
)

/**
 * Release group detection rules.
 *
 * Scene release groups are typically:
 * - At the end: "Movie.Title.2020.1080p.BluRay.x264-GROUP"
 * - In brackets for anime: "[SubsPlease] Anime Title"
 *
 * Must NOT match technical tags (codec, resolution, etc.)
 */
object GroupRules {
    // Technical tags that are NOT release groups (lowercase)
    private val invalidGroups =
        setOf(
            // Resolutions
            "sd",
            "hd",
            "720p",
            "1080p",
            "2160p",
            "4k",
            "uhd",
            // Codecs
            "x264",
            "x265",
            "hevc",
            "h264",
            "h265",
            "avc",
            "xvid",
            "divx",
            "av1",
            "vp9",
            // Audio
            "aac",
            "ac3",
            "dts",
            "dd",
            "flac",
            "mp3",
            "truehd",
            "atmos",
            // Sources
            "web",
            "webdl",
            "webrip",
            "bluray",
            "bdrip",
            "hdtv",
            "dvdrip",
            "dvd",
            "brrip",
            "hdrip",
            // Streaming services
            "amzn",
            "nf",
            "netflix",
            "dsnp",
            "hmax",
            "atvp",
            // Languages
            "german",
            "ger",
            "english",
            "eng",
            "multi",
            "dual",
            "dl",
            // Other tech terms
            "proper",
            "repack",
            "internal",
            "readnfo",
            "extended",
            "uncut",
            "directors",
            "dc",
            "remastered",
            "hdr",
            "hdr10",
            "dv",
            "imax",
            "3d",
        )

    // Known valid scene groups (sample - can be expanded)
    private val knownGroups =
        setOf(
            "sparks",
            "dimension",
            "lol",
            "fgt",
            "eta",
            "killers",
            "memento",
            "ntb",
            "ntg",
            "tbs",
            "yify",
            "yts",
            "flux",
            "playweb",
            "cmrg",
            "exploited",
        )

    // Known anime subgroups (sample)
    private val knownAnimeGroups =
        setOf(
            "subsplease",
            "erai-raws",
            "judas",
            "horriblesubs",
            "damedesuyo",
            "ember",
            "sallysubs",
            "doki",
        )

    /**
     * Detect release group from tokens.
     *
     * @param tokens List of tokens from scene name
     * @param rawInput Original input string (for bracket detection)
     */
    fun detect(
        tokens: List<Token>,
        rawInput: String,
    ): GroupResult {
        // First check for anime subgroup in brackets [SubGroup]
        val animeGroup = detectAnimeSubgroup(rawInput)
        if (animeGroup != null) {
            return GroupResult(
                group = animeGroup,
                isAnimeSubgroup = true,
            )
        }

        // Check last token for -GROUP pattern
        if (tokens.isNotEmpty()) {
            val lastToken = tokens.last()
            val lower = lastToken.lowerValue

            // Validate it's not a technical tag
            if (lower !in invalidGroups && isValidGroupName(lastToken.value)) {
                // Check if it looks like a group (short alphanumeric)
                if (lastToken.value.length in 2..12) {
                    return GroupResult(
                        group = lastToken.value,
                        tokenIndex = tokens.size - 1,
                    )
                }
            }
        }

        return GroupResult(group = null)
    }

    /**
     * Detect anime subgroup from [SubGroup] prefix.
     */
    private fun detectAnimeSubgroup(input: String): String? {
        // Simple linear scan for [...]
        if (input.startsWith("[")) {
            val endBracket = input.indexOf(']')
            if (endBracket > 1) {
                val group = input.substring(1, endBracket)
                // Validate: not a hash (8 hex chars)
                if (!isHashLike(group) && group.length >= 2) {
                    return group
                }
            }
        }
        return null
    }

    /**
     * Check if a string looks like a hash (e.g., "[ABCD1234]").
     */
    private fun isHashLike(value: String): Boolean {
        if (value.length != 8) return false
        return value.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    /**
     * Validate a group name.
     * Must be alphanumeric and not start with a digit.
     */
    private fun isValidGroupName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name[0].isDigit()) return false // Groups don't start with numbers
        // Allow letters, digits (after first char)
        return name.all { it.isLetterOrDigit() }
    }

    /**
     * Check if a token is likely a release group.
     */
    fun isGroupToken(token: Token): Boolean {
        val lower = token.lowerValue
        if (lower in invalidGroups) return false
        if (lower in knownGroups || lower in knownAnimeGroups) return true
        return isValidGroupName(token.value) && token.value.length in 2..12
    }

    /**
     * Get all known group names.
     */
    fun getKnownGroups(): Set<String> = knownGroups + knownAnimeGroups
}
