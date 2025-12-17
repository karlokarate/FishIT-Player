package com.fishit.player.core.metadata

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormalizerBehaviorTest {
    @Test
    fun `xtream live stays unlinked singleton`() {
        val raw =
                RawMediaMetadata(
                        originalTitle = "News Channel HD",
                        mediaType = MediaType.LIVE,
                        sourceType = SourceType.XTREAM,
                        sourceLabel = "Xtream Live",
                        sourceId = "xtream:live:1",
                        pipelineIdTag = PipelineIdTag.XTREAM,
                )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        val entry = normalized.first()
        assertEquals(null, entry.canonicalId)
        assertEquals(MediaType.LIVE, entry.mediaType)
    }

    @Test
    fun `unlinked items are not grouped`() {
        val first =
                RawMediaMetadata(
                        originalTitle = "Mystery Movie",
                        mediaType = MediaType.UNKNOWN,
                        sourceType = SourceType.TELEGRAM,
                        sourceLabel = "TG 1",
                        sourceId = "tg:1",
                        pipelineIdTag = PipelineIdTag.TELEGRAM,
                )
        val second =
                RawMediaMetadata(
                        originalTitle = "Mystery Movie",
                        mediaType = MediaType.UNKNOWN,
                        sourceType = SourceType.IO,
                        sourceLabel = "Local",
                        sourceId = "file://2",
                        pipelineIdTag = PipelineIdTag.IO,
                )

        val normalized = Normalizer.normalize(listOf(first, second))

        assertEquals(2, normalized.size)
        assertEquals(null, normalized[0].canonicalId)
        assertEquals(null, normalized[1].canonicalId)
        assertTrue(normalized[0].primarySourceId != normalized[1].primarySourceId)
    }
}
