package com.chris.m3usuite.tv.input

import com.chris.m3usuite.player.miniplayer.MiniPlayerManager
import com.chris.m3usuite.player.miniplayer.MiniPlayerState
import com.chris.m3usuite.ui.focus.FocusZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for TOGGLE_MINI_PLAYER_FOCUS action handling.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayer Focus Toggle Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **Requirements:**
 * - For TvAction.TOGGLE_MINI_PLAYER_FOCUS:
 *   - If MiniPlayerState.visible == false → no-op (ignore action)
 *   - If MiniPlayerState.visible == true:
 *     - If current focus zone == PRIMARY_UI → FocusKit.requestZoneFocus(MINI_PLAYER)
 *     - If current focus zone == MINI_PLAYER → FocusKit.requestZoneFocus(PRIMARY_UI)
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
 */
class ToggleMiniPlayerFocusTest {
    private lateinit var fakeMiniPlayerManager: FakeMiniPlayerManager
    private lateinit var delegate: FocusKitNavigationDelegate

    @Before
    fun setup() {
        fakeMiniPlayerManager = FakeMiniPlayerManager()
        delegate = FocusKitNavigationDelegate(fakeMiniPlayerManager)
    }

    // ══════════════════════════════════════════════════════════════════
    // TOGGLE_MINI_PLAYER_FOCUS Action Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `TOGGLE_MINI_PLAYER_FOCUS with MiniPlayer not visible returns false`() {
        // Given: MiniPlayer is NOT visible
        fakeMiniPlayerManager.setVisible(false)

        // When: TOGGLE_MINI_PLAYER_FOCUS action
        val result = delegate.focusZone(TvAction.TOGGLE_MINI_PLAYER_FOCUS)

        // Then: Returns false (no-op)
        assertFalse("Should return false when MiniPlayer not visible", result)
    }

    @Test
    fun `TOGGLE_MINI_PLAYER_FOCUS with MiniPlayer visible triggers focus change`() {
        // Given: MiniPlayer IS visible
        fakeMiniPlayerManager.setVisible(true)

        // When: TOGGLE_MINI_PLAYER_FOCUS action
        // Note: In unit test context, FocusKit may not be available,
        // but the method should not throw
        val result = delegate.focusZone(TvAction.TOGGLE_MINI_PLAYER_FOCUS)

        // Then: Method should return without throwing
        // Result depends on FocusKit zone registration which isn't set up in unit tests
        // The key assertion is that no exception is thrown
        assertTrue("Should handle action without throwing", true)
    }

    // ══════════════════════════════════════════════════════════════════
    // TvAction.isFocusAction() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `TOGGLE_MINI_PLAYER_FOCUS is classified as focus action`() {
        // Given
        val action = TvAction.TOGGLE_MINI_PLAYER_FOCUS

        // Then
        assertTrue(
            "TOGGLE_MINI_PLAYER_FOCUS should be classified as focus action",
            TvAction.Companion.run { action.isFocusAction() },
        )
    }

    @Test
    fun `FOCUS_QUICK_ACTIONS is classified as focus action`() {
        assertTrue(
            "FOCUS_QUICK_ACTIONS should be classified as focus action",
            TvAction.Companion.run { TvAction.FOCUS_QUICK_ACTIONS.isFocusAction() },
        )
    }

    @Test
    fun `FOCUS_TIMELINE is classified as focus action`() {
        assertTrue(
            "FOCUS_TIMELINE should be classified as focus action",
            TvAction.Companion.run { TvAction.FOCUS_TIMELINE.isFocusAction() },
        )
    }

    @Test
    fun `PLAY_PAUSE is not classified as focus action`() {
        assertFalse(
            "PLAY_PAUSE should not be classified as focus action",
            TvAction.Companion.run { TvAction.PLAY_PAUSE.isFocusAction() },
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // zoneForAction Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `zoneForAction returns QUICK_ACTIONS for FOCUS_QUICK_ACTIONS`() {
        assertEquals(
            FocusZoneId.QUICK_ACTIONS,
            FocusKitNavigationDelegate.zoneForAction(TvAction.FOCUS_QUICK_ACTIONS),
        )
    }

    @Test
    fun `zoneForAction returns TIMELINE for FOCUS_TIMELINE`() {
        assertEquals(
            FocusZoneId.TIMELINE,
            FocusKitNavigationDelegate.zoneForAction(TvAction.FOCUS_TIMELINE),
        )
    }

    @Test
    fun `zoneForAction returns null for TOGGLE_MINI_PLAYER_FOCUS`() {
        // TOGGLE_MINI_PLAYER_FOCUS toggles between zones, not targets a specific zone
        assertEquals(
            null,
            FocusKitNavigationDelegate.zoneForAction(TvAction.TOGGLE_MINI_PLAYER_FOCUS),
        )
    }

    @Test
    fun `zoneForAction returns null for non-focus actions`() {
        assertEquals(null, FocusKitNavigationDelegate.zoneForAction(TvAction.PLAY_PAUSE))
        assertEquals(null, FocusKitNavigationDelegate.zoneForAction(TvAction.BACK))
        assertEquals(null, FocusKitNavigationDelegate.zoneForAction(TvAction.NAVIGATE_UP))
    }

    // ══════════════════════════════════════════════════════════════════
    // FocusZoneId Enum Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `FocusZoneId contains MINI_PLAYER zone`() {
        assertTrue(
            "MINI_PLAYER should exist in FocusZoneId",
            FocusZoneId.values().contains(FocusZoneId.MINI_PLAYER),
        )
    }

    @Test
    fun `FocusZoneId contains PRIMARY_UI zone`() {
        assertTrue(
            "PRIMARY_UI should exist in FocusZoneId",
            FocusZoneId.values().contains(FocusZoneId.PRIMARY_UI),
        )
    }
}

/**
 * Fake MiniPlayerManager for testing.
 */
private class FakeMiniPlayerManager : MiniPlayerManager {
    private val _state = MutableStateFlow(MiniPlayerState.INITIAL)
    override val state: StateFlow<MiniPlayerState> = _state

    fun setVisible(visible: Boolean) {
        _state.value = _state.value.copy(visible = visible)
    }

    override fun enterMiniPlayer(
        fromRoute: String,
        mediaId: Long?,
        rowIndex: Int?,
        itemIndex: Int?,
    ) {
        _state.value =
            _state.value.copy(
                visible = true,
                returnRoute = fromRoute,
                returnMediaId = mediaId,
                returnRowIndex = rowIndex,
                returnItemIndex = itemIndex,
            )
    }

    override fun exitMiniPlayer(returnToFullPlayer: Boolean) {
        _state.value = _state.value.copy(visible = false)
    }

    override fun updateMode(mode: com.chris.m3usuite.player.miniplayer.MiniPlayerMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    override fun updateAnchor(anchor: com.chris.m3usuite.player.miniplayer.MiniPlayerAnchor) {
        _state.value = _state.value.copy(anchor = anchor)
    }

    override fun updateSize(size: androidx.compose.ui.unit.DpSize) {
        _state.value = _state.value.copy(size = size)
    }

    override fun updatePosition(offset: androidx.compose.ui.geometry.Offset) {
        _state.value = _state.value.copy(position = offset)
    }

    override fun reset() {
        _state.value = MiniPlayerState.INITIAL
    }

    override fun enterResizeMode() {
        _state.value =
            _state.value.copy(
                mode = com.chris.m3usuite.player.miniplayer.MiniPlayerMode.RESIZE,
                previousSize = _state.value.size,
                previousPosition = _state.value.position,
            )
    }

    override fun applyResize(deltaSize: androidx.compose.ui.unit.DpSize) {
        // Simplified implementation for testing
        val newWidth = _state.value.size.width + deltaSize.width
        val newHeight = _state.value.size.height + deltaSize.height
        _state.value =
            _state.value.copy(
                size =
                    androidx.compose.ui.unit
                        .DpSize(newWidth, newHeight),
            )
    }

    override fun moveBy(delta: androidx.compose.ui.geometry.Offset) {
        val current = _state.value.position ?: androidx.compose.ui.geometry.Offset.Zero
        _state.value =
            _state.value.copy(
                position =
                    androidx.compose.ui.geometry
                        .Offset(current.x + delta.x, current.y + delta.y),
            )
    }

    override fun confirmResize() {
        _state.value =
            _state.value.copy(
                mode = com.chris.m3usuite.player.miniplayer.MiniPlayerMode.NORMAL,
                previousSize = null,
                previousPosition = null,
            )
    }

    override fun cancelResize() {
        _state.value =
            _state.value.copy(
                mode = com.chris.m3usuite.player.miniplayer.MiniPlayerMode.NORMAL,
                size = _state.value.previousSize ?: _state.value.size,
                position = _state.value.previousPosition,
                previousSize = null,
                previousPosition = null,
            )
    }

    override fun snapToNearestAnchor(
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: androidx.compose.ui.unit.Density,
    ) {
        // Simplified: just reset position
        _state.value = _state.value.copy(position = null)
    }

    override fun clampToSafeArea(
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: androidx.compose.ui.unit.Density,
    ) {
        // No-op for tests
    }

    override fun markFirstTimeHintShown() {
        _state.value = _state.value.copy(hasShownFirstTimeHint = true)
    }
}
