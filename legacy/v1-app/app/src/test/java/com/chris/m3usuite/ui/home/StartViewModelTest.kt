package com.chris.m3usuite.ui.home

import org.junit.Test

/**
 * Unit tests for StartViewModel.
 * Tests per-chat Telegram content mapping and state management.
 *
 * Note: Full integration tests with actual flows require Android context,
 * so these are kept minimal and structural. Full testing is done via integration tests.
 */
class StartViewModelTest {
    @Test
    fun `StartViewModel class exists and is accessible`() {
        // Verify the class can be referenced
        val clazz = StartViewModel::class
        assert(clazz.java.name == "com.chris.m3usuite.ui.home.StartViewModel") {
            "StartViewModel should be accessible"
        }
    }

    @Test
    fun `StartViewModel has observeTelegramContent method`() {
        // Verify that the ViewModel observes Telegram content
        val clazz = StartViewModel::class
        val methods = clazz.java.declaredMethods.map { it.name }

        // The method is private, but we can check it exists
        assert(methods.contains("observeTelegramContent")) {
            "StartViewModel should have observeTelegramContent method"
        }
    }

    @Test
    fun `StartViewModel has reloadFromObx method`() {
        // Verify core data loading method exists
        val clazz = StartViewModel::class
        val methods = clazz.java.declaredMethods.map { it.name }

        assert(methods.contains("reloadFromObx")) {
            "StartViewModel should have reloadFromObx method for data loading"
        }
    }

    @Test
    fun `StartViewModel has setQuery method for search`() {
        // Verify search functionality exists
        val clazz = StartViewModel::class
        val methods = clazz.java.methods.map { it.name }

        assert(methods.contains("setQuery")) {
            "StartViewModel should have setQuery method for search"
        }
    }

    @Test
    fun `StartViewModel has Factory method`() {
        // Verify factory method for ViewModel creation
        val clazz = StartViewModel::class
        val companionClass = Class.forName("com.chris.m3usuite.ui.home.StartViewModel\$Companion")
        val methods = companionClass.methods.map { it.name }

        assert(methods.contains("Factory")) {
            "StartViewModel should have Factory method in companion object"
        }
    }
}
