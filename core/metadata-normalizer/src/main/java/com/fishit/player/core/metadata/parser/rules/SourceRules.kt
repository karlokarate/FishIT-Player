/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Media source detection rules.
 * Ported from TypeScript video-filename-parser reference.
 * NO Kotlin Regex - uses token matching only.
 */
package com.fishit.player.core.metadata.parser.rules

/**
 * Detected source information.
 */
data class SourceResult(
    val source: String?, // "BluRay", "WEB-DL", "HDTV", "DVD", etc.
    val tokenIndex: Int = -1,
)

/**
 * Media source detection rules.
 *
 * Supports:
 * - BluRay variants: Blu-Ray, BluRay, BDRIP, BRRip, BD25, BD50, HDDVD
 * - Web variants: WEB-DL, WEBDL, WEBRip, WEB, plus streaming services
 * - TV variants: HDTV, PDTV, SDTV, TVRip, DSR
 * - DVD variants: DVD, DVDRip, DVDSCR, DVD-R
 * - CAM/TS: CAM, HDCAM, TS, TELESYNC, TC, TELECINE
 */
object SourceRules {
    // BluRay source tokens
    private val blurayTokens =
        setOf(
            "bluray",
            "blu-ray",
            "bdrip",
            "brrip",
            "bd25",
            "bd50",
            "bd",
            "hddvd",
            "hd-dvd",
        )

    // Web source tokens (including streaming services)
    private val webTokens =
        setOf(
            "webdl",
            "web-dl",
            "webrip",
            "web",
            "webhd",
            // Streaming services
            "amzn",
            "amazon",
            "nf",
            "netflix",
            "dsnp",
            "disneyplus",
            "disney+",
            "hmax",
            "hbomax",
            "atvp",
            "appletv",
            "pcok",
            "peacock",
            "hulu",
            "stan",
            "it",
            "red",
            "crav",
            "crave",
            "pmtp",
            "paramount+",
            "paramount",
        )

    // HDTV source tokens
    private val hdtvTokens =
        setOf(
            "hdtv",
            "pdtv",
            "sdtv",
            "tvrip",
            "dsr",
            "dthrip",
            "dvbrip",
            "satrip",
            "hdtvrip",
        )

    // DVD source tokens
    private val dvdTokens =
        setOf(
            "dvd",
            "dvdrip",
            "dvdscr",
            "dvd-r",
            "dvdr",
            "r5",
            "r6",
        )

    // CAM/TS source tokens (low quality)
    private val camTokens =
        setOf(
            "cam",
            "hdcam",
            "ts",
            "telesync",
            "tc",
            "telecine",
            "hdts",
            "scr",
            "screener",
            "dvdscr",
            "bdscr",
        )

    /**
     * Detect source from tokens.
     */
    fun detect(tokens: List<Token>): SourceResult {
        for ((index, token) in tokens.withIndex()) {
            val lower = token.lowerValue

            when {
                lower in blurayTokens -> return SourceResult("BluRay", index)
                lower in webTokens -> return SourceResult("WEB-DL", index)
                lower in hdtvTokens -> return SourceResult("HDTV", index)
                lower in dvdTokens -> return SourceResult("DVD", index)
                lower in camTokens -> return SourceResult("CAM", index)
            }
        }

        return SourceResult(null, -1)
    }

    /**
     * Check if a token is a source token.
     */
    fun isSourceToken(token: Token): Boolean {
        val lower = token.lowerValue
        return lower in blurayTokens ||
            lower in webTokens ||
            lower in hdtvTokens ||
            lower in dvdTokens ||
            lower in camTokens
    }

    /**
     * Get all known source tokens (for tech boundary detection).
     */
    fun getAllSourceTokens(): Set<String> = blurayTokens + webTokens + hdtvTokens + dvdTokens + camTokens
}
