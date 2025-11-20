package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.actions.MediaAction

/**
 * Unified detail page mask used by Series/VOD/Live.
 * Controls the full layering (backdrop → gradients → accent card → header → sections),
 * the order of sections, and shared visuals. Optional sections are omitted when data is absent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailPage(
    isAdult: Boolean,
    pads: PaddingValues,
    listState: LazyListState,
    // Header
    title: String,
    heroUrl: Any?,
    posterUrl: Any?,
    actions: List<MediaAction>,
    meta: DetailMeta?,
    headerExtras: @Composable () -> Unit = {},
    showHeaderMetaChips: Boolean = false,
    // Sections (Resume/Plot)
    resumeText: String? = null,
    plot: String? = null,
    // Facts (full DetailFacts parity)
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
    director: String? = null,
    cast: String? = null,
    releaseDate: String? = null,
    imdbId: String? = null,
    tmdbId: String? = null,
    tmdbUrl: String? = null,
    audio: String? = null,
    video: String? = null,
    bitrate: String? = null,
    onOpenLink: ((String) -> Unit)? = null,
    // Trailer
    trailerUrl: String? = null,
    trailerHeaders: Map<String, String>? = null,
    // Optional type-specific items (episodes, similar rows, etc.)
    extraItems: (LazyListScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DetailBackdrop(heroUrl = heroUrl, isAdult = isAdult, pads = pads) {
        LazyColumn(
            modifier = modifier,
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                DetailHeader(
                    title = title,
                    subtitle = null,
                    heroUrl = heroUrl,
                    posterUrl = posterUrl,
                    actions = actions,
                    meta = if (showHeaderMetaChips) meta else null,
                    showHeroScrim = false,
                    headerExtras = { headerExtras() },
                )
            }
            if (!resumeText.isNullOrBlank()) item { androidx.compose.material3.Text(resumeText) }
            if (!plot.isNullOrBlank()) {
                item {
                    // Match Series: Rounded surface with darker accent color and badge alpha
                    val accent = if (!isAdult) com.chris.m3usuite.ui.theme.DesignTokens.KidAccent else com.chris.m3usuite.ui.theme.DesignTokens.Accent
                    val badgeColorDarker = if (!isAdult) accent.copy(alpha = 0.32f) else accent.copy(alpha = 0.26f)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeColorDarker,
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                    ) {
                        androidx.compose.material3.Text(plot!!, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            item {
                // Use shared DetailFacts to mirror Series styling and behavior
                DetailFacts(
                    year = year,
                    durationSecs = durationSecs,
                    containerExt = containerExt,
                    rating = rating,
                    mpaaRating = mpaaRating,
                    age = age,
                    provider = provider,
                    category = category,
                    genres = genres,
                    countries = countries,
                    director = director,
                    cast = cast,
                    releaseDate = releaseDate,
                    imdbId = imdbId,
                    tmdbId = tmdbId,
                    tmdbUrl = tmdbUrl,
                    audio = audio,
                    video = video,
                    bitrate = bitrate,
                    onOpenLink = onOpenLink,
                )
            }
            if (!trailerUrl.isNullOrBlank()) {
                item {
                    // Keep simple placeholder; screens can pass a richer extraItems slot for embedded TrailerBox if desired
                    androidx.compose.material3.Text("Trailer: $trailerUrl")
                }
            }
            extraItems?.invoke(this)
            item {
                androidx.compose.material3.Text(
                    "Tippe auf Poster oder Titel, um abzuspielen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
