package com.fishit.player.infra.work

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fishit.player.core.sourceactivation.SourceActivationSnapshot
import com.fishit.player.core.sourceactivation.SourceActivationState
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

// DataStore singleton extension
private val Context.sourceActivationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "source_activation",
)

/**
 * DataStore-backed implementation of [SourceActivationStore].
 *
 * Persists source activation states to survive app restarts.
 * Uses Preferences DataStore for simple key-value persistence.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-1)
 * - Sources are independent: Xtream, Telegram, IO can be ACTIVE/INACTIVE separately
 * - No source is ever required
 * - States survive process restart
 *
 * **Architecture Note:**
 * - Uses non-blocking lazy initialization to avoid main thread blocking
 * - Starts with EMPTY snapshot, loads persisted state asynchronously
 * - All consumers observe the flow, so they get updates when data is ready
 */
@Singleton
class DefaultSourceActivationStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SourceActivationStore {
        private val dataStore: DataStore<Preferences> = context.sourceActivationDataStore

        // Scope for background initialization
        private val initScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // In-memory cache - starts with EMPTY, populated asynchronously
        private val _snapshot = MutableStateFlow(SourceActivationSnapshot.EMPTY)

        init {
            // Non-blocking initialization from persisted state
            // Uses launchIn to avoid blocking the constructor
            dataStore.data
                .catch { e ->
                    UnifiedLog.e(TAG, e) { "Failed to read persisted state, using defaults" }
                    // Don't emit - just log and let snapshot remain at EMPTY default
                }.onEach { preferences ->
                    val snapshot = preferencesToSnapshot(preferences)
                    _snapshot.value = snapshot
                    UnifiedLog.d(TAG) { "Loaded state from DataStore: $snapshot" }
                }.launchIn(initScope)
        }

        // =========================================================================
        // SourceActivationStore Interface
        // =========================================================================

        override fun observeStates(): Flow<SourceActivationSnapshot> =
            dataStore.data.map { preferences ->
                val snapshot = preferencesToSnapshot(preferences)
                _snapshot.value = snapshot
                snapshot
            }

        override fun getCurrentSnapshot(): SourceActivationSnapshot = _snapshot.value

        override fun getActiveSources(): Set<SourceId> = _snapshot.value.activeSources

        // =========================================================================
        // Xtream
        // =========================================================================

        override suspend fun setXtreamActive() {
            updateState(SourceId.XTREAM, SourceActivationState.Active)
        }

        override suspend fun setXtreamInactive(reason: SourceErrorReason?) {
            val state =
                if (reason != null) {
                    SourceActivationState.Error(reason)
                } else {
                    SourceActivationState.Inactive
                }
            updateState(SourceId.XTREAM, state)
        }

        // =========================================================================
        // Telegram
        // =========================================================================

        override suspend fun setTelegramActive() {
            updateState(SourceId.TELEGRAM, SourceActivationState.Active)
        }

        override suspend fun setTelegramInactive(reason: SourceErrorReason?) {
            val state =
                if (reason != null) {
                    SourceActivationState.Error(reason)
                } else {
                    SourceActivationState.Inactive
                }
            updateState(SourceId.TELEGRAM, state)
        }

        // =========================================================================
        // IO
        // =========================================================================

        override suspend fun setIoActive() {
            updateState(SourceId.IO, SourceActivationState.Active)
        }

        override suspend fun setIoInactive(reason: SourceErrorReason?) {
            val state =
                if (reason != null) {
                    SourceActivationState.Error(reason)
                } else {
                    SourceActivationState.Inactive
                }
            updateState(SourceId.IO, state)
        }

        // =========================================================================
        // Persistence Helpers
        // =========================================================================

        private suspend fun updateState(
            sourceId: SourceId,
            state: SourceActivationState,
        ) {
            val key = stateKeyFor(sourceId)
            val reasonKey = reasonKeyFor(sourceId)

            dataStore.edit { preferences ->
                preferences[key] = stateToString(state)
                when (state) {
                    is SourceActivationState.Error -> {
                        preferences[reasonKey] = state.reason.name
                    }
                    else -> {
                        preferences.remove(reasonKey)
                    }
                }
            }

            // Update in-memory cache
            _snapshot.value = _snapshot.value.updateSource(sourceId, state)

            UnifiedLog.i(TAG) { "Source activation changed: $sourceId -> $state" }
        }

        private fun preferencesToSnapshot(preferences: Preferences): SourceActivationSnapshot =
            SourceActivationSnapshot(
                xtream = readState(preferences, SourceId.XTREAM),
                telegram = readState(preferences, SourceId.TELEGRAM),
                io = readState(preferences, SourceId.IO),
            )

        private fun readState(
            preferences: Preferences,
            sourceId: SourceId,
        ): SourceActivationState {
            val stateString = preferences[stateKeyFor(sourceId)] ?: return SourceActivationState.Inactive
            val reasonString = preferences[reasonKeyFor(sourceId)]

            return when (stateString) {
                STATE_ACTIVE -> SourceActivationState.Active
                STATE_ERROR -> {
                    val reason =
                        reasonString?.let {
                            runCatching { SourceErrorReason.valueOf(it) }.getOrNull()
                        } ?: SourceErrorReason.TRANSPORT_ERROR
                    SourceActivationState.Error(reason)
                }
                else -> SourceActivationState.Inactive
            }
        }

        private fun stateToString(state: SourceActivationState): String =
            when (state) {
                is SourceActivationState.Active -> STATE_ACTIVE
                is SourceActivationState.Error -> STATE_ERROR
                is SourceActivationState.Inactive -> STATE_INACTIVE
            }

        private fun stateKeyFor(sourceId: SourceId): Preferences.Key<String> =
            when (sourceId) {
                SourceId.XTREAM -> KEY_XTREAM_STATE
                SourceId.TELEGRAM -> KEY_TELEGRAM_STATE
                SourceId.IO -> KEY_IO_STATE
            }

        private fun reasonKeyFor(sourceId: SourceId): Preferences.Key<String> =
            when (sourceId) {
                SourceId.XTREAM -> KEY_XTREAM_REASON
                SourceId.TELEGRAM -> KEY_TELEGRAM_REASON
                SourceId.IO -> KEY_IO_REASON
            }

        private companion object {
            private const val TAG = "SourceActivationStore"

            // State values
            private const val STATE_INACTIVE = "inactive"
            private const val STATE_ACTIVE = "active"
            private const val STATE_ERROR = "error"

            // Preference keys
            private val KEY_XTREAM_STATE = stringPreferencesKey("xtream_state")
            private val KEY_XTREAM_REASON = stringPreferencesKey("xtream_reason")
            private val KEY_TELEGRAM_STATE = stringPreferencesKey("telegram_state")
            private val KEY_TELEGRAM_REASON = stringPreferencesKey("telegram_reason")
            private val KEY_IO_STATE = stringPreferencesKey("io_state")
            private val KEY_IO_REASON = stringPreferencesKey("io_reason")
        }
    }

/**
 * Extension to update a single source in a snapshot.
 */
private fun SourceActivationSnapshot.updateSource(
    sourceId: SourceId,
    state: SourceActivationState,
): SourceActivationSnapshot =
    when (sourceId) {
        SourceId.XTREAM -> copy(xtream = state)
        SourceId.TELEGRAM -> copy(telegram = state)
        SourceId.IO -> copy(io = state)
    }
