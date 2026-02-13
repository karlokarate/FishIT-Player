// Module: core/catalog-sync/sources/xtream/di
// Hilt DI bindings for Xtream catalog sync

package com.fishit.player.core.catalogsync.sources.xtream.di

import com.fishit.player.core.catalogsync.sources.xtream.XtreamCatalogSync
import com.fishit.player.core.catalogsync.sources.xtream.XtreamSyncService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Xtream sync service bindings.
 *
 * Provides the unified [XtreamSyncService] that replaces
 * the 6-method pattern with a single configurable entry point.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamSyncModule {

    /**
     * Bind the unified Xtream sync service implementation.
     *
     * The service is a singleton to ensure:
     * - Only one sync operation runs at a time
     * - Checkpoint state is consistent
     * - Cancellation works correctly
     */
    @Binds
    @Singleton
    abstract fun bindXtreamSyncService(
        impl: XtreamCatalogSync,
    ): XtreamSyncService
}
