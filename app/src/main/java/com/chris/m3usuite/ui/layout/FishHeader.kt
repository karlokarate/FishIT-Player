package com.chris.m3usuite.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

sealed class FishHeaderData(
    open val anchorKey: String,
) {
    data class Text(
        override val anchorKey: String,
        val text: String,
        val style: TextStyle? = null,
        val color: Color? = null,
        val background: Color? = null,
        val accent: Color = Color.Transparent,
        val badge: String? = null,
    ) : FishHeaderData(anchorKey)

    data class Chip(
        override val anchorKey: String,
        val label: String,
        val background: Color? = null,
        val contentColor: Color? = null,
        val outline: Color? = null,
    ) : FishHeaderData(anchorKey)

    data class Provider(
        override val anchorKey: String,
        val label: String,
        val background: Color? = null,
        val contentColor: Color? = null,
        val outline: Color? = null,
    ) : FishHeaderData(anchorKey)
}

class FishHeaderController internal constructor() {
    private val state: MutableState<FishHeaderData?> = mutableStateOf(null)

    val current: State<FishHeaderData?>
        get() = state

    fun activate(data: FishHeaderData) {
        state.value = data
    }

    fun deactivate(data: FishHeaderData) {
        if (state.value?.anchorKey == data.anchorKey) {
            state.value = null
        }
    }
}

val LocalFishHeaderController = compositionLocalOf<FishHeaderController?> { null }

@Composable
fun FishHeaderHost(
    modifier: Modifier = Modifier,
    overlayAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val controller = remember { FishHeaderController() }
    val active by controller.current

    Box(modifier) {
        CompositionLocalProvider(LocalFishHeaderController provides controller) {
            content()
        }
        AnimatedVisibility(
            visible = active != null,
            modifier = Modifier.align(overlayAlignment),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            active?.let { data ->
                when (data) {
                    is FishHeaderData.Text -> HeaderText(data)
                    is FishHeaderData.Chip -> HeaderChip(data)
                    is FishHeaderData.Provider ->
                        HeaderChip(
                            FishHeaderData.Chip(
                                anchorKey = data.anchorKey,
                                label = data.label,
                                background = data.background,
                                contentColor = data.contentColor,
                                outline = data.outline,
                            ),
                        )
                }
            }
        }
    }
}

@Composable
private fun HeaderText(data: FishHeaderData.Text) {
    val bg = data.background ?: Color.Black.copy(alpha = 0.55f)
    val textColor = data.color ?: MaterialTheme.colorScheme.onSurface
    val style = data.style ?: MaterialTheme.typography.titleMedium
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier = Modifier.padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (data.accent.alpha > 0f) {
                Box(
                    Modifier
                        .size(width = 6.dp, height = 24.dp)
                        .background(data.accent, RoundedCornerShape(999.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = data.text,
                style = style,
                color = textColor,
            )
            if (!data.badge.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = data.accent.takeIf { it.alpha > 0f } ?: MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = textColor,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = data.badge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(data: FishHeaderData.Chip) {
    val bg = data.background ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val content = data.contentColor ?: MaterialTheme.colorScheme.onPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(12.dp)
                .background(
                    color = bg,
                    shape = RoundedCornerShape(18.dp),
                ).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = data.label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
