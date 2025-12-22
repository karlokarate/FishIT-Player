package com.fishit.player.infra.transport.xtream

import javax.inject.Qualifier

/**
 * Qualifier for the device-aware parallelism value.
 *
 * Used to inject the parallelism level determined by [XtreamTransportConfig.getParallelism].
 *
 * Premium Contract Section 5:
 * - Phone/Tablet: parallelism = 10
 * - FireTV/low-RAM: parallelism = 3
 *
 * @see XtreamTransportConfig.getParallelism
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class XtreamParallelismValue

/**
 * XtreamParallelism – Device-aware parallelism wrapper for Xtream transport.
 *
 * This is a simple wrapper class (not value class due to Hilt/KSP limitations)
 * that provides the parallelism level determined by [XtreamTransportConfig.getParallelism].
 *
 * Used by:
 * - OkHttp Dispatcher (maxRequests, maxRequestsPerHost)
 * - Coroutine Semaphores in DefaultXtreamApiClient and XtreamDiscovery
 *
 * Premium Contract Section 5:
 * - Phone/Tablet: parallelism = 10
 * - FireTV/low-RAM: parallelism = 3
 *
 * @property value The parallelism level (number of concurrent requests).
 * @see XtreamTransportConfig.getParallelism
 */
data class XtreamParallelism(val value: Int) {
    init {
        require(value > 0) { "Parallelism must be positive, was $value" }
    }
}
