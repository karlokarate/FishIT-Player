package com.chris.m3usuite.ui.util

/**
 * Deprecated shim kept for backwards compatibility.
 * The implementation has moved to core.util.CategoryNormalizer to avoid UI dependencies in data/work.
 */
@Deprecated(
    message = "Use com.chris.m3usuite.core.util.CategoryNormalizer",
    replaceWith = ReplaceWith("com.chris.m3usuite.core.util.CategoryNormalizer"),
)
object CategoryNormalizer {
    @JvmStatic
    fun normalizeKey(raw: String?): String =
        com.chris.m3usuite.core.util.CategoryNormalizer
            .normalizeKey(raw)

    @JvmStatic
    fun displayLabel(key: String): String =
        com.chris.m3usuite.core.util.CategoryNormalizer
            .displayLabel(key)
}
