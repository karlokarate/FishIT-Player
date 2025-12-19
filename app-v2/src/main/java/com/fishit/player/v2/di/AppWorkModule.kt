package com.fishit.player.v2.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.TmdbEnrichmentScheduler
import com.fishit.player.v2.work.WorkManagerSyncStateObserver
import com.fishit.player.v2.work.CatalogSyncWorkSchedulerImpl
import com.fishit.player.v2.work.TmdbEnrichmentSchedulerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppWorkModule {

    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(
        workerFactory: HiltWorkerFactory,
    ): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * SSOT: All catalog sync scheduling goes through this single implementation.
     *
     * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
     * - uniqueWorkName = "catalog_sync_global"
     * - No UI/ViewModel may call CatalogSyncService directly
     */
    @Provides
    @Singleton
    fun provideCatalogSyncWorkScheduler(
        @ApplicationContext context: Context,
    ): CatalogSyncWorkScheduler = CatalogSyncWorkSchedulerImpl(context)
    
    /**
     * Provides SyncStateObserver for feature modules to observe sync state.
     * 
     * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
     */
    @Provides
    @Singleton
    fun provideSyncStateObserver(
        observer: WorkManagerSyncStateObserver,
    ): SyncStateObserver = observer

    /**
     * SSOT: All TMDB enrichment scheduling goes through this single implementation.
     *
     * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
     * - uniqueWorkName = "tmdb_enrichment_global"
     * - W-22: TMDB Scope Priority: DETAILS_BY_ID → RESOLVE_MISSING_IDS
     */
    @Provides
    @Singleton
    fun provideTmdbEnrichmentScheduler(
        @ApplicationContext context: Context,
    ): TmdbEnrichmentScheduler = TmdbEnrichmentSchedulerImpl(context)
}
