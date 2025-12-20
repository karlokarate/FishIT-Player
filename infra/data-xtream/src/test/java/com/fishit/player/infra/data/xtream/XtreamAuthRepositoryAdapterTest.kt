package com.fishit.player.infra.data.xtream

import app.cash.turbine.test
import com.fishit.player.feature.onboarding.domain.XtreamAuthState as DomainAuthState
import com.fishit.player.feature.onboarding.domain.XtreamConfig as DomainConfig
import com.fishit.player.feature.onboarding.domain.XtreamConnectionState as DomainConnectionState
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamAuthState
import com.fishit.player.infra.transport.xtream.XtreamCapabilities
import com.fishit.player.infra.transport.xtream.XtreamCategory
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamStoredConfig
import com.fishit.player.infra.transport.xtream.XtreamLiveStream
import com.fishit.player.infra.transport.xtream.XtreamSeriesStream
import com.fishit.player.infra.transport.xtream.XtreamServerInfo
import com.fishit.player.infra.transport.xtream.XtreamUserInfo
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.infra.transport.xtream.XtreamVodStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class XtreamAuthRepositoryAdapterTest {
    private val dispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(dispatcher)

    @Test
    fun `observeTransportStates forwards state changes`() = runTest(dispatcher) {
        val apiClient = FakeXtreamApiClient()
        val credentialsStore = InMemoryXtreamCredentialsStore()
        val adapter = XtreamAuthRepositoryAdapter(apiClient, credentialsStore, appScope)

        val config = DomainConfig(
            scheme = "http",
            host = "example.com",
            port = 8080,
            username = "user",
            password = "pass",
        )

        adapter.connectionState.test {
            assertTrue(awaitItem() is DomainConnectionState.Disconnected)
            val initializeResult = adapter.initialize(config)
            assertTrue(initializeResult.isSuccess)
            advanceUntilIdle()
            assertTrue(awaitItem() is DomainConnectionState.Connecting)

            apiClient.connectionState.value = XtreamConnectionState.Connected("http://example.com:8080", 12)
            advanceUntilIdle()
            assertTrue(awaitItem() is DomainConnectionState.Connected)
            cancelAndConsumeRemainingEvents()
        }

        adapter.authState.test {
            assertTrue(awaitItem() is DomainAuthState.Idle)
            apiClient.authState.value = XtreamAuthState.Pending
            advanceUntilIdle()
            assertTrue(awaitItem() is DomainAuthState.Idle)

            val userInfo =
                XtreamUserInfo(
                    username = "user",
                    status = XtreamUserInfo.UserStatus.ACTIVE,
                    expDateEpoch = 1_700_000_000,
                    maxConnections = 1,
                    activeConnections = 0,
                    isTrial = false,
                    allowedFormats = emptyList(),
                    createdAt = null,
                    message = null,
                )
            apiClient.authState.value = XtreamAuthState.Authenticated(userInfo)
            advanceUntilIdle()
            assertTrue(awaitItem() is DomainAuthState.Authenticated)
            cancelAndConsumeRemainingEvents()
        }
    }

    private class FakeXtreamApiClient : XtreamApiClient {
        override val authState = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Unknown)
        override val connectionState = MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
        override var capabilities: XtreamCapabilities? = null
            private set

        override suspend fun initialize(
            config: XtreamApiConfig,
            forceDiscovery: Boolean,
        ): Result<XtreamCapabilities> {
            connectionState.value = XtreamConnectionState.Connecting
            val resolvedCaps =
                XtreamCapabilities(
                    cacheKey = "${config.scheme}://${config.host}:${config.port ?: 80}|${config.username}",
                    baseUrl = "${config.scheme}://${config.host}:${config.port ?: 80}",
                    username = config.username,
                )
            capabilities = resolvedCaps
            return Result.success(resolvedCaps)
        }

        override suspend fun ping(): Boolean = true

        override fun close() {
            connectionState.value = XtreamConnectionState.Disconnected
        }

        override suspend fun getServerInfo(): Result<XtreamServerInfo> = Result.failure(UnsupportedOperationException())

        override suspend fun getUserInfo(): Result<XtreamUserInfo> = Result.failure(UnsupportedOperationException())

        override suspend fun getLiveCategories(): List<XtreamCategory> = emptyList()

        override suspend fun getVodCategories(): List<XtreamCategory> = emptyList()

        override suspend fun getSeriesCategories(): List<XtreamCategory> = emptyList()

        override suspend fun getLiveStreams(
            categoryId: String?,
            limit: Int,
            offset: Int,
        ): List<XtreamLiveStream> = emptyList()

        override suspend fun getVodStreams(
            categoryId: String?,
            limit: Int,
            offset: Int,
        ): List<XtreamVodStream> = emptyList()

        override suspend fun getSeries(
            categoryId: String?,
            limit: Int,
            offset: Int,
        ): List<XtreamSeriesStream> = emptyList()

        override suspend fun getVodInfo(vodId: Int): XtreamVodInfo? = null

        override suspend fun getSeriesInfo(seriesId: Int): Map<String, List<XtreamSeriesStream>> = emptyMap()

        override suspend fun rawApiCall(action: String, params: Map<String, String>): String? = null
    }

    private class InMemoryXtreamCredentialsStore : XtreamCredentialsStore {
        var stored: XtreamStoredConfig? = null

        override suspend fun read(): XtreamStoredConfig? = stored

        override suspend fun write(config: XtreamStoredConfig) {
            stored = config
        }

        override suspend fun clear() {
            stored = null
        }
    }
}
