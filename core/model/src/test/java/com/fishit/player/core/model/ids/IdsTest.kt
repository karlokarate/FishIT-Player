package com.fishit.player.core.model.ids

import kotlin.test.Test
import kotlin.test.assertEquals

class IdsTest {
    @Test
    fun `canonical id wraps string`() {
        val id = CanonicalId("tmdb:603")

        assertEquals("tmdb:603", id.value)
        assertEquals("tmdb:603", id.toString())
    }

    @Test
    fun `tmdb id wraps integer`() {
        val id = TmdbId(603)

        assertEquals(603, id.value)
        assertEquals("603", id.toString())
    }

    @Test
    fun `pipeline ids remain distinct`() {
        val canonical = CanonicalId("movie:abc")
        val remote = RemoteId("remote_123")
        val pipelineItemId = PipelineItemId("pipe:1")

        assertEquals(canonical, acceptCanonical(canonical))
        assertEquals(remote, acceptRemote(remote))
        assertEquals(pipelineItemId, PipelineItemId(pipelineItemId.value))
    }

    private fun acceptCanonical(id: CanonicalId): CanonicalId = id

    private fun acceptRemote(id: RemoteId): RemoteId = id
}
