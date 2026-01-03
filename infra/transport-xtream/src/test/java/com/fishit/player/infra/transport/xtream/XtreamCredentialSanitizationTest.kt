package com.fishit.player.infra.transport.xtream

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for XtreamApiConfig credential sanitization.
 *
 * Critical requirements from problem statement:
 * 1. Username: trim + remove ALL whitespace characters (\s+ → "")
 * 2. Password: trim + remove ALL whitespace characters (\s+ → "")
 * 3. BaseUrl: trim only (do NOT remove internal spaces if any)
 * 4. Prevents malformed URLs with newlines/spaces in path segments
 */
class XtreamCredentialSanitizationTest {
    companion object {
        private const val TEST_HOST = "konigtv.com"
        private const val TEST_PORT = 8080
    }

    // =========================================================================
    // Username Sanitization Tests
    // =========================================================================

    @Test
    fun `username with leading whitespace is trimmed`() {
        // Given: Username with leading spaces
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("  testuser"),
                password = XtreamApiConfig.sanitizeCredential("testpass"),
            )

        // Then: Username is trimmed
        assertEquals("testuser", config.username)
    }

    @Test
    fun `username with trailing whitespace is trimmed`() {
        // Given: Username with trailing spaces
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("testuser  "),
                password = XtreamApiConfig.sanitizeCredential("testpass"),
            )

        // Then: Username is trimmed
        assertEquals("testuser", config.username)
    }

    @Test
    fun `username with newlines is sanitized`() {
        // Given: Username with embedded newlines
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("test\nuser"),
                password = XtreamApiConfig.sanitizeCredential("testpass"),
            )

        // Then: Newlines are removed
        assertEquals("testuser", config.username)
    }

    @Test
    fun `username with leading and trailing newlines is sanitized`() {
        // Given: Username with leading/trailing newlines (common copy-paste issue)
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("\ntestuser\n"),
                password = XtreamApiConfig.sanitizeCredential("testpass"),
            )

        // Then: All newlines are removed
        assertEquals("testuser", config.username)
    }

    @Test
    fun `username with tabs is sanitized`() {
        // Given: Username with embedded tabs
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("test\tuser"),
                password = XtreamApiConfig.sanitizeCredential("testpass"),
            )

        // Then: Tabs are removed
        assertEquals("testuser", config.username)
    }

    @Test
    fun `username with mixed whitespace is sanitized`() {
        // Given: Username with spaces, tabs, and newlines
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential(" test \t user \n "),
                password = XtreamApiConfig.sanitizeCredential("testpass"),
            )

        // Then: All whitespace is removed
        assertEquals("testuser", config.username)
    }

    // =========================================================================
    // Password Sanitization Tests
    // =========================================================================

    @Test
    fun `password with leading whitespace is trimmed`() {
        // Given: Password with leading spaces
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("testuser"),
                password = XtreamApiConfig.sanitizeCredential("  testpass"),
            )

        // Then: Password is trimmed
        assertEquals("testpass", config.password)
    }

    @Test
    fun `password with trailing whitespace is trimmed`() {
        // Given: Password with trailing spaces
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("testuser"),
                password = XtreamApiConfig.sanitizeCredential("testpass  "),
            )

        // Then: Password is trimmed
        assertEquals("testpass", config.password)
    }

    @Test
    fun `password with newlines is sanitized`() {
        // Given: Password with embedded newlines
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("testuser"),
                password = XtreamApiConfig.sanitizeCredential("test\npass"),
            )

        // Then: Newlines are removed
        assertEquals("testpass", config.password)
    }

    @Test
    fun `password with leading and trailing newlines is sanitized`() {
        // Given: Password with leading/trailing newlines (common copy-paste issue)
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("testuser"),
                password = XtreamApiConfig.sanitizeCredential("\ntestpass\n"),
            )

        // Then: All newlines are removed
        assertEquals("testpass", config.password)
    }

    @Test
    fun `password with spaces in middle is sanitized`() {
        // Given: Password with embedded spaces
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("testuser"),
                password = XtreamApiConfig.sanitizeCredential("test pass"),
            )

        // Then: Spaces are removed
        assertEquals("testpass", config.password)
    }

    @Test
    fun `password with mixed whitespace is sanitized`() {
        // Given: Password with spaces, tabs, and newlines
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("testuser"),
                password = XtreamApiConfig.sanitizeCredential(" test \t pass \n "),
            )

        // Then: All whitespace is removed
        assertEquals("testpass", config.password)
    }

    // =========================================================================
    // Combined Credential Sanitization Tests
    // =========================================================================

    @Test
    fun `both username and password sanitized together`() {
        // Given: Both credentials with whitespace issues
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential(" Christoph10\n"),
                password = XtreamApiConfig.sanitizeCredential("JQ2rKsQ744 "),
            )

        // Then: Both are sanitized
        assertEquals("Christoph10", config.username)
        assertEquals("JQ2rKsQ744", config.password)
    }

    @Test
    fun `credentials with carriage returns are sanitized`() {
        // Given: Credentials with Windows-style line endings
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("user\r\n"),
                password = XtreamApiConfig.sanitizeCredential("pass\r\n"),
            )

        // Then: All whitespace is removed
        assertEquals("user", config.username)
        assertEquals("pass", config.password)
    }

    // =========================================================================
    // URL Building with Sanitized Credentials
    // =========================================================================

    @Test
    fun `URL builder produces clean paths with sanitized credentials`() {
        // Given: Config with whitespace-polluted credentials
        val config =
            XtreamApiConfig(
                scheme = "http",
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential(" test user \n"),
                password = XtreamApiConfig.sanitizeCredential(" test pass \n"),
            )

        val urlBuilder = XtreamUrlBuilder(config)

        // When: Building a series episode URL
        val url =
            urlBuilder.seriesEpisodeUrl(
                seriesId = 12663,
                seasonNumber = 3,
                episodeNumber = 4,
                episodeId = 638139,
                containerExtension = "mp4",
            )

        // Then: URL contains no whitespace in any segment
        assertTrue(
            url.contains("/series/testuser/testpass/638139.mp4"),
            "URL should have sanitized credentials with no whitespace: $url",
        )
        assertTrue(!url.contains(" "), "URL should not contain any spaces: $url")
        assertTrue(!url.contains("\n"), "URL should not contain any newlines: $url")
        assertTrue(!url.contains("\t"), "URL should not contain any tabs: $url")
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Test
    fun `blank username after sanitization fails validation`() {
        // Given: Username that is only whitespace
        // When/Then: Should throw exception during init
        assertFailsWith<IllegalArgumentException> {
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("   \n\t   "),
                password = XtreamApiConfig.sanitizeCredential("testpass"),
            )
        }
    }

    @Test
    fun `blank password after sanitization fails validation`() {
        // Given: Password that is only whitespace
        // When/Then: Should throw exception during init
        assertFailsWith<IllegalArgumentException> {
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("testuser"),
                password = XtreamApiConfig.sanitizeCredential("   \n\t   "),
            )
        }
    }

    @Test
    fun `fromM3uUrl also sanitizes credentials`() {
        // Given: M3U URL with whitespace in query params (unlikely but possible)
        // Note: Real URLs typically won't have unencoded whitespace, but testing defensively
        val config =
            XtreamApiConfig.fromM3uUrl(
                "http://example.com/get.php?username=test%20user&password=test%20pass",
            )

        // Then: Credentials should be sanitized (URL decoding happens first, then sanitization)
        // Note: %20 decodes to space, which should then be removed by sanitization
        assertEquals("testuser", config?.username)
        assertEquals("testpass", config?.password)
    }

    // =========================================================================
    // Real-World Scenarios
    // =========================================================================

    @Test
    fun `credentials copied from PDF with extra whitespace`() {
        // Given: Credentials with typical PDF copy-paste artifacts
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("MyUser123 "),
                password = XtreamApiConfig.sanitizeCredential(" MyPass!@# "),
            )

        // Then: Should work correctly
        assertEquals("MyUser123", config.username)
        assertEquals("MyPass!@#", config.password)
    }

    @Test
    fun `credentials copied from email with line breaks`() {
        // Given: Credentials with line breaks from email clients
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                username = XtreamApiConfig.sanitizeCredential("user\n123"),
                password = XtreamApiConfig.sanitizeCredential("pass\nword"),
            )

        // Then: Should remove line breaks
        assertEquals("user123", config.username)
        assertEquals("password", config.password)
    }
}
