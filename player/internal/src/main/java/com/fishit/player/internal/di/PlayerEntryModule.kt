package com.fishit.player.internal.di

import com.fishit.player.internal.InternalPlayerEntryImpl
import com.fishit.player.playback.domain.PlayerEntryPoint
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for player entry point binding.
 *
 * Binds the PlayerEntryPoint interface to its implementation,
 * allowing feature modules to depend on the abstraction without
 * knowing about concrete player internals.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerEntryModule {

    @Binds
    @Singleton
    abstract fun bindPlayerEntryPoint(
        impl: InternalPlayerEntryImpl
    ): PlayerEntryPoint
}
