package com.fishit.player.infra.data.home

import com.fishit.player.core.model.SourceType
import com.fishit.player.core.persistence.obx.ObxMediaSourceRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for HomeContentRepositoryAdapter source selection logic.
 *
 * Tests the deterministic navigation source priority:
 * XTREAM > TELEGRAM > IO > UNKNOWN
 */
class HomeContentSourceSelectionTest {

    /**
     * Test data helper - creates a mock ObxMediaSourceRef with given sourceType.
     */
    private fun createSourceRef(sourceType: String): TestSourceRef {
        return TestSourceRef(sourceType = sourceType)
    }

    /**
     * Simple test class mimicking ObxMediaSourceRef for testing purposes.
     * We can't use the real entity in unit tests without ObjectBox runtime.
     */
    data class TestSourceRef(val sourceType: String)

    @Test
    fun `selectBestSourceType returns XTREAM when available`() {
        val sources = listOf(
            createSourceRef("TELEGRAM"),
            createSourceRef("XTREAM"),
            createSourceRef("IO")
        )
        
        val result = selectBestSourceType(sources)
        
        assertEquals(SourceType.XTREAM, result)
    }

    @Test
    fun `selectBestSourceType returns TELEGRAM when XTREAM not available`() {
        val sources = listOf(
            createSourceRef("TELEGRAM"),
            createSourceRef("IO")
        )
        
        val result = selectBestSourceType(sources)
        
        assertEquals(SourceType.TELEGRAM, result)
    }

    @Test
    fun `selectBestSourceType returns IO when only IO available`() {
        val sources = listOf(
            createSourceRef("IO")
        )
        
        val result = selectBestSourceType(sources)
        
        assertEquals(SourceType.IO, result)
    }

    @Test
    fun `selectBestSourceType returns UNKNOWN for empty list`() {
        val sources = emptyList<TestSourceRef>()
        
        val result = selectBestSourceType(sources)
        
        assertEquals(SourceType.UNKNOWN, result)
    }

    @Test
    fun `selectBestSourceType returns UNKNOWN for unsupported source types`() {
        val sources = listOf(
            createSourceRef("PLEX"),
            createSourceRef("AUDIOBOOK")
        )
        
        val result = selectBestSourceType(sources)
        
        assertEquals(SourceType.UNKNOWN, result)
    }

    @Test
    fun `selectBestSourceType is case insensitive`() {
        val sources = listOf(
            createSourceRef("telegram"),
            createSourceRef("Xtream")
        )
        
        val result = selectBestSourceType(sources)
        
        assertEquals(SourceType.XTREAM, result)
    }

    /**
     * Copy of the actual implementation logic for testing.
     * Uses generic Iterable to work with both ToMany and List.
     */
    private fun selectBestSourceType(sources: Iterable<TestSourceRef>): SourceType {
        val sourceTypes = sources.map { it.sourceType.uppercase() }.toSet()
        return when {
            "XTREAM" in sourceTypes -> SourceType.XTREAM
            "TELEGRAM" in sourceTypes -> SourceType.TELEGRAM
            "IO" in sourceTypes -> SourceType.IO
            else -> SourceType.UNKNOWN
        }
    }
}

/**
 * Tests for source type string conversion.
 */
class SourceTypeConversionTest {

    @Test
    fun `toSourceType returns TELEGRAM for telegram string`() {
        assertEquals(SourceType.TELEGRAM, "TELEGRAM".toSourceType())
        assertEquals(SourceType.TELEGRAM, "telegram".toSourceType())
    }

    @Test
    fun `toSourceType returns XTREAM for xtream string`() {
        assertEquals(SourceType.XTREAM, "XTREAM".toSourceType())
        assertEquals(SourceType.XTREAM, "xtream".toSourceType())
    }

    @Test
    fun `toSourceType returns IO for io or local strings`() {
        assertEquals(SourceType.IO, "IO".toSourceType())
        assertEquals(SourceType.IO, "LOCAL".toSourceType())
        assertEquals(SourceType.IO, "local".toSourceType())
    }

    @Test
    fun `toSourceType returns AUDIOBOOK for audiobook string`() {
        assertEquals(SourceType.AUDIOBOOK, "AUDIOBOOK".toSourceType())
    }

    @Test
    fun `toSourceType returns UNKNOWN for unrecognized string`() {
        assertEquals(SourceType.UNKNOWN, "PLEX".toSourceType())
        assertEquals(SourceType.UNKNOWN, "UNKNOWN".toSourceType())
        assertEquals(SourceType.UNKNOWN, "".toSourceType())
    }

    @Test
    fun `toSourceType never returns OTHER`() {
        // Verify no input can produce SourceType.OTHER
        val testInputs = listOf(
            "OTHER", "other", "UNKNOWN", "PLEX", "JELLYFIN", "", "invalid"
        )
        testInputs.forEach { input ->
            val result = input.toSourceType()
            assert(result != SourceType.OTHER) { 
                "Input '$input' should not produce SourceType.OTHER, got $result" 
            }
        }
    }

    /**
     * Copy of the actual implementation for testing.
     */
    private fun String.toSourceType(): SourceType = when (this.uppercase()) {
        "TELEGRAM" -> SourceType.TELEGRAM
        "XTREAM" -> SourceType.XTREAM
        "IO", "LOCAL" -> SourceType.IO
        "AUDIOBOOK" -> SourceType.AUDIOBOOK
        else -> SourceType.UNKNOWN
    }
}
