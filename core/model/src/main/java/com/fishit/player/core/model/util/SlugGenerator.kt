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
 * @see docs/v2/NX_CONSOLIDATION_PLAN.md Section 13.3
 */
object SlugGenerator {

    /**
     * Generate a URL-safe slug from a title.
     *
     * Algorithm:
     * 1. NFD normalize (decompose diacritics: ü → u + combining mark)
     * 2. Remove combining diacritical marks
     * 3. Lowercase using ROOT locale
     * 4. Replace non-alphanumeric sequences with single hyphen
     * 5. Collapse multiple hyphens
     * 6. Trim leading/trailing hyphens
     * 7. Return "untitled" if result is empty
     *
     * @param title Input title (any Unicode string)
     * @return URL-safe slug
     */
    fun toSlug(title: String): String =
        Normalizer
            .normalize(title, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "untitled" }
}
