package com.fishit.player.pipeline.telegram.feature

import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.TelegramFeatures
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Telegram FeatureProviders.
 *
 * Verifies that each provider correctly declares its featureId, scope, and owner.
 */
class TelegramFeatureProviderTest {

    // -------------------------------------------------------------------------
    // TelegramFullHistoryFeatureProvider Tests
    // -------------------------------------------------------------------------

    @Test
    fun `TelegramFullHistoryFeatureProvider has correct featureId`() {
        val provider = TelegramFullHistoryFeatureProvider()

        assertEquals(TelegramFeatures.FULL_HISTORY_STREAMING, provider.featureId)
    }

    @Test
    fun `TelegramFullHistoryFeatureProvider has PIPELINE scope`() {
        val provider = TelegramFullHistoryFeatureProvider()

        assertEquals(FeatureScope.PIPELINE, provider.scope)
    }

    @Test
    fun `TelegramFullHistoryFeatureProvider declares correct owner module`() {
        val provider = TelegramFullHistoryFeatureProvider()

        assertEquals("pipeline:telegram", provider.owner.moduleName)
    }

    // -------------------------------------------------------------------------
    // TelegramLazyThumbnailsFeatureProvider Tests
    // -------------------------------------------------------------------------

    @Test
    fun `TelegramLazyThumbnailsFeatureProvider has correct featureId`() {
        val provider = TelegramLazyThumbnailsFeatureProvider()

        assertEquals(TelegramFeatures.LAZY_THUMBNAILS, provider.featureId)
    }

    @Test
    fun `TelegramLazyThumbnailsFeatureProvider has PIPELINE scope`() {
        val provider = TelegramLazyThumbnailsFeatureProvider()

        assertEquals(FeatureScope.PIPELINE, provider.scope)
    }

    @Test
    fun `TelegramLazyThumbnailsFeatureProvider declares correct owner module`() {
        val provider = TelegramLazyThumbnailsFeatureProvider()

        assertEquals("pipeline:telegram", provider.owner.moduleName)
    }

    // -------------------------------------------------------------------------
    // Owner Team Property Tests
    // -------------------------------------------------------------------------

    @Test
    fun `TelegramFullHistoryFeatureProvider owner team is null by default`() {
        val provider = TelegramFullHistoryFeatureProvider()

        assertEquals(null, provider.owner.team)
    }

    @Test
    fun `TelegramLazyThumbnailsFeatureProvider owner team is null by default`() {
        val provider = TelegramLazyThumbnailsFeatureProvider()

        assertEquals(null, provider.owner.team)
    }
}
