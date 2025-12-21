package com.fishit.player.infra.data.xtream

import app.cash.turbine.testIn
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
import com.fishit.player.infra.transport.xtream.XtreamEpisodeInfo
import com.fishit.player.infra.transport.xtream.XtreamLiveStream
import com.fishit.player.infra.transport.xtream.XtreamServerInfo
import com.fishit.player.infra.transport.xtream.XtreamShortEpg
import com.fishit.player.infra.transport.xtream.XtreamSimpleDataTable
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesStream
import com.fishit.player.infra.transport.xtream.XtreamStoredConfig
import com.fishit.player.infra.transport.xtream.XtreamUserInfo
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.infra.transport.xtream.XtreamVodStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XtreamAuthRepositoryAdapterTest {

    @Test
    fun `state flows emit transport updates`() = runTest(StandardTestDispatcher()) {
        val client = FakeXtreamApiClient()
        val adapter = XtreamAuthRepositoryAdapter(client, InMemoryCredentialsStore(), this)
        val config = DomainConfig("http", "demo.host", 8080, "user", "pass")

        val connectionTurbine = adapter.connectionState.testIn(this)
        val authTurbine = adapter.authState.testIn(this)

        assertEquals(DomainConnectionState.Disconnected, connectionTurbine.awaitItem())
        assertEquals(DomainAuthState.Idle, authTurbine.awaitItem())

        val initResult = adapter.initialize(config)
        assertTrue(initResult.isSuccess)

        assertEquals(DomainConnectionState.Connecting, connectionTurbine.awaitItem())

        client.connectionStateFlow.value = XtreamConnectionState.Connected("http://demo.host:8080", 42)
        client.authStateFlow.value =
            XtreamAuthState.Authenticated(
                XtreamUserInfo(
                    username = "user",
                    status = XtreamUserInfo.UserStatus.ACTIVE,
                    expDateEpoch = null,
                    maxConnections = 1,
                    activeConnections = 0,
                    isTrial = false,
                    allowedFormats = emptyList(),
                    createdAt = null,
                    message = null,
                ),
            )

        advanceUntilIdle()

        assertEquals(DomainConnectionState.Connected, connectionTurbine.awaitItem())
        assertTrue(authTurbine.awaitItem() is DomainAuthState.Authenticated)

        connectionTurbine.cancelAndConsumeRemainingEvents()
        authTurbine.cancelAndConsumeRemainingEvents()
    }
}

private class InMemoryCredentialsStore : XtreamCredentialsStore {
    private var stored: XtreamStoredConfig? = null

    override suspend fun read(): XtreamStoredConfig? = stored

    override suspend fun write(config: XtreamStoredConfig) {
        stored = config
    }

    override suspend fun clear() {
        stored = null
    }
}

private class FakeXtreamApiClient : XtreamApiClient {
    val connectionStateFlow = MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
    val authStateFlow = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Unknown)

    override val authState = authStateFlow
    override val connectionState = connectionStateFlow
    override val capabilities: XtreamCapabilities? = null

    override suspend fun initialize(config: XtreamApiConfig, forceDiscovery: Boolean): Result<XtreamCapabilities> =
        Result.success(
            XtreamCapabilities(
                cacheKey = "key",
                baseUrl = "${config.scheme}://${config.host}:${config.port ?: 80}",
                username = config.username,
            ),
        )

    override suspend fun ping(): Boolean = true

    override fun close() {
        connectionStateFlow.value = XtreamConnectionState.Disconnected
        authStateFlow.value = XtreamAuthState.Unknown
    }

    override suspend fun getServerInfo() = Result.failure<XtreamServerInfo>(UnsupportedOperationException())

    override suspend fun getUserInfo() = Result.failure<XtreamUserInfo>(UnsupportedOperationException())

    override suspend fun getLiveCategories(): List<XtreamCategory> = emptyList()

    override suspend fun getVodCategories(): List<XtreamCategory> = emptyList()

    override suspend fun getSeriesCategories(): List<XtreamCategory> = emptyList()

    override suspend fun getLiveStreams(categoryId: String?, limit: Int, offset: Int): List<XtreamLiveStream> = emptyList()

    override suspend fun getVodStreams(categoryId: String?, limit: Int, offset: Int): List<XtreamVodStream> = emptyList()

    override suspend fun getSeries(categoryId: String?, limit: Int, offset: Int): List<XtreamSeriesStream> = emptyList()

    override suspend fun getVodInfo(vodId: Int): XtreamVodInfo? = null

    override suspend fun getSeriesInfo(seriesId: Int): XtreamSeriesInfo? = null

    override suspend fun getEpisodeInfo(episodeId: Int): XtreamSeriesInfo? = null

    override suspend fun getEpisodes(seriesId: Int, season: Int?): Map<String, List<XtreamEpisodeInfo>> = emptyMap()

    override suspend fun getShortEpg(streamId: Int): XtreamShortEpg? = null

    override suspend fun getSimpleDataTable(): XtreamSimpleDataTable? = null
}
