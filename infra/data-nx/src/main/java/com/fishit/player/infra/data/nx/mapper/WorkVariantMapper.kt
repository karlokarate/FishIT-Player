/**
 * Maps between NX_WorkVariant entity and NxWorkVariantRepository.Variant domain model.
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.repository.NxWorkVariantRepository.Variant
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.infra.data.nx.mapper.base.PlaybackHintsDecoder

/**
 * Converts NX_WorkVariant entity to Variant domain model.
 */
fun NX_WorkVariant.toDomain(): Variant = Variant(
    variantKey = variantKey,
    workKey = workKey.ifEmpty { work.target?.workKey ?: "" },
    sourceKey = sourceKey,
    label = buildLabel(),
    isDefault = qualityTag == "source",
    qualityHeight = height,
    qualityWidth = width,
    bitrateKbps = bitrateBps?.let { (it / 1000).toInt() },
    container = containerFormat,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    audioLang = languageTag.takeIf { it != "original" },
    durationMs = null, // Entity doesn't store duration
    playbackHints = PlaybackHintsDecoder.decodeFromVariant(this),
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
        workKey = this.workKey,
        // Denormalized fields for indexed queries — SSOT is playbackHintsJson
        playbackUrl = playbackHints[PlaybackHintKeys.Xtream.DIRECT_SOURCE],
        playbackMethod = "DIRECT",
        containerFormat = container,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        width = qualityWidth,
        height = qualityHeight,
        bitrateBps = bitrateKbps?.toLong()?.times(1000),
        // JSON-serialized playbackHints (source-agnostic storage)
        playbackHintsJson = PlaybackHintsDecoder.encodeToJson(playbackHints),
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
