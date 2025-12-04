package com.chris.m3usuite.telegram.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for TelegramThumbKey and related models.
 */
class TelegramThumbKeyTest {
    @Test
    fun `TelegramThumbKey generates stable cache keys`() {
        val key1 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256,
            )

        val key2 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256,
            )

        // Same data should produce same cache key
        assertEquals(key1.toCacheKey(), key2.toCacheKey())
        assertEquals("tg_thumb_CHAT_MESSAGE_256_abc123", key1.toCacheKey())
    }

    @Test
    fun `TelegramThumbKey differentiates by remoteId`() {
        val key1 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256,
            )

        val key2 =
            TelegramThumbKey(
                remoteId = "xyz789",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256,
            )

        assertNotEquals(key1.toCacheKey(), key2.toCacheKey())
    }

    @Test
    fun `TelegramThumbKey differentiates by kind`() {
        val key1 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256,
            )

        val key2 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.POSTER,
                sizeBucket = 256,
            )

        assertNotEquals(key1.toCacheKey(), key2.toCacheKey())
        assertEquals("tg_thumb_CHAT_MESSAGE_256_abc123", key1.toCacheKey())
        assertEquals("tg_thumb_POSTER_256_abc123", key2.toCacheKey())
    }

    @Test
    fun `TelegramThumbKey differentiates by sizeBucket`() {
        val key1 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 128,
            )

        val key2 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 512,
            )

        assertNotEquals(key1.toCacheKey(), key2.toCacheKey())
        assertEquals("tg_thumb_CHAT_MESSAGE_128_abc123", key1.toCacheKey())
        assertEquals("tg_thumb_CHAT_MESSAGE_512_abc123", key2.toCacheKey())
    }

    @Test
    fun `TelegramThumbKey default sizeBucket is 0`() {
        val key =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.PREVIEW,
            )

        assertEquals(0, key.sizeBucket)
        assertEquals("tg_thumb_PREVIEW_0_abc123", key.toCacheKey())
    }

    @Test
    fun `TelegramThumbKey works with all ThumbKind values`() {
        val remoteId = "test123"
        val sizeBucket = 256

        val poster =
            TelegramThumbKey(
                remoteId = remoteId,
                kind = ThumbKind.POSTER,
                sizeBucket = sizeBucket,
            )
        assertEquals("tg_thumb_POSTER_256_test123", poster.toCacheKey())

        val chatMessage =
            TelegramThumbKey(
                remoteId = remoteId,
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = sizeBucket,
            )
        assertEquals("tg_thumb_CHAT_MESSAGE_256_test123", chatMessage.toCacheKey())

        val preview =
            TelegramThumbKey(
                remoteId = remoteId,
                kind = ThumbKind.PREVIEW,
                sizeBucket = sizeBucket,
            )
        assertEquals("tg_thumb_PREVIEW_256_test123", preview.toCacheKey())
    }

    @Test
    fun `TelegramThumbKey cache keys are stable across instances`() {
        // Simulate app restart by creating new instances with same data
        val keys =
            (1..5).map {
                TelegramThumbKey(
                    remoteId = "stable_remote_id",
                    kind = ThumbKind.CHAT_MESSAGE,
                    sizeBucket = 256,
                )
            }

        val cacheKeys = keys.map { it.toCacheKey() }

        // All cache keys should be identical
        assertEquals(1, cacheKeys.toSet().size)
        assertEquals("tg_thumb_CHAT_MESSAGE_256_stable_remote_id", cacheKeys.first())
    }

    @Test
    fun `TelegramThumbKey equality works correctly`() {
        val key1 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256,
            )

        val key2 =
            TelegramThumbKey(
                remoteId = "abc123",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256,
            )

        val key3 =
            TelegramThumbKey(
                remoteId = "different",
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256,
            )

        // Data classes with same data should be equal
        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())

        // Different data should not be equal
        assertNotEquals(key1, key3)
    }
}
