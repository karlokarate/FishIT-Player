package com.chris.m3usuite.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.Context.UI_MODE_SERVICE
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import com.chris.m3usuite.ui.skin.tvClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

fun isTv(context: Context): Boolean {
    val uiModeManager = context.getSystemService(UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun FocusableCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val tv = isTv(ctx)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (tv && focused) 1.05f else 1f, label = "focus-scale")

    Card(
        modifier = modifier
            .scale(scale)
            .focusable(tv)
            .onFocusEvent { focused = it.isFocused }
            .tvClickable(
                scaleFocused = 1f,
                scalePressed = 1f,
                brightenContent = false,
                onClick = onClick
            )
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (focused) 8.dp else 2.dp)
    ) {
        Box(Modifier.padding(8.dp)) { content() }
    }
}
