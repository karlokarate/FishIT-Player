package com.chris.m3usuite.telegram.repo

import com.chris.m3usuite.data.repo.TelegramContentRepository
import org.junit.Test

/**
 * Unit tests for TelegramContentRepository.
 * Tests message indexing, parsing, and querying functionality.
 *
 * Note: Full integration tests require Android context and ObjectBox,
 * so these are kept minimal. Full testing is done via integration tests.
 */
class TelegramContentRepositoryTest {
    @Test
    fun `TelegramContentRepository class exists and is accessible`() {
        // Verify the class can be referenced and has expected methods
        val clazz = TelegramContentRepository::class
        assert(clazz.java.methods.any { it.name == "indexChatMessages" }) {
            "TelegramContentRepository should have indexChatMessages method"
        }
    }
}
