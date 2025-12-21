package com.fishit.player.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.Assert.assertTrue

class OnboardingViewModelTest {

    @Test
    fun `parseXtreamUrl handles standard get php`() {
        val url = "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"

        val credentials = OnboardingViewModel.parseXtreamUrl(url)

        assertNotNull(credentials)
        credentials?.let {
            assertEquals("konigtv.com", it.host)
            assertEquals(8080, it.port)
            assertEquals("Christoph10", it.username)
            assertEquals("JQ2rKsQ744", it.password)
            assertFalse(it.useHttps)
        }
    }

    @Test
    fun `parseXtreamUrl handles player api url`() {
        val url = "http://demo.example.com/player_api.php?username=user1&password=pass1"

        val credentials = OnboardingViewModel.parseXtreamUrl(url)

        assertNotNull(credentials)
        credentials?.let {
            assertEquals("demo.example.com", it.host)
            assertEquals(80, it.port)
            assertEquals("user1", it.username)
            assertEquals("pass1", it.password)
            assertFalse(it.useHttps)
        }
    }

    @Test
    fun `parseXtreamUrl decodes percent encoded credentials`() {
        val url = "https://secure.host/get.php?username=Chris%2B10&password=p%40ss%2521"

        val credentials = OnboardingViewModel.parseXtreamUrl(url)

        assertNotNull(credentials)
        credentials?.let {
            assertEquals("secure.host", it.host)
            assertEquals(443, it.port)
            assertEquals("Chris+10", it.username)
            assertEquals("p@ss%21", it.password)
            assertTrue(it.useHttps)
        }
    }
}
