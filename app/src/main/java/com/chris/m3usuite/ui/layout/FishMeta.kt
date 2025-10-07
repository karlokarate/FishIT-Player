package com.chris.m3usuite.ui.layout

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.chris.m3usuite.model.MediaItem

/** Shared metadata helpers for Fish content composition. */
object FishMeta {
    fun displayVodTitle(media: MediaItem): String? {
        val y = media.year ?: com.chris.m3usuite.domain.selectors.extractYearFrom(media.name)
        return if (y != null) "${media.name} ($y)" else media.name
    }

    fun pickVodPoster(media: MediaItem): Any? = media.poster ?: media.logo ?: media.backdrop

    @Composable
    fun PlotFooter(plot: String) {
        Text(
            text = plot,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    /**
     * Compute resume fraction (0f..1f) for VOD; returns a mutable state updated asynchronously.
     */
    @Composable
    fun rememberVodResumeFraction(media: MediaItem): MutableState<Float?> {
        val state = remember(media.id, media.durationSecs) { mutableStateOf<Float?>(null) }
        val ctx = LocalContext.current
        LaunchedEffect(media.id, media.durationSecs, ctx) {
            val total = media.durationSecs ?: 0
            if (total > 0) {
                val secs = runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.chris.m3usuite.data.repo.ResumeRepository(ctx).getVodResume(media.id)
                    }
                }.getOrNull() ?: 0
                state.value = if (secs > 0) (secs.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
            } else state.value = null
        }
        return state
    }
}
