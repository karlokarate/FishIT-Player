package com.chris.m3usuite.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.focus.FocusColors
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.focusScaleOnTv

@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
    focusColors: FocusColors = FocusKit.FocusDefaults.Colors,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier.focusScaleOnTv(
                shape = shape,
                focusColors = focusColors,
                interactionSource = interactionSource,
                debugTag = "TvButton",
            ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun TvTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
    focusColors: FocusColors = FocusKit.FocusDefaults.Colors,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier =
            modifier.focusScaleOnTv(
                shape = shape,
                focusColors = focusColors,
                interactionSource = interactionSource,
                debugTag = "TvTextButton",
            ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun TvOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
    focusColors: FocusColors = FocusKit.FocusDefaults.Colors,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier.focusScaleOnTv(
                shape = shape,
                focusColors = focusColors,
                interactionSource = interactionSource,
                debugTag = "TvOutlinedButton",
            ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    interactionSource: MutableInteractionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
    focusColors: FocusColors = FocusKit.FocusDefaults.Colors,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier.focusScaleOnTv(
                shape = shape,
                focusColors = focusColors,
                interactionSource = interactionSource,
                debugTag = "TvIconButton",
            ),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}
