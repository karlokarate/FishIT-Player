package com.fishit.player.infra.transport.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit tests for XtreamParallelism wrapper class.
 */
class XtreamParallelismTest {
    @Test
    fun `XtreamParallelism stores correct value`() {
        val parallelism = XtreamParallelism(12)
        assertEquals(12, parallelism.value)
    }

    @Test
    fun `XtreamParallelism accepts FireTV parallelism value`() {
        val parallelism = XtreamParallelism(3)
        assertEquals(3, parallelism.value)
    }

    @Test
    fun `XtreamParallelism rejects zero value`() {
        assertThrows(IllegalArgumentException::class.java) {
            XtreamParallelism(0)
        }
    }

    @Test
    fun `XtreamParallelism rejects negative value`() {
        assertThrows(IllegalArgumentException::class.java) {
            XtreamParallelism(-1)
        }
    }

    @Test
    fun `XtreamParallelism equality works correctly`() {
        val p1 = XtreamParallelism(12)
        val p2 = XtreamParallelism(12)
        val p3 = XtreamParallelism(3)

        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
        assert(p1 != p3)
    }
}
