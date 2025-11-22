package com.chris.m3usuite.telegram.repo

import com.chris.m3usuite.data.repo.TelegramContentRepository
import org.junit.Test
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberFunctions

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

    @Test
    fun `TelegramContentRepository has required per-chat query methods`() {
        // Verify per-chat methods exist for unified handling (Section 2)
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.methods.map { it.name }
        
        assert(methods.contains("getTelegramVodByChat")) {
            "TelegramContentRepository should have getTelegramVodByChat method"
        }
        assert(methods.contains("getTelegramSeriesByChat")) {
            "TelegramContentRepository should have getTelegramSeriesByChat method"
        }
    }

    @Test
    fun `TelegramContentRepository toMediaItem is accessible via reflection`() {
        // Verify toMediaItem exists as the single source of truth (Section 1)
        // Note: Since it's private, we can only verify the class structure
        val clazz = TelegramContentRepository::class
        val privateMethods = clazz.declaredMemberFunctions.map { it.name }
        
        assert(privateMethods.contains("toMediaItem")) {
            "TelegramContentRepository should have toMediaItem method as single source of truth"
        }
    }
}
