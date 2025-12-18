/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Resolution detection rules.
 * Ported from TypeScript video-filename-parser reference.
 * NO Kotlin Regex - uses token matching only.
 */
package com.fishit.player.core.metadata.parser.rules

/**
 * Detected resolution information.
 */
data class ResolutionResult(
    val resolution: String?, // "2160p", "1080p", "720p", "480p", etc.
    val tokenIndex: Int = -1, // Index of token where resolution was found
)

/**
 * Resolution detection rules.
 *
 * Supports:
 * - Standard: 2160p, 1080p, 1080i, 720p, 576p, 480p
 * - Aliases: 4K, UHD (→ 2160p), HD (→ 720p)
 * - Numeric: 4320, 2160, 1080, 720, etc. (when followed by p/i)
 */
object ResolutionRules {
    // Resolution tokens (case-insensitive)
    private val resolution2160pTokens = setOf("2160p", "4k", "uhd", "4320p")
    private val resolution1080pTokens = setOf("1080p", "1080i")
    private val resolution720pTokens = setOf("720p", "720i", "960p", "hd")
    private val resolution576pTokens = setOf("576p", "576i")
    private val resolution480pTokens = setOf("480p", "480i", "sd")

    /**
     * Detect resolution from tokens.
     *
     * @param tokens List of tokens from scene name
     * @return ResolutionResult with detected resolution and token index
     */
    fun detect(tokens: List<Token>): ResolutionResult {
        for ((index, token) in tokens.withIndex()) {
            val lower = token.lowerValue

            when {
                lower in resolution2160pTokens -> {
                    return ResolutionResult("2160p", index)
                }
                lower in resolution1080pTokens -> {
                    return ResolutionResult("1080p", index)
                }
                lower in resolution720pTokens -> {
                    return ResolutionResult("720p", index)
                }
                lower in resolution576pTokens -> {
                    return ResolutionResult("576p", index)
                }
                lower in resolution480pTokens -> {
                    return ResolutionResult("480p", index)
                }
            }
        }

        return ResolutionResult(null, -1)
    }

    /**
     * Check if a token is a resolution token.
     */
    fun isResolutionToken(token: Token): Boolean {
        val lower = token.lowerValue
        return lower in resolution2160pTokens ||
            lower in resolution1080pTokens ||
            lower in resolution720pTokens ||
            lower in resolution576pTokens ||
            lower in resolution480pTokens
    }

    /**
     * Get all known resolution tokens (for tech boundary detection).
     */
    fun getAllResolutionTokens(): Set<String> =
        resolution2160pTokens +
            resolution1080pTokens +
            resolution720pTokens +
            resolution576pTokens +
            resolution480pTokens
}
