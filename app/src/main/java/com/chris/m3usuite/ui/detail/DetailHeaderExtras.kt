package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Compact chips shown inline under the title in DetailHeader. */
@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun DetailHeaderExtras(
    mpaaRating: String? = null,
    age: String? = null,
    audio: String? = null,
    video: String? = null,
) {
    val chips = buildList<String> {
        mpaaRating?.takeIf { it.isNotBlank() }?.let { add("MPAA: $it") }
        age?.takeIf { it.isNotBlank() }?.let { add("Age: $it") }
        audio?.takeIf { it.isNotBlank() }?.let { add("Audio: ${it.take(18)}") }
        video?.takeIf { it.isNotBlank() }?.let { add("Video: ${it.take(18)}") }
    }
    if (chips.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { label ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(label) },
                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

