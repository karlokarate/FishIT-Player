package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.repository.NxSourceAccountRepository
import com.fishit.player.core.onboarding.domain.XtreamConfig
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamAuthState
import com.fishit.player.infra.transport.xtream.XtreamCapabilities
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamUserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Unit tests for XtreamAuthRepositoryAdapter.
 *
 * Tests that successful Xtream connect properly activates XTREAM source.
 */
class XtreamAuthRepositoryAdapterTest {
    private lateinit var apiClient: XtreamApiClient
    private lateinit var credentialsStore: XtreamCredentialsStore
    private lateinit var sourceActivationStore: SourceActivationStore
    private lateinit var nxSourceAccountRepository: NxSourceAccountRepository
    private lateinit var testScope: TestScope
    private lateinit var adapter: XtreamAuthRepositoryAdapter

    private val connectionStateFlow = MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
    private val authStateFlow = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Unknown)

    @Before
    fun setup() {
        apiClient = mockk(relaxed = true)
        credentialsStore = mockk(relaxed = true)
        sourceActivationStore = mockk(relaxed = true)
        nxSourceAccountRepository = mockk(relaxed = true)
        testScope = TestScope(StandardTestDispatcher())

        every { apiClient.connectionState } returns connectionStateFlow
        every { apiClient.authState } returns authStateFlow

        coEvery { sourceActivationStore.setXtreamActive() } returns Unit
        coEvery { sourceActivationStore.setXtreamInactive(any()) } returns Unit

        adapter =
            XtreamAuthRepositoryAdapter(
                apiClient = apiClient,
                credentialsStore = credentialsStore,
                sourceActivationStore = sourceActivationStore,
                nxSourceAccountRepository = nxSourceAccountRepository,
                appScope = testScope,
            )
    }

    @Test
    fun `successful initialize should activate XTREAM source`() =
        runTest {
            // Given: API client will return success
            val capabilities =
                XtreamCapabilities(
                    cacheKey = "http://test.com:8080|user",
                    baseUrl = "http://test.com:8080",
                    username = "user",
                )
            coEvery { apiClient.initialize(any()) } returns Result.success(capabilities)

            val config =
                XtreamConfig(
                    scheme = "http",
                    host = "test.com",
                    port = 8080,
                    username = "user",
                    password = "pass",
                )

            // When: Initialize is called
            val result = adapter.initialize(config)

            // Then: Result is success AND SourceActivationStore.setXtreamActive() was called
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { sourceActivationStore.setXtreamActive() }
            coVerify(exactly = 0) { sourceActivationStore.setXtreamInactive(any()) }
        }

    @Test
    fun `failed initialize should deactivate XTREAM source with error`() =
        runTest {
            // Given: API client will return failure
            coEvery { apiClient.initialize(any()) } returns Result.failure(RuntimeException("Connection failed"))

            val config =
                XtreamConfig(
                    scheme = "http",
                    host = "test.com",
                    port = 8080,
                    username = "user",
                    password = "pass",
                )

            // When: Initialize is called
            val result = adapter.initialize(config)

            // Then: Result is failure AND SourceActivationStore.setXtreamInactive() was called
            assertTrue(result.isFailure)
            coVerify(exactly = 0) { sourceActivationStore.setXtreamActive() }
            coVerify(exactly = 1) { sourceActivationStore.setXtreamInactive(SourceErrorReason.TRANSPORT_ERROR) }
        }

    @Test
    fun `close should deactivate XTREAM source`() =
        runTest {
            // When: Close is called
            adapter.close()

            // Then: SourceActivationStore.setXtreamInactive() was called (without error reason)
            coVerify(exactly = 1) { sourceActivationStore.setXtreamInactive(null) }
        }

    @Test
    fun `adapter should observe transport state changes from construction without calling initialize`() =
        runTest(testScope.testScheduler) {
            // Need to advance past the init block observation setup
            testScope.testScheduler.advanceUntilIdle()

            // Given: Adapter is constructed (already done in setup)
            // and transport authState is initially Unknown

            // When: Transport layer changes authState to Authenticated
            // (simulating XtreamSessionBootstrap initializing XtreamApiClient directly)
            val userInfo =
                XtreamUserInfo(
                    username = "testuser",
                    status = XtreamUserInfo.UserStatus.ACTIVE,
                    expDateEpoch = null,
                    maxConnections = 1,
                    activeConnections = 0,
                    isTrial = false,
                    allowedFormats = listOf("m3u8"),
                    createdAt = null,
                    message = null,
                )
            authStateFlow.value = XtreamAuthState.Authenticated(userInfo)

            // Advance coroutines to let the flow collection emit
            testScope.testScheduler.advanceUntilIdle()

            // Then: The adapter's domain authState should reflect the change
            assertTrue(
                adapter.authState.value is com.fishit.player.core.onboarding.domain.XtreamAuthState.Authenticated,
                "Expected authState to be Authenticated but was ${adapter.authState.value}",
            )
        }

    @Test
    fun `adapter should observe transport connection state changes from construction`() =
        runTest(testScope.testScheduler) {
            // Need to advance past the init block observation setup
            testScope.testScheduler.advanceUntilIdle()

            // Given: Adapter is constructed (already done in setup)
            // and transport connectionState is initially Disconnected

            // When: Transport layer changes connectionState to Connected
            connectionStateFlow.value =
                XtreamConnectionState.Connected(
                    baseUrl = "http://test.com:8080",
                    latencyMs = 100,
                )

            // Advance coroutines to let the flow collection emit
            testScope.testScheduler.advanceUntilIdle()

            // Then: The adapter's domain connectionState should reflect the change
            assertTrue(
                adapter.connectionState.value is com.fishit.player.core.onboarding.domain.XtreamConnectionState.Connected,
                "Expected connectionState to be Connected but was ${adapter.connectionState.value}",
            )
        }

    @Test
    fun `adapter should restart observation after close and reinitialize`() =
        runTest(testScope.testScheduler) {
            // Need to advance past the init block observation setup
            testScope.testScheduler.advanceUntilIdle()

            // Given: Adapter is constructed and observing
            // First verify observation is working
            val userInfo =
                XtreamUserInfo(
                    username = "testuser",
                    status = XtreamUserInfo.UserStatus.ACTIVE,
                    expDateEpoch = null,
                    maxConnections = 1,
                    activeConnections = 0,
                    isTrial = false,
                    allowedFormats = listOf("m3u8"),
                    createdAt = null,
                    message = null,
                )
            authStateFlow.value = XtreamAuthState.Authenticated(userInfo)
            testScope.testScheduler.advanceUntilIdle()

            assertTrue(
                adapter.authState.value is com.fishit.player.core.onboarding.domain.XtreamAuthState.Authenticated,
                "Pre-close: Expected authState to be Authenticated but was ${adapter.authState.value}",
            )

            // When: close() is called (cancels observation)
            adapter.close()
            testScope.testScheduler.advanceUntilIdle()

            // Verify state is reset to Idle after close
            assertTrue(
                adapter.authState.value is com.fishit.player.core.onboarding.domain.XtreamAuthState.Idle,
                "After close: Expected authState to be Idle but was ${adapter.authState.value}",
            )

            // And: Transport state changes (simulating external auth happening)
            authStateFlow.value = XtreamAuthState.Unknown // Reset first
            testScope.testScheduler.advanceUntilIdle()

            // Setup for reinitialize
            val capabilities =
                XtreamCapabilities(
                    cacheKey = "http://test.com:8080|user",
                    baseUrl = "http://test.com:8080",
                    username = "user",
                )
            coEvery { apiClient.initialize(any()) } returns Result.success(capabilities)

            val config =
                XtreamConfig(
                    scheme = "http",
                    host = "test.com",
                    port = 8080,
                    username = "user",
                    password = "pass",
                )

            // When: initialize() is called again (logout→login scenario)
            adapter.initialize(config)
            testScope.testScheduler.advanceUntilIdle()

            // And: Transport state changes after reinitialize
            authStateFlow.value = XtreamAuthState.Authenticated(userInfo)
            testScope.testScheduler.advanceUntilIdle()

            // Then: The adapter should observe the change (observation was restarted)
            assertTrue(
                adapter.authState.value is com.fishit.player.core.onboarding.domain.XtreamAuthState.Authenticated,
                "After reinitialize: Expected authState to be Authenticated but was ${adapter.authState.value}",
            )
        }
}
