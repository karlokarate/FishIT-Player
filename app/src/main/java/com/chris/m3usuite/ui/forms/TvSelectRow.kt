package com.chris.m3usuite.ui.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.tvClickable

@Composable
fun <T> TvSelectRow(
    label: String,
    options: List<T>,
    selected: T?,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    helperText: String? = null,
    errorText: String? = null,
    modifier: Modifier = Modifier
) {
    val idx = remember(options, selected) {
        val i = if (selected == null) -1 else options.indexOf(selected)
        if (i >= 0) i else 0
    }
    fun bump(dir: Int) {
        if (options.isEmpty()) return
        val n = options.size
        val next = ((idx + dir) % n + n) % n
        onSelected(options[next])
    }
    val currentText = if (options.isEmpty()) "—" else optionLabel(options.getOrNull(idx) ?: options.first())

    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(debugTag = "TvSelectRow:$label", onClick = { bump(+1) })
                .onPreviewKeyEvent {
                    when (it.key) {
                        Key.DirectionLeft -> { bump(-1); true }
                        Key.DirectionRight -> { bump(+1); true }
                        else -> false
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(currentText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        ValidationHint(helperText = helperText, errorText = errorText)
    }
}
