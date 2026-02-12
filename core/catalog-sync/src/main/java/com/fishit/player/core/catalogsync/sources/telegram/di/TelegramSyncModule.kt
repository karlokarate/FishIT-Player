// Module: core/catalog-sync/sources/telegram/di
// Hilt DI bindings for unified Telegram sync service

package com.fishit.player.core.catalogsync.sources.telegram.di

import com.fishit.player.core.catalogsync.sources.telegram.DefaultTelegramSyncService
import com.fishit.player.core.catalogsync.sources.telegram.TelegramSyncService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Telegram sync service bindings.
 *
 * Mirrors [XtreamSyncModule]: provides the unified [TelegramSyncService]
 * that encapsulates all Telegram sync logic (checkpoint, pipeline, persistence).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramSyncModule {

    /**
     * Bind the unified Telegram sync service implementation.
     *
     * Singleton to ensure:
     * - Only one sync operation runs at a time
     * - Checkpoint state is consistent
     * - Cancellation works correctly
     */
    @Binds
    @Singleton
    abstract fun bindTelegramSyncService(
        impl: DefaultTelegramSyncService,
    ): TelegramSyncService
}
