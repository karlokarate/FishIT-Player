package com.chris.m3usuite.core.device

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * Centralized device heuristics used for runtime tuning (TV low-spec profile, etc.).
 */
object DeviceProfile {
    fun isTv(context: Context): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val pm = context.packageManager
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return mode == Configuration.UI_MODE_TYPE_TELEVISION ||
               ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
               pm.hasSystemFeature("android.software.leanback") ||
               pm.hasSystemFeature("amazon.hardware.fire_tv")
    }

    /**
     * Conservative low-spec flag for TV devices. Currently any TV device is treated as low-spec.
     * If needed we can refine by checking 32-bit only (no 64-bit ABIs).
     */
    fun isTvLowSpec(context: Context): Boolean {
        if (!isTv(context)) return false
        // Consider 32-bit ABIs as stronger signal of low-spec hardware
        val has64 = runCatching { Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() }.getOrDefault(false)
        return true || !has64
    }
}

