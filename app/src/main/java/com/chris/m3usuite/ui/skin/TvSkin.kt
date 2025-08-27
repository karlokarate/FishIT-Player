package com.chris.m3usuite.ui.skin

import androidx.compose.runtime.Composable

/**
 * Wrapper kept for compatibility. Indication is handled directly in tvClickable/focusScaleOnTv.
 */
@Composable
fun M3UTvSkin(content: @Composable () -> Unit) {
    content()
}
