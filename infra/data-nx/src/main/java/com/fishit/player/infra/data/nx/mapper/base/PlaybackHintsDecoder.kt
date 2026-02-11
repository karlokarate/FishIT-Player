/**
 * SSOT for PlaybackHints encoding, decoding, and legacy-field fallback.
 *
 * **Eliminates 5 duplicate implementations:**
 * - NxCanonicalMediaRepositoryImpl.buildPlaybackHints() / buildLegacyPlaybackHints()
 * - NxDetailMediaRepositoryImpl.buildPlaybackHints() / buildLegacyPlaybackHints()
 * - WorkDetailMapper.buildPlaybackHints() / buildLegacyPlaybackHints()
 * - WorkVariantMapper.decodePlaybackHints() / buildLegacyPlaybackHints()
 * - NxXtreamSeriesIndexRepository.buildPlaybackHintsJsonFromVariant()
 *
 * **Key design decisions:**
 * - ALL keys use [PlaybackHintKeys] constants — no raw strings
 * - JSON decoding uses [ignoreUnknownKeys = true] for forward compatibility
 * - Legacy fallback maps old entity fields to correct PlaybackHintKeys constants
 * - Single encode/decode path eliminates key-schema divergence
 */
package com.fishit.player.infra.data.nx.mapper.base

import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lenient JSON serializer for playbackHints.
 *
 * `ignoreUnknownKeys = true` prevents crashes when stored JSON contains keys
 * from a newer app version or from pipelines that add extra hints.
 */
private val hintsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * Central utility for all PlaybackHints operations.
 *
 * All callers MUST use this instead of implementing their own decode/encode/legacy logic.
 */
object PlaybackHintsDecoder {

    // =========================================================================
    // DECODE: JSON → Map<String, String>
    // =========================================================================

    /**
     * Decodes playbackHints from a variant entity, with legacy-field fallback.
     *
     * **Use when:** You have only a variant (no sourceRef context).
     * Called by: WorkVariantMapper.toDomain(), NxCanonicalMediaRepositoryImpl.mapToMediaSourceRef()
     *
     * Strategy:
     * 1. If `playbackHintsJson` is present → decode from JSON
     * 2. Fallback → build from legacy entity fields using [PlaybackHintKeys] constants
     */
    fun decodeFromVariant(variant: NX_WorkVariant?): Map<String, String> {
        if (variant == null) return emptyMap()

        // Primary: JSON storage (contains all source-specific hints)
        if (!variant.playbackHintsJson.isNullOrBlank()) {
            return decodeJson(variant.playbackHintsJson!!)
                ?: buildLegacyHintsFromVariant(variant)
        }

        // Fallback: Legacy entity fields (for old data without JSON)
        return buildLegacyHintsFromVariant(variant)
    }

    /**
     * Decodes playbackHints from a variant + sourceRef pair, with legacy-field fallback.
     *
     * **Use when:** You have both variant and sourceRef (detail view, source info).
     * Called by: NxDetailMediaRepositoryImpl, WorkDetailMapper
     *
     * The sourceRef contributes source-identity hints (telegram IDs, xtream IDs)
     * that are NOT available in variant-only context.
     */
    fun decodeFromVariantAndSource(
        variant: NX_WorkVariant?,
        sourceRef: NX_WorkSourceRef,
    ): Map<String, String> {
        // Primary: JSON storage from variant
        if (variant != null && !variant.playbackHintsJson.isNullOrBlank()) {
            return decodeJson(variant.playbackHintsJson!!)
                ?: buildLegacyHintsFromSourceAndVariant(sourceRef, variant)
        }

        // Fallback: Build from entity fields using PlaybackHintKeys
        return buildLegacyHintsFromSourceAndVariant(sourceRef, variant)
    }

    /**
     * Decodes a raw JSON string to a hints map.
     *
     * @return decoded map, or null on parse error
     */
    fun decodeJson(json: String): Map<String, String>? = try {
        hintsJson.decodeFromString<Map<String, String>>(json)
    } catch (_: Exception) {
        null
    }

    // =========================================================================
    // ENCODE: Map<String, String> → JSON
    // =========================================================================

    /**
     * Encodes a hints map to JSON for entity storage.
     *
     * @return JSON string, or null if hints are empty (space optimization)
     */
    fun encodeToJson(hints: Map<String, String>): String? {
        if (hints.isEmpty()) return null
        return try {
            hintsJson.encodeToString(hints)
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // LEGACY FALLBACK: Entity fields → Map using PlaybackHintKeys
    // =========================================================================

    /**
     * Builds hints from variant-only legacy fields.
     *
     * Maps old entity fields to [PlaybackHintKeys] constants:
     * - containerFormat → [PlaybackHintKeys.Xtream.CONTAINER_EXT]
     * - videoCodec → [PlaybackHintKeys.VIDEO_CODEC]
     * - audioCodec → [PlaybackHintKeys.AUDIO_CODEC]
     *
     * Note: playbackUrl and playbackMethod are v1 legacy fields with NO consumer
     * in the v2 PlaybackSourceFactory system. They are NOT included because
     * no downstream code reads them via PlaybackHintKeys.
     */
    private fun buildLegacyHintsFromVariant(variant: NX_WorkVariant): Map<String, String> =
        buildMap {
            variant.containerFormat?.let { put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it) }
            variant.videoCodec?.let { put(PlaybackHintKeys.VIDEO_CODEC, it) }
            variant.audioCodec?.let { put(PlaybackHintKeys.AUDIO_CODEC, it) }
            variant.height?.let { put(PlaybackHintKeys.VIDEO_HEIGHT, it.toString()) }
            variant.width?.let { put(PlaybackHintKeys.VIDEO_WIDTH, it.toString()) }
        }

    /**
     * Builds hints from sourceRef + variant legacy fields.
     *
     * Includes source-identity hints needed for playback resolution:
     * - Telegram: chatId, messageId
     * - Xtream: streamId, containerExtension
     * - Common: codec info, file metadata
     */
    private fun buildLegacyHintsFromSourceAndVariant(
        sourceRef: NX_WorkSourceRef,
        variant: NX_WorkVariant?,
    ): Map<String, String> = buildMap {
        // Source-specific identity hints (required for playback URL construction)
        when (sourceRef.sourceType.lowercase()) {
            "telegram" -> {
                sourceRef.telegramChatId?.let { put(PlaybackHintKeys.Telegram.CHAT_ID, it.toString()) }
                sourceRef.telegramMessageId?.let { put(PlaybackHintKeys.Telegram.MESSAGE_ID, it.toString()) }
                sourceRef.mimeType?.let { put(PlaybackHintKeys.Telegram.MIME_TYPE, it) }
                sourceRef.fileSizeBytes?.let { put(PlaybackHintKeys.Telegram.FILE_SIZE, it.toString()) }
            }
            "xtream" -> {
                sourceRef.xtreamStreamId?.let { put(PlaybackHintKeys.Xtream.STREAM_ID, it.toString()) }
            }
        }

        // Variant fields (quality + codec hints)
        variant?.let { v ->
            v.containerFormat?.let { put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it) }
            v.videoCodec?.let { put(PlaybackHintKeys.VIDEO_CODEC, it) }
            v.audioCodec?.let { put(PlaybackHintKeys.AUDIO_CODEC, it) }
            v.height?.let { put(PlaybackHintKeys.VIDEO_HEIGHT, it.toString()) }
            v.width?.let { put(PlaybackHintKeys.VIDEO_WIDTH, it.toString()) }
        }
    }
}
