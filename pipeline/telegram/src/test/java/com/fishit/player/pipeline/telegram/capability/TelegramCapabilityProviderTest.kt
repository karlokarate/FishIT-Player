package com.fishit.player.pipeline.telegram.capability

import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.TelegramFeatures
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Telegram Capability Providers.
 *
 * Verifies that each provider correctly declares its featureId, scope, and owner.
 * These are **pipeline capabilities**, not App Features.
 */
class TelegramCapabilityProviderTest {
    // -------------------------------------------------------------------------
    // TelegramFullHistoryCapabilityProvider Tests
    // -------------------------------------------------------------------------

    @Test
    fun `TelegramFullHistoryCapabilityProvider has correct featureId`() {
        val provider = TelegramFullHistoryCapabilityProvider()

        assertEquals(TelegramFeatures.FULL_HISTORY_STREAMING, provider.featureId)
    }

    @Test
    fun `TelegramFullHistoryCapabilityProvider has PIPELINE scope`() {
        val provider = TelegramFullHistoryCapabilityProvider()

        assertEquals(FeatureScope.PIPELINE, provider.scope)
    }

    @Test
    fun `TelegramFullHistoryCapabilityProvider declares correct owner module`() {
        val provider = TelegramFullHistoryCapabilityProvider()

        assertEquals("pipeline:telegram", provider.owner.moduleName)
    }

    // -------------------------------------------------------------------------
    // TelegramLazyThumbnailsCapabilityProvider Tests
    // -------------------------------------------------------------------------

    @Test
    fun `TelegramLazyThumbnailsCapabilityProvider has correct featureId`() {
        val provider = TelegramLazyThumbnailsCapabilityProvider()

        assertEquals(TelegramFeatures.LAZY_THUMBNAILS, provider.featureId)
    }

    @Test
    fun `TelegramLazyThumbnailsCapabilityProvider has PIPELINE scope`() {
        val provider = TelegramLazyThumbnailsCapabilityProvider()

        assertEquals(FeatureScope.PIPELINE, provider.scope)
    }

    @Test
    fun `TelegramLazyThumbnailsCapabilityProvider declares correct owner module`() {
        val provider = TelegramLazyThumbnailsCapabilityProvider()

        assertEquals("pipeline:telegram", provider.owner.moduleName)
    }

    // -------------------------------------------------------------------------
    // Owner Team Property Tests
    // -------------------------------------------------------------------------

    @Test
    fun `TelegramFullHistoryCapabilityProvider owner team is null by default`() {
        val provider = TelegramFullHistoryCapabilityProvider()

        assertEquals(null, provider.owner.team)
    }

    @Test
    fun `TelegramLazyThumbnailsCapabilityProvider owner team is null by default`() {
        val provider = TelegramLazyThumbnailsCapabilityProvider()

        assertEquals(null, provider.owner.team)
    }
}
