package com.fishit.player.core.sourceactivation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for SourceActivationStore state transitions.
 *
 * Verifies the API contract for source activation remains stable after
 * the refactoring that moved types from core:model to core:source-activation-api.
 *
 * This test uses a simple in-memory implementation to validate the contract.
 */
class SourceActivationStoreTest {

    private lateinit var store: TestSourceActivationStore

    @Before
    fun setUp() {
        store = TestSourceActivationStore()
    }

    @Test
    fun `initial state has all sources inactive`() {
        val snapshot = store.getCurrentSnapshot()
        assertEquals(SourceActivationState.Inactive, snapshot.xtream)
        assertEquals(SourceActivationState.Inactive, snapshot.telegram)
        assertEquals(SourceActivationState.Inactive, snapshot.io)
        assertFalse(snapshot.hasActiveSources)
        assertTrue(snapshot.activeSources.isEmpty())
    }

    @Test
    fun `setXtreamActive marks Xtream as active`() = runTest {
        store.setXtreamActive()
        
        val snapshot = store.getCurrentSnapshot()
        assertEquals(SourceActivationState.Active, snapshot.xtream)
        assertTrue(snapshot.hasActiveSources)
        assertTrue(SourceId.XTREAM in snapshot.activeSources)
    }

    @Test
    fun `setXtreamInactive marks Xtream as inactive`() = runTest {
        store.setXtreamActive()
        store.setXtreamInactive()
        
        val snapshot = store.getCurrentSnapshot()
        assertEquals(SourceActivationState.Inactive, snapshot.xtream)
    }

    @Test
    fun `setXtreamInactive with error reason sets Error state`() = runTest {
        store.setXtreamActive()
        store.setXtreamInactive(SourceErrorReason.TRANSPORT_ERROR)
        
        val snapshot = store.getCurrentSnapshot()
        val expectedState = SourceActivationState.Error(SourceErrorReason.TRANSPORT_ERROR)
        assertEquals(expectedState, snapshot.xtream)
        assertFalse(snapshot.xtream.isActive)
    }

    @Test
    fun `setTelegramActive marks Telegram as active`() = runTest {
        store.setTelegramActive()
        
        val snapshot = store.getCurrentSnapshot()
        assertEquals(SourceActivationState.Active, snapshot.telegram)
        assertTrue(SourceId.TELEGRAM in snapshot.activeSources)
    }

    @Test
    fun `setIoActive marks IO as active`() = runTest {
        store.setIoActive()
        
        val snapshot = store.getCurrentSnapshot()
        assertEquals(SourceActivationState.Active, snapshot.io)
        assertTrue(SourceId.IO in snapshot.activeSources)
    }

    @Test
    fun `observeStates emits state changes`() = runTest {
        val states = mutableListOf<SourceActivationSnapshot>()
        
        // Collect initial state
        states.add(store.observeStates().first())
        
        // Activate Xtream
        store.setXtreamActive()
        states.add(store.getCurrentSnapshot())
        
        // Activate Telegram
        store.setTelegramActive()
        states.add(store.getCurrentSnapshot())
        
        // Verify progression
        assertEquals(3, states.size)
        assertFalse(states[0].hasActiveSources) // Initial: all inactive
        assertTrue(states[1].xtream.isActive)   // After Xtream activation
        assertTrue(states[2].telegram.isActive) // After Telegram activation
    }

    @Test
    fun `multiple sources can be active simultaneously`() = runTest {
        store.setXtreamActive()
        store.setTelegramActive()
        store.setIoActive()
        
        val snapshot = store.getCurrentSnapshot()
        assertEquals(3, snapshot.activeSources.size)
        assertTrue(SourceId.XTREAM in snapshot.activeSources)
        assertTrue(SourceId.TELEGRAM in snapshot.activeSources)
        assertTrue(SourceId.IO in snapshot.activeSources)
    }

    @Test
    fun `SourceActivationSnapshot_EMPTY has all inactive`() {
        val empty = SourceActivationSnapshot.EMPTY
        assertEquals(SourceActivationState.Inactive, empty.xtream)
        assertEquals(SourceActivationState.Inactive, empty.telegram)
        assertEquals(SourceActivationState.Inactive, empty.io)
        assertFalse(empty.hasActiveSources)
    }

    /**
     * Simple in-memory implementation for testing the contract.
     */
    private class TestSourceActivationStore : SourceActivationStore {
        private val _state = MutableStateFlow(SourceActivationSnapshot.EMPTY)

        override fun observeStates(): Flow<SourceActivationSnapshot> = _state

        override fun getCurrentSnapshot(): SourceActivationSnapshot = _state.value

        override fun getActiveSources(): Set<SourceId> = _state.value.activeSources

        override suspend fun setXtreamActive() {
            _state.value = _state.value.copy(xtream = SourceActivationState.Active)
        }

        override suspend fun setXtreamInactive(reason: SourceErrorReason?) {
            val state = if (reason != null) {
                SourceActivationState.Error(reason)
            } else {
                SourceActivationState.Inactive
            }
            _state.value = _state.value.copy(xtream = state)
        }

        override suspend fun setTelegramActive() {
            _state.value = _state.value.copy(telegram = SourceActivationState.Active)
        }

        override suspend fun setTelegramInactive(reason: SourceErrorReason?) {
            val state = if (reason != null) {
                SourceActivationState.Error(reason)
            } else {
                SourceActivationState.Inactive
            }
            _state.value = _state.value.copy(telegram = state)
        }

        override suspend fun setIoActive() {
            _state.value = _state.value.copy(io = SourceActivationState.Active)
        }

        override suspend fun setIoInactive(reason: SourceErrorReason?) {
            val state = if (reason != null) {
                SourceActivationState.Error(reason)
            } else {
                SourceActivationState.Inactive
            }
            _state.value = _state.value.copy(io = state)
        }
    }
}
