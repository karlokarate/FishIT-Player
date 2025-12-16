package com.fishit.player.core.metadata

import com.fishit.player.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalIdUtilTest {
    @Test
    fun `generates movie fallback key without hash`() {
        val key = GlobalIdUtil.generateCanonicalId("Fight Club 1080p", 1999)

        assertEquals("movie:fight-club:1999", key.value)
    }

    @Test
    fun `generates episode fallback key`() {
        val key = GlobalIdUtil.generateCanonicalId(
                originalTitle = "Breaking Bad",
                year = 2008,
                season = 5,
                episode = 16,
                mediaType = MediaType.SERIES_EPISODE,
        )

        assertEquals("episode:breaking-bad:S05E16", key.value)
    }
}
