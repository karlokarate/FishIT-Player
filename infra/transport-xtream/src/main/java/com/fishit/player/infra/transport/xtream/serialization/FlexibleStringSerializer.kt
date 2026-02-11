package com.fishit.player.infra.transport.xtream.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for Xtream API fields that can return String OR Array formats.
 *
 * **Real API Behavior:**
 * - Most providers return: `"backdrop_path": "https://image.url"`
 * - Some providers return: `"backdrop_path": ["https://image.url"]`
 * - Edge cases: `null`, `[]`, `["url1", "url2"]`
 *
 * **Deserialization Logic:**
 * 1. String → return as-is
 * 2. Array → return first non-null element
 * 3. null/empty/invalid → return null
 *
 * **Usage:**
 * ```kotlin
 * @Serializable(with = FlexibleStringSerializer::class)
 * val backdropPath: String? = null
 * ```
 *
 * **Graceful Degradation:**
 * Parse errors return `null` instead of crashing entire response.
 * This prevents one malformed field from breaking all media metadata.
 *
 * @see com.fishit.player.infra.transport.xtream.XtreamApiModels
 */
object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    /**
     * Extracts a non-blank string from a [JsonElement] that may be a String, Array, or other type.
     * Usable from both the serializer and manual JSON parsing (e.g., streaming parsers).
     */
    fun extractString(element: JsonElement?): String? {
        if (element == null) return null
        return try {
            when (element) {
                is JsonPrimitive -> if (element.isString) element.content.takeIf { it.isNotBlank() } else null
                is JsonArray -> element.firstOrNull()?.jsonPrimitive?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            return try {
                extractString(jsonDecoder.decodeJsonElement())
            } catch (_: Exception) {
                null
            }
        }

        // Non-JSON fallback: mirror encodeNullableSerializableValue and apply blank filtering
        val raw = decoder.decodeNullableSerializableValue(String.serializer())
        return raw?.takeIf { it.isNotBlank() }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeNullableSerializableValue(String.serializer(), value)
    }
}
