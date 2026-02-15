package com.fishit.player.infra.priority

import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [ApiPriorityDispatcher].
 *
 * **Implementation Details:**
 * - Uses [AtomicInteger] counters for thread-safe priority tracking
 * - Uses [Mutex] for coordinating critical sections
 * - Background operations check [shouldYield] and cooperatively pause
 * - No hard blocking - relies on cooperative coroutine cancellation
 *
 * **Thread Safety:**
 * - All state mutations are atomic or mutex-protected
 * - StateFlow provides consistent snapshots to observers
 *
 * **Logging:**
 * - Priority transitions are logged via UnifiedLog
 * - Operations are tagged for debugging
 */
@Singleton
class DefaultApiPriorityDispatcher
    @Inject
    constructor() : ApiPriorityDispatcher {
        companion object {
            private const val TAG = "ApiPriority"
        }

        // Counters for active operations (thread-safe)
        private val activeHighPriorityCount = AtomicInteger(0)
        private val activeCriticalCount = AtomicInteger(0)

        // Mutex for critical playback operations (ensures serialization)
        private val criticalMutex = Mutex()

        // StateFlow for reactive observation
        private val _priorityState = MutableStateFlow(PriorityState())
        override val priorityState: StateFlow<PriorityState> = _priorityState.asStateFlow()

        override suspend fun <T> withHighPriority(
            tag: String,
            block: suspend () -> T,
        ): T {
            try {
                val count = activeHighPriorityCount.incrementAndGet()
                updateState(tag, highPriority = count)
                UnifiedLog.d(TAG) { "⬆️ HIGH_PRIORITY started: $tag (active=$count)" }

                return block()
            } finally {
                val count = activeHighPriorityCount.decrementAndGet()
                updateState(tag = null, highPriority = count)
                UnifiedLog.d(TAG) { "⬇️ HIGH_PRIORITY ended: $tag (active=$count)" }
            }
        }

        override suspend fun <T> withCriticalPriority(
            tag: String,
            timeoutMs: Long,
            block: suspend () -> T,
        ): T? =
            withTimeoutOrNull(timeoutMs) {
                // Critical operations are serialized via mutex
                criticalMutex.withLock {
                    try {
                        val count = activeCriticalCount.incrementAndGet()
                        updateState(tag, critical = count)
                        UnifiedLog.i(TAG) { "🔴 CRITICAL_PRIORITY started: $tag (timeout=${timeoutMs}ms)" }

                        block()
                    } finally {
                        val count = activeCriticalCount.decrementAndGet()
                        updateState(tag = null, critical = count)
                        UnifiedLog.i(TAG) { "🟢 CRITICAL_PRIORITY ended: $tag" }
                    }
                }
            }.also { result ->
                if (result == null) {
                    UnifiedLog.w(TAG) { "⏱️ CRITICAL_PRIORITY timeout: $tag after ${timeoutMs}ms" }
                }
            }

        override suspend fun <T> withBackgroundPriority(
            tag: String,
            block: suspend () -> T,
        ): T {
            // Background priority doesn't modify state counters
            // It simply executes and checks shouldYield internally
            UnifiedLog.v(TAG) { "📋 BACKGROUND started: $tag" }
            try {
                return block()
            } finally {
                UnifiedLog.v(TAG) { "📋 BACKGROUND ended: $tag" }
            }
        }

        override fun shouldYield(): Boolean = _priorityState.value.hasActiveHighPriority

        override suspend fun awaitHighPriorityComplete() {
            if (!shouldYield()) return

            UnifiedLog.d(TAG) { "⏸️ Awaiting high-priority completion..." }

            // Wait until no high-priority operations are active
            _priorityState.first { !it.hasActiveHighPriority }

            UnifiedLog.d(TAG) { "▶️ High-priority complete, resuming..." }
        }

        private fun updateState(
            tag: String?,
            highPriority: Int = activeHighPriorityCount.get(),
            critical: Int = activeCriticalCount.get(),
        ) {
            _priorityState.update { current ->
                current.copy(
                    activeHighPriorityCalls = highPriority,
                    activeCriticalCalls = critical,
                    backgroundSuspended = (highPriority > 0 || critical > 0),
                    currentOperation = tag ?: current.currentOperation.takeIf { highPriority > 0 || critical > 0 },
                )
            }
        }
    }
