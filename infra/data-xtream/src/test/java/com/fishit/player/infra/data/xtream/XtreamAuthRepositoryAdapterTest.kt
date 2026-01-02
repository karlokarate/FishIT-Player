package com.fishit.player.infra.data.xtream

import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.core.onboarding.domain.XtreamConfig
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamAuthState
import com.fishit.player.infra.transport.xtream.XtreamCapabilities
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
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
    private lateinit var testScope: TestScope
    private lateinit var adapter: XtreamAuthRepositoryAdapter

    private val connectionStateFlow = MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
    private val authStateFlow = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Unknown)

    @Before
    fun setup() {
        apiClient = mockk(relaxed = true)
        credentialsStore = mockk(relaxed = true)
        sourceActivationStore = mockk(relaxed = true)
        testScope = TestScope(StandardTestDispatcher())

        every { apiClient.connectionState } returns connectionStateFlow
        every { apiClient.authState } returns authStateFlow
        
        coEvery { sourceActivationStore.setXtreamActive() } returns Unit
        coEvery { sourceActivationStore.setXtreamInactive(any()) } returns Unit

        adapter = XtreamAuthRepositoryAdapter(
            apiClient = apiClient,
            credentialsStore = credentialsStore,
            sourceActivationStore = sourceActivationStore,
            appScope = testScope,
        )
    }

    @Test
    fun `successful initialize should activate XTREAM source`() = runTest {
        // Given: API client will return success
        val capabilities = XtreamCapabilities(
            baseUrl = "http://test.com:8080",
            vodEnabled = true,
            seriesEnabled = true,
            liveEnabled = true,
        )
        coEvery { apiClient.initialize(any()) } returns Result.success(capabilities)

        val config = XtreamConfig(
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
    fun `failed initialize should deactivate XTREAM source with error`() = runTest {
        // Given: API client will return failure
        coEvery { apiClient.initialize(any()) } returns Result.failure(RuntimeException("Connection failed"))

        val config = XtreamConfig(
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
    fun `close should deactivate XTREAM source`() = runTest {
        // When: Close is called
        adapter.close()

        // Then: SourceActivationStore.setXtreamInactive() was called (without error reason)
        coVerify(exactly = 1) { sourceActivationStore.setXtreamInactive(null) }
    }
}
