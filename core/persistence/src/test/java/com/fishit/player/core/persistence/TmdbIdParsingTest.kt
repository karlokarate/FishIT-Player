package com.fishit.player.core.persistence

import com.fishit.player.core.model.ids.TmdbId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TmdbIdParsingTest {
    @Test
    fun `parses tmdb-prefixed strings`() {
        assertEquals(TmdbId(550), "tmdb:550".toTmdbIdOrNull())
    }

    @Test
    fun `parses numeric strings`() {
        assertEquals(TmdbId(1399), "1399".toTmdbIdOrNull())
    }

    @Test
    fun `rejects non-tmdb values`() {
        assertNull("xtream:vod:12345".toTmdbIdOrNull())
    }
}
