package com.fishit.player.playback.telegram

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration test for Telegram PlaybackSourceFactory.
 *
 * Tests that:
 * - Factory supports SourceType.TELEGRAM
 * - Factory can resolve PlaybackContext to PlaybackSource
 * - URI building works correctly for tg:// scheme
 */
class TelegramPlaybackSourceFactoryIntegrationTest {

    private lateinit var testTransportClient: TestTelegramTransportClient
    private lateinit var factory: TelegramPlaybackSourceFactoryImpl

    @Before
    fun setup() {
        testTransportClient = TestTelegramTransportClient()
        factory = TelegramPlaybackSourceFactoryImpl(testTransportClient)
    }

    @Test
    fun `factory supports Telegram source type`() {
        // Given/When
        val supports = factory.supports(SourceType.TELEGRAM)

        // Then
        assertTrue("Factory should support TELEGRAM source type", supports)
    }

    @Test
    fun `factory does not support other source types`() {
        // Given/When/Then
        assertEquals(false, factory.supports(SourceType.XTREAM))
        assertEquals(false, factory.supports(SourceType.HTTP))
        assertEquals(false, factory.supports(SourceType.FILE))
    }

    @Test
    fun `createSource builds tg URI from extras`() = runTest {
        // Given
        val context = PlaybackContext(
            canonicalId = "telegram:123:456",
            sourceType = SourceType.TELEGRAM,
            sourceKey = "remoteId123",
            title = "Test Video",
            extras = mapOf(
                "chatId" to "123",
                "messageId" to "456",
                "fileId" to "789",
                "remoteId" to "remoteId123"
            )
        )

        // When
        val source = factory.createSource(context)

        // Then
        assertTrue("URI should use tg:// scheme", source.uri.startsWith("tg://"))
        assertTrue("URI should contain fileId", source.uri.contains("/789"))
    }

    @Test
    fun `createSource uses existing tg URI if provided`() = runTest {
        // Given
        val existingUri = "tg://file/123?chatId=456&messageId=789&remoteId=abc"
        val context = PlaybackContext(
            canonicalId = "telegram:456:789",
            sourceType = SourceType.TELEGRAM,
            uri = existingUri,
            title = "Test Video"
        )

        // When
        val source = factory.createSource(context)

        // Then
        assertEquals("Should use existing tg:// URI", existingUri, source.uri)
    }

    @Test
    fun `createSource builds URI from sourceKey`() = runTest {
        // Given
        val context = PlaybackContext(
            canonicalId = "telegram:123:456",
            sourceType = SourceType.TELEGRAM,
            sourceKey = "789:remoteId123:123:456",
            title = "Test Video"
        )

        // When
        val source = factory.createSource(context)

        // Then
        assertTrue("URI should use tg:// scheme", source.uri.startsWith("tg://"))
        assertTrue("URI should be built from sourceKey", source.uri.isNotEmpty())
    }

    /**
     * Test implementation of TelegramTransportClient.
     *
     * Per repository testing practices, we use simple test doubles.
     */
    private class TestTelegramTransportClient : TelegramTransportClient {
        // Minimal stub - not used in these tests
        override suspend fun getAuthState(): com.fishit.player.infra.transport.telegram.AuthState {
            throw UnsupportedOperationException("Not implemented in test")
        }
    }
}
