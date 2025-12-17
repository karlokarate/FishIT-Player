/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scene name tokenizer with hyphen preservation.
 * NO Kotlin Regex allowed - uses linear algorithms only.
 */
package com.fishit.player.core.metadata.parser.rules

/**
 * Token from scene name parsing.
 *
 * @property value The token value (original casing preserved)
 * @property lowerValue Lowercase version for case-insensitive matching
 * @property startIndex Start position in original string
 * @property endIndex End position in original string (exclusive)
 * @property isNumeric True if token contains only digits
 */
data class Token(
    val value: String,
    val lowerValue: String = value.lowercase(),
    val startIndex: Int,
    val endIndex: Int,
    val isNumeric: Boolean = value.all { it.isDigit() },
)

/**
 * Tokenizer for scene release names.
 *
 * Key features:
 * - Preserves hyphens inside words (Spider-Man, X-Men)
 * - Splits on dots, underscores, spaces
 * - Hyphens NOT between letters are treated as separators
 * - Linear time O(n), no regex
 *
 * Example:
 * "Spider-Man.Across.The.Spider-Verse.2023.1080p.WEB-DL"
 * → ["Spider-Man", "Across", "The", "Spider-Verse", "2023", "1080p", "WEB", "DL"]
 */
object SceneTokenizer {

    /**
     * Tokenize a scene release name.
     *
     * @param input The scene release name to tokenize
     * @return List of tokens with position information
     */
    fun tokenize(input: String): List<Token> {
        if (input.isBlank()) return emptyList()

        val tokens = mutableListOf<Token>()
        var tokenStart = -1
        var i = 0

        while (i < input.length) {
            val c = input[i]

            when {
                // Clear separator: dot, underscore, space
                c == '.' || c == '_' || c == ' ' -> {
                    if (tokenStart >= 0) {
                        addToken(tokens, input, tokenStart, i)
                        tokenStart = -1
                    }
                }

                // Hyphen: check if between letters
                c == '-' -> {
                    if (tokenStart >= 0 && isHyphenBetweenLetters(input, i)) {
                        // Keep hyphen as part of token (Spider-Man)
                        // Don't end the token
                    } else {
                        // Hyphen is a separator
                        if (tokenStart >= 0) {
                            addToken(tokens, input, tokenStart, i)
                            tokenStart = -1
                        }
                    }
                }

                // Regular character: start or continue token
                else -> {
                    if (tokenStart < 0) {
                        tokenStart = i
                    }
                }
            }
            i++
        }

        // Add final token if any
        if (tokenStart >= 0) {
            addToken(tokens, input, tokenStart, input.length)
        }

        return tokens
    }

    private fun addToken(tokens: MutableList<Token>, input: String, start: Int, end: Int) {
        if (start < end) {
            val value = input.substring(start, end)
            if (value.isNotBlank()) {
                tokens.add(
                    Token(
                        value = value,
                        startIndex = start,
                        endIndex = end,
                    ),
                )
            }
        }
    }

    /**
     * Join tokens back into a string with spaces.
     */
    fun joinTokens(tokens: List<Token>): String {
        return tokens.joinToString(" ") { it.value }
    }

    /**
     * Join tokens up to (exclusive) a given index.
     */
    fun joinTokensUpTo(tokens: List<Token>, endIndex: Int): String {
        return tokens.take(endIndex).joinToString(" ") { it.value }
    }

    /**
     * Find token index by its start position in original string.
     * Returns -1 if not found.
     */
    fun findTokenIndexByPosition(tokens: List<Token>, position: Int): Int {
        return tokens.indexOfFirst { it.startIndex >= position }
    }
}

/**
 * Extension to check if a token matches any of the given values (case-insensitive).
 */
fun Token.matchesAny(vararg values: String): Boolean {
    return values.any { it.equals(this.value, ignoreCase = true) }
}

/**
 * Extension to check if a token starts with a prefix (case-insensitive).
 */
fun Token.startsWithIgnoreCase(prefix: String): Boolean {
    return this.lowerValue.startsWith(prefix.lowercase())
}

/**
 * Extension to check if a token ends with a suffix (case-insensitive).
 */
fun Token.endsWithIgnoreCase(suffix: String): Boolean {
    return this.lowerValue.endsWith(suffix.lowercase())
}
