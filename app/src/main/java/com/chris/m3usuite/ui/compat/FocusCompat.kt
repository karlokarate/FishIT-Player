package com.chris.m3usuite.ui.compat

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Compatibility shim for a TV-friendly focus group modifier.
 * No-op behavior beyond enabling focus properties to ensure a stable compile
 * across Compose versions and to centralize future adjustments.
 */
fun Modifier.focusGroup(): Modifier = this.focusProperties { }

