package com.chris.m3usuite.ui.components.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.focusScaleOnTv

/**
 * Green "+" for granting/allowing (e.g., Inhalte für Kinder freigeben)
 */
@Composable
fun GrantIconButton(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF2ECC71),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.focusScaleOnTv(),
        enabled = true,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
    ) {
        Icon(
            imageVector = Icons.Rounded.AddCircle,
            contentDescription = "Freigeben",
            modifier = Modifier.size(28.dp),
            tint = tint
        )
    }
}

/**
 * Red "-" for removing/disallowing (e.g., aus Kinderprofil entfernen)
 */
@Composable
fun RemoveIconButton(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFE74C3C),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.focusScaleOnTv(),
        enabled = true,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
    ) {
        Icon(
            imageVector = Icons.Rounded.RemoveCircle,
            contentDescription = "Entfernen",
            modifier = Modifier.size(28.dp),
            tint = tint
        )
    }
}
