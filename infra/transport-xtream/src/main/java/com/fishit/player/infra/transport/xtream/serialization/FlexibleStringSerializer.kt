package com.fishit.player.infra.transport.xtream.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

    override fun deserialize(decoder: Decoder): String? {
        return try {
            val jsonDecoder = decoder as? JsonDecoder
                ?: return decoder.decodeString()

            when (val element = jsonDecoder.decodeJsonElement()) {
                is JsonPrimitive -> {
                    element.contentOrNull?.takeIf { it.isNotBlank() }
                }
                is JsonArray -> {
                    element
                        .firstOrNull()
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        } else {
            encoder.encodeNull()
        }
    }
}
