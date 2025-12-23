package com.fishit.player.infra.data.xtream.di

import com.fishit.player.feature.library.domain.LibraryContentRepository
import com.fishit.player.feature.live.domain.LiveContentRepository
import com.fishit.player.infra.data.xtream.LibraryContentRepositoryAdapter
import com.fishit.player.infra.data.xtream.LiveContentRepositoryAdapter
import com.fishit.player.infra.data.xtream.ObxXtreamCatalogRepository
import com.fishit.player.infra.data.xtream.ObxXtreamLiveRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamDataModule {

    /**
     * Binds [ObxXtreamCatalogRepository] as the implementation for [XtreamCatalogRepository].
     *
     * Handles VOD, Series, and Episodes persistence using ObjectBox entities:
     * - ObxVod for movies
     * - ObxSeries for series metadata
     * - ObxEpisode for individual episodes
     */
    @Binds
    @Singleton
    abstract fun bindXtreamCatalogRepository(
        impl: ObxXtreamCatalogRepository
    ): XtreamCatalogRepository

    /**
     * Binds [ObxXtreamLiveRepository] as the implementation for [XtreamLiveRepository].
     *
     * Handles Live channel persistence using ObjectBox entity:
     * - ObxLive for live streams
     */
    @Binds
    @Singleton
    abstract fun bindXtreamLiveRepository(
        impl: ObxXtreamLiveRepository
    ): XtreamLiveRepository

    // ========== Feature Layer Repository Bindings ==========

    /**
     * Binds [LibraryContentRepositoryAdapter] as the implementation for [LibraryContentRepository].
     *
     * Provides VOD/Series content for the Library feature screen.
     * Maps RawMediaMetadata → LibraryMediaItem domain models.
     */
    @Binds
    @Singleton
    abstract fun bindLibraryContentRepository(
        impl: LibraryContentRepositoryAdapter
    ): LibraryContentRepository

    /**
     * Binds [LiveContentRepositoryAdapter] as the implementation for [LiveContentRepository].
     *
     * Provides live TV channels for the Live feature screen.
     * Maps RawMediaMetadata → LiveChannel domain models.
     */
    @Binds
    @Singleton
    abstract fun bindLiveContentRepository(
        impl: LiveContentRepositoryAdapter
    ): LiveContentRepository
}
