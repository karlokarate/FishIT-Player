package com.fishit.player.infra.transport.xtream.strategy

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CategoryFallbackStrategy.
 *
 * Tests the standard Xtream fallback pattern: specific → * → 0 → null
 */
class CategoryFallbackStrategyTest {
    private val strategy = CategoryFallbackStrategy()

    // =========================================================================
    // fetchWithFallback Tests
    // =========================================================================

    @Test
    fun `fetchWithFallback returns result for specific category when non-empty`() =
        runTest {
            val result =
                strategy.fetchWithFallback("123") { categoryId ->
                    if (categoryId == "123") listOf("item1", "item2") else emptyList()
                }

            assertEquals(listOf("item1", "item2"), result)
        }

    @Test
    fun `fetchWithFallback tries fallback sequence when specific category returns empty`() =
        runTest {
            var attemptedCategories = mutableListOf<String?>()

            val result =
                strategy.fetchWithFallback("123") { categoryId ->
                    attemptedCategories.add(categoryId)
                    when (categoryId) {
                        "123" -> emptyList() // Specific fails
                        "*" -> emptyList() // Star fails
                        "0" -> listOf("item1") // Zero succeeds
                        else -> emptyList()
                    }
                }

            assertEquals(listOf("item1"), result)
            assertEquals(listOf("123", "*", "0"), attemptedCategories)
        }

    @Test
    fun `fetchWithFallback tries all fallbacks and returns empty when all fail`() =
        runTest {
            var attemptedCategories = mutableListOf<String?>()

            val result =
                strategy.fetchWithFallback("123") { categoryId ->
                    attemptedCategories.add(categoryId)
                    emptyList<String>()
                }

            assertEquals(emptyList<String>(), result)
            assertEquals(listOf("123", "*", "0", null), attemptedCategories)
        }

    @Test
    fun `fetchWithFallback skips specific category when null and starts with star`() =
        runTest {
            var attemptedCategories = mutableListOf<String?>()

            val result =
                strategy.fetchWithFallback(null) { categoryId ->
                    attemptedCategories.add(categoryId)
                    when (categoryId) {
                        "*" -> listOf("item1")
                        else -> emptyList()
                    }
                }

            assertEquals(listOf("item1"), result)
            assertEquals(listOf("*"), attemptedCategories)
        }

    @Test
    fun `fetchWithFallback continues on exception during fallback`() =
        runTest {
            var attemptedCategories = mutableListOf<String?>()

            val result =
                strategy.fetchWithFallback("123") { categoryId ->
                    attemptedCategories.add(categoryId)
                    when (categoryId) {
                        "123" -> emptyList()
                        "*" -> throw RuntimeException("Network error")
                        "0" -> listOf("item1")
                        else -> emptyList()
                    }
                }

            assertEquals(listOf("item1"), result)
            assertEquals(listOf("123", "*", "0"), attemptedCategories)
        }

    @Test
    fun `fetchWithFallback does not retry specific category if it is in fallback sequence`() =
        runTest {
            var attemptedCategories = mutableListOf<String?>()

            // When categoryId is "*", it should go straight to fallback
            val result =
                strategy.fetchWithFallback("*") { categoryId ->
                    attemptedCategories.add(categoryId)
                    when (categoryId) {
                        "*" -> emptyList()
                        "0" -> listOf("item1")
                        else -> emptyList()
                    }
                }

            assertEquals(listOf("item1"), result)
            // "*" should only be tried once as part of fallback sequence
            assertEquals(listOf("*", "0"), attemptedCategories)
        }

    // =========================================================================
    // fetchScalarWithFallback Tests
    // =========================================================================

    @Test
    fun `fetchScalarWithFallback returns result for specific category when valid`() =
        runTest {
            val result =
                strategy.fetchScalarWithFallback("123") { categoryId ->
                    if (categoryId == "123") 42 else 0
                }

            assertEquals(42, result)
        }

    @Test
    fun `fetchScalarWithFallback tries fallback sequence when specific category returns zero`() =
        runTest {
            var attemptedCategories = mutableListOf<String?>()

            val result =
                strategy.fetchScalarWithFallback("123") { categoryId ->
                    attemptedCategories.add(categoryId)
                    when (categoryId) {
                        "123" -> 0 // Specific fails
                        "*" -> 0 // Star fails
                        "0" -> 10 // Zero succeeds
                        else -> 0
                    }
                }

            assertEquals(10, result)
            assertEquals(listOf("123", "*", "0"), attemptedCategories)
        }

    @Test
    fun `fetchScalarWithFallback returns null when all fallbacks return zero`() =
        runTest {
            val result = strategy.fetchScalarWithFallback("123") { 0 }

            assertNull(result)
        }

    @Test
    fun `fetchScalarWithFallback uses custom validation predicate`() =
        runTest {
            // Custom predicate: accept negative numbers
            val result =
                strategy.fetchScalarWithFallback(
                    categoryId = "123",
                    isValidResult = { it != null && it != 0 },
                ) { categoryId ->
                    when (categoryId) {
                        "123" -> 0
                        "*" -> -1 // Negative is valid per custom predicate
                        else -> 0
                    }
                }

            assertEquals(-1, result)
        }

    @Test
    fun `fetchScalarWithFallback continues on exception during fallback`() =
        runTest {
            var attemptedCategories = mutableListOf<String?>()

            val result =
                strategy.fetchScalarWithFallback("123") { categoryId ->
                    attemptedCategories.add(categoryId)
                    when (categoryId) {
                        "123" -> 0
                        "*" -> throw RuntimeException("Network error")
                        "0" -> 10
                        else -> 0
                    }
                }

            assertEquals(10, result)
            assertEquals(listOf("123", "*", "0"), attemptedCategories)
        }

    // =========================================================================
    // isFallbackCategory Tests
    // =========================================================================

    @Test
    fun `isFallbackCategory returns true for null`() {
        assertTrue(strategy.isFallbackCategory(null))
    }

    @Test
    fun `isFallbackCategory returns true for star`() {
        assertTrue(strategy.isFallbackCategory("*"))
    }

    @Test
    fun `isFallbackCategory returns true for zero`() {
        assertTrue(strategy.isFallbackCategory("0"))
    }

    @Test
    fun `isFallbackCategory returns false for specific category`() {
        assertFalse(strategy.isFallbackCategory("123"))
        assertFalse(strategy.isFallbackCategory("movies"))
        assertFalse(strategy.isFallbackCategory("live_tv"))
    }
}
