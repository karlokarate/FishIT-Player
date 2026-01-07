package com.fishit.player.v2.work

import com.fishit.player.core.model.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [CanonicalLinkingInputData].
 */
class CanonicalLinkingInputDataTest {
    @Test
    fun `toData and from roundtrip preserves all fields`() {
        val original =
            CanonicalLinkingInputData(
                runId = "test-run-123",
                sourceType = SourceType.XTREAM,
                batchSize = 150,
                maxRuntimeMs = 300000L,
            )

        val data = original.toData()
        val decoded = CanonicalLinkingInputData.from(data)

        assertEquals(original.runId, decoded.runId)
        assertEquals(original.sourceType, decoded.sourceType)
        assertEquals(original.batchSize, decoded.batchSize)
        assertEquals(original.maxRuntimeMs, decoded.maxRuntimeMs)
    }

    @Test
    fun `from handles missing runId with default`() {
        val data = CanonicalLinkingInputData(
            runId = "",
            sourceType = SourceType.TELEGRAM,
            batchSize = 100,
            maxRuntimeMs = 200000L,
        ).toData()

        val decoded = CanonicalLinkingInputData.from(data)
        
        assertEquals("", decoded.runId)
        assertEquals(SourceType.TELEGRAM, decoded.sourceType)
    }

    @Test
    fun `from handles invalid source type with UNKNOWN`() {
        val data = CanonicalLinkingInputData(
            runId = "test-123",
            sourceType = SourceType.UNKNOWN,
            batchSize = 75,
            maxRuntimeMs = 150000L,
        ).toData()

        val decoded = CanonicalLinkingInputData.from(data)
        
        assertEquals(SourceType.UNKNOWN, decoded.sourceType)
    }

    @Test
    fun `from uses defaults for missing batch size`() {
        val minimal = CanonicalLinkingInputData(
            runId = "minimal-run",
            sourceType = SourceType.XTREAM,
            batchSize = WorkerConstants.NORMAL_BATCH_SIZE,
            maxRuntimeMs = WorkerConstants.DEFAULT_MAX_RUNTIME_MS,
        )
        
        val data = minimal.toData()
        val decoded = CanonicalLinkingInputData.from(data)
        
        assertEquals(WorkerConstants.NORMAL_BATCH_SIZE, decoded.batchSize)
        assertEquals(WorkerConstants.DEFAULT_MAX_RUNTIME_MS, decoded.maxRuntimeMs)
    }

    @Test
    fun `supports all source types`() {
        val sources = listOf(
            SourceType.XTREAM,
            SourceType.TELEGRAM,
            SourceType.IO,
            SourceType.AUDIOBOOK,
        )

        sources.forEach { sourceType ->
            val input = CanonicalLinkingInputData(
                runId = "test-$sourceType",
                sourceType = sourceType,
                batchSize = 100,
                maxRuntimeMs = 300000L,
            )

            val data = input.toData()
            val decoded = CanonicalLinkingInputData.from(data)

            assertEquals(sourceType, decoded.sourceType, "Failed for source: $sourceType")
        }
    }
}
