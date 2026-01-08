package com.fishit.player.infra.device

import com.fishit.player.core.device.DeviceClassProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for device detection.
 *
 * Binds AndroidDeviceClassProvider as the implementation of DeviceClassProvider.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {
    @Binds
    @Singleton
    abstract fun bindDeviceClassProvider(
        impl: AndroidDeviceClassProvider,
    ): DeviceClassProvider
}
