package com.chris.m3usuite.core.xtream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central gate/queue for Xtream import/seeding work.
 * - Exposes a public flag to show a non-blocking UI hint while seeding is in flight.
 * - Provides simple queuing helpers for background, non-critical tasks.
 */
object XtreamImportCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _seederInFlight = MutableStateFlow(false)
    val seederInFlight = _seederInFlight.asStateFlow()

    /**
     * Runs a seeding action under a visible in-flight flag so the UI can inform the user.
     */
    suspend fun <T> runSeeding(block: suspend () -> T): T {
        _seederInFlight.value = true
        return try {
            block()
        } finally {
            _seederInFlight.value = false
        }
    }

    /**
     * Lightweight background queue – fire-and-forget scheduling for follow-up work.
     */
    fun enqueueWork(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }

    /**
     * Blocks the caller until seeding is not active.
     */
    suspend fun waitUntilIdle() {
        while (_seederInFlight.value) {
            delay(50)
        }
    }
}

