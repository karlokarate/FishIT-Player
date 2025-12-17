/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Video codec detection rules.
 * Ported from TypeScript video-filename-parser reference.
 * NO Kotlin Regex - uses token matching only.
 */
package com.fishit.player.core.metadata.parser.rules

/**
 * Detected video codec information.
 */
data class VideoCodecResult(
    val codec: String?, // "x265", "x264", "AV1", "VP9", etc.
    val tokenIndex: Int = -1,
)

/**
 * Video codec detection rules.
 *
 * Supports:
 * - H.265/HEVC variants: x265, HEVC, H.265, H265
 * - H.264/AVC variants: x264, AVC, H.264, H264
 * - VP9, AV1 (newer web codecs)
 * - XviD, DivX (legacy)
 */
object VideoCodecRules {

    // H.265/HEVC tokens
    private val h265Tokens = setOf(
        "x265", "hevc", "h265", "h.265",
    )

    // H.264/AVC tokens
    private val h264Tokens = setOf(
        "x264", "avc", "h264", "h.264",
    )

    // Other codecs
    private val av1Tokens = setOf("av1")
    private val vp9Tokens = setOf("vp9")
    private val xvidTokens = setOf("xvid", "divx")

    /**
     * Detect video codec from tokens.
     */
    fun detect(tokens: List<Token>): VideoCodecResult {
        for ((index, token) in tokens.withIndex()) {
            val lower = token.lowerValue

            when {
                lower in h265Tokens -> return VideoCodecResult("x265", index)
                lower in h264Tokens -> return VideoCodecResult("x264", index)
                lower in av1Tokens -> return VideoCodecResult("AV1", index)
                lower in vp9Tokens -> return VideoCodecResult("VP9", index)
                lower in xvidTokens -> return VideoCodecResult("XviD", index)
            }
        }

        return VideoCodecResult(null, -1)
    }

    /**
     * Check if a token is a video codec token.
     */
    fun isCodecToken(token: Token): Boolean {
        val lower = token.lowerValue
        return lower in h265Tokens ||
            lower in h264Tokens ||
            lower in av1Tokens ||
            lower in vp9Tokens ||
            lower in xvidTokens
    }

    /**
     * Get all known codec tokens (for tech boundary detection).
     */
    fun getAllCodecTokens(): Set<String> {
        return h265Tokens + h264Tokens + av1Tokens + vp9Tokens + xvidTokens
    }
}

/**
 * Audio codec detection rules.
 */
object AudioCodecRules {

    // DTS variants
    private val dtsTokens = setOf(
        "dts", "dts-hd", "dtshd", "dts-ma", "dtsma", "dts-x", "dtsx",
    )

    // Dolby variants
    private val dolbyTokens = setOf(
        "ac3", "dd", "dd5.1", "dd7.1", "eac3", "ddp", "ddp5.1",
        "dolby", "truehd", "atmos",
    )

    // AAC variants
    private val aacTokens = setOf("aac", "aac2.0", "aac5.1")

    // Lossless
    private val flacTokens = setOf("flac")
    private val pcmTokens = setOf("pcm", "lpcm")

    // MP3
    private val mp3Tokens = setOf("mp3")

    /**
     * Detect audio codec from tokens.
     */
    fun detect(tokens: List<Token>): String? {
        for (token in tokens) {
            val lower = token.lowerValue

            when {
                lower in dtsTokens -> return "DTS"
                lower in dolbyTokens -> return "DD"
                lower in aacTokens -> return "AAC"
                lower in flacTokens -> return "FLAC"
                lower in pcmTokens -> return "PCM"
                lower in mp3Tokens -> return "MP3"
            }
        }

        return null
    }

    /**
     * Check if a token is an audio codec token.
     */
    fun isAudioToken(token: Token): Boolean {
        val lower = token.lowerValue
        return lower in dtsTokens ||
            lower in dolbyTokens ||
            lower in aacTokens ||
            lower in flacTokens ||
            lower in pcmTokens ||
            lower in mp3Tokens
    }

    /**
     * Get all known audio tokens.
     */
    fun getAllAudioTokens(): Set<String> {
        return dtsTokens + dolbyTokens + aacTokens + flacTokens + pcmTokens + mp3Tokens
    }
}
