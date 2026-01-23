package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkRedirectRepository
import com.fishit.player.core.persistence.obx.NX_WorkRedirect

/**
 * Mapper between NX_WorkRedirect entity and NxWorkRedirectRepository.Redirect domain model.
 */

internal fun NX_WorkRedirect.toDomain(): NxWorkRedirectRepository.Redirect =
    NxWorkRedirectRepository.Redirect(
        obsoleteWorkKey = oldWorkKey,
        targetWorkKey = newWorkKey,
        createdAtMs = createdAt,
    )

internal fun NxWorkRedirectRepository.Redirect.toEntity(): NX_WorkRedirect =
    NX_WorkRedirect(
        oldWorkKey = obsoleteWorkKey,
        newWorkKey = targetWorkKey,
        reason = "dedupe", // Default reason
        createdAt = if (createdAtMs > 0) createdAtMs else System.currentTimeMillis(),
    )
