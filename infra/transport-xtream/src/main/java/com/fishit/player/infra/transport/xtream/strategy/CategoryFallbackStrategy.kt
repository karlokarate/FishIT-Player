package com.fishit.player.infra.transport.xtream.strategy

import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CategoryFallbackStrategy - Shared Fallback Logic for Category Filtering
 *
 * Implements the standard Xtream fallback pattern: * → 0 → null
 * Eliminates ~150 lines of duplicated fallback code across multiple fetch methods.
 *
 * **Cyclomatic Complexity: ≤3**
 * - fetchWithFallback: 2 (forEach + isNotEmpty check)
 * - Total: 2
 *
 * Pattern inspired by PR #668 handler refactoring.
 */
@Singleton
class CategoryFallbackStrategy
    @Inject
    constructor() {
        companion object {
            private const val TAG = "CategoryFallbackStrategy"

            /**
             * Standard fallback sequence for category filtering in Xtream panels.
             * Tries: specific category → "*" (all) → "0" (none) → null (unfiltered)
             */
            private val FALLBACK_SEQUENCE = listOf("*", "0", null)
        }

    /**
     * Fetch with category fallback.
     *
     * Tries fetching with the specified categoryId first. If empty result, falls back
     * through the standard sequence until a non-empty result is found.
     *
     * @param categoryId Original category ID to try, or null for unfiltered
     * @param fetcher Suspend function that performs the fetch with a category parameter
     * @return List of results, or empty list if all attempts fail
     *
     * **CC: 2** (forEach + isNotEmpty)
     */
    suspend fun <T> fetchWithFallback(
            categoryId: String?,
            fetcher: suspend (categoryId: String?) -> List<T>,
        ): List<T> {
            // Try original categoryId first
            if (categoryId != null && categoryId !in FALLBACK_SEQUENCE) {
                val result = fetcher(categoryId)
                if (result.isNotEmpty()) {
                    UnifiedLog.d(TAG) { "fetchWithFallback: Success with categoryId=$categoryId" }
                    return result
                }
            }

            // Fallback through sequence
            for (fallbackCat in FALLBACK_SEQUENCE) {
                try {
                    val result = fetcher(fallbackCat)
                    if (result.isNotEmpty()) {
                        UnifiedLog.d(TAG) {
                            "fetchWithFallback: Success with fallback categoryId=$fallbackCat (original=$categoryId)"
                        }
                        return result
                    }
                } catch (e: Exception) {
                    UnifiedLog.d(TAG, e) {
                        "fetchWithFallback: categoryId=$fallbackCat failed, continuing..."
                    }
                }
            }

            UnifiedLog.d(TAG) {
                "fetchWithFallback: All fallback attempts exhausted for categoryId=$categoryId"
            }
            return emptyList()
    }

    /**
     * Fetch with category fallback for scalar results (e.g., counts).
     *
     * Similar to [fetchWithFallback], but designed for scalar results like counts.
     * Returns the first non-null/non-zero value from the fallback sequence.
     *
     * @param categoryId Original category ID to try, or null for unfiltered
     * @param fetcher Suspend function that performs the fetch with a category parameter
     * @param isValidResult Predicate to determine if a result is valid (defaults to non-null and > 0 for numbers)
     * @return First valid result, or null if all attempts fail
     *
     * **CC: 2** (forEach + isValidResult check)
     */
    suspend fun <T> fetchScalarWithFallback(
            categoryId: String?,
            isValidResult: (T?) -> Boolean = { it != null && (it as? Number)?.toInt() ?: 0 > 0 },
            fetcher: suspend (categoryId: String?) -> T?,
        ): T? {
            // Try original categoryId first
            if (categoryId != null && categoryId !in FALLBACK_SEQUENCE) {
                val result = fetcher(categoryId)
                if (isValidResult(result)) {
                    UnifiedLog.d(TAG) { "fetchScalarWithFallback: Success with categoryId=$categoryId, result=$result" }
                    return result
                }
            }

            // Fallback through sequence
            for (fallbackCat in FALLBACK_SEQUENCE) {
                try {
                    val result = fetcher(fallbackCat)
                    if (isValidResult(result)) {
                        UnifiedLog.d(TAG) {
                            "fetchScalarWithFallback: Success with fallback categoryId=$fallbackCat (original=$categoryId), result=$result"
                        }
                        return result
                    }
                } catch (e: Exception) {
                    UnifiedLog.d(TAG, e) {
                        "fetchScalarWithFallback: categoryId=$fallbackCat failed, continuing..."
                    }
                }
            }

            UnifiedLog.d(TAG) {
                "fetchScalarWithFallback: All fallback attempts exhausted for categoryId=$categoryId"
            }
            return null
    }

    /**
     * Check if a categoryId would trigger fallback behavior.
     *
     * @return true if the categoryId is in the fallback sequence or null
     */
    fun isFallbackCategory(categoryId: String?): Boolean =
        categoryId == null || categoryId in FALLBACK_SEQUENCE
}
