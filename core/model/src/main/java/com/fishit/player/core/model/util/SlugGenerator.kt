package com.fishit.player.core.model.util

import java.text.Normalizer
import java.util.Locale

/**
 * SSOT for slug generation across ALL modules.
 *
 * Uses NFD Unicode normalization for correct diacritics handling.
 * All modules requiring slug generation MUST delegate to this object.
 *
 * Consumers:
 * - `NxKeyGenerator.toSlug()` (core/persistence) — delegates here
 * - `FallbackCanonicalKeyGenerator.toSlug()` (core/metadata-normalizer) — delegates here
 *
 * **NX_CONSOLIDATION_PLAN Phase 7 — Duplicate #1**
 *
 * @see contracts/NX_SSOT_CONTRACT.md Section 13.3
 */
object SlugGenerator {
    /**
     * Pattern matching leading articles in multiple languages.
     *
     * Stripped before slug generation to ensure cross-source matching:
     * - "The Matrix" and "Matrix" both produce slug "matrix"
     * - "Der Untergang" and "Untergang" both produce slug "untergang"
     *
     * Supported: EN (the/a/an), DE (der/die/das/ein/eine), FR (le/la/les/l'),
     * ES (el/los/las), IT (il/lo/la/i/gli/le)
     */
    private val LEADING_ARTICLE =
        Regex(
            """^(the|a|an|der|die|das|ein|eine|le|la|les|l'|el|los|las|il|lo|i|gli)\s+""",
            RegexOption.IGNORE_CASE,
        )

    // Pre-compiled regexes for hot-path performance (called per media item during sync)
    private val COMBINING_MARKS = Regex("[\\p{InCombiningDiacriticalMarks}]")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val MULTI_HYPHEN = Regex("-+")

    /**
     * Generate a URL-safe slug from a title.
     *
     * Algorithm:
     * 1. Strip leading articles ("The Matrix" → "Matrix")
     * 2. NFD normalize (decompose diacritics: ü → u + combining mark)
     * 3. Remove combining diacritical marks
     * 4. Lowercase using ROOT locale
     * 5. Replace non-alphanumeric sequences with single hyphen
     * 6. Collapse multiple hyphens
     * 7. Trim leading/trailing hyphens
     * 8. Return "untitled" if result is empty
     *
     * @param title Input title (any Unicode string)
     * @return URL-safe slug
     */
    fun toSlug(title: String): String {
        val stripped = title.trim().replace(LEADING_ARTICLE, "")
        return Normalizer
            .normalize(stripped, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(NON_ALNUM, "-")
            .replace(MULTI_HYPHEN, "-")
            .trim('-')
            .ifEmpty { "untitled" }
    }
}
