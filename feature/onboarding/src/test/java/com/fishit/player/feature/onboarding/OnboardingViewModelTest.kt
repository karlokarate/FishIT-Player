package com.fishit.player.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OnboardingViewModelTest {
    @Test
    fun `parseXtreamUrl handles get php url`() {
        val url =
            "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"

        val credentials = OnboardingViewModel.parseXtreamUrl(url)

        assertNotNull(credentials)
        assertEquals("konigtv.com", credentials.host)
        assertEquals(8080, credentials.port)
        assertEquals("Christoph10", credentials.username)
        assertEquals(false, credentials.useHttps)
    }

    @Test
    fun `parseXtreamUrl handles player api url`() {
        val url = "http://example.com/player_api.php?username=user&password=pass"

        val credentials = OnboardingViewModel.parseXtreamUrl(url)

        assertNotNull(credentials)
        assertEquals("example.com", credentials.host)
        assertEquals(80, credentials.port)
        assertEquals("user", credentials.username)
        assertEquals("pass", credentials.password)
    }

    @Test
    fun `parseXtreamUrl decodes encoded credentials`() {
        val url = "http://example.com:8000/get.php?username=User%2BName&password=pa%24%24word"

        val credentials = OnboardingViewModel.parseXtreamUrl(url)

        assertNotNull(credentials)
        assertEquals("User+Name", credentials.username)
        assertEquals("pa$$word", credentials.password)
    }

    @Test
    fun `parseXtreamUrl returns null for missing credentials`() {
        val url = "http://example.com/get.php?username=onlyuser"

        val credentials = OnboardingViewModel.parseXtreamUrl(url)

        assertNull(credentials)
    }
}
