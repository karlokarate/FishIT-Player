package com.chris.m3usuite.domain.selectors

private val yearRegex = Regex("""\((\d{4})\)""")

fun extractYearFrom(text: String?): Int? =
    text?.let { yearRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

fun <T> sortByYearDesc(
    items: List<T>,
    yearOf: (T) -> Int?,
    titleOf: (T) -> String
): List<T> = items.sortedWith(
    compareByDescending<T> { yearOf(it) ?: extractYearFrom(titleOf(it)) ?: Int.MIN_VALUE }
        .thenBy { titleOf(it) }
)

fun <T> filterGermanTv(
    items: List<T>,
    countryOf: (T) -> String?,
    languageOf: (T) -> String?,
    groupOf: (T) -> String?,
    nameOf: (T) -> String
): List<T> {
    fun isDe(s: String?) = s?.contains("de", true) == true || s?.contains("germ", true) == true
    return items.filter { item ->
        isDe(countryOf(item)) || isDe(languageOf(item)) || isDe(groupOf(item)) ||
        nameOf(item).contains(" DE ", true) ||
        nameOf(item).startsWith("DE-", true) ||
        nameOf(item).contains("Germany", true)
    }
}

