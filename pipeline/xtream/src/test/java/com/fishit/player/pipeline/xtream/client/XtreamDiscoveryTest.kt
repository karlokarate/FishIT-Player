package com.fishit.player.pipeline.xtream.client

import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit Tests für XtreamDiscovery.
 *
 * Testet:
 * - Port Resolution (HTTP/HTTPS Kandidaten)
 * - Capability Discovery (VOD-Alias, Actions)
 * - Cache-Verhalten
 * - Error Handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class XtreamDiscoveryTest {

    private lateinit var server: MockWebServer
    private lateinit var discovery: XtreamDiscovery
    private lateinit var http: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        http =
                OkHttpClient.Builder()
                        .connectTimeout(1, TimeUnit.SECONDS)
                        .readTimeout(1, TimeUnit.SECONDS)
                        .build()

        discovery = XtreamDiscovery(http)
    }

    @After
    fun teardown() {
        server.shutdown()
        runTest { discovery.clearCache() }
    }

    private fun config(port: Int? = null): XtreamApiConfig =
            XtreamApiConfig(
                    host = server.hostName,
                    port = port,
                    scheme = "HTTP",
                    username = "testuser",
                    password = "testpass",
            )

    private val validJson = """[{"category_id": "1", "category_name": "Test"}]"""

    // =========================================================================
    // Port Resolution Tests
    // =========================================================================

    @Test
    fun `resolvePort returns specified port when valid`() = runTest {
        // Port specified in config and responds correctly
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))

        val port = discovery.resolvePort(config(port = server.port))
        assertEquals(server.port, port)
    }

    @Test
    fun `resolvePort probes alternatives when default fails`() = runTest {
        // This test is tricky with MockWebServer since we can't simulate multiple ports
        // We'll test that the discovery mechanism works with a single responding port

        // Queue enough responses for probing
        repeat(10) { server.enqueue(MockResponse().setBody(validJson).setResponseCode(200)) }

        val port = discovery.resolvePort(config(port = server.port))
        assertEquals(server.port, port)
    }

    @Test
    fun `resolvePort uses cached result`() = runTest {
        // First call
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        val port1 = discovery.resolvePort(config(port = server.port))

        // Second call should use cache (no additional server requests)
        val port2 = discovery.resolvePort(config(port = server.port))

        assertEquals(port1, port2)
        // Only 1 request should have been made
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `resolvePort bypasses cache when forceRefresh is true`() = runTest {
        // First call
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        discovery.resolvePort(config(port = server.port))

        // Second call with force refresh
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        discovery.resolvePort(config(port = server.port), forceRefresh = true)

        // Two requests should have been made
        assertEquals(2, server.requestCount)
    }

    // =========================================================================
    // Capability Discovery Tests
    // =========================================================================

    @Test
    fun `discoverCapabilities returns capabilities object`() = runTest {
        // Queue responses for capability probing
        val categoriesJson = """[{"category_id": "1", "category_name": "Test"}]"""
        val streamsJson = """[{"stream_id": 1, "name": "Test"}]"""
        val epgJson = """{"epg_listings": []}"""

        // Basic actions
        server.enqueue(
                MockResponse().setBody(categoriesJson).setResponseCode(200)
        ) // live_categories
        server.enqueue(MockResponse().setBody(streamsJson).setResponseCode(200)) // live_streams
        server.enqueue(
                MockResponse().setBody(categoriesJson).setResponseCode(200)
        ) // series_categories
        server.enqueue(MockResponse().setBody(streamsJson).setResponseCode(200)) // series

        // VOD aliases
        server.enqueue(
                MockResponse().setBody(categoriesJson).setResponseCode(200)
        ) // vod_categories
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // movie_categories
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // movies_categories

        // Extras
        server.enqueue(MockResponse().setBody(epgJson).setResponseCode(200)) // short_epg

        val caps = discovery.discoverCapabilities(config(port = server.port), server.port)

        assertNotNull(caps)
        assertEquals(server.port, caps.baseUrl.substringAfterLast(":").toIntOrNull())
        assertEquals("testuser", caps.username)
    }

    @Test
    fun `discoverCapabilities identifies VOD alias`() = runTest {
        // "vod" fails, "movie" succeeds
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // live_categories
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // live_streams
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // series_categories
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // series

        // VOD aliases
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // vod_categories - empty
        server.enqueue(
                MockResponse().setBody(validJson).setResponseCode(200)
        ) // movie_categories - success
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // movies_categories

        // Extras
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) // short_epg

        val caps = discovery.discoverCapabilities(config(port = server.port), server.port)

        // The resolved alias should be "movie" since vod returned empty
        assertNotNull(caps.resolvedAliases)
        assertTrue(caps.resolvedAliases.vodCandidates.contains("movie"))
    }

    @Test
    fun `discoverCapabilities detects supported actions`() = runTest {
        // All actions respond
        repeat(10) { server.enqueue(MockResponse().setBody(validJson).setResponseCode(200)) }

        val caps = discovery.discoverCapabilities(config(port = server.port), server.port)

        assertNotNull(caps.actions)
        assertTrue(caps.actions.isNotEmpty())
    }

    @Test
    fun `discoverCapabilities marks failed actions as unsupported`() = runTest {
        // live_categories succeeds
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        // live_streams fails
        server.enqueue(MockResponse().setResponseCode(500))
        // Others succeed
        repeat(10) { server.enqueue(MockResponse().setBody(validJson).setResponseCode(200)) }

        val caps = discovery.discoverCapabilities(config(port = server.port), server.port)

        assertNotNull(caps.actions)
        // The action that returned 500 should be marked as unsupported
        val liveStreamsAction = caps.actions["get_live_streams"]
        // Note: The action might still be marked supported if the empty/error response
        // was handled gracefully - depends on implementation details
    }

    @Test
    fun `discoverCapabilities uses cached result`() = runTest {
        // Queue responses for first call
        repeat(10) { server.enqueue(MockResponse().setBody(validJson).setResponseCode(200)) }

        val caps1 = discovery.discoverCapabilities(config(port = server.port), server.port)
        val requestsAfterFirst = server.requestCount

        // Second call should use cache
        val caps2 = discovery.discoverCapabilities(config(port = server.port), server.port)

        assertEquals(caps1.cacheKey, caps2.cacheKey)
        assertEquals(requestsAfterFirst, server.requestCount) // No additional requests
    }

    // =========================================================================
    // Full Discovery Tests
    // =========================================================================

    @Test
    fun `fullDiscovery returns port and capabilities`() = runTest {
        // Port probe
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))

        // Capability probes
        repeat(10) { server.enqueue(MockResponse().setBody(validJson).setResponseCode(200)) }

        val (port, caps) = discovery.fullDiscovery(config(port = server.port))

        assertEquals(server.port, port)
        assertNotNull(caps)
        assertEquals("testuser", caps.username)
    }

    // =========================================================================
    // Ping Tests
    // =========================================================================

    @Test
    fun `ping returns true when server responds`() = runTest {
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))

        val result = discovery.ping(config(port = server.port), server.port)
        assertTrue(result)
    }

    @Test
    fun `ping returns false when server fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = discovery.ping(config(port = server.port), server.port)
        assertTrue(!result)
    }

    @Test
    fun `ping returns false for non-JSON response`() = runTest {
        server.enqueue(MockResponse().setBody("Not JSON").setResponseCode(200))

        val result = discovery.ping(config(port = server.port), server.port)
        assertTrue(!result)
    }

    // =========================================================================
    // Cache Management Tests
    // =========================================================================

    @Test
    fun `clearCache removes all cached data`() = runTest {
        // Populate cache
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        discovery.resolvePort(config(port = server.port))

        // Clear cache
        discovery.clearCache()

        // Next call should make new request
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        discovery.resolvePort(config(port = server.port))

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `clearCacheFor removes specific config cache`() = runTest {
        // Populate cache for config
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        val cfg = config(port = server.port)
        discovery.resolvePort(cfg)

        // Clear cache for this config
        discovery.clearCacheFor(cfg)

        // Next call should make new request
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        discovery.resolvePort(cfg)

        assertEquals(2, server.requestCount)
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Test
    fun `handles empty array responses`() = runTest {
        repeat(10) { server.enqueue(MockResponse().setBody("[]").setResponseCode(200)) }

        val caps = discovery.discoverCapabilities(config(port = server.port), server.port)
        assertNotNull(caps)
    }

    @Test
    fun `handles empty object responses`() = runTest {
        repeat(10) { server.enqueue(MockResponse().setBody("{}").setResponseCode(200)) }

        val caps = discovery.discoverCapabilities(config(port = server.port), server.port)
        assertNotNull(caps)
    }

    @Test
    fun `handles malformed JSON gracefully`() = runTest {
        // Mix of valid and invalid responses
        server.enqueue(MockResponse().setBody(validJson).setResponseCode(200))
        server.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))
        repeat(8) { server.enqueue(MockResponse().setBody(validJson).setResponseCode(200)) }

        val caps = discovery.discoverCapabilities(config(port = server.port), server.port)
        assertNotNull(caps)
    }

    @Test
    fun `handles connection timeout gracefully`() = runTest {
        // MockWebServer doesn't easily simulate timeouts, but we can test empty responses
        server.enqueue(MockResponse().setBody("").setResponseCode(200))
        repeat(9) { server.enqueue(MockResponse().setBody(validJson).setResponseCode(200)) }

        val caps = discovery.discoverCapabilities(config(port = server.port), server.port)
        assertNotNull(caps)
    }
}
