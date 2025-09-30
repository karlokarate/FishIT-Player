package com.chris.m3usuite.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

// Composition-local tag to identify the current UI module/area for focus logs
val LocalFocusAreaTag = compositionLocalOf<String?> { null }

@Composable
fun ProvideFocusArea(tag: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFocusAreaTag provides tag) { content() }
}

