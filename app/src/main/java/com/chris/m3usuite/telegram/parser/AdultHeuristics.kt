package com.chris.m3usuite.telegram.parser

/**
 * Conservative heuristics for adult content detection.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Section 2.1 (Finalized Design Decisions):
 *
 * **Primary signal**: Chat title only
 * - Matches common adult indicators in chat titles (e.g., "porn", "xxx", "adult", "18+")
 *
 * **Secondary signal**: Caption text ONLY for extreme explicit sexual terms
 * - Whitelist: bareback, gangbang, bukkake, fisting, bdsm, deepthroat, cumshot
 *
 * **NOT used for adult classification**:
 * - FSK value (FSK 18 does NOT imply adult content)
 * - Genre-based classification
 * - Broad NSFW heuristics or fuzzy matching
 */
object AdultHeuristics {
    /**
     * Adult indicators for chat titles only.
     * These are explicit markers that strongly indicate the chat contains adult content.
     */
    private val adultTitlePatterns =
        Regex(
            """\b(porn|xxx|adult|nsfw|erotic[s]?)\b|18\+""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Adult emoji indicators for chat titles.
     */
    private val adultEmojis = setOf("🔞", "🍑", "🍆", "💦")

    /**
     * Extreme explicit sexual terms - ONLY these trigger adult classification from captions.
     * This is intentionally a narrow whitelist per design decision.
     */
    private val extremeExplicitTerms =
        Regex(
            """\b(bareback|gangbang|bukkake|fisting|bdsm|deepthroat|cumshot)\b""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Check if a chat title indicates adult content.
     *
     * This is the PRIMARY signal for adult classification.
     *
     * @param title Chat title to check (can be null)
     * @return true if the title contains adult indicators
     */
    fun isAdultChatTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return false

        // Check for adult patterns in title
        if (adultTitlePatterns.containsMatchIn(title)) {
            return true
        }

        // Check for adult emojis
        return adultEmojis.any { title.contains(it) }
    }

    /**
     * Check if caption text contains extreme explicit sexual terms.
     *
     * This is the SECONDARY signal for adult classification.
     * Per design decision: Only extremely explicit sexual terms trigger this.
     *
     * @param caption Caption text to check (can be null)
     * @return true if caption contains extreme explicit terms
     */
    fun hasExtremeExplicitTerms(caption: String?): Boolean {
        if (caption.isNullOrBlank()) return false
        return extremeExplicitTerms.containsMatchIn(caption)
    }

    /**
     * Combined check for adult content using both chat title and caption.
     *
     * @param chatTitle Chat title (primary signal)
     * @param caption Caption text (secondary signal - extreme terms only)
     * @return true if content should be classified as adult
     */
    fun isAdultContent(
        chatTitle: String?,
        caption: String?,
    ): Boolean = isAdultChatTitle(chatTitle) || hasExtremeExplicitTerms(caption)
}
