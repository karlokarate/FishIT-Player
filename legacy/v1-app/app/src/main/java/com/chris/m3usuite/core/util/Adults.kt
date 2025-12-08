package com.chris.m3usuite.core.util

/**
 * Helpers to consistently detect Adult categories and providers.
 *
 * Policy:
 * - Category id/label that starts with "For Adult" or "adult_" is treated as adult.
 * - Normalized provider/genre keys that start with "adult_" are treated as adult.
 */
fun isAdultCategory(
    categoryId: String?,
    categoryName: String?,
): Boolean {
    val id = (categoryId ?: "").trim().lowercase()
    val name = (categoryName ?: "").trim().lowercase()
    if (id.startsWith("adult_")) return true
    if (id.startsWith("for adult")) return true
    if (name.startsWith("for adult")) return true
    if (name.startsWith("adult_")) return true
    return false
}

fun isAdultCategoryLabel(label: String?): Boolean {
    val s = (label ?: "").trim().lowercase()
    if (s.isBlank()) return false
    if (s.startsWith("for adult")) return true
    if (s.startsWith("adult_")) return true
    // Common textual hints
    if ("18+" in s || "xxx" in s) return true
    return false
}

fun isAdultProvider(providerOrGenreKey: String?): Boolean {
    val k = (providerOrGenreKey ?: "").trim().lowercase()
    return k.startsWith("adult_") || k == "for_adults"
}
