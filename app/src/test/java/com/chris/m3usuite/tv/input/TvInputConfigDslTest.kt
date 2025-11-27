package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TvInputConfigDsl.
 *
 * Tests verify that the DSL builds expected bindings correctly.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.2
 */
class TvInputConfigDslTest {
    // ══════════════════════════════════════════════════════════════════
    // BASIC DSL SYNTAX TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `tvInputConfig creates empty map when no screens defined`() {
        val configs = tvInputConfig { }

        assertTrue(configs.isEmpty())
    }

    @Test
    fun `tvInputConfig creates map with single screen`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                }
            }

        assertEquals(1, configs.size)
        assertTrue(configs.containsKey(TvScreenId.PLAYER))
    }

    @Test
    fun `tvInputConfig creates map with multiple screens`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                }
                screen(TvScreenId.LIBRARY) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.PAGE_DOWN
                }
            }

        assertEquals(2, configs.size)
        assertTrue(configs.containsKey(TvScreenId.PLAYER))
        assertTrue(configs.containsKey(TvScreenId.LIBRARY))
    }

    // ══════════════════════════════════════════════════════════════════
    // BINDING CREATION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `on-mapsTo creates correct binding`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                }
            }

        val playerConfig = configs[TvScreenId.PLAYER]!!
        assertEquals(TvAction.SEEK_FORWARD_30S, playerConfig.getRawAction(TvKeyRole.FAST_FORWARD))
    }

    @Test
    fun `on-mapsTo null creates explicit null binding`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.LIBRARY) {
                    on(TvKeyRole.DPAD_CENTER) mapsTo null
                }
            }

        val libraryConfig = configs[TvScreenId.LIBRARY]!!
        assertTrue(libraryConfig.hasBinding(TvKeyRole.DPAD_CENTER))
        assertNull(libraryConfig.getRawAction(TvKeyRole.DPAD_CENTER))
    }

    @Test
    fun `multiple bindings in same screen work correctly`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                    on(TvKeyRole.REWIND) mapsTo TvAction.SEEK_BACKWARD_30S
                    on(TvKeyRole.MENU) mapsTo TvAction.OPEN_QUICK_ACTIONS
                    on(TvKeyRole.BACK) mapsTo TvAction.BACK
                }
            }

        val playerConfig = configs[TvScreenId.PLAYER]!!
        assertEquals(4, playerConfig.bindings.size)
        assertEquals(TvAction.SEEK_FORWARD_30S, playerConfig.getRawAction(TvKeyRole.FAST_FORWARD))
        assertEquals(TvAction.SEEK_BACKWARD_30S, playerConfig.getRawAction(TvKeyRole.REWIND))
        assertEquals(TvAction.OPEN_QUICK_ACTIONS, playerConfig.getRawAction(TvKeyRole.MENU))
        assertEquals(TvAction.BACK, playerConfig.getRawAction(TvKeyRole.BACK))
    }

    // ══════════════════════════════════════════════════════════════════
    // SCREEN CONFIG BUILDER TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `ScreenConfigBuilder sets screenId correctly`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.SETTINGS) {
                    on(TvKeyRole.BACK) mapsTo TvAction.BACK
                }
            }

        val settingsConfig = configs[TvScreenId.SETTINGS]!!
        assertEquals(TvScreenId.SETTINGS, settingsConfig.screenId)
    }

    @Test
    fun `empty screen block creates empty bindings`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.UNKNOWN) { }
            }

        val unknownConfig = configs[TvScreenId.UNKNOWN]!!
        assertTrue(unknownConfig.bindings.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // DIFFERENT SCREENS DIFFERENT MAPPINGS TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `same key role maps to different actions on different screens`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                }
                screen(TvScreenId.LIBRARY) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.PAGE_DOWN
                }
            }

        assertEquals(
            TvAction.SEEK_FORWARD_30S,
            configs[TvScreenId.PLAYER]!!.getRawAction(TvKeyRole.FAST_FORWARD),
        )
        assertEquals(
            TvAction.PAGE_DOWN,
            configs[TvScreenId.LIBRARY]!!.getRawAction(TvKeyRole.FAST_FORWARD),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // IMMUTABILITY TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `returned map is immutable snapshot`() {
        val builder = TvInputConfigBuilder()
        builder.screen(TvScreenId.PLAYER) {
            on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
        }

        val configs1 = builder.build()

        // Add more screens to builder after first build
        builder.screen(TvScreenId.LIBRARY) {
            on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.PAGE_DOWN
        }

        val configs2 = builder.build()

        // configs1 should only have PLAYER (snapshot at time of build)
        // configs2 should have both PLAYER and LIBRARY
        assertEquals(1, configs1.size)
        assertEquals(2, configs2.size)
    }

    @Test
    fun `screen config bindings are immutable`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                }
            }

        val playerConfig = configs[TvScreenId.PLAYER]!!

        // Bindings should be a defensive copy (immutable Map)
        assertNotNull(playerConfig.bindings)
        assertEquals(1, playerConfig.bindings.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // EXTENSION FUNCTION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `getOrEmpty returns config for existing screen`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                }
            }

        val playerConfig = configs.getOrEmpty(TvScreenId.PLAYER)
        assertEquals(TvScreenId.PLAYER, playerConfig.screenId)
        assertEquals(TvAction.SEEK_FORWARD_30S, playerConfig.getRawAction(TvKeyRole.FAST_FORWARD))
    }

    @Test
    fun `getOrEmpty returns empty config for non-existing screen`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                }
            }

        val libraryConfig = configs.getOrEmpty(TvScreenId.LIBRARY)
        assertEquals(TvScreenId.LIBRARY, libraryConfig.screenId)
        assertTrue(libraryConfig.bindings.isEmpty())
    }

    @Test
    fun `resolve extension works correctly`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                }
            }

        val ctx = TvScreenContext.player()
        val action = configs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx)

        assertEquals(TvAction.SEEK_FORWARD_30S, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // ALL KEY ROLES CAN BE BOUND
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all TvKeyRole values can be used in DSL`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                    on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                    on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                    on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT
                    on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.PLAY_PAUSE
                    on(TvKeyRole.PLAY_PAUSE) mapsTo TvAction.PLAY_PAUSE
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                    on(TvKeyRole.REWIND) mapsTo TvAction.SEEK_BACKWARD_30S
                    on(TvKeyRole.MENU) mapsTo TvAction.OPEN_QUICK_ACTIONS
                    on(TvKeyRole.BACK) mapsTo TvAction.BACK
                    on(TvKeyRole.CHANNEL_UP) mapsTo TvAction.CHANNEL_UP
                    on(TvKeyRole.CHANNEL_DOWN) mapsTo TvAction.CHANNEL_DOWN
                    on(TvKeyRole.INFO) mapsTo TvAction.OPEN_QUICK_ACTIONS
                    on(TvKeyRole.GUIDE) mapsTo TvAction.OPEN_LIVE_LIST
                    // Number keys can be bound too
                    on(TvKeyRole.NUM_0) mapsTo null
                    on(TvKeyRole.NUM_1) mapsTo null
                    on(TvKeyRole.NUM_2) mapsTo null
                    on(TvKeyRole.NUM_3) mapsTo null
                    on(TvKeyRole.NUM_4) mapsTo null
                    on(TvKeyRole.NUM_5) mapsTo null
                    on(TvKeyRole.NUM_6) mapsTo null
                    on(TvKeyRole.NUM_7) mapsTo null
                    on(TvKeyRole.NUM_8) mapsTo null
                    on(TvKeyRole.NUM_9) mapsTo null
                }
            }

        val playerConfig = configs[TvScreenId.PLAYER]!!
        // All 24 roles should be bound
        assertEquals(24, playerConfig.bindings.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // ALL TvAction VALUES CAN BE USED
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all TvAction values can be used as binding targets`() {
        val configs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.NUM_0) mapsTo TvAction.PLAY_PAUSE
                    on(TvKeyRole.NUM_1) mapsTo TvAction.SEEK_FORWARD_10S
                    on(TvKeyRole.NUM_2) mapsTo TvAction.SEEK_FORWARD_30S
                    on(TvKeyRole.NUM_3) mapsTo TvAction.SEEK_BACKWARD_10S
                    on(TvKeyRole.NUM_4) mapsTo TvAction.SEEK_BACKWARD_30S
                    on(TvKeyRole.NUM_5) mapsTo TvAction.OPEN_CC_MENU
                    on(TvKeyRole.NUM_6) mapsTo TvAction.OPEN_ASPECT_MENU
                    on(TvKeyRole.NUM_7) mapsTo TvAction.OPEN_QUICK_ACTIONS
                    on(TvKeyRole.NUM_8) mapsTo TvAction.OPEN_LIVE_LIST
                    on(TvKeyRole.NUM_9) mapsTo TvAction.PAGE_UP
                    on(TvKeyRole.DPAD_UP) mapsTo TvAction.PAGE_DOWN
                    on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.FOCUS_QUICK_ACTIONS
                    on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.FOCUS_TIMELINE
                    on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_UP
                    on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.NAVIGATE_DOWN
                    on(TvKeyRole.PLAY_PAUSE) mapsTo TvAction.NAVIGATE_LEFT
                    on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.NAVIGATE_RIGHT
                    on(TvKeyRole.REWIND) mapsTo TvAction.CHANNEL_UP
                    on(TvKeyRole.MENU) mapsTo TvAction.CHANNEL_DOWN
                    on(TvKeyRole.BACK) mapsTo TvAction.BACK
                }
            }

        val playerConfig = configs[TvScreenId.PLAYER]!!
        assertEquals(20, playerConfig.bindings.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // DEFAULT CONFIG VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `DefaultTvScreenConfigs contains expected screens`() {
        assertTrue(DefaultTvScreenConfigs.all.containsKey(TvScreenId.PLAYER))
        assertTrue(DefaultTvScreenConfigs.all.containsKey(TvScreenId.LIBRARY))
        assertTrue(DefaultTvScreenConfigs.all.containsKey(TvScreenId.SETTINGS))
        assertTrue(DefaultTvScreenConfigs.all.containsKey(TvScreenId.PROFILE_GATE))
    }

    @Test
    fun `DefaultTvScreenConfigs PLAYER has required bindings`() {
        val playerConfig = DefaultTvScreenConfigs.forScreen(TvScreenId.PLAYER)

        assertTrue(playerConfig.hasBinding(TvKeyRole.FAST_FORWARD))
        assertTrue(playerConfig.hasBinding(TvKeyRole.REWIND))
        assertTrue(playerConfig.hasBinding(TvKeyRole.MENU))
        assertTrue(playerConfig.hasBinding(TvKeyRole.BACK))
        assertTrue(playerConfig.hasBinding(TvKeyRole.PLAY_PAUSE))
    }
}
