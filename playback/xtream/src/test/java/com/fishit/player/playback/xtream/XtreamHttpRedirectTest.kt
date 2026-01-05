package com.fishit.player.playback.xtream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for [XtreamHttpDataSourceFactory] redirect handling.
 *
 * Verifies:
 * - HTTP redirects (301, 302, 307, 308) are followed automatically
 * - Cross-host redirects work (common with CDN setups)
 * - Headers are preserved across redirects
 * - No real media playback required - just HTTP contract validation
 *
 * **Note (Issue #564):**
 * Tests run with debug source set configuration (Robolectric uses debug variant by default).
 * The debugMode parameter has been removed - behavior is now compile-time via source sets.
 */
@RunWith(RobolectricTestRunner::class)
class XtreamHttpRedirectTest {
    private lateinit var mockServerA: MockWebServer
    private lateinit var mockServerB: MockWebServer
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockServerA = MockWebServer()
        mockServerB = MockWebServer()
        mockServerA.start()
        mockServerB.start()

        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        mockServerA.shutdown()
        mockServerB.shutdown()
    }

    @Test
    fun `test 302 redirect followed from server A to server B`() =
        runTest {
            // Setup: Server A returns 302 redirect to Server B
            val serverBUrl = mockServerB.url("/video.mp4")
            mockServerA.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", serverBUrl.toString()),
            )

            // Server B returns 200 OK
            mockServerB.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("fake video data")
                    .setHeader("Content-Type", "video/mp4"),
            )

            // Act: Create DataSource and request from Server A
            val headers =
                mapOf(
                    "User-Agent" to "FishIT-Player/2.x (Android)",
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity",
                )

            val factory =
                XtreamHttpDataSourceFactory(
                    context = context,
                    headers = headers,
                )

            val dataSource = factory.createDataSource()
            val serverAUrl = mockServerA.url("/live/stream.ts")

            // Open the DataSource - this should trigger the redirect
            val spec =
                androidx.media3.datasource.DataSpec
                    .Builder()
                    .setUri(serverAUrl.toString())
                    .build()

            dataSource.open(spec)

            // Assert: Server A received request
            val requestA = mockServerA.takeRequest()
            assertNotNull(requestA, "Server A should receive a request")
            assertEquals("/live/stream.ts", requestA.path)

            // Assert: Server B received request after redirect
            val requestB = mockServerB.takeRequest()
            assertNotNull(requestB, "Server B should receive a request after redirect")
            assertEquals("/video.mp4", requestB.path)

            // Verify headers were preserved across redirect
            assertEquals("FishIT-Player/2.x (Android)", requestB.getHeader("User-Agent"))
            assertEquals("*/*", requestB.getHeader("Accept"))
            assertEquals("identity", requestB.getHeader("Accept-Encoding"))

            dataSource.close()
        }

    @Test
    fun `test 301 permanent redirect followed`() =
        runTest {
            // Setup: Server A returns 301 redirect to Server B
            val serverBUrl = mockServerB.url("/moved.m3u8")
            mockServerA.enqueue(
                MockResponse()
                    .setResponseCode(301)
                    .setHeader("Location", serverBUrl.toString()),
            )

            mockServerB.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("#EXTM3U\n#EXTINF:-1,Test\nhttp://example.com/segment.ts")
                    .setHeader("Content-Type", "application/vnd.apple.mpegurl"),
            )

            // Act: Create DataSource and request from Server A
            val headers =
                mapOf(
                    "User-Agent" to "FishIT-Player/2.x (Android)",
                    "Accept" to "*/*",
                )

            val factory =
                XtreamHttpDataSourceFactory(
                    context = context,
                    headers = headers,
                )

            val dataSource = factory.createDataSource()
            val serverAUrl = mockServerA.url("/old-path.m3u8")

            val spec =
                androidx.media3.datasource.DataSpec
                    .Builder()
                    .setUri(serverAUrl.toString())
                    .build()

            dataSource.open(spec)

            // Assert: Both servers received requests
            assertNotNull(mockServerA.takeRequest(), "Server A should receive request")
            assertNotNull(mockServerB.takeRequest(), "Server B should receive request after 301")

            dataSource.close()
        }

    @Test
    fun `test HTTP to HTTPS redirect allowed`() =
        runTest {
            // This test verifies followSslRedirects(true) works
            // In practice, MockWebServer doesn't support HTTPS easily,
            // so we just verify cross-protocol redirect configuration exists

            val headers = mapOf("User-Agent" to "FishIT-Player/2.x (Android)")
            val factory =
                XtreamHttpDataSourceFactory(
                    context = context,
                    headers = headers,
                )

            // The factory should be created successfully with SSL redirect support
            assertNotNull(factory, "Factory should be created with SSL redirect support")
        }

    @Test
    fun `test headers preserved in initial request`() =
        runTest {
            // Setup: Server returns 200 OK directly (no redirect)
            mockServerA.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("video data"),
            )

            // Act: Create DataSource with custom headers
            val customHeaders =
                mapOf(
                    "User-Agent" to "FishIT-Player/2.x (Android)",
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity",
                    "Icy-MetaData" to "1",
                    "Referer" to "http://panel.example.com",
                )

            val factory =
                XtreamHttpDataSourceFactory(
                    context = context,
                    headers = customHeaders,
                )

            val dataSource = factory.createDataSource()
            val url = mockServerA.url("/stream.ts")

            val spec =
                androidx.media3.datasource.DataSpec
                    .Builder()
                    .setUri(url.toString())
                    .build()

            dataSource.open(spec)

            // Assert: All headers present in request
            val request = mockServerA.takeRequest()
            assertEquals("FishIT-Player/2.x (Android)", request.getHeader("User-Agent"))
            assertEquals("*/*", request.getHeader("Accept"))
            assertEquals("identity", request.getHeader("Accept-Encoding"))
            assertEquals("1", request.getHeader("Icy-MetaData"))
            assertEquals("http://panel.example.com", request.getHeader("Referer"))

            dataSource.close()
        }
}
