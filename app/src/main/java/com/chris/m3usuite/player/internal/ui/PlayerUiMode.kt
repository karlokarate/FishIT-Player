package com.chris.m3usuite.player.internal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.chris.m3usuite.ui.focus.FocusKit

/**
 * Enum representing the UI mode for player overlay layout.
 *
 * **SIP Responsive Overlay Contract:**
 * - PHONE: Touch devices with screenWidthDp < 600
 * - TABLET: Touch devices with screenWidthDp >= 600
 * - TV: Android TV / Fire TV devices (detected via FocusKit.isTvDevice)
 *
 * Used to determine which overlay layout to render:
 * - PHONE/TABLET: Centered controls, larger tap targets
 * - TV: Bottom-aligned controls, DPAD-focusable
 *
 * **Important:** TV detection uses the centralized FocusKit.isTvDevice() method,
 * which is the single source of truth for TV device detection at app startup.
 */
enum class PlayerUiMode {
    PHONE,
    TABLET,
    TV,
}

/**
 * Threshold for tablet detection (in dp).
 * Devices with screenWidthDp >= 600 are considered tablets.
 */
const val TABLET_WIDTH_THRESHOLD_DP = 600

/**
 * Detects the current player UI mode based on device characteristics.
 *
 * **Detection Logic:**
 * 1. If FocusKit.isTvDevice() returns true → TV
 * 2. If screenWidthDp >= 600 → TABLET
 * 3. Otherwise → PHONE
 *
 * **Note:** This function uses FocusKit.isTvDevice() as the single source of truth
 * for TV detection, ensuring consistency across all modules.
 *
 * @return The detected [PlayerUiMode]
 */
@Composable
fun detectPlayerUiMode(): PlayerUiMode {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current

    // Use FocusKit.isTvDevice() as the centralized TV detection method
    val isTv = FocusKit.isTvDevice(ctx)

    return when {
        isTv -> PlayerUiMode.TV
        config.screenWidthDp >= TABLET_WIDTH_THRESHOLD_DP -> PlayerUiMode.TABLET
        else -> PlayerUiMode.PHONE
    }
}

/**
 * Non-composable version of PlayerUiMode detection for use in tests
 * or non-composable contexts.
 *
 * @param isTvDevice Whether the device is a TV (from FocusKit.isTvDevice)
 * @param screenWidthDp Current screen width in dp
 * @return The detected [PlayerUiMode]
 */
fun detectPlayerUiMode(
    isTvDevice: Boolean,
    screenWidthDp: Int,
): PlayerUiMode {
    return when {
        isTvDevice -> PlayerUiMode.TV
        screenWidthDp >= TABLET_WIDTH_THRESHOLD_DP -> PlayerUiMode.TABLET
        else -> PlayerUiMode.PHONE
    }
}
