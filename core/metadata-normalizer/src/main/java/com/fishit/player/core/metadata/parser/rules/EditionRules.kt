/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Edition detection rules.
 * Ported from TypeScript video-filename-parser reference.
 * NO Kotlin Regex - uses token matching only.
 */
package com.fishit.player.core.metadata.parser.rules

/**
 * Detected edition information.
 */
data class EditionResult(
    val extended: Boolean = false,
    val directors: Boolean = false,
    val unrated: Boolean = false,
    val theatrical: Boolean = false,
    val threeD: Boolean = false,
    val imax: Boolean = false,
    val remastered: Boolean = false,
    val proper: Boolean = false,
    val repack: Boolean = false,
    val hdr: Boolean = false,
    val dolbyVision: Boolean = false,
)

/**
 * Edition detection rules.
 *
 * Detects special editions:
 * - Extended, Director's Cut, Unrated, Theatrical
 * - IMAX, 3D
 * - Remastered, Anniversary, Restored
 * - PROPER, REPACK
 * - HDR, Dolby Vision
 */
object EditionRules {
    // Extended edition tokens
    private val extendedTokens =
        setOf(
            "extended",
            "uncut",
            "collector",
            "ultimate",
            "special",
        )

    // Director's cut tokens
    private val directorsTokens =
        setOf(
            "director",
            "directors",
            "dc",
        )

    // Unrated tokens
    private val unratedTokens =
        setOf(
            "unrated",
            "uncensored",
        )

    // Theatrical tokens
    private val theatricalTokens =
        setOf(
            "theatrical",
        )

    // 3D tokens
    private val threeDTokens =
        setOf(
            "3d",
        )

    // IMAX tokens
    private val imaxTokens =
        setOf(
            "imax",
        )

    // Remastered tokens
    private val remasteredTokens =
        setOf(
            "remastered",
            "anniversary",
            "restored",
            "4kremastered",
        )

    // Proper/Repack tokens
    private val properTokens = setOf("proper", "real")
    private val repackTokens = setOf("repack", "rerip")

    // HDR tokens
    private val hdrTokens =
        setOf(
            "hdr",
            "hdr10",
            "hdr10+",
            "hdr10plus",
        )

    // Dolby Vision tokens
    private val dolbyVisionTokens =
        setOf(
            "dv",
            "dolbyvision",
            "dolby-vision",
        )

    /**
     * Detect edition information from tokens.
     */
    fun detect(tokens: List<Token>): EditionResult {
        var extended = false
        var directors = false
        var unrated = false
        var theatrical = false
        var threeD = false
        var imax = false
        var remastered = false
        var proper = false
        var repack = false
        var hdr = false
        var dolbyVision = false

        for (token in tokens) {
            val lower = token.lowerValue

            when {
                lower in extendedTokens -> extended = true
                lower in directorsTokens -> directors = true
                lower in unratedTokens -> unrated = true
                lower in theatricalTokens -> theatrical = true
                lower in threeDTokens -> threeD = true
                lower in imaxTokens -> imax = true
                lower in remasteredTokens -> remastered = true
                lower in properTokens -> proper = true
                lower in repackTokens -> repack = true
                lower in hdrTokens -> hdr = true
                lower in dolbyVisionTokens -> dolbyVision = true
            }
        }

        return EditionResult(
            extended = extended,
            directors = directors,
            unrated = unrated,
            theatrical = theatrical,
            threeD = threeD,
            imax = imax,
            remastered = remastered,
            proper = proper,
            repack = repack,
            hdr = hdr,
            dolbyVision = dolbyVision,
        )
    }

    /**
     * Check if a token is an edition token.
     */
    fun isEditionToken(token: Token): Boolean {
        val lower = token.lowerValue
        return lower in extendedTokens ||
            lower in directorsTokens ||
            lower in unratedTokens ||
            lower in theatricalTokens ||
            lower in threeDTokens ||
            lower in imaxTokens ||
            lower in remasteredTokens ||
            lower in properTokens ||
            lower in repackTokens ||
            lower in hdrTokens ||
            lower in dolbyVisionTokens
    }

    /**
     * Get all known edition tokens.
     */
    fun getAllEditionTokens(): Set<String> =
        extendedTokens +
            directorsTokens +
            unratedTokens +
            theatricalTokens +
            threeDTokens +
            imaxTokens +
            remasteredTokens +
            properTokens +
            repackTokens +
            hdrTokens +
            dolbyVisionTokens
}
