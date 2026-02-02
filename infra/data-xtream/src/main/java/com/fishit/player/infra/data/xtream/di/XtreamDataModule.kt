package com.fishit.player.infra.data.xtream.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module providing Xtream data layer bindings.
 *
 * **Architecture Compliance:**
 * - Binds ObjectBox-backed implementations to repository interfaces
 * - Repositories work only with RawMediaMetadata (no pipeline DTOs)
 * - Sits in Data layer between Pipeline and Domain
 *
 * **Layer Boundaries:**
 * - Transport → Pipeline → **Data** → Domain → UI
 *
 * **Dependencies:**
 * - Requires BoxStore from core:persistence module
 *
 * ## NX Migration Status (Jan 2026)
 * The following bindings have been migrated to NxDataModule:
 * - XtreamCatalogRepository → NxXtreamCatalogRepositoryImpl
 * - XtreamLiveRepository → NxXtreamLiveRepositoryImpl
 * - XtreamSeriesIndexRepository → NxXtreamSeriesIndexRepository
 *
 * ## PLATIN Phase 3 (Feb 2026)
 * XtreamSeriesIndexRefresher has been REMOVED. Its callers now use
 * UnifiedDetailLoader directly:
 * - LoadSeriesSeasonsUseCase → unifiedDetailLoader.loadSeriesDetailBySeriesId()
 * - LoadSeasonEpisodesUseCase → unifiedDetailLoader.loadSeriesDetailBySeriesId()
 * - EnsureEpisodePlaybackReadyUseCase → unifiedDetailLoader.loadSeriesDetailBySeriesId()
 *
 * See: core/detail-domain/UnifiedDetailLoader.kt
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamDataModule {
    // NOTE: XtreamCatalogRepository binding has been migrated to NxDataModule.
    // The NX implementation (NxXtreamCatalogRepositoryImpl) reads from NX_Work + NX_WorkSourceRef.
    // See: infra/data-nx/src/main/java/.../xtream/NxXtreamCatalogRepositoryImpl.kt

    // /**
    //  * @deprecated Replaced by NxXtreamCatalogRepositoryImpl in infra:data-nx.
    //  */
    // @Binds
    // @Singleton
    // abstract fun bindXtreamCatalogRepository(impl: ObxXtreamCatalogRepository): XtreamCatalogRepository

    // NOTE: XtreamLiveRepository binding has been migrated to NxDataModule.
    // The NX implementation (NxXtreamLiveRepositoryImpl) reads from NX_Work + NX_WorkSourceRef.
    // See: infra/data-nx/src/main/java/.../xtream/NxXtreamLiveRepositoryImpl.kt

    // /**
    //  * @deprecated Replaced by NxXtreamLiveRepositoryImpl in infra:data-nx.
    //  */
    // @Binds
    // @Singleton
    // abstract fun bindXtreamLiveRepository(impl: ObxXtreamLiveRepository): XtreamLiveRepository

    // ========== Feature Layer Repository Bindings ==========
    // NOTE: These bindings have been migrated to NxDataModule (NX-based implementations).
    // The legacy adapters are kept for reference but bindings are disabled to avoid DI conflicts.

    // /**
    //  * Binds [LibraryContentRepositoryAdapter] as the implementation for [LibraryContentRepository].
    //  *
    //  * Provides VOD/Series content for the Library feature screen.
    //  * Maps ObxVod/ObxSeries entities → LibraryMediaItem domain models (direct entity mapping).
    //  *
    //  * Direct entity mapping provides access to:
    //  * - categoryId, categoryName (via lookup)
    //  * - genres (parsed from entity)
    //  * - description/plot
    //  *
    //  * @deprecated Replaced by NxLibraryContentRepositoryImpl in infra:data-nx.
    //  */
    // @Binds
    // @Singleton
    // abstract fun bindLibraryContentRepository(impl: LibraryContentRepositoryAdapter): LibraryContentRepository

    // /**
    //  * Binds [LiveContentRepositoryAdapter] as the implementation for [LiveContentRepository].
    //  *
    //  * Provides live TV channels for the Live feature screen.
    //  * Maps ObxLive entities → LiveChannel domain models (direct entity mapping).
    //  *
    //  * Direct entity mapping provides access to:
    //  * - categoryId, categoryName (via lookup)
    //  * - channelNumber (from streamId)
    //  * - EPG data (from ObxEpgNowNext)
    //  *
    //  * @deprecated Replaced by NxLiveContentRepositoryImpl in infra:data-nx.
    //  */
    // @Binds
    // @Singleton
    // abstract fun bindLiveContentRepository(impl: LiveContentRepositoryAdapter): LiveContentRepository

    // NOTE: XtreamSeriesIndexRepository binding has been migrated to NxDataModule.
    // The NX implementation uses NX_Work + NX_WorkRelation instead of ObxSeasonIndex/ObxEpisodeIndex.
    // See: NxXtreamSeriesIndexRepository

    // /**
    //  * Binds [ObxXtreamSeriesIndexRepository] as the implementation for [XtreamSeriesIndexRepository].
    //  *
    //  * Provides lazy-loaded season and episode indices for series detail screens.
    //  * Features:
    //  * - Season index (7-day TTL)
    //  * - Episode index with paging (7-day TTL)
    //  * - Playback hints for deterministic playback (30-day TTL)
    //  * - Used by LoadSeriesSeasonsUseCase, LoadSeasonEpisodesUseCase, EnsureEpisodePlaybackReadyUseCase
    //  *
    //  * @deprecated Replaced by NxXtreamSeriesIndexRepository in infra:data-nx.
    //  */
    // @Binds
    // @Singleton
    // abstract fun bindXtreamSeriesIndexRepository(impl: ObxXtreamSeriesIndexRepository): XtreamSeriesIndexRepository

    // ========== PLATIN Phase 3: XtreamSeriesIndexRefresher REMOVED ==========
    // The deprecated XtreamSeriesIndexRefresher has been completely removed.
    // All callers now use UnifiedDetailLoader directly:
    //
    // - LoadSeriesSeasonsUseCase.ensureSeasonsLoaded() → unifiedDetailLoader.loadSeriesDetailBySeriesId()
    // - LoadSeasonEpisodesUseCase.ensureEpisodesLoaded() → unifiedDetailLoader.loadSeriesDetailBySeriesId()
    // - EnsureEpisodePlaybackReadyUseCase.invoke() → unifiedDetailLoader.loadSeriesDetailBySeriesId()
    //
    // OLD (deprecated):
    // @Binds
    // @Singleton
    // abstract fun bindXtreamSeriesIndexRefresher(impl: XtreamSeriesIndexRefresherImpl): XtreamSeriesIndexRefresher
}
