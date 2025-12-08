package com.fishit.player.v2.feature

import com.fishit.player.core.feature.FeatureId
import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppFeatureRegistry].
 *
 * Tests the core registry behavior: registration, lookup, and edge cases.
 */
class AppFeatureRegistryTest {

    // -------------------------------------------------------------------------
    // Test Feature IDs
    // -------------------------------------------------------------------------

    private val featureA = FeatureId("test.feature_a")
    private val featureB = FeatureId("test.feature_b")
    private val unknownFeature = FeatureId("test.unknown")

    // -------------------------------------------------------------------------
    // Test Helpers
    // -------------------------------------------------------------------------

    private fun createProvider(
        featureId: FeatureId,
        scope: FeatureScope = FeatureScope.APP,
        moduleName: String = "test:module",
    ): FeatureProvider = object : FeatureProvider {
        override val featureId: FeatureId = featureId
        override val scope: FeatureScope = scope
        override val owner: FeatureOwner = FeatureOwner(moduleName = moduleName)
    }

    // -------------------------------------------------------------------------
    // isSupported() Tests
    // -------------------------------------------------------------------------

    @Test
    fun `isSupported returns true for registered feature`() {
        val registry = AppFeatureRegistry(setOf(createProvider(featureA)))

        assertTrue(registry.isSupported(featureA))
    }

    @Test
    fun `isSupported returns false for unknown feature`() {
        val registry = AppFeatureRegistry(setOf(createProvider(featureA)))

        assertFalse(registry.isSupported(unknownFeature))
    }

    @Test
    fun `isSupported returns false for empty registry`() {
        val registry = AppFeatureRegistry(emptySet())

        assertFalse(registry.isSupported(featureA))
    }

    @Test
    fun `isSupported works with multiple registered features`() {
        val registry = AppFeatureRegistry(
            setOf(
                createProvider(featureA),
                createProvider(featureB),
            )
        )

        assertTrue(registry.isSupported(featureA))
        assertTrue(registry.isSupported(featureB))
        assertFalse(registry.isSupported(unknownFeature))
    }

    // -------------------------------------------------------------------------
    // providersFor() Tests
    // -------------------------------------------------------------------------

    @Test
    fun `providersFor returns provider for registered feature`() {
        val provider = createProvider(featureA)
        val registry = AppFeatureRegistry(setOf(provider))

        val result = registry.providersFor(featureA)

        assertEquals(1, result.size)
        assertEquals(featureA, result[0].featureId)
    }

    @Test
    fun `providersFor returns empty list for unknown feature`() {
        val registry = AppFeatureRegistry(setOf(createProvider(featureA)))

        val result = registry.providersFor(unknownFeature)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `providersFor returns multiple providers for same featureId`() {
        val provider1 = createProvider(featureA, moduleName = "module:one")
        val provider2 = createProvider(featureA, moduleName = "module:two")
        val registry = AppFeatureRegistry(setOf(provider1, provider2))

        val result = registry.providersFor(featureA)

        assertEquals(2, result.size)
    }

    // -------------------------------------------------------------------------
    // ownerOf() Tests
    // -------------------------------------------------------------------------

    @Test
    fun `ownerOf returns owner for registered feature`() {
        val registry = AppFeatureRegistry(
            setOf(createProvider(featureA, moduleName = "pipeline:telegram"))
        )

        val owner = registry.ownerOf(featureA)

        assertNotNull(owner)
        assertEquals("pipeline:telegram", owner?.moduleName)
    }

    @Test
    fun `ownerOf returns null for unknown feature`() {
        val registry = AppFeatureRegistry(setOf(createProvider(featureA)))

        val owner = registry.ownerOf(unknownFeature)

        assertNull(owner)
    }

    // -------------------------------------------------------------------------
    // featureCount and allFeatureIds Tests
    // -------------------------------------------------------------------------

    @Test
    fun `featureCount returns 0 for empty registry`() {
        val registry = AppFeatureRegistry(emptySet())

        assertEquals(0, registry.featureCount)
    }

    @Test
    fun `featureCount returns correct count for registry with features`() {
        val registry = AppFeatureRegistry(
            setOf(
                createProvider(featureA),
                createProvider(featureB),
            )
        )

        assertEquals(2, registry.featureCount)
    }

    @Test
    fun `featureCount counts unique featureIds not providers`() {
        // Two providers for the same feature
        val registry = AppFeatureRegistry(
            setOf(
                createProvider(featureA, moduleName = "module:one"),
                createProvider(featureA, moduleName = "module:two"),
            )
        )

        assertEquals(1, registry.featureCount)
    }

    @Test
    fun `allFeatureIds returns all registered feature IDs`() {
        val registry = AppFeatureRegistry(
            setOf(
                createProvider(featureA),
                createProvider(featureB),
            )
        )

        val ids = registry.allFeatureIds

        assertEquals(2, ids.size)
        assertTrue(ids.contains(featureA))
        assertTrue(ids.contains(featureB))
    }

    @Test
    fun `allFeatureIds returns empty set for empty registry`() {
        val registry = AppFeatureRegistry(emptySet())

        assertTrue(registry.allFeatureIds.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Scope Preservation Tests
    // -------------------------------------------------------------------------

    @Test
    fun `provider scope is preserved in registry`() {
        val registry = AppFeatureRegistry(
            setOf(createProvider(featureA, scope = FeatureScope.PIPELINE))
        )

        val providers = registry.providersFor(featureA)

        assertEquals(FeatureScope.PIPELINE, providers[0].scope)
    }
}
