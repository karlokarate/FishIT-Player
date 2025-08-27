package com.chris.m3usuite.ui.skin

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

fun isTvDevice(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val pm = context.packageManager
    val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return mode == Configuration.UI_MODE_TYPE_TELEVISION ||
           ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
           pm.hasSystemFeature("android.software.leanback") ||
           pm.hasSystemFeature("amazon.hardware.fire_tv")
}

