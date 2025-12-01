package com.chris.m3usuite.player.internal.ui

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Enum representing the UI mode for player overlay layout.
 *
 * **SIP Responsive Overlay Contract:**
 * - PHONE: Touch devices with screenWidthDp < 600
 * - TABLET: Touch devices with screenWidthDp >= 600
 * - TV: Android TV / Fire TV devices with leanback or television system feature
 *
 * Used to determine which overlay layout to render:
 * - PHONE/TABLET: Centered controls, larger tap targets
 * - TV: Bottom-aligned controls, DPAD-focusable
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
 * System feature string for TV devices.
 * Using constant string to avoid deprecated PackageManager.FEATURE_TELEVISION reference.
 */
private const val FEATURE_TELEVISION = "android.hardware.type.television"

/**
 * Detects the current player UI mode based on device characteristics.
 *
 * **Detection Logic:**
 * 1. If the device has leanback or television system feature → TV
 * 2. If screenWidthDp >= 600 → TABLET
 * 3. Otherwise → PHONE
 *
 * @return The detected [PlayerUiMode]
 */
@Composable
fun detectPlayerUiMode(): PlayerUiMode {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current

    val isTv = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        ctx.packageManager.hasSystemFeature(FEATURE_TELEVISION)

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
 * @param hasLeanbackFeature Whether the device has leanback system feature
 * @param hasTelevisionFeature Whether the device has television system feature
 * @param screenWidthDp Current screen width in dp
 * @return The detected [PlayerUiMode]
 */
fun detectPlayerUiMode(
    hasLeanbackFeature: Boolean,
    hasTelevisionFeature: Boolean,
    screenWidthDp: Int,
): PlayerUiMode {
    val isTv = hasLeanbackFeature || hasTelevisionFeature
    return when {
        isTv -> PlayerUiMode.TV
        screenWidthDp >= TABLET_WIDTH_THRESHOLD_DP -> PlayerUiMode.TABLET
        else -> PlayerUiMode.PHONE
    }
}
