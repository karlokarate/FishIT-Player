package com.chris.m3usuite.ui.skin

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Wrap your app with this to enable TV-style indication globally (phones & TV).
 * Phone: overlay appears on press; TV: overlay appears on focus/press.
 */
@Composable
fun M3UTvSkin(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides TvFocusIndication()) {
        MaterialTheme { content() }
    }
}

