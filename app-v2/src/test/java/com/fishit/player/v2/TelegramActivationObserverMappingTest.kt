package com.fishit.player.v2

import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.core.sourceactivation.SourceActivationSnapshot
import com.fishit.player.core.sourceactivation.SourceActivationState
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.core.sourceactivation.SourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [TelegramActivationObserver] auth state mapping.
 *
 * Verifies the fix for "Telegram shown as INACTIVE after successful auth" bug:
 * - Idle state should NOT trigger setTelegramInactive()
 * - Connected state should trigger setTelegramActive()
 * - Disconnected/Error states should trigger setTelegramInactive()
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelegramActivationObserverMappingTest {

    private lateinit var fakeAuthRepository: FakeTelegramAuthRepository
    private lateinit var fakeActivationStore: FakeSourceActivationStore
    private lateinit var testScope: TestScope
    private lateinit var observer: TelegramActivationObserver

    @Before
    fun setup() {
        fakeAuthRepository = FakeTelegramAuthRepository()
        fakeActivationStore = FakeSourceActivationStore()
        testScope = TestScope()

        observer = TelegramActivationObserver(
            telegramAuthRepository = fakeAuthRepository,
            sourceActivationStore = fakeActivationStore,
            appScope = testScope as CoroutineScope,
        )
    }

    @Test
    fun `Idle state does NOT trigger setTelegramInactive`() = testScope.runTest {
        // Given: initial state is Idle
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Idle

        // When: observer starts
        observer.start()
        advanceUntilIdle()

        // Then: setTelegramInactive should NOT have been called
        assertEquals(0, fakeActivationStore.setInactiveCalls.size,
            "Idle should NOT call setTelegramInactive()")
        assertEquals(0, fakeActivationStore.setActiveCalls,
            "Idle should NOT call setTelegramActive()")
    }

    @Test
    fun `Connected state triggers setTelegramActive`() = testScope.runTest {
        // Given: initial state is Idle
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Idle
        observer.start()
        advanceUntilIdle()

        // When: state changes to Connected
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Connected
        advanceUntilIdle()

        // Then: setTelegramActive should have been called once
        assertEquals(1, fakeActivationStore.setActiveCalls,
            "Connected should call setTelegramActive() exactly once")
    }

    @Test
    fun `Disconnected state triggers setTelegramInactive`() = testScope.runTest {
        // Given: initial state is Connected
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Connected
        observer.start()
        advanceUntilIdle()
        fakeActivationStore.reset()

        // When: state changes to Disconnected
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Disconnected
        advanceUntilIdle()

        // Then: setTelegramInactive should have been called
        assertEquals(1, fakeActivationStore.setInactiveCalls.size,
            "Disconnected should call setTelegramInactive() exactly once")
    }

    @Test
    fun `Error state triggers setTelegramInactive with reason`() = testScope.runTest {
        // Given: initial state is Connected
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Connected
        observer.start()
        advanceUntilIdle()
        fakeActivationStore.reset()

        // When: state changes to Error
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Error("Auth failed")
        advanceUntilIdle()

        // Then: setTelegramInactive should have been called with reason
        assertEquals(1, fakeActivationStore.setInactiveCalls.size,
            "Error should call setTelegramInactive() exactly once")
        assertEquals(SourceErrorReason.LOGIN_REQUIRED, fakeActivationStore.setInactiveCalls.first(),
            "Error should pass LOGIN_REQUIRED reason")
    }

    @Test
    fun `WaitingForPhone does NOT change activation state`() = testScope.runTest {
        // Given: Telegram was previously active
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Connected
        observer.start()
        advanceUntilIdle()
        fakeActivationStore.reset()

        // When: state changes to WaitingForPhone (re-auth scenario)
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.WaitingForPhone
        advanceUntilIdle()

        // Then: no activation change should occur
        assertEquals(0, fakeActivationStore.setActiveCalls,
            "WaitingForPhone should NOT call setTelegramActive()")
        assertEquals(0, fakeActivationStore.setInactiveCalls.size,
            "WaitingForPhone should NOT call setTelegramInactive()")
    }

    @Test
    fun `multiple Idle emissions do not spam setInactive`() = testScope.runTest {
        // Given: initial state is Idle
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Idle
        observer.start()
        advanceUntilIdle()

        // When: Idle is emitted multiple times (simulated by re-setting same value)
        // Note: distinctUntilChanged should prevent re-processing
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Idle
        advanceUntilIdle()

        // Then: setTelegramInactive should never have been called
        assertEquals(0, fakeActivationStore.setInactiveCalls.size,
            "Multiple Idle emissions should not call setTelegramInactive()")
    }

    @Test
    fun `full flow - Idle to Connected to Disconnected`() = testScope.runTest {
        // Given: initial state is Idle
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Idle
        observer.start()
        advanceUntilIdle()

        // Initial: no calls
        assertEquals(0, fakeActivationStore.setActiveCalls, "No setActive on Idle")
        assertEquals(0, fakeActivationStore.setInactiveCalls.size, "No setInactive on Idle")

        // When: Connected
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Connected
        advanceUntilIdle()
        assertEquals(1, fakeActivationStore.setActiveCalls, "setActive on Connected")

        // When: Disconnected
        fakeAuthRepository.authStateFlow.value = TelegramAuthState.Disconnected
        advanceUntilIdle()
        assertEquals(1, fakeActivationStore.setInactiveCalls.size, "setInactive on Disconnected")
    }

    // --- Fake implementations ---

    private class FakeTelegramAuthRepository : TelegramAuthRepository {
        val authStateFlow = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)

        override val authState: kotlinx.coroutines.flow.StateFlow<TelegramAuthState>
            get() = authStateFlow

        override suspend fun ensureAuthorized() {}
        override suspend fun sendPhoneNumber(phoneNumber: String) {}
        override suspend fun sendCode(code: String) {}
        override suspend fun sendPassword(password: String) {}
        override suspend fun logout() {}
    }

    private class FakeSourceActivationStore : SourceActivationStore {
        var setActiveCalls = 0
        val setInactiveCalls = mutableListOf<SourceErrorReason?>()

        fun reset() {
            setActiveCalls = 0
            setInactiveCalls.clear()
        }

        override suspend fun setTelegramActive() {
            setActiveCalls++
        }

        override suspend fun setTelegramInactive(reason: SourceErrorReason?) {
            setInactiveCalls.add(reason)
        }

        // --- Unused methods for this test ---
        override fun observeStates(): Flow<SourceActivationSnapshot> = flowOf()
        override fun getCurrentSnapshot(): SourceActivationSnapshot = SourceActivationSnapshot(
            emptyMap(), emptySet()
        )
        override fun getActiveSources(): Set<SourceId> = emptySet()
        override suspend fun setXtreamActive() {}
        override suspend fun setXtreamInactive(reason: SourceErrorReason?) {}
        override suspend fun setIoActive() {}
        override suspend fun setIoInactive(reason: SourceErrorReason?) {}
        override suspend fun setAudiobookActive() {}
        override suspend fun setAudiobookInactive(reason: SourceErrorReason?) {}
        override suspend fun markXtreamLoginRequired() {}
        override suspend fun clearXtreamError() {}
    }
}
