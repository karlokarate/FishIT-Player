package com.chris.m3usuite.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.chris.m3usuite.model.Episode
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.common.AccentCard
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.ui.util.AppAsyncImage
import com.chris.m3usuite.ui.util.AppHeroImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.TvFocusColors

@Composable
private fun parseTags(raw: String?): List<String> = raw
    ?.split(',', ';', '|', '/')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    ?: emptyList()

@Composable
private fun normalizeTrailerUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val lower = trimmed.lowercase()
    return when {
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed) -> trimmed
        lower.startsWith("//") -> "https:$trimmed"
        lower.startsWith("www.") -> "https://$trimmed"
        Regex("^[A-Za-z0-9_-]{6,}$").matches(trimmed) -> "https://www.youtube.com/watch?v=$trimmed"
        else -> trimmed
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesDetailMask(
    isAdult: Boolean,
    pads: PaddingValues,
    listState: LazyListState,
    store: SettingsStore,
    backdrop: String?,
    poster: String?,
    cover: String?,
    title: String,
    plot: String?,
    year: Int?,
    rating: Double?,
    genre: String?,
    providerLabel: String?,
    categoryLabel: String?,
    country: String?,
    releaseDate: String?,
    imdbId: String?,
    tmdbId: String?,
    trailer: String?,
    seriesStreamId: Int?,
    seasons: List<Int>,
    seasonSel: Int?,
    onSelectSeason: (Int) -> Unit,
    episodes: List<Episode>,
    resumeLookup: suspend (Episode) -> Int?,
    onPlayEpisode: (Episode, Boolean, Int?) -> Unit,
    onOpenLink: (String) -> Unit,
    onGrant: () -> Unit = {},
    onRevoke: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize()) {
        val accent = if (!isAdult) DesignTokens.KidAccent else DesignTokens.Accent
        val heroUrl = backdrop ?: poster
        // Full-screen hero
        heroUrl?.let { url ->
            AppHeroImage(
                url = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = HERO_SCRIM_IMAGE_ALPHA),
                crossfade = true
            )
        }
        val badgeColor = if (!isAdult) accent.copy(alpha = 0.26f) else accent.copy(alpha = 0.20f)
        val badgeColorDarker = if (!isAdult) accent.copy(alpha = 0.32f) else accent.copy(alpha = 0.26f)

        // Foreground content area
        Box(Modifier.fillMaxSize().padding(pads)) {
            // Readability gradients
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        )
                    )
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (!isAdult) 0.20f else 0.12f),
                                Color.Transparent
                            ),
                            radius = with(LocalDensity.current) { 680.dp.toPx() }
                        )
                    )
            )
            AccentCard(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                accent = accent
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        val actions = buildList<com.chris.m3usuite.ui.actions.MediaAction> {
                            val firstEp = episodes.firstOrNull()
                            if (firstEp != null) add(
                                com.chris.m3usuite.ui.actions.MediaAction(
                                    id = com.chris.m3usuite.ui.actions.MediaActionId.Play,
                                    label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_play),
                                    primary = true,
                                    onClick = { onPlayEpisode(firstEp, true, null) }
                                )
                            )
                            val tr = normalizeTrailerUrl(trailer)
                            if (!tr.isNullOrBlank()) add(
                                com.chris.m3usuite.ui.actions.MediaAction(
                                    id = com.chris.m3usuite.ui.actions.MediaActionId.Trailer,
                                    label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_trailer),
                                    onClick = { runCatching { onOpenLink(tr) } }
                                )
                            )
                        }
                        val meta = DetailMeta(
                            year = year,
                            genres = parseTags(genre),
                            provider = providerLabel,
                            category = categoryLabel
                        )
                        DetailHeader(
                            title = title,
                            subtitle = null,
                            heroUrl = backdrop ?: poster,
                            posterUrl = cover ?: poster ?: backdrop,
                            actions = actions,
                            meta = meta,
                            showHeroScrim = false,
                            headerExtras = { DetailHeaderExtras() }
                        )
                        // Whitelist actions (permissions: adults only)
                        val ctxLocal = androidx.compose.ui.platform.LocalContext.current
                        val permRepo = remember(ctxLocal) { com.chris.m3usuite.data.repo.PermissionRepository(ctxLocal, store) }
                        var canEditWhitelist by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { canEditWhitelist = permRepo.current().canEditWhitelist }
                        if (canEditWhitelist) {
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                                com.chris.m3usuite.ui.common.AppIconButton(
                                    icon = com.chris.m3usuite.ui.common.AppIcon.AddKid,
                                    variant = com.chris.m3usuite.ui.common.IconVariant.Solid,
                                    contentDescription = "Für Kinder freigeben",
                                    onClick = { onGrant() }
                                )
                                Spacer(Modifier.width(8.dp))
                                com.chris.m3usuite.ui.common.AppIconButton(
                                    icon = com.chris.m3usuite.ui.common.AppIcon.RemoveKid,
                                    variant = com.chris.m3usuite.ui.common.IconVariant.Solid,
                                    contentDescription = "Freigabe entfernen",
                                    onClick = { onRevoke() }
                                )
                            }
                        }
                    }
                    if (!plot.isNullOrBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = badgeColorDarker,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                            ) {
                                Text(plot!!, modifier = Modifier.padding(12.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    item {
                        DetailFacts(
                            modifier = Modifier.fillMaxWidth(),
                            year = year,
                            durationSecs = null,
                            containerExt = null,
                            rating = rating,
                            mpaaRating = null,
                            age = null,
                            provider = providerLabel,
                            category = categoryLabel,
                            genres = parseTags(genre),
                            countries = parseTags(country).ifEmpty { country?.let { listOf(it) } ?: emptyList() },
                            director = null,
                            cast = null,
                            releaseDate = releaseDate,
                            imdbId = imdbId,
                            tmdbId = tmdbId,
                            tmdbUrl = null,
                            audio = null,
                            video = null,
                            bitrate = null,
                            onOpenLink = onOpenLink
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (seasons.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = badgeColor,
                                contentColor = Color.White,
                                modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                            ) {
                                Text(
                                    "Staffeln",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            com.chris.m3usuite.ui.tv.TvFocusRow(
                                stateKey = "series:seasons:${seriesStreamId ?: -1}",
                                itemSpacing = 8.dp,
                                contentPadding = PaddingValues(end = 8.dp),
                                itemCount = seasons.size,
                                itemKey = { idx -> seasons[idx] }
                            ) { idx ->
                                val s = seasons[idx]
                                val chipIsrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                        // 1) Mark item focusable for TvFocusRow (brings into view, saves index)
                                        .then(com.chris.m3usuite.ui.skin.run {
                                            Modifier.tvFocusableItem(
                                                stateKey = "series:seasons:${seriesStreamId ?: -1}",
                                                index = idx
                                            )
                                        })
                                        // 2) Apply TV glow/scale exactly like TvButton
                                        .focusScaleOnTv(
                                            shape = RoundedCornerShape(50.dp),
                                            focusColors = TvFocusColors.Default,
                                            interactionSource = chipIsrc
                                        )
                                        // 3) Make wrapper clickable without extra scaling/halo
                                        .then(com.chris.m3usuite.ui.skin.run {
                                            Modifier.tvClickable(
                                                scaleFocused = 1f,
                                                scalePressed = 1f,
                                                focusBorderWidth = 0.dp,
                                                brightenContent = false,
                                                onClick = { onSelectSeason(s) }
                                            )
                                        })
                                        .onFocusEvent { st ->
                                            if (st.isFocused || st.hasFocus) {
                                                com.chris.m3usuite.core.debug.GlobalDebug.logFocusWidget(
                                                    component = "Chip",
                                                    module = com.chris.m3usuite.metrics.RouteTag.current,
                                                    tag = "season-$s"
                                                )
                                            }
                                        }
                                ) {
                                    FilterChip(
                                        selected = seasonSel == s,
                                        onClick = { onSelectSeason(s) },
                                        label = { Text("S$s") },
                                        interactionSource = chipIsrc,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = badgeColor,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    itemsIndexed(
                        items = episodes,
                        key = { index, item -> "${item.seriesStreamId}-${item.season}-${item.episodeNum}" }
                    ) { index, e ->
                        val compositeKey = "${e.seriesStreamId}-${e.season}-${e.episodeNum}"
                        var resumeSecs by remember(compositeKey) { mutableStateOf<Int?>(null) }
                        LaunchedEffect(compositeKey) { resumeSecs = withContext(Dispatchers.IO) { resumeLookup(e) } }

                        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            val thumbSize = 48.dp
                            val shape = RoundedCornerShape(28.dp)
                            var epFocused by remember { mutableStateOf(false) }

                            Box(Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (epFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                                        .clip(shape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                                        .focusScaleOnTv(
                                            shape = shape,
                                            focusColors = TvFocusColors.Default
                                        )
                                        .then(com.chris.m3usuite.ui.skin.run {
                                            Modifier.tvClickable {
                                                if (resumeSecs != null) onPlayEpisode(e, false, resumeSecs)
                                                else onPlayEpisode(e, true, null)
                                            }
                                        })
                                        .onFocusEvent { ev -> epFocused = ev.isFocused }
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.06f)) {
                                            Box(Modifier.size(thumbSize)) {
                                                var loaded by remember { mutableStateOf(false) }
                                                val alpha by animateFloatAsState(if (loaded) 1f else 0f, animationSpec = tween(260), label = "thumbFade")
                                                if (!loaded)
                                                    com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.fillMaxSize(), cornerRadius = 12.dp)
                                                AppAsyncImage(
                                                    url = e.poster,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                                                    crossfade = false,
                                                    onLoading = { loaded = false },
                                                    onSuccess = { loaded = true },
                                                    onError = { loaded = true }
                                                )
                                                if ((e.durationSecs ?: 0) > 0) {
                                                    val secs = e.durationSecs ?: 0
                                                    val h = secs / 3600
                                                    val m = (secs % 3600) / 60
                                                    val text = if (h > 0) String.format("%dh %02dm", h, m) else String.format("%dm", m)
                                                    Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                                                        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                }
                                            }
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            val titleLine = buildString { append("S"); append(e.season); append("E"); append(e.episodeNum); val nm = e.title; if (!nm.isNullOrBlank()) { append(" · "); append(nm) } }
                                            Text(titleLine, style = MaterialTheme.typography.titleSmall)
                                            val sub = e.plot?.takeIf { it.isNotBlank() } ?: e.title ?: ""
                                            if (sub.isNotBlank()) Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
