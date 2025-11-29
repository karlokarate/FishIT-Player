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
    fun `TelegramContentRepository has getTelegramContentByChatIds method`() {
        // Verify method for retrieving content by multiple chat IDs exists
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.declaredMethods.map { it.name }

        assert(methods.contains("getTelegramContentByChatIds")) {
            "TelegramContentRepository should have getTelegramContentByChatIds method"
        }
    }

    @Test
    fun `TelegramContentRepository has searchTelegramContent method`() {
        // Verify search method exists
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("searchTelegramContent")) {
            "TelegramContentRepository should have searchTelegramContent method"
        }
    }

    @Test
    fun `TelegramContentRepository has getTelegramContentByChat method`() {
        // Verify single-chat query method exists
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("getTelegramContentByChat")) {
            "TelegramContentRepository should have getTelegramContentByChat method with per-chat grouping"
        }
    }

    @Test
    fun `TelegramContentRepository has resolveChatTitle method`() {
        // Verify resolveChatTitle method exists for chat title resolution
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.declaredMethods.map { it.name }

        assert(methods.contains("resolveChatTitle")) {
            "TelegramContentRepository should have resolveChatTitle method"
        }
    }

    @Test
    fun `MediaItem has required fields for Telegram integration`() {
        // Structural test: Verify that MediaItem has all required Telegram fields
        val mediaItemClass = com.chris.m3usuite.model.MediaItem::class
        val properties = mediaItemClass.java.declaredFields.map { it.name }

        // Required fields for Telegram integration
        val requiredFields =
            listOf(
                "id",
                "name",
                "url",
                "posterId",
                "localPosterPath",
                "localVideoPath",
                "localPhotoPath",
                "localDocumentPath",
                "tgChatId",
                "tgMessageId",
                "tgFileId",
            )

        requiredFields.forEach { field ->
            assert(properties.contains(field)) {
                "MediaItem should have $field field for Telegram integration"
            }
        }
    }

    @Test
    fun `TelegramContentRepository VOD mapping returns correct type`() {
        // Structural test: Verify getTelegramVodByChat returns Flow
        val clazz = TelegramContentRepository::class
        val method = clazz.java.methods.find { it.name == "getTelegramVodByChat" }

        assert(method != null) {
            "getTelegramVodByChat method should exist"
        }

        // Verify return type is Flow
        assert(method!!.returnType.name.contains("Flow")) {
            "getTelegramVodByChat should return Flow type"
        }
    }

    @Test
    fun `TelegramContentRepository Series mapping returns correct type`() {
        // Structural test: Verify getTelegramSeriesByChat returns Flow
        val clazz = TelegramContentRepository::class
        val method = clazz.java.methods.find { it.name == "getTelegramSeriesByChat" }

        assert(method != null) {
            "getTelegramSeriesByChat method should exist"
        }

        // Verify return type is Flow
        assert(method!!.returnType.name.contains("Flow")) {
            "getTelegramSeriesByChat should return Flow type"
        }
    }

    @Test
    fun `TelegramContentRepository has toMediaItem mapping method`() {
        // Verify toMediaItem method exists (single-source mapping)
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.declaredMethods.map { it.name }

        assert(methods.contains("toMediaItem")) {
            "TelegramContentRepository should have toMediaItem method for single-source mapping"
        }
    }

    @Test
    fun `TelegramContentRepository has buildTelegramUrl method`() {
        // Verify buildTelegramUrl method exists for URL generation
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.declaredMethods.map { it.name }

        assert(methods.contains("buildTelegramUrl")) {
            "TelegramContentRepository should have buildTelegramUrl method for tg:// URL generation"
        }
    }

    // =========================================================================
    // Phase B - New Domain-Oriented API Tests
    // =========================================================================

    @Test
    fun `TelegramContentRepository has upsertItems method for TelegramItem domain objects`() {
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("upsertItems")) {
            "TelegramContentRepository should have upsertItems method for Phase B"
        }
    }

    @Test
    fun `TelegramContentRepository has observeItemsByChat method`() {
        val clazz = TelegramContentRepository::class
        val method = clazz.java.methods.find { it.name == "observeItemsByChat" }

        assert(method != null) {
            "TelegramContentRepository should have observeItemsByChat method for Phase B"
        }
        assert(method!!.returnType.name.contains("Flow")) {
            "observeItemsByChat should return Flow type"
        }
    }

    @Test
    fun `TelegramContentRepository has observeAllItems method`() {
        val clazz = TelegramContentRepository::class
        val method = clazz.java.methods.find { it.name == "observeAllItems" }

        assert(method != null) {
            "TelegramContentRepository should have observeAllItems method for Phase B"
        }
        assert(method!!.returnType.name.contains("Flow")) {
            "observeAllItems should return Flow type"
        }
    }

    @Test
    fun `TelegramContentRepository has getItem method`() {
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("getItem")) {
            "TelegramContentRepository should have getItem method for Phase B"
        }
    }

    @Test
    fun `TelegramContentRepository has deleteItem method`() {
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("deleteItem")) {
            "TelegramContentRepository should have deleteItem method for Phase B"
        }
    }

    @Test
    fun `TelegramContentRepository has clearAllItems method`() {
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("clearAllItems")) {
            "TelegramContentRepository should have clearAllItems method for Phase B"
        }
    }

    @Test
    fun `TelegramContentRepository has getTelegramItemCount method`() {
        val clazz = TelegramContentRepository::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("getTelegramItemCount")) {
            "TelegramContentRepository should have getTelegramItemCount method for Phase B"
        }
    }

    @Test
    fun `indexChatMessages is deprecated`() {
        val clazz = TelegramContentRepository::class
        val method = clazz.java.methods.find { it.name == "indexChatMessages" }

        assert(method != null) {
            "indexChatMessages method should exist"
        }

        // Check that it has Deprecated annotation
        val hasDeprecated = method!!.annotations.any { it.annotationClass.simpleName == "Deprecated" }
        assert(hasDeprecated) {
            "indexChatMessages should be marked as @Deprecated"
        }
    }
}
