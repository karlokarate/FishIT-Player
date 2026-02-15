package com.fishit.player.internal.source

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSource
import com.fishit.player.playback.domain.PlaybackSourceException
import com.fishit.player.playback.domain.PlaybackSourceFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Wiring test for PlaybackSourceResolver with Telegram factory.
 *
 * **Purpose:**
 * This test guards against regressions in DI/module configuration where:
 * - The Telegram factory's @IntoSet binding might be removed
 * - The resolver stops using the injected factory set
 * - Factory discovery logic breaks
 *
 * **Approach:**
 * - Uses a fake Telegram factory (no TDLib dependencies)
 * - Tests resolver selection logic without actual playback
 * - Fast, deterministic, no network/runtime requirements
 *
 * **NOT Tested Here:**
 * - Actual playback (covered by integration tests)
 * - TDLib file resolution (covered by TelegramPlaybackSourceFactoryImpl tests)
 * - Full Hilt graph (would require instrumentation tests)
 */
class PlaybackSourceResolverTelegramWiringTest {
    @Test
    fun `resolver selects Telegram factory for SourceType TELEGRAM`() =
        runTest {
            // Given: A resolver with a fake Telegram factory
            val telegramFactory = FakeTelegramPlaybackSourceFactory()
            val resolver = PlaybackSourceResolver(setOf(telegramFactory))

            val context =
                PlaybackContext(
                    canonicalId = "tg:media:123",
                    sourceType = SourceType.TELEGRAM,
                    sourceKey = "tg:media:123",
                    title = "Test Video",
                    extras = mapOf("chatId" to "123", "messageId" to "456"),
                )

            // When: Resolving the context
            val source = resolver.resolve(context)

            // Then: Should use Telegram factory
            assertEquals(DataSourceType.TELEGRAM_FILE, source.dataSourceType)
            assertTrue("Expected Telegram URI", source.uri.startsWith("tg://"))
        }

    @Test
    fun `resolver handles empty factory set gracefully`() =
        runTest {
            // Given: A resolver with no factories
            val resolver = PlaybackSourceResolver(emptySet())

            val context =
                PlaybackContext(
                    canonicalId = "tg:media:123",
                    sourceType = SourceType.TELEGRAM,
                    sourceKey = "tg:media:123",
                    title = "Test Video",
                    extras = emptyMap(),
                )

            // When/Then: Should throw PlaybackSourceException since no factory
            // and no URI is available for resolution
            try {
                resolver.resolve(context)
                fail("Expected PlaybackSourceException to be thrown")
            } catch (e: PlaybackSourceException) {
                assertTrue(
                    "Exception should mention source type",
                    e.message?.contains("TELEGRAM") == true,
                )
            }
        }

    @Test
    fun `resolver selects correct factory when multiple factories present`() =
        runTest {
            // Given: Multiple factories for different source types
            val telegramFactory = FakeTelegramPlaybackSourceFactory()
            val otherFactory = FakeOtherSourceFactory()
            val resolver = PlaybackSourceResolver(setOf(telegramFactory, otherFactory))

            val context =
                PlaybackContext(
                    canonicalId = "tg:media:123",
                    sourceType = SourceType.TELEGRAM,
                    sourceKey = "tg:media:123",
                    title = "Test Video",
                    extras = emptyMap(),
                )

            // When: Resolving Telegram context
            val source = resolver.resolve(context)

            // Then: Should select Telegram factory, not the other one
            assertEquals(DataSourceType.TELEGRAM_FILE, source.dataSourceType)
            assertTrue("Expected Telegram URI", source.uri.startsWith("tg://"))
        }

    @Test
    fun `resolver supports check works correctly`() {
        // Given
        val telegramFactory = FakeTelegramPlaybackSourceFactory()
        val resolver = PlaybackSourceResolver(setOf(telegramFactory))

        // When/Then: Can resolve Telegram
        assertTrue(resolver.canResolve(SourceType.TELEGRAM))
    }

    @Test
    fun `resolver reports correct factory count`() {
        // Given
        val telegramFactory = FakeTelegramPlaybackSourceFactory()
        val otherFactory = FakeOtherSourceFactory()
        val resolver = PlaybackSourceResolver(setOf(telegramFactory, otherFactory))

        // When/Then
        assertEquals(2, resolver.factoryCount())
    }

    /**
     * Fake Telegram factory for testing.
     *
     * Mimics TelegramPlaybackSourceFactoryImpl behavior without TDLib dependencies.
     */
    private class FakeTelegramPlaybackSourceFactory : PlaybackSourceFactory {
        override fun supports(sourceType: SourceType): Boolean = sourceType == SourceType.TELEGRAM

        override suspend fun createSource(context: PlaybackContext): PlaybackSource {
            // Return a fake Telegram PlaybackSource
            return PlaybackSource(
                uri = "tg://file/fake123?chatId=123&messageId=456",
                dataSourceType = DataSourceType.TELEGRAM_FILE,
                mimeType = "video/mp4",
            )
        }
    }

    /**
     * Fake factory for other source types (e.g., HTTP, Xtream).
     */
    private class FakeOtherSourceFactory : PlaybackSourceFactory {
        override fun supports(sourceType: SourceType): Boolean = sourceType == SourceType.HTTP || sourceType == SourceType.XTREAM

        override suspend fun createSource(context: PlaybackContext): PlaybackSource =
            PlaybackSource(
                uri = "http://example.com/video.mp4",
                dataSourceType = DataSourceType.DEFAULT,
                mimeType = "video/mp4",
            )
    }
}
