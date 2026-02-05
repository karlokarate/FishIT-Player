package com.fishit.player.core.synccommon.di

import android.content.Context
import com.fishit.player.core.synccommon.checkpoint.SyncCheckpointStore
import com.fishit.player.core.synccommon.device.DeviceProfileDetector
import com.fishit.player.core.synccommon.metrics.SyncPerfMetrics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing sync infrastructure components.
 *
 * **Scope:**
 * All components are singletons to ensure consistent state across sync operations.
 *
 * **Provided Components:**
 * - [DeviceProfileDetector] - Runtime device detection
 * - [SyncCheckpointStore] - Checkpoint persistence (via @Inject constructor)
 * - [SyncPerfMetrics] - Singleton metrics collector (enabled in debug builds)
 *
 * **Usage:**
 * ```kotlin
 * @Inject lateinit var detector: DeviceProfileDetector
 * @Inject lateinit var checkpointStore: SyncCheckpointStore
 * @Inject lateinit var syncMetrics: SyncPerfMetrics
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncCommonModule {

    /**
     * Provide DeviceProfileDetector.
     *
     * Note: DeviceProfileDetector has @Inject constructor, so this explicit
     * @Provides is optional. Included for visibility and documentation.
     */
    @Provides
    @Singleton
    fun provideDeviceProfileDetector(
        @ApplicationContext context: Context,
    ): DeviceProfileDetector = DeviceProfileDetector(context)

    /**
     * Provide SyncPerfMetrics singleton.
     *
     * This provides a shared metrics instance for all sync services.
     * Metrics are always enabled for debug visibility; can be toggled via settings later.
     */
    @Provides
    @Singleton
    fun provideSyncPerfMetrics(): SyncPerfMetrics = SyncPerfMetrics(
        isEnabled = true,
        syncId = "app-default",
    )

    /**
     * SyncCheckpointStore is provided via its @Inject constructor.
     * No explicit @Provides needed.
     */
}
