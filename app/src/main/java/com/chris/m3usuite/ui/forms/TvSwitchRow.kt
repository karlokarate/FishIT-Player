package com.chris.m3usuite.ui.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.tvClickable

@Composable
fun TvSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helperText: String? = null,
    errorText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(
                    role = androidx.compose.ui.semantics.Role.Switch,
                    debugTag = "TvSwitchRow:$label",
                    onClick = { onCheckedChange(!checked) }
                )
                .onPreviewKeyEvent {
                    when (it.key) {
                        Key.DirectionLeft -> { if (checked) onCheckedChange(false); true }
                        Key.DirectionRight -> { if (!checked) onCheckedChange(true); true }
                        else -> false
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Spacer(Modifier.height(4.dp))
        ValidationHint(helperText = helperText, errorText = errorText)
    }
}
