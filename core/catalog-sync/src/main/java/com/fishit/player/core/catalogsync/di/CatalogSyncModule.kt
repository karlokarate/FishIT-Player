package com.fishit.player.core.catalogsync.di

import com.fishit.player.core.catalogsync.CatalogSyncService
import com.fishit.player.core.catalogsync.DefaultCatalogSyncService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing CatalogSync bindings.
 *
 * **Architecture Position:**
 * Transport → Pipeline → **CatalogSync** → Data → Domain → UI
 *
 * **Dependencies:**
 * - TelegramCatalogPipeline (from pipeline:telegram)
 * - XtreamCatalogPipeline (from pipeline:xtream)
 * - TelegramContentRepository (from infra:data-telegram)
 * - XtreamCatalogRepository (from infra:data-xtream)
 * - XtreamLiveRepository (from infra:data-xtream)
 * - MediaMetadataNormalizer (from core:metadata-normalizer)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogSyncModule {
    @Binds
    @Singleton
    abstract fun bindCatalogSyncService(impl: DefaultCatalogSyncService): CatalogSyncService
}
