package com.fishit.player.core.metadata.parser

import org.junit.Assert.*
import org.junit.Test

class RegexSceneNameParserSimpleTest {
    private val parser = RegexSceneNameParser()

    @Test
    fun testSimpleMovie() {
        val parsed = parser.parse("Die Maske - 1994.mp4")
        assertEquals("Die Maske", parsed.title)
        assertEquals(1994, parsed.year)
    }
}
