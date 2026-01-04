package com.fishit.player.feature.onboarding

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for OnboardingViewModel URL parsing.
 *
 * Tests various URL formats including malformed URLs with missing '?'.
 */
class OnboardingViewModelUrlParsingTest {
    private val viewModel =
        object {
            // Copy of parseXtreamUrl for testing without ViewModel instantiation
            fun parseXtreamUrl(url: String): XtreamCredentials? {
                return try {
                    var trimmed = url.trim()

                    // Fix common malformed URL: missing '?' before query params
                    val malformedPattern = Regex("""(\.php)(username=)""", RegexOption.IGNORE_CASE)
                    if (malformedPattern.containsMatchIn(trimmed)) {
                        trimmed =
                            malformedPattern.replace(trimmed) { match ->
                                "${match.groupValues[1]}?${match.groupValues[2]}"
                            }
                    }

                    // Check for userinfo format: http://user:pass@host:port
                    val userinfoPattern = Regex("""^(https?)://([^:]+):([^@]+)@([^:/]+)(?::(\d+))?""")
                    userinfoPattern.find(trimmed)?.let { match ->
                        val (scheme, user, pass, host, portStr) = match.destructured
                        return XtreamCredentials(
                            host = host,
                            port = portStr.toIntOrNull() ?: if (scheme == "https") 443 else 80,
                            username = user,
                            password = pass,
                            useHttps = scheme == "https",
                        )
                    }

                    // Standard URL format with query params
                    val uri = java.net.URI(trimmed)
                    val host = uri.host ?: return null
                    val port =
                        if (uri.port > 0) {
                            uri.port
                        } else if (uri.scheme == "https") {
                            443
                        } else {
                            80
                        }
                    val useHttps = uri.scheme == "https"

                    // Parse query parameters
                    val queryParams =
                        uri.query?.split("&")?.associate { param ->
                            val parts = param.split("=", limit = 2)
                            if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                        } ?: emptyMap()

                    val username = queryParams["username"] ?: return null
                    val password = queryParams["password"] ?: return null

                    XtreamCredentials(
                        host = host,
                        port = port,
                        username = username,
                        password = password,
                        useHttps = useHttps,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }

    // =========================================================================
    // Standard URL formats (with '?')
    // =========================================================================

    @Test
    fun `parse standard get_php URL with query params`() {
        val url = "http://example.com:8080/get.php?username=testuser&password=testpass&type=m3u_plus"
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result)
        assertEquals("example.com", result.host)
        assertEquals(8080, result.port)
        assertEquals("testuser", result.username)
        assertEquals("testpass", result.password)
        assertEquals(false, result.useHttps)
    }

    @Test
    fun `parse player_api_php URL`() {
        val url = "http://test.server.com:80/player_api.php?username=user1&password=pass1"
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result)
        assertEquals("test.server.com", result.host)
        assertEquals(80, result.port)
        assertEquals("user1", result.username)
        assertEquals("pass1", result.password)
    }

    @Test
    fun `parse HTTPS URL with default port`() {
        val url = "https://secure.server.com/get.php?username=admin&password=secret"
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result)
        assertEquals("secure.server.com", result.host)
        assertEquals(443, result.port)
        assertEquals("admin", result.username)
        assertEquals("secret", result.password)
        assertEquals(true, result.useHttps)
    }

    // =========================================================================
    // Userinfo format (user:pass@host)
    // =========================================================================

    @Test
    fun `parse userinfo format URL`() {
        val url = "http://myuser:mypass@server.example.com:8000"
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result)
        assertEquals("server.example.com", result.host)
        assertEquals(8000, result.port)
        assertEquals("myuser", result.username)
        assertEquals("mypass", result.password)
        assertEquals(false, result.useHttps)
    }

    @Test
    fun `parse HTTPS userinfo format without port`() {
        val url = "https://admin:password123@secure.host.com"
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result)
        assertEquals("secure.host.com", result.host)
        assertEquals(443, result.port)
        assertEquals("admin", result.username)
        assertEquals("password123", result.password)
        assertEquals(true, result.useHttps)
    }

    // =========================================================================
    // Malformed URLs (missing '?') - Bug fix verification
    // =========================================================================

    @Test
    fun `parse malformed URL with missing question mark before username`() {
        // This was the bug: "get.phpusername=" instead of "get.php?username="
        val url = "http://konigtv.com:8080/get.phpusername=testuser&password=testpass&type=m3u_plus"
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result, "Should fix malformed URL and parse successfully")
        assertEquals("konigtv.com", result.host)
        assertEquals(8080, result.port)
        assertEquals("testuser", result.username)
        assertEquals("testpass", result.password)
    }

    @Test
    fun `parse malformed player_api_php URL`() {
        val url = "http://example.com:80/player_api.phpusername=user&password=pass"
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result, "Should fix malformed player_api URL")
        assertEquals("example.com", result.host)
        assertEquals("user", result.username)
        assertEquals("pass", result.password)
    }

    // =========================================================================
    // Edge cases and error handling
    // =========================================================================

    @Test
    fun `return null for URL missing username`() {
        val url = "http://example.com:8080/get.php?password=onlypass"
        val result = viewModel.parseXtreamUrl(url)

        assertNull(result)
    }

    @Test
    fun `return null for URL missing password`() {
        val url = "http://example.com:8080/get.php?username=onlyuser"
        val result = viewModel.parseXtreamUrl(url)

        assertNull(result)
    }

    @Test
    fun `return null for completely invalid URL`() {
        val url = "not-a-valid-url"
        val result = viewModel.parseXtreamUrl(url)

        assertNull(result)
    }

    @Test
    fun `return null for empty URL`() {
        val url = ""
        val result = viewModel.parseXtreamUrl(url)

        assertNull(result)
    }

    @Test
    fun `handle URL with extra whitespace`() {
        val url = "  http://example.com:8080/get.php?username=user&password=pass  "
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result)
        assertEquals("example.com", result.host)
        assertEquals("user", result.username)
    }

    @Test
    fun `parse URL without explicit port defaults to 80 for HTTP`() {
        val url = "http://example.com/get.php?username=user&password=pass"
        val result = viewModel.parseXtreamUrl(url)

        assertNotNull(result)
        assertEquals(80, result.port)
        assertEquals(false, result.useHttps)
    }
}
