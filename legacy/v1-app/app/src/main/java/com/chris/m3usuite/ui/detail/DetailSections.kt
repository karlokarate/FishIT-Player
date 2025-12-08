package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class InfoEntry(
    val title: String,
    val value: String,
)

@Composable
fun PlotSectionCard(plot: String) {
    Card { Text(plot, modifier = Modifier.padding(16.dp)) }
}

@Composable
fun FactsSectionCard(entries: List<InfoEntry>) {
    if (entries.isEmpty()) return
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { e -> Text("${e.title}: ${e.value}") }
        }
    }
}
