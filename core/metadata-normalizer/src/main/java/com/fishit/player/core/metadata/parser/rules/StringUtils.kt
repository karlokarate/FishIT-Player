/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Linear-time string utilities.
 * NO Kotlin Regex allowed in this package - only RE2J or linear code.
 */
package com.fishit.player.core.metadata.parser.rules

/**
 * Extract only digit characters from a string.
 * Linear time O(n), no regex.
 *
 * Example: "S01E02" → "0102", "(2020)" → "2020"
 */
fun extractDigits(input: String): String {
    val sb = StringBuilder(input.length)
    for (c in input) {
        if (c.isDigit()) {
            sb.append(c)
        }
    }
    return sb.toString()
}

/**
 * Collapse multiple whitespace/separator characters into single spaces.
 * Treats dots, underscores as separators.
 * Linear time O(n), no regex.
 *
 * Example: "Movie...Title___2020" → "Movie Title 2020"
 */
fun collapseSeparators(input: String): String {
    val sb = StringBuilder(input.length)
    var lastWasSpace = true // Start true to trim leading

    for (c in input) {
        val isSpace = c == ' ' || c == '.' || c == '_'
        if (isSpace) {
            if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
        } else {
            sb.append(c)
            lastWasSpace = false
        }
    }

    // Trim trailing space
    if (sb.isNotEmpty() && sb.last() == ' ') {
        sb.deleteAt(sb.length - 1)
    }

    return sb.toString()
}

/**
 * Collapse only whitespace (not dots/underscores) into single spaces.
 * Linear time O(n), no regex.
 */
fun collapseWhitespace(input: String): String {
    val sb = StringBuilder(input.length)
    var lastWasSpace = true // Start true to trim leading

    for (c in input) {
        if (c.isWhitespace()) {
            if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
        } else {
            sb.append(c)
            lastWasSpace = false
        }
    }

    // Trim trailing space
    if (sb.isNotEmpty() && sb.last() == ' ') {
        sb.deleteAt(sb.length - 1)
    }

    return sb.toString()
}

/**
 * Check if character at position is surrounded by letters (for hyphen preservation).
 */
fun isHyphenBetweenLetters(input: String, pos: Int): Boolean {
    if (pos <= 0 || pos >= input.length - 1) return false
    return input[pos - 1].isLetter() && input[pos + 1].isLetter()
}

/**
 * Convert separators (dots, underscores) to spaces while preserving hyphens between letters.
 * Spider-Man → Spider-Man (preserved)
 * Movie.Title → Movie Title (converted)
 * WEB-DL → WEB DL (converted - hyphen not between letters on both sides)
 *
 * Linear time O(n), no regex.
 */
fun convertSeparatorsPreservingHyphens(input: String): String {
    val sb = StringBuilder(input.length)

    for (i in input.indices) {
        val c = input[i]
        when {
            c == '.' || c == '_' -> sb.append(' ')
            c == '-' -> {
                if (isHyphenBetweenLetters(input, i)) {
                    sb.append(c) // Preserve hyphen in Spider-Man, X-Men
                } else {
                    sb.append(' ') // Convert standalone hyphen
                }
            }
            else -> sb.append(c)
        }
    }

    return sb.toString()
}

/**
 * Remove trailing separator characters (space, dot, underscore, hyphen).
 * Linear time O(n), no regex.
 */
fun trimTrailingSeparators(input: String): String {
    var end = input.length
    while (end > 0) {
        val c = input[end - 1]
        if (c == ' ' || c == '.' || c == '_' || c == '-') {
            end--
        } else {
            break
        }
    }
    return if (end == input.length) input else input.substring(0, end)
}

/**
 * Check if a string contains a substring (case-insensitive).
 * Linear time O(n*m), but still no regex overhead.
 */
fun containsIgnoreCase(haystack: String, needle: String): Boolean {
    return haystack.lowercase().contains(needle.lowercase())
}

/**
 * Check if a string equals any of the given values (case-insensitive).
 */
fun equalsAnyIgnoreCase(value: String, vararg options: String): Boolean {
    val lower = value.lowercase()
    return options.any { it.lowercase() == lower }
}

/**
 * Check if a string starts with any of the given prefixes (case-insensitive).
 */
fun startsWithAnyIgnoreCase(value: String, vararg prefixes: String): Boolean {
    val lower = value.lowercase()
    return prefixes.any { lower.startsWith(it.lowercase()) }
}

/**
 * Check if a string ends with any of the given suffixes (case-insensitive).
 */
fun endsWithAnyIgnoreCase(value: String, vararg suffixes: String): Boolean {
    val lower = value.lowercase()
    return suffixes.any { lower.endsWith(it.lowercase()) }
}
