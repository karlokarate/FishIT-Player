package com.fishit.player.feature.telegram

import com.fishit.player.core.feature.FeatureId
import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureRegistry
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.TelegramFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TelegramMediaViewModel].
 *
 * Tests feature-gated behavior: ViewModel should adapt its capabilities
 * based on what the FeatureRegistry reports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelegramMediaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TelegramMediaViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    // ========== TESTS WITH ALL FEATURES AVAILABLE ==========

    @Test
    fun `feature checks pass when all features are registered`() {
        // Given: Registry with both Telegram features
        val registry = createRegistryWithFeatures(
            listOf(TelegramFeatures.FULL_HISTORY_STREAMING, TelegramFeatures.LAZY_THUMBNAILS)
        )

        // When: ViewModel is created
        viewModel = TelegramMediaViewModel(registry)

        // Then: Both features are supported
        assertTrue(viewModel.supportsFullHistoryStreaming)
        assertTrue(viewModel.supportsLazyThumbnails)

        // And: UI state reflects feature availability
        val state = viewModel.uiState.value
        assertTrue(state.canSyncFullHistory)
        assertTrue(state.canLoadThumbnailsLazily)
    }

    @Test
    fun `syncFullHistory updates state when feature is supported`() = runTest {
        // Given: Registry with full history feature
        val registry = createRegistryWithFeatures(listOf(TelegramFeatures.FULL_HISTORY_STREAMING))
        viewModel = TelegramMediaViewModel(registry)

        // When: Sync is triggered
        viewModel.syncFullHistory()
        advanceUntilIdle() // Let coroutines complete

        // Then: State indicates sync was initiated (placeholder behavior)
        val state = viewModel.uiState.value
        assertNotNull(state.lastSyncStatus)
        assertTrue(state.lastSyncStatus!!.contains("sync would be initiated"))
    }

    @Test
    fun `loadThumbnails executes when feature is supported`() {
        // Given: Registry with lazy thumbnails feature
        val registry = createRegistryWithFeatures(listOf(TelegramFeatures.LAZY_THUMBNAILS))
        viewModel = TelegramMediaViewModel(registry)

        // When: Thumbnail load is triggered
        // This should NOT throw or produce errors
        viewModel.loadThumbnails(listOf("remote1", "remote2", "remote3"))

        // Then: No error state
        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
    }

    // ========== TESTS WITH MISSING FEATURES ==========

    @Test
    fun `feature checks fail when features are not registered`() {
        // Given: Empty registry (no features)
        val registry = createRegistryWithFeatures() // Empty

        // When: ViewModel is created
        viewModel = TelegramMediaViewModel(registry)

        // Then: Features are not supported
        assertFalse(viewModel.supportsFullHistoryStreaming)
        assertFalse(viewModel.supportsLazyThumbnails)

        // And: UI state reflects lack of features
        val state = viewModel.uiState.value
        assertFalse(state.canSyncFullHistory)
        assertFalse(state.canLoadThumbnailsLazily)
    }

    @Test
    fun `syncFullHistory sets error when feature is not supported`() {
        // Given: Registry without full history feature
        val registry = createRegistryWithFeatures() // Empty
        viewModel = TelegramMediaViewModel(registry)

        // When: Sync is triggered without feature support
        viewModel.syncFullHistory()

        // Then: Error message is set
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("not available"))
    }

    @Test
    fun `loadThumbnails skips when feature is not supported`() {
        // Given: Registry without lazy thumbnails feature
        val registry = createRegistryWithFeatures() // Empty
        viewModel = TelegramMediaViewModel(registry)

        // When: Thumbnail load is triggered without feature support
        // This should silently skip (logged but not error)
        viewModel.loadThumbnails(listOf("remote1", "remote2"))

        // Then: No error (just skipped)
        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
    }

    @Test
    fun `clearError removes error message`() {
        // Given: Registry without features, error state set
        val registry = createRegistryWithFeatures()
        viewModel = TelegramMediaViewModel(registry)
        viewModel.syncFullHistory() // Sets error

        // When: Error is cleared
        viewModel.clearError()

        // Then: Error message is null
        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
    }

    // ========== PARTIAL FEATURE SUPPORT ==========

    @Test
    fun `only full history works when lazy thumbnails is missing`() {
        // Given: Registry with only full history
        val registry = createRegistryWithFeatures(listOf(TelegramFeatures.FULL_HISTORY_STREAMING))
        viewModel = TelegramMediaViewModel(registry)

        // Then: Only full history is supported
        assertTrue(viewModel.supportsFullHistoryStreaming)
        assertFalse(viewModel.supportsLazyThumbnails)

        // And: UI state reflects partial feature availability
        val state = viewModel.uiState.value
        assertTrue(state.canSyncFullHistory)
        assertFalse(state.canLoadThumbnailsLazily)
    }

    @Test
    fun `only lazy thumbnails works when full history is missing`() {
        // Given: Registry with only lazy thumbnails
        val registry = createRegistryWithFeatures(listOf(TelegramFeatures.LAZY_THUMBNAILS))
        viewModel = TelegramMediaViewModel(registry)

        // Then: Only lazy thumbnails is supported
        assertFalse(viewModel.supportsFullHistoryStreaming)
        assertTrue(viewModel.supportsLazyThumbnails)

        // And: UI state reflects partial feature availability
        val state = viewModel.uiState.value
        assertFalse(state.canSyncFullHistory)
        assertTrue(state.canLoadThumbnailsLazily)
    }

    // ========== TEST HELPERS ==========

    /**
     * Create a test FeatureRegistry with the given feature IDs.
     */
    private fun createRegistryWithFeatures(vararg featureIdValues: String): FeatureRegistry {
        val providers = featureIdValues.map { value ->
            TestFeatureProvider(FeatureId(value))
        }.toSet()

        return TestFeatureRegistry(providers)
    }

    /**
     * Helper to create registry with FeatureId objects.
     */
    private fun createRegistryWithFeatures(featureIds: List<FeatureId>): FeatureRegistry {
        return createRegistryWithFeatures(*featureIds.map { it.value }.toTypedArray())
    }

    /**
     * Test implementation of FeatureProvider.
     */
    private data class TestFeatureProvider(
        override val featureId: FeatureId,
        override val scope: FeatureScope = FeatureScope.PIPELINE,
        override val owner: FeatureOwner = FeatureOwner("test-module")
    ) : FeatureProvider

    /**
     * Test implementation of FeatureRegistry.
     */
    private class TestFeatureRegistry(
        providers: Set<FeatureProvider>
    ) : FeatureRegistry {
        private val providersById: Map<FeatureId, List<FeatureProvider>> =
            providers.groupBy { it.featureId }

        private val ownersById: Map<FeatureId, FeatureOwner> =
            providers.associate { it.featureId to it.owner }

        override fun isSupported(featureId: FeatureId): Boolean =
            providersById.containsKey(featureId)

        override fun providersFor(featureId: FeatureId): List<FeatureProvider> =
            providersById[featureId].orEmpty()

        override fun ownerOf(featureId: FeatureId): FeatureOwner? =
            ownersById[featureId]
    }
}
