package com.fishit.player.internal.di

import com.fishit.player.internal.miniplayer.InternalMiniPlayerStateSource
import com.fishit.player.ui.api.MiniPlayerStateSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for mini-player state source binding.
 *
 * Binds the MiniPlayerStateSource interface to its implementation,
 * allowing mini-player to depend on the abstraction without
 * knowing about internal player state.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MiniPlayerStateModule {

    @Binds
    @Singleton
    abstract fun bindMiniPlayerStateSource(
        impl: InternalMiniPlayerStateSource
    ): MiniPlayerStateSource
}
