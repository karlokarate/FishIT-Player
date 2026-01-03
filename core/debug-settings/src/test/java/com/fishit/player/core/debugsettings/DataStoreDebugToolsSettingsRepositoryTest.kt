package com.fishit.player.core.debugsettings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for DataStoreDebugToolsSettingsRepository.
 *
 * Tests that:
 * - Default values are false for both settings
 * - Settings can be updated and persisted
 * - Flows emit correct values
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DataStoreDebugToolsSettingsRepositoryTest {
    private lateinit var testContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testScope: TestScope
    private lateinit var repository: DataStoreDebugToolsSettingsRepository

    @Before
    fun setup() {
        testContext = ApplicationProvider.getApplicationContext()
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher + Job())

        // Create test DataStore with unique name for each test
        testDataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { testContext.preferencesDataStoreFile("test_debug_settings_${System.currentTimeMillis()}") },
            )

        // Create repository with test context (uses internal DataStore)
        repository = DataStoreDebugToolsSettingsRepository(testContext)
    }

    @After
    fun teardown() {
        // Clean up is handled by test DataStore scope
    }

    @Test
    fun `defaults are false for both settings`() =
        runTest {
            // Verify network inspector defaults to false
            repository.networkInspectorEnabledFlow.test {
                assertFalse(awaitItem(), "networkInspectorEnabled should default to false")
            }

            // Verify leak canary defaults to false
            repository.leakCanaryEnabledFlow.test {
                assertFalse(awaitItem(), "leakCanaryEnabled should default to false")
            }
        }

    @Test
    fun `can update network inspector setting`() =
        runTest {
            repository.networkInspectorEnabledFlow.test {
                // Default
                assertEquals(false, awaitItem())

                // Enable
                repository.setNetworkInspectorEnabled(true)
                assertEquals(true, awaitItem())

                // Disable
                repository.setNetworkInspectorEnabled(false)
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `can update leak canary setting`() =
        runTest {
            repository.leakCanaryEnabledFlow.test {
                // Default
                assertEquals(false, awaitItem())

                // Enable
                repository.setLeakCanaryEnabled(true)
                assertEquals(true, awaitItem())

                // Disable
                repository.setLeakCanaryEnabled(false)
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `settings are independent`() =
        runTest {
            // Enable network inspector
            repository.setNetworkInspectorEnabled(true)

            // Verify network inspector is enabled
            repository.networkInspectorEnabledFlow.test {
                assertEquals(true, awaitItem())
            }

            // Verify leak canary is still disabled (independent)
            repository.leakCanaryEnabledFlow.test {
                assertEquals(false, awaitItem())
            }
        }
}
