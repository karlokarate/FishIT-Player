package com.chris.m3usuite.player.internal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.chris.m3usuite.ui.focus.isTvDevice

/**
 * Player UI mode representing the device form factor.
 *
 * Used by the SIP Internal Player overlay to determine the optimal control layout:
 * - TV: Small, discrete, bottom-aligned controls with DPAD focus
 * - PHONE/TABLET: Large, centered controls optimized for touch
 */
enum class PlayerUiMode {
    /** Phone form factor - small screen touch device */
    PHONE,

    /** Tablet form factor - large screen touch device (screenWidthDp >= 600) */
    TABLET,

    /** TV form factor - lean-back device with DPAD/remote control */
    TV,
}

/**
 * Detects the current player UI mode based on device characteristics.
 *
 * Detection priority:
 * 1. TV/Leanback features → TV mode
 * 2. Screen width >= 600dp → TABLET mode
 * 3. Otherwise → PHONE mode
 *
 * @return The detected [PlayerUiMode] for the current device
 */
@Composable
fun detectPlayerUiMode(): PlayerUiMode {
    val context = LocalContext.current
    val config = LocalConfiguration.current

    return remember(context, config.screenWidthDp) {
        val pm = context.packageManager

        // Check for TV/Leanback features using existing isTvDevice() plus Fire TV detection
        // Note: isTvDevice() already checks FEATURE_LEANBACK and FEATURE_TELEVISION
        val isTv = isTvDevice(context) || pm.hasSystemFeature("amazon.hardware.fire_tv")

        when {
            isTv -> PlayerUiMode.TV
            config.screenWidthDp >= 600 -> PlayerUiMode.TABLET
            else -> PlayerUiMode.PHONE
        }
    }
}

/**
 * Returns true if the current UI mode is a touch-based device (PHONE or TABLET).
 */
fun PlayerUiMode.isTouchDevice(): Boolean = this == PlayerUiMode.PHONE || this == PlayerUiMode.TABLET
