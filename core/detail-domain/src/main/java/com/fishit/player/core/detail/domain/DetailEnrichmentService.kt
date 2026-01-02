package com.fishit.player.core.detail.domain

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaWithSources

interface DetailEnrichmentService {
    suspend fun ensureEnriched(
        canonicalId: CanonicalMediaId,
        sourceKey: PipelineItemId? = null,
        requiredHints: List<String> = emptyList(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): CanonicalMediaWithSources?

    suspend fun enrichIfNeeded(media: CanonicalMediaWithSources): CanonicalMediaWithSources

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
    }
}
