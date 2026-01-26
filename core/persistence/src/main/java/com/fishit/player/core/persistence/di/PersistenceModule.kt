package com.fishit.player.core.persistence.di

import com.fishit.player.core.persistence.inspector.DefaultObxDatabaseInspector
import com.fishit.player.core.persistence.inspector.ObxDatabaseInspector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for binding **legacy** Obx repository implementations.
 *
 * ⚠️ MIGRATION STATUS (2025-01-20):
 * All legacy bindings have been migrated to NX. See NxDataModule.kt for active bindings.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - CanonicalMediaRepository → NxCanonicalMediaRepository (in NxDataModule)
 * - ProfileRepository → NxProfileRepository (in NxDataModule)
 * - ScreenTimeRepository → NxProfileUsageRepository (in NxDataModule)
 * - ContentRepository → HomeContentRepository/LibraryContentRepository/LiveContentRepository (in NxDataModule)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {

    // ────────────────────────────────────────────────────────────────────
    // ⚠️ MIGRATED TO NX: ProfileRepository binding moved to NxDataModule
    // The NX implementation (NxProfileRepositoryImpl) is now the SSOT.
    // See: infra/data-nx/src/main/java/.../di/NxDataModule.kt
    //
    // Old binding (REMOVED - DO NOT UNCOMMENT):
    // @Binds
    // @Singleton
    // abstract fun bindProfileRepository(impl: ObxProfileRepository): ProfileRepository
    // ────────────────────────────────────────────────────────────────────

    // ────────────────────────────────────────────────────────────────────
    // ⚠️ MIGRATED TO NX: ContentRepository eliminated
    // Feature-specific repositories now serve UI:
    // - HomeContentRepository (NxHomeContentRepositoryImpl)
    // - LibraryContentRepository (NxLibraryContentRepositoryImpl)
    // - LiveContentRepository (NxLiveContentRepositoryImpl)
    // See: infra/data-nx/src/main/java/.../di/NxDataModule.kt
    //
    // Old binding (REMOVED - DO NOT UNCOMMENT):
    // @Binds
    // @Singleton
    // abstract fun bindContentRepository(impl: ObxContentRepository): ContentRepository
    // ────────────────────────────────────────────────────────────────────

    // ────────────────────────────────────────────────────────────────────
    // ⚠️ MIGRATED TO NX: ScreenTimeRepository binding moved to NxDataModule
    // The NX implementation (NxProfileUsageRepositoryImpl) is now the SSOT.
    // See: infra/data-nx/src/main/java/.../di/NxDataModule.kt
    //
    // Old binding (REMOVED - DO NOT UNCOMMENT):
    // @Binds
    // @Singleton
    // abstract fun bindScreenTimeRepository(impl: ObxScreenTimeRepository): ScreenTimeRepository
    // ────────────────────────────────────────────────────────────────────

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
