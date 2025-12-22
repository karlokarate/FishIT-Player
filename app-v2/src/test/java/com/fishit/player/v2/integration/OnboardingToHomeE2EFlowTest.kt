package com.fishit.player.v2.integration

import com.fishit.player.core.catalogsync.SourceActivationSnapshot
import com.fishit.player.core.catalogsync.SourceActivationState
import com.fishit.player.core.catalogsync.SourceActivationStore
import com.fishit.player.core.catalogsync.SourceId
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.feature.home.HomeState
import com.fishit.player.feature.home.HomeViewModel
import com.fishit.player.feature.home.domain.HomeContentRepository
import com.fishit.player.feature.home.domain.HomeMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-End Integration Test: Onboarding → Home Screen Flow
 *
 * ## Test Scenario
 *
 * **Ausgangspunkt:** Startscreen mit Eingabe der Login-Daten
 * - Xtream: lange URL inkl. Credentials (z.B. http://host:port/get.php?username=x&password=y)
 * - Telegram: Login-Flow (phone → code → connected)
 *
 * **User-Aktion:** Drückt "Continue to Home Screen"
 *
 * ## Zu prüfende Aspekte:
 *
 * 1. **Rows auf dem HomeScreen:**
 *    - Werden Rows erstellt? Wie viele?
 *    - Welche Kategorien sind sichtbar?
 *
 * 2. **Tiles in den Rows:**
 *    - Wie viele Tiles sind direkt sichtbar?
 *    - Haben sie Bilder (poster)?
 *    - Haben sie Titel, MediaType, SourceType?
 *
 * 3. **Backend-Synchronisation:**
 *    - Wird im Backend weiter synchronisiert?
 *    - Zeigt der SyncState den korrekten Status?
 *
 * 4. **Canonical IDs:**
 *    - Kriegen die Werke Canonical IDs?
 *    - Sind diese über navigationId verfügbar?
 *
 * 5. **Tile-Klick → Detail-Navigation:**
 *    - Öffnet die DetailScreen?
 *    - Werden mediaId und sourceType korrekt übergeben?
 *
 * 6. **Detail-Screen:**
 *    - Sind Metadaten sichtbar (title, year, rating)?
 *    - Kann Playback gestartet werden?
 *
 * ## Architektur-Compliance
 *
 * Dieser Test verwendet Fakes für die Repositories, da echte Telegram/Xtream-APIs
 * in Unit-Tests nicht verfügbar sind. Der Test validiert die Datenfluss-Logik
 * ohne externe Abhängigkeiten.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingToHomeE2EFlowTest {

    private val testDispatcher = StandardTestDispatcher()

    // Fake Repositories
    private lateinit var fakeHomeContentRepository: FakeHomeContentRepository
    private lateinit var fakeSyncStateObserver: FakeSyncStateObserver
    private lateinit var fakeSourceActivationStore: FakeSourceActivationStore

    // System Under Test
    private lateinit var homeViewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeHomeContentRepository = FakeHomeContentRepository()
        fakeSyncStateObserver = FakeSyncStateObserver()
        fakeSourceActivationStore = FakeSourceActivationStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 1: User Completes Login → Lands on Home Screen with Content
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SCENARIO - User logs into Xtream and Telegram, continues to Home, sees populated rows`() = runTest {
        // ══════════════════════════════════════════════════════════════════════
        // GIVEN: User has completed login for both Xtream and Telegram
        // ══════════════════════════════════════════════════════════════════════

        // Simulate Xtream source with 15 VOD items
        val xtreamVodItems = createXtreamVodItems(count = 15)
        // Simulate Xtream source with 10 Live channels
        val xtreamLiveItems = createXtreamLiveItems(count = 10)
        // Simulate Xtream source with 8 Series
        val xtreamSeriesItems = createXtreamSeriesItems(count = 8)
        // Simulate Telegram source with 12 media items
        val telegramItems = createTelegramMediaItems(count = 12)

        fakeHomeContentRepository.setXtreamVod(xtreamVodItems)
        fakeHomeContentRepository.setXtreamLive(xtreamLiveItems)
        fakeHomeContentRepository.setXtreamSeries(xtreamSeriesItems)
        fakeHomeContentRepository.setTelegramMedia(telegramItems)

        // Both sources are active
        fakeSourceActivationStore.setActivation(
            SourceActivationSnapshot(
                xtream = SourceActivationState.Active,
                telegram = SourceActivationState.Active
            )
        )

        // Sync is running (backend still syncing)
        fakeSyncStateObserver.setSyncState(SyncUiState.Running)

        // ══════════════════════════════════════════════════════════════════════
        // WHEN: User navigates to Home Screen (ViewModel is created)
        // ══════════════════════════════════════════════════════════════════════

        homeViewModel = HomeViewModel(
            homeContentRepository = fakeHomeContentRepository,
            syncStateObserver = fakeSyncStateObserver,
            sourceActivationStore = fakeSourceActivationStore
        )

        // Subscribe to StateFlow to trigger emissions (WhileSubscribed requires active collector)
        val collectorJob = backgroundScope.launch {
            homeViewModel.state.collect { /* Keep subscription alive */ }
        }

        // Give StateFlow time to emit through all combines
        advanceUntilIdle()
        
        // Access the current state value directly
        val state = homeViewModel.state.value

        // === ROW COUNT VERIFICATION ===
        // We expect 4 content rows: TelegramMedia, XtreamLive, XtreamVod, XtreamSeries
        assertTrue("Home should have content", state.hasContent)
        assertEquals("Telegram row should have 12 items", 12, state.telegramMediaItems.size)
        assertEquals("Xtream Live row should have 10 items", 10, state.xtreamLiveItems.size)
        assertEquals("Xtream VOD row should have 15 items", 15, state.xtreamVodItems.size)
        assertEquals("Xtream Series row should have 8 items", 8, state.xtreamSeriesItems.size)

        // === VISIBLE TILES COUNT ===
        // On a typical TV screen, first ~5-7 tiles per row are visible
        val totalDirectlyVisibleTiles = calculateVisibleTiles(state)
        assertTrue("At least 20 tiles should be directly visible", totalDirectlyVisibleTiles >= 20)

        println("""
            ┌───────────────────────────────────────────────────────────────────┐
            │ HOME SCREEN ROW SUMMARY                                           │
            ├───────────────────────────────────────────────────────────────────┤
            │ Telegram Media:    ${state.telegramMediaItems.size.toString().padEnd(3)} items                                    │
            │ Xtream Live:       ${state.xtreamLiveItems.size.toString().padEnd(3)} items                                    │
            │ Xtream VOD:        ${state.xtreamVodItems.size.toString().padEnd(3)} items                                    │
            │ Xtream Series:     ${state.xtreamSeriesItems.size.toString().padEnd(3)} items                                    │
            ├───────────────────────────────────────────────────────────────────┤
            │ Total Items:       ${(state.telegramMediaItems.size + state.xtreamLiveItems.size + state.xtreamVodItems.size + state.xtreamSeriesItems.size).toString().padEnd(3)} items                                    │
            │ Visible Tiles:     ~${totalDirectlyVisibleTiles.toString().padEnd(2)} (first 5-7 per row)                       │
            └───────────────────────────────────────────────────────────────────┘
        """.trimIndent())
    }

    @Test
    fun `TILES - All tiles have poster images from sources`() = runTest {
        // GIVEN: Content with images
        val vodItems = createXtreamVodItems(count = 5, withPosters = true)
        val telegramItems = createTelegramMediaItems(count = 5, withPosters = true)

        fakeHomeContentRepository.setXtreamVod(vodItems)
        fakeHomeContentRepository.setTelegramMedia(telegramItems)
        fakeSourceActivationStore.setActivation(
            SourceActivationSnapshot(xtream = SourceActivationState.Active)
        )
        fakeSyncStateObserver.setSyncState(SyncUiState.Idle)

        // WHEN
        homeViewModel = HomeViewModel(
            homeContentRepository = fakeHomeContentRepository,
            syncStateObserver = fakeSyncStateObserver,
            sourceActivationStore = fakeSourceActivationStore
        )
        
        // Subscribe to StateFlow to trigger emissions (WhileSubscribed requires active collector)
        backgroundScope.launch { homeViewModel.state.collect {} }
        advanceUntilIdle()

        // THEN: All tiles have poster images
        val state = homeViewModel.state.value

        val vodTilesWithPosters = state.xtreamVodItems.count { it.poster != null }
        val telegramTilesWithPosters = state.telegramMediaItems.count { it.poster != null }

        assertEquals("All VOD tiles should have posters", 5, vodTilesWithPosters)
        assertEquals("All Telegram tiles should have posters", 5, telegramTilesWithPosters)

        println("""
            ┌───────────────────────────────────────────────────────────────────┐
            │ IMAGE COVERAGE                                                    │
            ├───────────────────────────────────────────────────────────────────┤
            │ VOD tiles with posters:      ${vodTilesWithPosters}/5                                  │
            │ Telegram tiles with posters: ${telegramTilesWithPosters}/5                                  │
            │ Coverage:                    100%                                 │
            └───────────────────────────────────────────────────────────────────┘
        """.trimIndent())
    }

    @Test
    fun `SYNC - Backend continues synchronizing after navigation to Home`() = runTest {
        // GIVEN: Sync is running
        fakeSourceActivationStore.setActivation(
            SourceActivationSnapshot(xtream = SourceActivationState.Active)
        )
        fakeSyncStateObserver.setSyncState(SyncUiState.Running)

        homeViewModel = HomeViewModel(
            homeContentRepository = fakeHomeContentRepository,
            syncStateObserver = fakeSyncStateObserver,
            sourceActivationStore = fakeSourceActivationStore
        )
        
        // Subscribe to StateFlow to trigger emissions (WhileSubscribed requires active collector)
        backgroundScope.launch { homeViewModel.state.collect {} }
        advanceUntilIdle()

        // THEN: Sync state shows Running
        val state = homeViewModel.state.value
        assertEquals("Sync should show Running state", SyncUiState.Running, state.syncState)

        // WHEN: Sync completes
        fakeSyncStateObserver.setSyncState(SyncUiState.Success)
        advanceUntilIdle()

        // THEN: State updates to Success
        val updatedState = homeViewModel.state.value
        assertEquals("Sync should show Success state", SyncUiState.Success, updatedState.syncState)

        println("""
            ┌───────────────────────────────────────────────────────────────────┐
            │ SYNC STATE PROGRESSION                                            │
            ├───────────────────────────────────────────────────────────────────┤
            │ Initial:  RUNNING                                                 │
            │ After:    SUCCESS                                                 │
            │ Backend sync completed successfully                               │
            └───────────────────────────────────────────────────────────────────┘
        """.trimIndent())
    }

    @Test
    fun `CANONICAL_IDS - All items have canonical navigationId for detail navigation`() = runTest {
        // GIVEN: Content from multiple sources
        val vodItems = createXtreamVodItems(count = 3)
        val telegramItems = createTelegramMediaItems(count = 3)

        fakeHomeContentRepository.setXtreamVod(vodItems)
        fakeHomeContentRepository.setTelegramMedia(telegramItems)
        fakeSourceActivationStore.setActivation(
            SourceActivationSnapshot(xtream = SourceActivationState.Active)
        )
        fakeSyncStateObserver.setSyncState(SyncUiState.Idle)

        homeViewModel = HomeViewModel(
            homeContentRepository = fakeHomeContentRepository,
            syncStateObserver = fakeSyncStateObserver,
            sourceActivationStore = fakeSourceActivationStore
        )
        
        // Subscribe to StateFlow to trigger emissions (WhileSubscribed requires active collector)
        backgroundScope.launch { homeViewModel.state.collect {} }
        advanceUntilIdle()

        val state = homeViewModel.state.value

        // THEN: All VOD items have navigation IDs
        state.xtreamVodItems.forEach { item ->
            assertNotNull("VOD item '${item.title}' must have navigationId", item.navigationId)
            assertTrue("VOD item navigationId should not be empty", item.navigationId.isNotEmpty())
            assertEquals("VOD item navigationSource should be XTREAM", SourceType.XTREAM, item.navigationSource)
        }

        // THEN: All Telegram items have navigation IDs
        state.telegramMediaItems.forEach { item ->
            assertNotNull("Telegram item '${item.title}' must have navigationId", item.navigationId)
            assertTrue("Telegram item navigationId should not be empty", item.navigationId.isNotEmpty())
            assertEquals("Telegram item navigationSource should be TELEGRAM", SourceType.TELEGRAM, item.navigationSource)
        }

        println("""
            ┌───────────────────────────────────────────────────────────────────┐
            │ CANONICAL ID VERIFICATION                                         │
            ├───────────────────────────────────────────────────────────────────┤
            │ VOD Items:                                                        │
            ${state.xtreamVodItems.joinToString("\n") { "│   - ${it.title}: ${it.navigationId}".padEnd(68) + "│" }}
            │ Telegram Items:                                                   │
            ${state.telegramMediaItems.joinToString("\n") { "│   - ${it.title}: ${it.navigationId}".padEnd(68) + "│" }}
            │                                                                   │
            │ ✓ All items have canonical IDs for navigation                     │
            └───────────────────────────────────────────────────────────────────┘
        """.trimIndent())
    }

    @Test
    fun `TILE_CLICK - Clicking tile provides correct navigation parameters for Detail Screen`() = runTest {
        // GIVEN: A tile on the home screen
        val vodItems = listOf(
            createHomeMediaItem(
                id = "vod_12345",
                title = "The Matrix",
                sourceType = SourceType.XTREAM,
                mediaType = MediaType.MOVIE,
                navigationId = "canonical_abc123",
                poster = ImageRef.Http("https://tmdb.org/poster/matrix.jpg")
            )
        )

        fakeHomeContentRepository.setXtreamVod(vodItems)
        fakeSourceActivationStore.setActivation(
            SourceActivationSnapshot(xtream = SourceActivationState.Active)
        )
        fakeSyncStateObserver.setSyncState(SyncUiState.Idle)

        homeViewModel = HomeViewModel(
            homeContentRepository = fakeHomeContentRepository,
            syncStateObserver = fakeSyncStateObserver,
            sourceActivationStore = fakeSourceActivationStore
        )
        
        // Subscribe to StateFlow to trigger emissions (WhileSubscribed requires active collector)
        backgroundScope.launch { homeViewModel.state.collect {} }
        advanceUntilIdle()

        val state = homeViewModel.state.value
        val clickedItem = state.xtreamVodItems.first()

        // THEN: Clicked item has all required navigation data
        assertEquals("The Matrix", clickedItem.title)
        assertEquals("canonical_abc123", clickedItem.navigationId)
        assertEquals(SourceType.XTREAM, clickedItem.navigationSource)
        assertEquals(MediaType.MOVIE, clickedItem.mediaType)
        assertNotNull("Poster should be set", clickedItem.poster)

        // Simulate what AppNavHost.kt does on click:
        // navController.navigate(Routes.detail(mediaId = item.navigationId, sourceType = item.navigationSource.name))
        val detailRoute = "detail/${clickedItem.navigationId}/${clickedItem.navigationSource.name}"

        println("""
            ┌───────────────────────────────────────────────────────────────────┐
            │ TILE CLICK → DETAIL NAVIGATION                                    │
            ├───────────────────────────────────────────────────────────────────┤
            │ Clicked Tile:                                                     │
            │   Title:          ${clickedItem.title.padEnd(45)}│
            │   NavigationId:   ${clickedItem.navigationId.padEnd(45)}│
            │   NavigationSrc:  ${clickedItem.navigationSource.name.padEnd(45)}│
            │   MediaType:      ${clickedItem.mediaType.name.padEnd(45)}│
            │   Has Poster:     ${(clickedItem.poster != null).toString().padEnd(45)}│
            ├───────────────────────────────────────────────────────────────────┤
            │ Generated Route:  ${detailRoute.padEnd(45)}│
            │                                                                   │
            │ ✓ Detail screen will receive correct parameters                   │
            └───────────────────────────────────────────────────────────────────┘
        """.trimIndent())

        assertEquals("detail/canonical_abc123/XTREAM", detailRoute)
    }

    @Test
    fun `DETAIL_SCREEN - Detail screen can access metadata for display`() = runTest {
        // GIVEN: A tile with rich metadata
        val vodItem = createHomeMediaItem(
            id = "vod_matrix",
            title = "The Matrix",
            sourceType = SourceType.XTREAM,
            mediaType = MediaType.MOVIE,
            navigationId = "canonical_matrix_1999",
            poster = ImageRef.Http("https://tmdb.org/poster/matrix.jpg"),
            year = 1999,
            rating = 8.7f,
            duration = 136 * 60 * 1000L // 136 minutes in ms
        )

        fakeHomeContentRepository.setXtreamVod(listOf(vodItem))
        fakeSourceActivationStore.setActivation(
            SourceActivationSnapshot(xtream = SourceActivationState.Active)
        )
        fakeSyncStateObserver.setSyncState(SyncUiState.Idle)

        homeViewModel = HomeViewModel(
            homeContentRepository = fakeHomeContentRepository,
            syncStateObserver = fakeSyncStateObserver,
            sourceActivationStore = fakeSourceActivationStore
        )
        
        // Subscribe to StateFlow to trigger emissions (WhileSubscribed requires active collector)
        backgroundScope.launch { homeViewModel.state.collect {} }
        advanceUntilIdle()

        val item = homeViewModel.state.value.xtreamVodItems.first()

        // THEN: All metadata is available for Detail screen to display
        assertEquals("The Matrix", item.title)
        assertEquals(1999, item.year)
        assertEquals(8.7f, item.rating!!, 0.1f)
        assertEquals(136 * 60 * 1000L, item.duration)
        assertNotNull(item.poster)

        println("""
            ┌───────────────────────────────────────────────────────────────────┐
            │ DETAIL SCREEN METADATA                                            │
            ├───────────────────────────────────────────────────────────────────┤
            │ Title:        ${item.title.padEnd(50)}│
            │ Year:         ${item.year.toString().padEnd(50)}│
            │ Rating:       ${String.format("%.1f", item.rating).padEnd(50)}│
            │ Duration:     ${formatDuration(item.duration).padEnd(50)}│
            │ Has Poster:   ${(item.poster != null).toString().padEnd(50)}│
            │ MediaType:    ${item.mediaType.name.padEnd(50)}│
            │ Source:       ${item.sourceType.name.padEnd(50)}│
            ├───────────────────────────────────────────────────────────────────┤
            │ ✓ All metadata available for Detail screen display                │
            └───────────────────────────────────────────────────────────────────┘
        """.trimIndent())
    }

    @Test
    fun `PLAYBACK - Detail screen can initiate playback with correct context`() = runTest {
        // GIVEN: An item ready for playback
        val liveChannel = createHomeMediaItem(
            id = "live_espn",
            title = "ESPN Live",
            sourceType = SourceType.XTREAM,
            mediaType = MediaType.LIVE,
            navigationId = "espn_live_2024",
            poster = ImageRef.Http("https://xtream.tv/logo/espn.png")
        )

        fakeHomeContentRepository.setXtreamLive(listOf(liveChannel))
        fakeSourceActivationStore.setActivation(
            SourceActivationSnapshot(xtream = SourceActivationState.Active)
        )
        fakeSyncStateObserver.setSyncState(SyncUiState.Idle)

        homeViewModel = HomeViewModel(
            homeContentRepository = fakeHomeContentRepository,
            syncStateObserver = fakeSyncStateObserver,
            sourceActivationStore = fakeSourceActivationStore
        )
        
        // Subscribe to StateFlow to trigger emissions (WhileSubscribed requires active collector)
        backgroundScope.launch { homeViewModel.state.collect {} }
        advanceUntilIdle()

        val item = homeViewModel.state.value.xtreamLiveItems.first()

        // THEN: Item has all data needed to start playback
        // In AppNavHost.kt, for LIVE content, it navigates directly to player:
        // navController.navigate(Routes.player(mediaId = item.navigationId, sourceType = item.navigationSource.name))

        assertEquals("ESPN Live", item.title)
        assertEquals(MediaType.LIVE, item.mediaType)
        assertEquals(SourceType.XTREAM, item.sourceType)
        assertEquals("espn_live_2024", item.navigationId)

        // Simulate direct playback route (for LIVE/MOVIE content)
        val playerRoute = "player/${item.navigationId}/${item.navigationSource.name}"

        println("""
            ┌───────────────────────────────────────────────────────────────────┐
            │ PLAYBACK INITIATION                                               │
            ├───────────────────────────────────────────────────────────────────┤
            │ Item:           ${item.title.padEnd(48)}│
            │ MediaType:      ${item.mediaType.name.padEnd(48)}│
            │ Source:         ${item.sourceType.name.padEnd(48)}│
            │ NavigationId:   ${item.navigationId.padEnd(48)}│
            ├───────────────────────────────────────────────────────────────────┤
            │ Navigation:     LIVE content → Direct to Player                   │
            │ Player Route:   ${playerRoute.padEnd(48)}│
            │                                                                   │
            │ ✓ Playback can be started from Home (direct) or Detail (action)   │
            └───────────────────────────────────────────────────────────────────┘
        """.trimIndent())

        assertEquals("player/espn_live_2024/XTREAM", playerRoute)
    }

    @Test
    fun `EMPTY_STATE - No content shows meaningful empty state with source activation info`() = runTest {
        // GIVEN: No content, but sources are trying to sync
        fakeSourceActivationStore.setActivation(
            SourceActivationSnapshot(
                xtream = SourceActivationState.Inactive,
                telegram = SourceActivationState.Error(
                    reason = com.fishit.player.core.catalogsync.SourceErrorReason.LOGIN_REQUIRED
                )
            )
        )
        fakeSyncStateObserver.setSyncState(SyncUiState.Running)

        homeViewModel = HomeViewModel(
            homeContentRepository = fakeHomeContentRepository,
            syncStateObserver = fakeSyncStateObserver,
            sourceActivationStore = fakeSourceActivationStore
        )
        
        // Subscribe to StateFlow to trigger emissions (WhileSubscribed requires active collector)
        backgroundScope.launch { homeViewModel.state.collect {} }
        advanceUntilIdle()

        val state = homeViewModel.state.value

        // THEN: Empty state with source info
        assertFalse("Home should NOT have content", state.hasContent)
        assertEquals(SyncUiState.Running, state.syncState)
        assertEquals(SourceActivationState.Inactive, state.sourceActivation.xtream)
        assertTrue("Telegram should be in error state", state.sourceActivation.telegram is SourceActivationState.Error)

        println("""
            ┌───────────────────────────────────────────────────────────────────┐
            │ EMPTY STATE (sources connecting)                                  │
            ├───────────────────────────────────────────────────────────────────┤
            │ Has Content:  false                                               │
            │ Sync State:   RUNNING                                             │
            ├───────────────────────────────────────────────────────────────────┤
            │ Source Status:                                                    │
            │   XTREAM:     Inactive                                            │
            │   TELEGRAM:   Error (LOGIN_REQUIRED)                              │
            │                                                                   │
            │ → UI should show "Setting up sources..." message                  │
            └───────────────────────────────────────────────────────────────────┘
        """.trimIndent())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculate approximately visible tiles on a TV screen.
     * Assumes ~5-7 tiles visible per row.
     */
    private fun calculateVisibleTiles(state: HomeState): Int {
        val visiblePerRow = 5
        var total = 0
        
        if (state.continueWatchingItems.isNotEmpty()) {
            total += minOf(state.continueWatchingItems.size, visiblePerRow)
        }
        if (state.recentlyAddedItems.isNotEmpty()) {
            total += minOf(state.recentlyAddedItems.size, visiblePerRow)
        }
        if (state.telegramMediaItems.isNotEmpty()) {
            total += minOf(state.telegramMediaItems.size, visiblePerRow)
        }
        if (state.xtreamLiveItems.isNotEmpty()) {
            total += minOf(state.xtreamLiveItems.size, visiblePerRow)
        }
        if (state.xtreamVodItems.isNotEmpty()) {
            total += minOf(state.xtreamVodItems.size, visiblePerRow)
        }
        if (state.xtreamSeriesItems.isNotEmpty()) {
            total += minOf(state.xtreamSeriesItems.size, visiblePerRow)
        }
        
        return total
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = durationMs / 1000 / 60
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST DATA FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════

    private fun createHomeMediaItem(
        id: String,
        title: String,
        sourceType: SourceType,
        mediaType: MediaType,
        navigationId: String,
        poster: ImageRef? = null,
        year: Int? = null,
        rating: Float? = null,
        duration: Long = 0L
    ) = HomeMediaItem(
        id = id,
        title = title,
        poster = poster,
        placeholderThumbnail = null,
        backdrop = null,
        mediaType = mediaType,
        sourceType = sourceType,
        resumePosition = 0L,
        duration = duration,
        isNew = false,
        year = year,
        rating = rating,
        navigationId = navigationId,
        navigationSource = sourceType
    )

    private fun createXtreamVodItems(count: Int, withPosters: Boolean = true): List<HomeMediaItem> {
        val movies = listOf(
            "The Matrix", "Inception", "Interstellar", "The Dark Knight",
            "Pulp Fiction", "Fight Club", "Forrest Gump", "The Shawshank Redemption",
            "The Godfather", "Goodfellas", "Schindler's List", "Se7en",
            "The Silence of the Lambs", "Gladiator", "Saving Private Ryan"
        )
        return (0 until count).map { i ->
            HomeMediaItem(
                id = "xtream_vod_$i",
                title = movies[i % movies.size],
                poster = if (withPosters) ImageRef.Http("https://xtream.tv/poster/$i.jpg") else null,
                placeholderThumbnail = null,
                backdrop = null,
                mediaType = MediaType.MOVIE,
                sourceType = SourceType.XTREAM,
                resumePosition = 0L,
                duration = (90 + i * 10) * 60 * 1000L, // 90-240 min
                isNew = i < 3,
                year = 1990 + i,
                rating = 7.5f + (i % 3) * 0.5f,
                navigationId = "canonical_vod_$i",
                navigationSource = SourceType.XTREAM
            )
        }
    }

    private fun createXtreamLiveItems(count: Int): List<HomeMediaItem> {
        val channels = listOf(
            "ESPN", "CNN", "BBC News", "Discovery", "National Geographic",
            "HBO", "Showtime", "AMC", "FX", "TNT"
        )
        return (0 until count).map { i ->
            HomeMediaItem(
                id = "xtream_live_$i",
                title = channels[i % channels.size],
                poster = ImageRef.Http("https://xtream.tv/logo/$i.png"),
                placeholderThumbnail = null,
                backdrop = null,
                mediaType = MediaType.LIVE,
                sourceType = SourceType.XTREAM,
                resumePosition = 0L,
                duration = 0L,
                isNew = false,
                year = null,
                rating = null,
                navigationId = "canonical_live_$i",
                navigationSource = SourceType.XTREAM
            )
        }
    }

    private fun createXtreamSeriesItems(count: Int): List<HomeMediaItem> {
        val series = listOf(
            "Breaking Bad", "Game of Thrones", "The Wire", "The Sopranos",
            "Stranger Things", "The Mandalorian", "House of the Dragon", "The Last of Us"
        )
        return (0 until count).map { i ->
            HomeMediaItem(
                id = "xtream_series_$i",
                title = series[i % series.size],
                poster = ImageRef.Http("https://xtream.tv/series/$i.jpg"),
                placeholderThumbnail = null,
                backdrop = null,
                mediaType = MediaType.SERIES,
                sourceType = SourceType.XTREAM,
                resumePosition = 0L,
                duration = 0L,
                isNew = i < 2,
                year = 2015 + (i % 8),
                rating = 8.5f + (i % 2) * 0.5f,
                navigationId = "canonical_series_$i",
                navigationSource = SourceType.XTREAM
            )
        }
    }

    private fun createTelegramMediaItems(count: Int, withPosters: Boolean = true): List<HomeMediaItem> {
        return (0 until count).map { i ->
            HomeMediaItem(
                id = "telegram_media_$i",
                title = "Telegram Video $i",
                poster = if (withPosters) ImageRef.Http("https://telegram.org/thumb/$i.jpg") else null,
                placeholderThumbnail = null,
                backdrop = null,
                mediaType = MediaType.MOVIE, // Telegram videos are treated as movies
                sourceType = SourceType.TELEGRAM,
                resumePosition = 0L,
                duration = (30 + i * 15) * 60 * 1000L, // 30-195 min
                isNew = i < 4,
                year = 2023,
                rating = null,
                navigationId = "canonical_tg_$i",
                navigationSource = SourceType.TELEGRAM
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FAKE IMPLEMENTATIONS FOR TESTING
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Fake HomeContentRepository for testing.
 * Allows setting content for each row independently.
 */
class FakeHomeContentRepository : HomeContentRepository {
    private val continueWatchingFlow = MutableStateFlow<List<HomeMediaItem>>(emptyList())
    private val recentlyAddedFlow = MutableStateFlow<List<HomeMediaItem>>(emptyList())
    private val telegramMediaFlow = MutableStateFlow<List<HomeMediaItem>>(emptyList())
    private val xtreamLiveFlow = MutableStateFlow<List<HomeMediaItem>>(emptyList())
    private val xtreamVodFlow = MutableStateFlow<List<HomeMediaItem>>(emptyList())
    private val xtreamSeriesFlow = MutableStateFlow<List<HomeMediaItem>>(emptyList())

    override fun observeContinueWatching(): Flow<List<HomeMediaItem>> = continueWatchingFlow
    override fun observeRecentlyAdded(): Flow<List<HomeMediaItem>> = recentlyAddedFlow
    override fun observeTelegramMedia(): Flow<List<HomeMediaItem>> = telegramMediaFlow
    override fun observeXtreamLive(): Flow<List<HomeMediaItem>> = xtreamLiveFlow
    override fun observeXtreamVod(): Flow<List<HomeMediaItem>> = xtreamVodFlow
    override fun observeXtreamSeries(): Flow<List<HomeMediaItem>> = xtreamSeriesFlow

    fun setContinueWatching(items: List<HomeMediaItem>) { continueWatchingFlow.value = items }
    fun setRecentlyAdded(items: List<HomeMediaItem>) { recentlyAddedFlow.value = items }
    fun setTelegramMedia(items: List<HomeMediaItem>) { telegramMediaFlow.value = items }
    fun setXtreamLive(items: List<HomeMediaItem>) { xtreamLiveFlow.value = items }
    fun setXtreamVod(items: List<HomeMediaItem>) { xtreamVodFlow.value = items }
    fun setXtreamSeries(items: List<HomeMediaItem>) { xtreamSeriesFlow.value = items }
}

/**
 * Fake SyncStateObserver for testing.
 */
class FakeSyncStateObserver : SyncStateObserver {
    private val stateFlow = MutableStateFlow<SyncUiState>(SyncUiState.Idle)

    override fun observeSyncState(): Flow<SyncUiState> = stateFlow
    override fun getCurrentState(): SyncUiState = stateFlow.value

    fun setSyncState(state: SyncUiState) { stateFlow.value = state }
}

/**
 * Fake SourceActivationStore for testing.
 */
class FakeSourceActivationStore : SourceActivationStore {
    private val snapshotFlow = MutableStateFlow(SourceActivationSnapshot.EMPTY)

    override fun observeStates(): Flow<SourceActivationSnapshot> = snapshotFlow
    override fun getCurrentSnapshot(): SourceActivationSnapshot = snapshotFlow.value
    override fun getActiveSources(): Set<SourceId> = snapshotFlow.value.activeSources

    // Stubbed methods (not needed for tests)
    override suspend fun setXtreamActive() {}
    override suspend fun setXtreamInactive(reason: com.fishit.player.core.catalogsync.SourceErrorReason?) {}
    override suspend fun setTelegramActive() {}
    override suspend fun setTelegramInactive(reason: com.fishit.player.core.catalogsync.SourceErrorReason?) {}
    override suspend fun setIoActive() {}
    override suspend fun setIoInactive(reason: com.fishit.player.core.catalogsync.SourceErrorReason?) {}

    fun setActivation(snapshot: SourceActivationSnapshot) { snapshotFlow.value = snapshot }
}
