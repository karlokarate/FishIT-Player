package com.chris.m3usuite.ui.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.tvClickable
import kotlin.math.roundToInt

@Composable
fun TvSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
    helperText: String? = null,
    errorText: String? = null,
    trackHeight: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val clamped = value.coerceIn(range)
    val frac = if (range.last > range.first) {
        (clamped - range.first).toFloat() / (range.last - range.first).toFloat()
    } else 0f

    fun bump(delta: Int) {
        val raw = clamped + delta
        val stepped = (raw / step.toFloat()).roundToInt() * step
        onValueChange(stepped.coerceIn(range))
    }

    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(debugTag = "TvSliderRow:$label", onClick = { /* noop: handled by DPAD */ })
                .onPreviewKeyEvent {
                    when (it.key) {
                        Key.DirectionLeft -> { bump(-step); true }
                        Key.DirectionRight -> { bump(+step); true }
                        else -> false
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("$clamped", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val f = frac.coerceIn(0f, 1f)
            Box(Modifier.weight(if (f == 0f) 0.0001f else f).fillMaxWidth().height(trackHeight).background(MaterialTheme.colorScheme.primary))
            Box(Modifier.weight(if (f == 1f) 0.0001f else 1f - f).fillMaxWidth().height(trackHeight))
        }
        Spacer(Modifier.height(4.dp))
        ValidationHint(helperText = helperText, errorText = errorText)
    }
}
