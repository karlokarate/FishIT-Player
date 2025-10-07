package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Stable
data class DetailMeta(
    val year: Int? = null,
    val durationSecs: Int? = null,
    val videoQuality: String? = null,
    val hdr: String? = null,
    val audio: String? = null,
    val genres: List<String> = emptyList(),
    val provider: String? = null,
    val category: String? = null
)

@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun MetaChips(
    meta: DetailMeta,
    compact: Boolean = false,
    collapsible: Boolean = false,
    modifier: Modifier = Modifier
) {
    val spacing = if (compact) 6.dp else 8.dp
    val chips = remember(meta) {
        buildList {
            meta.year?.let { add(it.toString()) }
            meta.durationSecs?.let { secs -> add(formatDuration(secs)) }
            meta.videoQuality?.takeIf { it.isNotBlank() }?.let { add(it) }
            meta.hdr?.takeIf { it.isNotBlank() }?.let { add(it) }
            meta.audio?.takeIf { it.isNotBlank() }?.let { add("Audio: $it") }
            meta.provider?.takeIf { it.isNotBlank() }?.let { add(it) }
            meta.category?.takeIf { it.isNotBlank() }?.let { add(it) }
            meta.genres.forEach { g -> add(g) }
        }
    }
    val maxCollapsed = 3
    val canCollapse = collapsible && chips.size > maxCollapsed
    var expanded by rememberSaveable(meta, collapsible) { mutableStateOf(!canCollapse) }
    val visible = if (!canCollapse || expanded) chips else chips.take(maxCollapsed)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        visible.forEach { Chip(it) }
        if (canCollapse) {
            val label = if (expanded) "Weniger" else "Mehr…"
            AssistChip(
                onClick = { expanded = !expanded },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun Chip(text: String) {
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val label = MaterialTheme.colorScheme.onSurface
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = label
        )
    )
}

private fun formatDuration(totalSecs: Int): String {
    val s = if (totalSecs < 0) 0 else totalSecs
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
