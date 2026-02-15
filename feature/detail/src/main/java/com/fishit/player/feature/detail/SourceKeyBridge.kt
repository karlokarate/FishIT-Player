package com.fishit.player.feature.detail

import com.fishit.player.core.model.SourceType

/**
 * SSOT for extracting [SourceType] from sourceKey strings in the feature/detail layer.
 *
 * **Why this exists:**
 * Feature/detail cannot import `SourceKeyParser` (infra/data-nx, layer boundary).
 * This bridge provides the SINGLE source of truth for sourceType extraction
 * from sourceKey strings when [MediaSourceRef.sourceType] is [SourceType.UNKNOWN].
 *
 * **Supported formats:**
 * - NX format: `src:xtream:account:category:id` → XTREAM
 * - Legacy format: `xtream:vod:123` → XTREAM
 * - Telegram: `telegram:chatId:messageId` → TELEGRAM
 *
 * **Root Cause Note:**
 * This fallback exists because some legacy mappers don't convert String→Enum
 * correctly, producing [SourceType.UNKNOWN]. The long-term fix is to fix the
 * mapper in the data layer; then this bridge becomes dead code.
 *
 * @see SourceSelection.fixSourceTypeIfUnknown
 * @see PlayMediaUseCase.buildPlaybackContext
 */
internal object SourceKeyBridge {
    /**
     * Extract [SourceType] from a sourceKey/sourceId string.
     *
     * @param sourceKey The sourceKey to parse
     * @return Extracted [SourceType] or null if cannot determine
     */
    fun extractSourceType(sourceKey: String): SourceType? {
        val parts = sourceKey.split(":")
        if (parts.isEmpty()) return null

        // Determine format and extract sourceType candidate
        val sourceTypeCandidate =
            when {
                // NX format: src:xtream:account:... → index 1
                parts.size >= 2 && parts[0] == "src" -> parts[1]
                // Legacy format: xtream:vod:... → index 0
                parts.isNotEmpty() -> parts[0]
                else -> return null
            }

        // Map to SourceType enum
        return when (sourceTypeCandidate.lowercase()) {
            "telegram", "tg" -> SourceType.TELEGRAM
            "xtream", "xc" -> SourceType.XTREAM
            "io", "file", "local" -> SourceType.IO
            "audiobook" -> SourceType.AUDIOBOOK
            "plex" -> SourceType.PLEX
            else -> null
        }
    }
}
