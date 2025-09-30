package com.chris.m3usuite.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.tvClickable as skinTvClickable
import com.chris.m3usuite.ui.skin.focusScaleOnTv as skinFocusScaleOnTv
import com.chris.m3usuite.ui.skin.tvFocusableItem as skinTvFocusableItem
import com.chris.m3usuite.ui.skin.tvFocusFrame as skinTvFocusFrame

/**
 * SkinScope exposes member extension wrappers so calls like
 * `com.chris.m3usuite.ui.skin.run { Modifier.tvClickable() }`
 * resolve without explicit imports at use sites.
 */
object SkinScope {
    @Composable
    fun Modifier.tvClickable(
        enabled: Boolean = true,
        role: androidx.compose.ui.semantics.Role? = androidx.compose.ui.semantics.Role.Button,
        scaleFocused: Float = 1.08f,
        scalePressed: Float = 1.12f,
        elevationFocusedDp: Float = 12f,
        autoBringIntoView: Boolean = true,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        focusColors: TvFocusColors = TvFocusColors.Default,
        focusBorderWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
        brightenContent: Boolean = true,
        onClick: () -> Unit
    ): Modifier = this@tvClickable.run {
        // Delegate to the top-level extension via alias to avoid name shadowing
        this.skinTvClickable(
            enabled = enabled,
            role = role,
            scaleFocused = scaleFocused,
            scalePressed = scalePressed,
            elevationFocusedDp = elevationFocusedDp,
            autoBringIntoView = autoBringIntoView,
            shape = shape,
            focusColors = focusColors,
            focusBorderWidth = focusBorderWidth,
            brightenContent = brightenContent,
            onClick = onClick
        )
    }

    fun Modifier.focusScaleOnTv(
        focusedScale: Float? = null,
        pressedScale: Float? = null,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        focusColors: TvFocusColors = TvFocusColors.Default,
        focusBorderWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
        brightenContent: Boolean = true
    ): Modifier = this@focusScaleOnTv.run {
        this.skinFocusScaleOnTv(
            focusedScale = focusedScale,
            pressedScale = pressedScale,
            shape = shape,
            focusColors = focusColors,
            focusBorderWidth = focusBorderWidth,
            interactionSource = interactionSource,
            brightenContent = brightenContent
        )
    }

    @Composable
    fun Modifier.tvFocusableItem(
        stateKey: String,
        index: Int,
        autoBringIntoView: Boolean = true,
        onFocused: () -> Unit = {}
    ): Modifier = this@tvFocusableItem.run {
        this.skinTvFocusableItem(
            stateKey = stateKey,
            index = index,
            autoBringIntoView = autoBringIntoView,
            onFocused = onFocused
        )
    }

    fun Modifier.tvFocusFrame(
        focusedScale: Float = 1.40f,
        pressedScale: Float = 1.40f,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        focusColors: TvFocusColors = TvFocusColors.Default,
        focusBorderWidth: androidx.compose.ui.unit.Dp = 2.5.dp,
        brightenContent: Boolean = false
    ): Modifier = this@tvFocusFrame.run {
        this.skinTvFocusFrame(
            focusedScale = focusedScale,
            pressedScale = pressedScale,
            shape = shape,
            focusColors = focusColors,
            focusBorderWidth = focusBorderWidth,
            brightenContent = brightenContent
        )
    }
}

@Composable
inline fun <T> run(block: @Composable SkinScope.() -> T): T = SkinScope.block()
