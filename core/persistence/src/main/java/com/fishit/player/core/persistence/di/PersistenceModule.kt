package com.fishit.player.core.persistence.di

import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.ContentRepository
import com.fishit.player.core.model.repository.ProfileRepository
import com.fishit.player.core.model.repository.ScreenTimeRepository
import com.fishit.player.core.persistence.inspector.DefaultObxDatabaseInspector
import com.fishit.player.core.persistence.inspector.ObxDatabaseInspector
import com.fishit.player.core.persistence.repository.ObxCanonicalMediaRepository
import com.fishit.player.core.persistence.repository.ObxContentRepository
import com.fishit.player.core.persistence.repository.ObxProfileRepository
import com.fishit.player.core.persistence.repository.ObxScreenTimeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for binding repository implementations.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - CanonicalMediaRepository provides cross-pipeline media unification
 * - Single canonical entry per unique media work
 * - Multiple source references linked to each canonical entry
 * - Unified resume across all sources
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ObxProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: ObxContentRepository): ContentRepository

    @Binds
    @Singleton
    abstract fun bindScreenTimeRepository(impl: ObxScreenTimeRepository): ScreenTimeRepository

    // ────────────────────────────────────────────────────────────────────
    // ⚠️ MIGRATED TO NX: CanonicalMediaRepository binding moved to NxDataModule
    // The NX implementation (NxCanonicalMediaRepositoryImpl) is now the SSOT.
    // See: infra/data-nx/src/main/java/.../di/NxDataModule.kt
    //
    // Old binding (REMOVED - DO NOT UNCOMMENT):
    // @Binds
    // @Singleton
    // abstract fun bindCanonicalMediaRepository(impl: ObxCanonicalMediaRepository): CanonicalMediaRepository
    // ────────────────────────────────────────────────────────────────────

    // Debug / power-user tooling
    @Binds
    @Singleton
    abstract fun bindObxDatabaseInspector(impl: DefaultObxDatabaseInspector): ObxDatabaseInspector
}
