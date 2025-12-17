/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Language detection rules.
 * Ported from TypeScript video-filename-parser reference.
 * NO Kotlin Regex - uses token matching only.
 */
package com.fishit.player.core.metadata.parser.rules

/**
 * Detected language information.
 */
data class LanguageResult(
    val language: String?, // ISO 639-1 code: "de", "en", "fr", etc.
    val multi: Boolean = false, // Multi-language release
    val dual: Boolean = false, // Dual audio
    val tokenIndex: Int = -1,
)

/**
 * Language detection rules.
 *
 * Detects audio language from scene naming conventions:
 * - German: German, GER, Deutsch
 * - English: English, ENG
 * - Multi/Dual: MULTi, DUAL, DL (German scene convention)
 * - Other European languages
 */
object LanguageRules {

    // German language tokens
    private val germanTokens = setOf(
        "german", "ger", "deutsch", "deu",
    )

    // English language tokens
    private val englishTokens = setOf(
        "english", "eng", "en",
    )

    // French language tokens
    private val frenchTokens = setOf(
        "french", "fra", "fre", "fr", "vff", "vf", "vfq", "truefrench",
    )

    // Spanish language tokens
    private val spanishTokens = setOf(
        "spanish", "spa", "es", "esp", "castellano", "latino",
    )

    // Italian language tokens
    private val italianTokens = setOf(
        "italian", "ita", "it",
    )

    // Russian language tokens
    private val russianTokens = setOf(
        "russian", "rus", "ru",
    )

    // Japanese language tokens
    private val japaneseTokens = setOf(
        "japanese", "jpn", "jp", "jap",
    )

    // Korean language tokens
    private val koreanTokens = setOf(
        "korean", "kor", "kr",
    )

    // Chinese language tokens
    private val chineseTokens = setOf(
        "chinese", "chi", "zho", "cn", "mandarin", "cantonese",
    )

    // Multi/Dual language tokens
    private val multiTokens = setOf(
        "multi", "ml",
    )
    private val dualTokens = setOf(
        "dual", "dl",
    )

    /**
     * Detect language from tokens.
     */
    fun detect(tokens: List<Token>): LanguageResult {
        var detectedLang: String? = null
        var tokenIdx = -1
        var multi = false
        var dual = false

        for ((index, token) in tokens.withIndex()) {
            val lower = token.lowerValue

            // Check multi/dual first (can coexist with language)
            if (lower in multiTokens) {
                multi = true
                if (tokenIdx < 0) tokenIdx = index
            }
            if (lower in dualTokens) {
                dual = true
                if (tokenIdx < 0) tokenIdx = index
            }

            // Only set language once (first match wins)
            if (detectedLang == null) {
                detectedLang = when {
                    lower in germanTokens -> "de"
                    lower in englishTokens -> "en"
                    lower in frenchTokens -> "fr"
                    lower in spanishTokens -> "es"
                    lower in italianTokens -> "it"
                    lower in russianTokens -> "ru"
                    lower in japaneseTokens -> "ja"
                    lower in koreanTokens -> "ko"
                    lower in chineseTokens -> "zh"
                    else -> null
                }
                if (detectedLang != null && tokenIdx < 0) {
                    tokenIdx = index
                }
            }
        }

        return LanguageResult(
            language = detectedLang,
            multi = multi,
            dual = dual,
            tokenIndex = tokenIdx,
        )
    }

    /**
     * Check if a token is a language token.
     */
    fun isLanguageToken(token: Token): Boolean {
        val lower = token.lowerValue
        return lower in germanTokens ||
            lower in englishTokens ||
            lower in frenchTokens ||
            lower in spanishTokens ||
            lower in italianTokens ||
            lower in russianTokens ||
            lower in japaneseTokens ||
            lower in koreanTokens ||
            lower in chineseTokens ||
            lower in multiTokens ||
            lower in dualTokens
    }

    /**
     * Get all known language tokens.
     */
    fun getAllLanguageTokens(): Set<String> {
        return germanTokens +
            englishTokens +
            frenchTokens +
            spanishTokens +
            italianTokens +
            russianTokens +
            japaneseTokens +
            koreanTokens +
            chineseTokens +
            multiTokens +
            dualTokens
    }
}
