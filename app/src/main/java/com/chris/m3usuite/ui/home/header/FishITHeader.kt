package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.IconVariant
import com.chris.m3usuite.ui.common.resId
import com.chris.m3usuite.ui.debug.safePainter
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.focusGroup
import com.chris.m3usuite.ui.home.ChromeLibraryFocusRefs
import com.chris.m3usuite.ui.home.ChromeHeaderFocusRefs
import com.chris.m3usuite.ui.home.LocalChromeLibraryFocusRefs
import com.chris.m3usuite.ui.home.LocalChromeHeaderFocusRefs
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

object FishITHeaderHeights {
    val topBar = 56.dp
    val spacer = 2.dp
    val total = topBar + spacer
}

private val HeaderBaseColor = Color(0xFF05080F)

// CompositionLocals provided by the Scaffold
val LocalHeaderFirstFocus: androidx.compose.runtime.ProvidableCompositionLocal<FocusRequester?> = compositionLocalOf { null }
val LocalLibraryFirstFocus: androidx.compose.runtime.ProvidableCompositionLocal<FocusRequester?> = compositionLocalOf { null }
// When true, the header will attach the initial FocusRequester to the Settings button if available,
// even if Search/Profile actions are present. Defaults to false.
val LocalPreferSettingsFirstFocus: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
val LocalChromeOnAction: androidx.compose.runtime.ProvidableCompositionLocal<(() -> Unit)?> = compositionLocalOf { null }

/** Translucent overlay header with app icon + settings gear; alpha controls scrim intensity. */
@Composable
fun FishITHeader(
    title: String,
    onSettings: (() -> Unit)?,
    scrimAlpha: Float, // 0f..1f depending on scroll
    onSearch: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
    librarySelected: String? = null,
    onLibrarySelect: ((String) -> Unit)? = null,
) {
    val firstFocus = LocalHeaderFirstFocus.current
    val libraryPrimaryFocus = LocalLibraryFirstFocus.current
    val onChromeAction = LocalChromeOnAction.current
    val preferSettingsFirst = LocalPreferSettingsFirstFocus.current
    val headerRefs = LocalChromeHeaderFocusRefs.current
        ?: remember { ChromeHeaderFocusRefs(FocusRequester(), FocusRequester(), FocusRequester(), FocusRequester()) }
    val libraryRefs = LocalChromeLibraryFocusRefs.current
    val scrim = scrimAlpha.coerceIn(0f, 1f)
    val attachToSettings = firstFocus != null && onSettings != null && preferSettingsFirst
    val attachToSearch = firstFocus != null && onSearch != null && !attachToSettings
    val attachToProfiles = firstFocus != null && onProfiles != null && !attachToSettings && !attachToSearch
    val attachToLogo = firstFocus != null && onLogo != null && !attachToSettings && !attachToSearch && !attachToProfiles
    val logoRequester = if (attachToLogo) firstFocus!! else headerRefs.logo
    val searchRequester = if (attachToSearch) firstFocus!! else headerRefs.search
    val profileRequester = if (attachToProfiles) firstFocus!! else headerRefs.profile
    val settingsRequester = if (attachToSettings) firstFocus!! else headerRefs.settings

    val showLibraryNav = librarySelected != null && onLibrarySelect != null
    val libraryRefsResolved = libraryRefs ?: remember { ChromeLibraryFocusRefs(FocusRequester(), FocusRequester(), FocusRequester()) }
    val libraryLiveRequester = if (showLibraryNav && librarySelected == "live" && libraryPrimaryFocus != null) libraryPrimaryFocus else libraryRefsResolved.live
    val libraryVodRequester = if (showLibraryNav && librarySelected == "vod" && libraryPrimaryFocus != null) libraryPrimaryFocus else libraryRefsResolved.vod
    val librarySeriesRequester = if (showLibraryNav && librarySelected == "series" && libraryPrimaryFocus != null) libraryPrimaryFocus else libraryRefsResolved.series
    val firstActionRequester = when {
        onSearch != null -> searchRequester
        onProfiles != null -> profileRequester
        onSettings != null -> settingsRequester
        else -> null
    }
    val firstRightOfLogo = if (showLibraryNav) libraryLiveRequester else firstActionRequester

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to HeaderBaseColor,
                        0.45f to HeaderBaseColor.copy(alpha = (0.78f + scrim * 0.18f).coerceIn(0f, 1f)),
                        1f to HeaderBaseColor.copy(alpha = 0f)
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier
                .height(FishITHeaderHeights.topBar)
                .fillMaxWidth()
                .then(if (FocusKit.isTvDevice(LocalContext.current)) Modifier.focusGroup() else Modifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val logoModifier = Modifier
                    .padding(vertical = 8.dp)
                    .then(
                        FocusKit.run {
                            Modifier.focusNeighbors(
                                right = firstRightOfLogo
                            )
                        }
                    )
                if (onLogo != null) {
                    androidx.compose.foundation.layout.Box(
                        modifier = logoModifier
                            .semantics { this.contentDescription = title }
                            .then(
                                FocusKit.run {
                                    Modifier.tvClickable(
                                        onClick = { onChromeAction?.invoke(); onLogo() },
                                        focusRequester = logoRequester,
                                        focusColors = FocusKit.FocusDefaults.IconColors,
                                        focusBorderWidth = 2.2.dp,
                                        debugTag = "Logo"
                                    )
                                }
                            )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = safePainter(com.chris.m3usuite.R.drawable.fisch_header, label = "FishITHeader"),
                            contentDescription = title,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    androidx.compose.foundation.Image(
                        painter = safePainter(com.chris.m3usuite.R.drawable.fisch_header, label = "FishITHeader"),
                        contentDescription = title,
                        modifier = logoModifier
                    )
                }

                if (showLibraryNav) {
                    val selectLibrary = onLibrarySelect!!
                    val selectedKey = librarySelected!!
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val liveIcon = AppIcon.LiveTv.resId(if (selectedKey == "live") IconVariant.Primary else IconVariant.Duotone)
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .semantics { this.contentDescription = "TV" }
                                .then(
                                    FocusKit.run {
                                        Modifier
                                            .tvClickable(
                                                onClick = { onChromeAction?.invoke(); selectLibrary("live") },
                                                focusRequester = libraryLiveRequester,
                                                focusColors = FocusKit.FocusDefaults.IconColors,
                                                focusBorderWidth = 2.2.dp,
                                                debugTag = "Library/Live"
                                            )
                                            .focusNeighbors(
                                                left = logoRequester,
                                                right = libraryVodRequester
                                            )
                                    }
                                )
                        ) {
                            androidx.compose.foundation.Image(
                                painter = safePainter(liveIcon, label = "Header/LibraryLive"),
                                contentDescription = "TV",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        val vodIcon = AppIcon.MovieVod.resId(if (selectedKey == "vod") IconVariant.Primary else IconVariant.Duotone)
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .semantics { this.contentDescription = "Filme" }
                                .then(
                                    FocusKit.run {
                                        Modifier
                                            .tvClickable(
                                                onClick = { onChromeAction?.invoke(); selectLibrary("vod") },
                                                focusRequester = libraryVodRequester,
                                                focusColors = FocusKit.FocusDefaults.IconColors,
                                                focusBorderWidth = 2.2.dp,
                                                debugTag = "Library/Vod"
                                            )
                                            .focusNeighbors(
                                                left = libraryLiveRequester,
                                                right = librarySeriesRequester
                                            )
                                    }
                                )
                        ) {
                            androidx.compose.foundation.Image(
                                painter = safePainter(vodIcon, label = "Header/LibraryVod"),
                                contentDescription = "Filme",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        val seriesIcon = AppIcon.Series.resId(if (selectedKey == "series") IconVariant.Primary else IconVariant.Duotone)
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .semantics { this.contentDescription = "Serien" }
                                .then(
                                    FocusKit.run {
                                        Modifier
                                            .tvClickable(
                                                onClick = { onChromeAction?.invoke(); selectLibrary("series") },
                                                focusRequester = librarySeriesRequester,
                                                focusColors = FocusKit.FocusDefaults.IconColors,
                                                focusBorderWidth = 2.2.dp,
                                                debugTag = "Library/Series"
                                            )
                                            .focusNeighbors(
                                                left = libraryVodRequester,
                                                right = firstActionRequester
                                            )
                                    }
                                )
                        ) {
                            androidx.compose.foundation.Image(
                                painter = safePainter(seriesIcon, label = "Header/LibrarySeries"),
                                contentDescription = "Serien",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSearch != null) {
                    val searchRight = when {
                        onProfiles != null -> profileRequester
                        onSettings != null -> settingsRequester
                        else -> null
                    }
                    val searchIcon = AppIcon.Search.resId()
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .semantics { this.contentDescription = "Suche öffnen" }
                            .then(
                                FocusKit.run {
                                    Modifier
                                        .tvClickable(
                                            onClick = { onChromeAction?.invoke(); onSearch() },
                                            focusRequester = searchRequester,
                                            focusColors = FocusKit.FocusDefaults.IconColors,
                                            focusBorderWidth = 2.2.dp,
                                            debugTag = "Search"
                                        )
                                        .focusNeighbors(
                                            left = if (showLibraryNav) librarySeriesRequester else logoRequester,
                                            right = searchRight
                                        )
                                }
                            )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = safePainter(searchIcon, label = "Header/Search"),
                            contentDescription = "Suche öffnen",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (onProfiles != null) {
                    val profileLeft = when {
                        onSearch != null -> searchRequester
                        showLibraryNav -> librarySeriesRequester
                        else -> logoRequester
                    }
                    val profileRight = if (onSettings != null) settingsRequester else null
                    val profileIcon = AppIcon.Profile.resId()
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .semantics { this.contentDescription = "Profile" }
                            .then(
                                FocusKit.run {
                                    Modifier
                                        .tvClickable(
                                            onClick = { onChromeAction?.invoke(); onProfiles() },
                                            focusRequester = profileRequester,
                                            focusColors = FocusKit.FocusDefaults.IconColors,
                                            focusBorderWidth = 2.2.dp,
                                            debugTag = "Profile"
                                        )
                                        .focusNeighbors(
                                            left = profileLeft,
                                            right = profileRight
                                        )
                                }
                            )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = safePainter(profileIcon, label = "Header/Profile"),
                            contentDescription = "Profile",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (onSettings != null) {
                    val settingsLeft = when {
                        onProfiles != null -> profileRequester
                        onSearch != null -> searchRequester
                        showLibraryNav -> librarySeriesRequester
                        else -> logoRequester
                    }
                    val settingsIcon = AppIcon.Settings.resId(IconVariant.Primary)
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .semantics { this.contentDescription = "Einstellungen" }
                            .then(
                                FocusKit.run {
                                    Modifier
                                        .tvClickable(
                                            onClick = { onChromeAction?.invoke(); onSettings() },
                                            focusRequester = settingsRequester,
                                            focusColors = FocusKit.FocusDefaults.IconColors,
                                            focusBorderWidth = 2.2.dp,
                                            debugTag = "Settings"
                                        )
                                        .focusNeighbors(
                                            left = settingsLeft
                                        )
                                }
                            )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = safePainter(settingsIcon, label = "Header/Settings"),
                            contentDescription = "Einstellungen",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(FishITHeaderHeights.spacer))
    }
}
