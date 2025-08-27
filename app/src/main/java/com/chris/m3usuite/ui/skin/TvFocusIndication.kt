package com.chris.m3usuite.ui.skin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Translucent overlay for focus/press. Works with any clickable that uses LocalIndication.
 */
class TvFocusIndication(
    private val focusColor: Color = Color(0xFF00E0FF).copy(alpha = 0.18f),
    private val pressColor: Color = Color(0xFF00E0FF).copy(alpha = 0.28f)
) : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: MutableInteractionSource): IndicationInstance {
        var focused by remember { mutableStateOf(false) }
        var pressed by remember { mutableStateOf(false) }

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { i ->
                when (i) {
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                }
            }
        }

        val target = when {
            pressed -> 1f
            focused -> 0.64f
            else -> 0f
        }
        val alpha by animateFloatAsState(target, spring(stiffness = 600f), label = "tvMaskAlpha")

        return object : IndicationInstance {
            override fun ContentDrawScope.drawIndication() {
                drawContent()
                if (alpha == 0f) return
                val c = if (pressed) pressColor else focusColor
                drawRect(color = c, alpha = alpha)
                drawIntoCanvas { /* subtle extra pass already above */ }
            }
        }
    }
}

