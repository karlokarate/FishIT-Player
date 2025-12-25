/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Title extraction and simplification rules.
 * Ported from TypeScript video-filename-parser reference.
 * NO Kotlin Regex - uses token-based logic only.
 */
package com.fishit.player.core.metadata.parser.rules

import com.google.re2j.Pattern as Re2Pattern

/**
 * Title extraction result.
 */
data class TitleResult(
    val title: String, // Cleaned title
    val endTokenIndex: Int, // Index where title ends (exclusive)
)

/**
 * Title extraction and simplification rules.
 *
 * Responsibilities:
 * - Remove noise tokens (PROPER, REAL, READNFO, etc.)
 * - Remove release group prefixes ([SubGroup])
 * - Clean and normalize title
 * - Find where title ends (before technical tags)
 */
object TitleSimplifierRules {
    // Noise tokens to remove from title
    private val noiseTokens =
        setOf(
            "proper",
            "real",
            "readnfo",
            "read",
            "nfo",
            "internal",
            "limited",
            "festival",
            "complete",
        )

    // Provider prefixes to remove (e.g., "N|", "UHD|", "D|")
    private val providerPrefixPattern = Re2Pattern.compile("^[A-Z]+\\|")

    // Provider tags in underscore format (e.g., "___NF___", "___HD___", "___4K___", "___DE___")
    private val underscoreProviderPattern = Re2Pattern.compile("___[A-Z0-9]+___")

    // Channel tags to remove (e.g., "@ArcheMovie")
    private val channelTagPattern = Re2Pattern.compile("@[A-Za-z0-9_]+")

    // Hash suffix pattern (e.g., "[ABCD1234]")
    private val hashPattern = Re2Pattern.compile("\\[[A-Fa-f0-9]{8}\\]")

    // File extensions
    private val extensionPattern = Re2Pattern.compile("(?i)\\.(?:mkv|mp4|avi|m4v|mov|wmv|flv|webm|ts|m2ts|vob|iso)$")

    /**
     * Extract title from tokens up to the first technical tag.
     *
     * @param tokens List of tokens
     * @param techBoundaryIndex Index of first technical token (or tokens.size if none)
     * @return Cleaned title result
     */
    fun extractTitle(
        tokens: List<Token>,
        techBoundaryIndex: Int,
    ): TitleResult {
        val titleTokens = mutableListOf<String>()
        var endIndex = minOf(techBoundaryIndex, tokens.size)

        for (i in 0 until endIndex) {
            val token = tokens[i]
            val lower = token.lowerValue

            // Skip noise tokens
            if (lower in noiseTokens) {
                continue
            }

            titleTokens.add(token.value)
        }

        val title = titleTokens.joinToString(" ").ifBlank { "Unknown" }
        return TitleResult(title = title, endTokenIndex = endIndex)
    }

    /**
     * Pre-clean raw input before tokenization.
     *
     * Removes:
     * - File extension
     * - Provider prefix (e.g., "N|")
     * - Provider tags in underscore format (___NF___, ___HD___, etc.)
     * - Channel tags (@ArcheMovie)
     * - Hash suffixes [ABCD1234]
     */
    fun preclean(input: String): String {
        var result = input

        // Remove file extension
        result = extensionPattern.matcher(result).replaceAll("")

        // Remove provider prefix
        result = providerPrefixPattern.matcher(result).replaceAll("")

        // Remove underscore provider tags (___NF___, ___HD___, ___4K___, ___DE___)
        result = underscoreProviderPattern.matcher(result).replaceAll(" ")

        // Remove channel tags
        result = channelTagPattern.matcher(result).replaceAll("")

        // Remove hash suffixes
        result = hashPattern.matcher(result).replaceAll("")

        return result.trim()
    }

    /**
     * Check if a token is a noise token.
     */
    fun isNoiseToken(token: Token): Boolean = token.lowerValue in noiseTokens

    /**
     * Get all noise tokens.
     */
    fun getNoiseTokens(): Set<String> = noiseTokens

    /**
     * Remove anime subgroup prefix [SubGroup] from start of string.
     */
    fun removeAnimeSubgroup(input: String): String {
        if (input.startsWith("[")) {
            val endBracket = input.indexOf(']')
            if (endBracket > 0 && endBracket < input.length - 1) {
                return input.substring(endBracket + 1).trim()
            }
        }
        return input
    }
}

/**
 * Combined tech boundary detection using all rule packs.
 */
object TechBoundaryDetector {
    /**
     * Find the index of the first technical token.
     *
     * Technical tokens include:
     * - Resolution (1080p, 4K, etc.)
     * - Source (BluRay, WEB-DL, etc.)
     * - Codec (x264, HEVC, etc.)
     * - Language (German, MULTi, etc.)
     * - Audio (DTS, AC3, etc.)
     * - Edition (Extended, Remastered, etc.)
     *
     * @return Index of first tech token, or tokens.size if none found
     */
    fun findTechBoundary(tokens: List<Token>): Int {
        for ((index, token) in tokens.withIndex()) {
            if (isTechToken(token)) {
                return index
            }
        }
        return tokens.size
    }

    /**
     * Check if a token is a technical token.
     */
    fun isTechToken(token: Token): Boolean =
        ResolutionRules.isResolutionToken(token) ||
            SourceRules.isSourceToken(token) ||
            VideoCodecRules.isCodecToken(token) ||
            AudioCodecRules.isAudioToken(token) ||
            LanguageRules.isLanguageToken(token) ||
            EditionRules.isEditionToken(token) ||
            TitleSimplifierRules.isNoiseToken(token)
}
