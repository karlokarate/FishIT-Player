/**
 * Maps between NX_WorkVariant entity and NxWorkVariantRepository.Variant domain model.
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkVariantRepository.Variant
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON serializer for playbackHints.
 * Configured for leniency (ignores unknown keys on decode).
 */
private val hintsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * Converts NX_WorkVariant entity to Variant domain model.
 */
fun NX_WorkVariant.toDomain(): Variant = Variant(
    variantKey = variantKey,
    workKey = work.target?.workKey ?: "",
    sourceKey = sourceKey,
    label = buildLabel(),
    isDefault = qualityTag == "source",
    qualityHeight = height,
    bitrateKbps = bitrateBps?.let { (it / 1000).toInt() },
    container = containerFormat,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    audioLang = languageTag.takeIf { it != "original" },
    durationMs = null, // Entity doesn't store duration
    playbackHints = decodePlaybackHints(),
    createdAtMs = createdAt,
    updatedAtMs = createdAt, // Entity only has createdAt
    lastVerifiedAtMs = null,
)

/**
 * Converts Variant domain model to NX_WorkVariant entity.
 */
fun Variant.toEntity(existingEntity: NX_WorkVariant? = null): NX_WorkVariant {
    val entity = existingEntity ?: NX_WorkVariant()
    return entity.copy(
        id = existingEntity?.id ?: 0,
        variantKey = variantKey,
        qualityTag = qualityHeight?.let { "${it}p" } ?: "source",
        languageTag = audioLang ?: "original",
        // Legacy fields kept for backwards compatibility (may be null with new JSON storage)
        playbackUrl = playbackHints["url"] ?: playbackHints["playbackUrl"],
        playbackMethod = playbackHints["method"] ?: playbackHints["playbackMethod"] ?: "DIRECT",
        containerFormat = container,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        width = null, // Derived from height with aspect ratio
        height = qualityHeight,
        bitrateBps = bitrateKbps?.toLong()?.times(1000),
        // JSON-serialized playbackHints (source-agnostic storage)
        playbackHintsJson = encodePlaybackHints(playbackHints),
        sourceKey = sourceKey,
        createdAt = if (existingEntity == null) createdAtMs.takeIf { it > 0 } ?: System.currentTimeMillis() else existingEntity.createdAt,
    )
}

/**
 * Builds a user-readable label for variant selection UI.
 */
private fun NX_WorkVariant.buildLabel(): String {
    val parts = mutableListOf<String>()
    height?.let { parts.add("${it}p") }
    languageTag.takeIf { it != "original" }?.let { parts.add(it.uppercase()) }
    videoCodec?.let { parts.add(it.uppercase()) }
    return parts.joinToString(" • ").ifEmpty { qualityTag }
}

/**
 * Decodes playbackHints from JSON storage.
 *
 * Primary source: playbackHintsJson (new, source-agnostic)
 * Fallback: Legacy entity fields (for backwards compatibility with old data)
 */
private fun NX_WorkVariant.decodePlaybackHints(): Map<String, String> {
    // Primary: JSON storage (contains all source-specific hints)
    if (!playbackHintsJson.isNullOrBlank()) {
        return try {
            hintsJson.decodeFromString<Map<String, String>>(playbackHintsJson!!)
        } catch (e: Exception) {
            // Fallback to legacy on decode error
            buildLegacyPlaybackHints()
        }
    }
    // Fallback: Legacy entity fields (for old data without JSON)
    return buildLegacyPlaybackHints()
}

/**
 * Builds playback hints from legacy entity fields.
 *
 * Used for backwards compatibility with variants created before JSON storage.
 * These fields are NOT sufficient for Xtream/Telegram playback (missing vodId, chatId, etc.)
 * but provide basic hints for simple cases.
 */
private fun NX_WorkVariant.buildLegacyPlaybackHints(): Map<String, String> = buildMap {
    playbackUrl?.let { put("url", it) }
    put("method", playbackMethod)
    containerFormat?.let { put("container", it) }
    videoCodec?.let { put("video_codec", it) }
    audioCodec?.let { put("audio_codec", it) }
}

/**
 * Encodes playbackHints to JSON for storage.
 *
 * @return JSON string, or null if hints are empty (space optimization)
 */
private fun encodePlaybackHints(hints: Map<String, String>): String? {
    if (hints.isEmpty()) return null
    return try {
        hintsJson.encodeToString(hints)
    } catch (e: Exception) {
        null
    }
}
