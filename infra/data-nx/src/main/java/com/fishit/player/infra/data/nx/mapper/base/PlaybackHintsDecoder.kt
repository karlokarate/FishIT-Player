/**
 * SSOT for PlaybackHints encoding and decoding.
 *
 * **Eliminates 5 duplicate implementations:**
 * - NxCanonicalMediaRepositoryImpl.buildPlaybackHints()
 * - NxDetailMediaRepositoryImpl.buildPlaybackHints()
 * - WorkDetailMapper.buildPlaybackHints()
 * - WorkVariantMapper.decodePlaybackHints()
 * - NxXtreamSeriesIndexRepository.buildPlaybackHintsJsonFromVariant()
 *
 * **Key design decisions:**
 * - ALL keys use [PlaybackHintKeys] constants — no raw strings
 * - JSON decoding uses [ignoreUnknownKeys = true] for forward compatibility
 * - Single encode/decode path eliminates key-schema divergence
 *
 * **DEV PHASE:** No legacy/migration fallbacks. Every sync writes fresh data.
 * If playbackHintsJson is empty/corrupt, return emptyMap() — next sync fixes it.
 */
package com.fishit.player.infra.data.nx.mapper.base

import com.fishit.player.core.persistence.obx.NX_WorkVariant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lenient JSON serializer for playbackHints.
 *
 * `ignoreUnknownKeys = true` prevents crashes when stored JSON contains keys
 * from a newer app version or from pipelines that add extra hints.
 */
private val hintsJson =
    Json {
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
     * Decodes playbackHints from a variant entity.
     *
     * **Use when:** You have only a variant (no sourceRef context).
     * Called by: WorkVariantMapper.toDomain(), NxCanonicalMediaRepositoryImpl.mapToMediaSourceRef()
     *
     * **DEV PHASE:** No legacy fallback. If JSON is missing/corrupt → emptyMap().
     * Next sync writes fresh data with correct playbackHintsJson.
     */
    fun decodeFromVariant(variant: NX_WorkVariant?): Map<String, String> {
        if (variant == null) return emptyMap()
        if (variant.playbackHintsJson.isNullOrBlank()) return emptyMap()
        return decodeJson(variant.playbackHintsJson!!) ?: emptyMap()
    }

    /**
     * Decodes playbackHints from a variant's JSON storage.
     *
     * **Use when:** You have both variant and sourceRef (detail view, source info).
     * Called by: NxDetailMediaRepositoryImpl, WorkDetailMapper
     *
     * **DEV PHASE:** Source-identity hints (xtream streamId, telegram chatId) are
     * stored IN the playbackHintsJson at write time. No need to reconstruct from
     * entity fields.
     */
    fun decodeFromVariantAndSource(variant: NX_WorkVariant?): Map<String, String> = decodeFromVariant(variant)

    /**
     * Decodes a raw JSON string to a hints map.
     *
     * @return decoded map, or null on parse error
     */
    fun decodeJson(json: String): Map<String, String>? =
        try {
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
}
