package com.fishit.player.infra.data.telegram.auth

import com.fishit.player.core.feature.auth.TelegramAuthState as DomainAuthState
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.api.TelegramAuthException
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramAuthRepositoryImplTest {

    @Test
    fun `maps transport auth states to domain`() = runTest {
        val transport = FakeTelegramAuthClient()
        val repository = TelegramAuthRepositoryImpl(transport, backgroundScope)
        advanceUntilIdle()

        val cases = listOf(
            TdlibAuthState.Idle to DomainAuthState.Idle,
            TdlibAuthState.Connecting to DomainAuthState.Idle,
            TdlibAuthState.WaitTdlibParameters to DomainAuthState.Idle,
            TdlibAuthState.WaitEncryptionKey to DomainAuthState.Idle,
            TdlibAuthState.WaitPhoneNumber() to DomainAuthState.WaitingForPhone,
            TdlibAuthState.WaitCode(phoneNumber = "+123", codeLength = 6) to DomainAuthState.WaitingForCode,
            TdlibAuthState.WaitPassword(passwordHint = "hint", hasRecoveryEmail = true) to DomainAuthState.WaitingForPassword,
            TdlibAuthState.LoggingOut to DomainAuthState.Disconnected,
            TdlibAuthState.Closed to DomainAuthState.Disconnected,
            TdlibAuthState.LoggedOut to DomainAuthState.Disconnected,
            TdlibAuthState.Ready to DomainAuthState.Connected,
        )

        cases.forEach { (transportState, expectedDomain) ->
            transport.emit(transportState)
            assertEquals(expectedDomain, repository.awaitStateMatching { it == expectedDomain })
        }

        transport.emit(TdlibAuthState.Error("boom"))
        val errorFromTransport = repository.awaitStateMatching { it is DomainAuthState.Error }
        assertEquals("boom", (errorFromTransport as DomainAuthState.Error).message)

        transport.emit(TdlibAuthState.Unknown("weird"))
        val unknownState = repository.awaitStateMatching { it is DomainAuthState.Error && it.message.contains("Unknown auth state") }
        assertTrue((unknownState as DomainAuthState.Error).message.contains("Unknown auth state"))
    }

    @Test
    fun `delegates operations and surfaces ensureAuthorized failure`() = runTest {
        val transport = FakeTelegramAuthClient()
        val repository = TelegramAuthRepositoryImpl(transport, backgroundScope)
        advanceUntilIdle()

        transport.ensureAuthorizedError = TelegramAuthException("boom")
        repository.ensureAuthorized()
        advanceUntilIdle()

        val errorState = repository.authState.value
        assertEquals("boom", (errorState as DomainAuthState.Error).message)
        assertEquals(1, transport.ensureAuthorizedCalls)

        repository.sendPhoneNumber("+491234")
        repository.sendCode("12345")
        repository.sendPassword("hunter2")
        repository.logout()

        assertEquals(listOf("+491234"), transport.phoneNumbers)
        assertEquals(listOf("12345"), transport.codes)
        assertEquals(listOf("hunter2"), transport.passwords)
        assertEquals(1, transport.logoutCalls)
    }

    private class FakeTelegramAuthClient : TelegramAuthClient {
        val authStateFlow = MutableSharedFlow<TdlibAuthState>(replay = 1, extraBufferCapacity = 1).also {
            it.tryEmit(TdlibAuthState.Idle)
        }
        var ensureAuthorizedCalls = 0
        var ensureAuthorizedError: Throwable? = null
        val phoneNumbers = mutableListOf<String>()
        val codes = mutableListOf<String>()
        val passwords = mutableListOf<String>()
        var logoutCalls = 0

        override val authState: Flow<TdlibAuthState> = authStateFlow

        suspend fun emit(state: TdlibAuthState) {
            authStateFlow.emit(state)
        }

        override suspend fun ensureAuthorized() {
            ensureAuthorizedCalls++
            ensureAuthorizedError?.let { throw it }
        }

        override suspend fun isAuthorized(): Boolean = false

        override suspend fun sendPhoneNumber(phoneNumber: String) {
            phoneNumbers += phoneNumber
        }

        override suspend fun sendCode(code: String) {
            codes += code
        }

        override suspend fun sendPassword(password: String) {
            passwords += password
        }

        override suspend fun logout() {
            logoutCalls++
        }
    }

    private suspend fun TelegramAuthRepositoryImpl.awaitStateMatching(
        predicate: (DomainAuthState) -> Boolean,
    ): DomainAuthState {
        return withTimeout(1_000) {
            authState.first { predicate(it) }
        }
    }
}
