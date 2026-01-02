package com.fishit.player.core.debugsettings

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime holder for debug tool flags using AtomicBooleans.
 *
 * **Purpose:**
 * Provides fast, thread-safe access to debug flags for interceptors
 * and initializers without Flow collection overhead in hot paths.
 *
 * **Contract:**
 * - Defaults to false (OFF)
 * - Updated by DebugToolsInitializer from DataStore flows
 * - Thread-safe via AtomicBoolean
 * - No direct mutation outside initializer
 *
 * **Usage (read-only in interceptors):**
 * ```kotlin
 * class GatedChuckerInterceptor(private val flagsHolder: DebugFlagsHolder) {
 *     override fun intercept(chain: Chain): Response {
 *         if (!flagsHolder.chuckerEnabled.get()) {
 *             return chain.proceed(chain.request())
 *         }
 *         // delegate to actual Chucker
 *     }
 * }
 * ```
 *
 * **Updates (only in initializer):**
 * ```kotlin
 * settingsRepo.networkInspectorEnabledFlow.collect { enabled ->
 *     flagsHolder.chuckerEnabled.set(enabled)
 * }
 * ```
 */
@Singleton
class DebugFlagsHolder
    @Inject
    constructor() {
        /**
         * Chucker (network inspector) enabled flag.
         * Default: false (OFF)
         */
        val chuckerEnabled: AtomicBoolean = AtomicBoolean(false)

        /**
         * LeakCanary (leak detection) enabled flag.
         * Default: false (OFF)
         */
        val leakCanaryEnabled: AtomicBoolean = AtomicBoolean(false)
    }
