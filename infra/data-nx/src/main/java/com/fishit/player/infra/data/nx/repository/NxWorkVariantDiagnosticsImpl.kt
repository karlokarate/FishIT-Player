/**
 * ObjectBox implementation of [NxWorkVariantDiagnostics].
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkVariantDiagnostics
import com.fishit.player.core.model.repository.NxWorkVariantRepository.Variant
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.infra.data.nx.mapper.toDomain
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostics implementation for playback variants.
 */
@Singleton
class NxWorkVariantDiagnosticsImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkVariantDiagnostics {
    private val box by lazy { boxStore.boxFor<NX_WorkVariant>() }
    private val workBox by lazy { boxStore.boxFor<NX_Work>() }

    override suspend fun countAll(): Long = withContext(Dispatchers.IO) {
        box.count()
    }

    override suspend fun findOrphanedVariants(limit: Int): List<Variant> = withContext(Dispatchers.IO) {
        val validWorkIds = workBox.all.map { it.id }.toSet()
        box.all
            .filter { it.work.targetId == 0L || it.work.targetId !in validWorkIds }
            .take(limit)
            .map { it.toDomain() }
    }

    override suspend fun findVariantsMissingPlaybackHints(limit: Int): List<Variant> = withContext(Dispatchers.IO) {
        box.all
            .filter { it.playbackHintsJson.isNullOrBlank() && it.playbackUrl.isNullOrBlank() }
            .take(limit)
            .map { it.toDomain() }
    }

    override suspend fun countVariantsMissingPlaybackHints(): Long = withContext(Dispatchers.IO) {
        box.all.count { it.playbackHintsJson.isNullOrBlank() && it.playbackUrl.isNullOrBlank() }.toLong()
    }
}
