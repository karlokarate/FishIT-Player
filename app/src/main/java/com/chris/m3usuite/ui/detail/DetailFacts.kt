package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun DetailFacts(
    modifier: Modifier = Modifier,
    // chips
    year: Int? = null,
    durationSecs: Int? = null,
    containerExt: String? = null,
    rating: Double? = null,
    mpaaRating: String? = null,
    age: String? = null,
    provider: String? = null,
    category: String? = null,
    genres: List<String> = emptyList(),
    countries: List<String> = emptyList(),
    // rows
    director: String? = null,
    cast: String? = null,
    releaseDate: String? = null,
    imdbId: String? = null,
    tmdbId: String? = null,
    tmdbUrl: String? = null,
    // tech
    audio: String? = null,
    video: String? = null,
    bitrate: String? = null,
    onOpenLink: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Primary chips
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            year?.let { Chip(it.toString()) }
            durationSecs?.let { Chip(formatDuration(it)) }
            containerExt?.takeIf { it.isNotBlank() }?.let { Chip(it.uppercase()) }
            rating?.let { Chip("★ ${"%.1f".format(it)}") }
            mpaaRating?.takeIf { it.isNotBlank() }?.let { Chip("MPAA: $it") }
            age?.takeIf { it.isNotBlank() }?.let { Chip("Age: $it") }
            provider?.takeIf { it.isNotBlank() }?.let { Chip(it) }
            category?.takeIf { it.isNotBlank() }?.let { Chip(it) }
            genres.forEach { g -> Chip(g) }
            if (countries.isNotEmpty()) countries.forEach { c -> Chip(c) }
        }

        // People
        if (!director.isNullOrBlank() || !cast.isNullOrBlank() || !releaseDate.isNullOrBlank()) {
            Surface(shape = MaterialTheme.shapes.medium, color = Color.White.copy(alpha = 0.04f)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    director?.takeIf { it.isNotBlank() }?.let { Text("Regie: $it", style = MaterialTheme.typography.bodySmall) }
                    cast?.takeIf { it.isNotBlank() }?.let { Text("Cast: $it", style = MaterialTheme.typography.bodySmall) }
                    releaseDate?.takeIf { it.isNotBlank() }?.let { Text("Release: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // Tech
        if (!audio.isNullOrBlank() || !video.isNullOrBlank() || !bitrate.isNullOrBlank()) {
            Text("Technische Daten", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                audio?.takeIf { it.isNotBlank() }?.let { Chip("Audio: $it") }
                video?.takeIf { it.isNotBlank() }?.let { Chip("Video: $it") }
                bitrate?.takeIf { it.isNotBlank() }?.let { Chip("Bitrate: $it") }
            }
        }

        // Links/IDs
        if (!imdbId.isNullOrBlank() || !tmdbId.isNullOrBlank() || !tmdbUrl.isNullOrBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                imdbId?.takeIf { it.isNotBlank() }?.let { id ->
                    val imdbUrl = if (id.startsWith("tt", ignoreCase = true)) "https://www.imdb.com/title/$id" else "https://www.imdb.com/find?q=$id"
                    Chip("IMDB: $id") { onOpenLink?.invoke(imdbUrl) }
                }
                tmdbId?.takeIf { it.isNotBlank() }?.let { id ->
                    val url = "https://www.themoviedb.org/movie/$id"
                    Chip("TMDB: $id") { onOpenLink?.invoke(url) }
                }
                tmdbUrl?.takeIf { it.isNotBlank() }?.let { url -> Chip("TMDB Link") { onOpenLink?.invoke(url) } }
            }
        }
    }
}

@Composable
private fun Chip(text: String, onClick: (() -> Unit)? = null) {
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val label = MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.AssistChip(
        onClick = onClick ?: {},
        enabled = onClick != null,
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
