package com.fishit.player.infra.transport.xtream.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [FlexibleStringSerializer] that handles Xtream API fields
 * returning either String or Array formats.
 */
class FlexibleStringSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TestModel(
        @Serializable(with = FlexibleStringSerializer::class)
        val field: String? = null,
    )

    @Test
    fun `deserialize string format`() {
        val result = json.decodeFromString<TestModel>("""{"field": "https://image.url"}""")
        assertEquals("https://image.url", result.field)
    }

    @Test
    fun `deserialize array format - single element`() {
        val result = json.decodeFromString<TestModel>("""{"field": ["https://image.url"]}""")
        assertEquals("https://image.url", result.field)
    }

    @Test
    fun `deserialize array format - multiple elements takes first`() {
        val result = json.decodeFromString<TestModel>("""{"field": ["url1", "url2"]}""")
        assertEquals("url1", result.field)
    }

    @Test
    fun `deserialize null`() {
        val result = json.decodeFromString<TestModel>("""{"field": null}""")
        assertNull(result.field)
    }

    @Test
    fun `deserialize empty array`() {
        val result = json.decodeFromString<TestModel>("""{"field": []}""")
        assertNull(result.field)
    }

    @Test
    fun `deserialize blank string`() {
        val result = json.decodeFromString<TestModel>("""{"field": "   "}""")
        assertNull(result.field)
    }

    @Test
    fun `deserialize empty string`() {
        val result = json.decodeFromString<TestModel>("""{"field": ""}""")
        assertNull(result.field)
    }

    @Test
    fun `graceful fallback on object`() {
        val result = json.decodeFromString<TestModel>("""{"field": {}}""")
        assertNull(result.field)
    }

    @Test
    fun `missing field uses default null`() {
        val result = json.decodeFromString<TestModel>("""{}""")
        assertNull(result.field)
    }

    @Test
    fun `deserialize number primitive returns null`() {
        val result = json.decodeFromString<TestModel>("""{"field": 42}""")
        assertNull(result.field)
    }

    @Test
    fun `deserialize boolean primitive returns null`() {
        val result = json.decodeFromString<TestModel>("""{"field": true}""")
        assertNull(result.field)
    }

    @Test
    fun `serialize string value`() {
        val model = TestModel(field = "https://image.url")
        val result = json.encodeToString(TestModel.serializer(), model)
        assertEquals("""{"field":"https://image.url"}""", result)
    }

    @Test
    fun `serialize null value`() {
        val model = TestModel(field = null)
        val result = json.encodeToString(TestModel.serializer(), model)
        // Default value (null) may be included or omitted depending on Json config
        assert(result == """{"field":null}""" || result == """{}""") {
            "Unexpected serialization result: $result"
        }
    }
}
