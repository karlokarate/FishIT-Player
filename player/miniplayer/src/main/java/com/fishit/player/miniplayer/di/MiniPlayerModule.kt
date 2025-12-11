package com.fishit.player.miniplayer.di

import com.fishit.player.miniplayer.DefaultMiniPlayerManager
import com.fishit.player.miniplayer.MiniPlayerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for MiniPlayer dependencies.
 *
 * ════════════════════════════════════════════════════════════════════════════════ PHASE 5 –
 * MiniPlayer DI ════════════════════════════════════════════════════════════════════════════════
 *
 * Provides [MiniPlayerManager] as a singleton for app-wide MiniPlayer state management.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MiniPlayerModule {

    @Binds
    @Singleton
    abstract fun bindMiniPlayerManager(impl: DefaultMiniPlayerManager): MiniPlayerManager
}
