package com.fishit.player.core.catalogsync.di

import com.fishit.player.core.catalogsync.DataStoreSyncCheckpointStore
import com.fishit.player.core.catalogsync.SyncCheckpointStore
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
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogSyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncCheckpointStore(impl: DataStoreSyncCheckpointStore): SyncCheckpointStore
}
