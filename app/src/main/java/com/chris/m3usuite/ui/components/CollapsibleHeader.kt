package com.chris.m3usuite.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.launch

/**
 * Einklappbarer Header mit Pfeil. Default in Landscape = eingeklappt (aus Settings).
 */
@Composable
fun CollapsibleHeader(
    store: SettingsStore,
    title: @Composable () -> Unit,
    headerContent: @Composable () -> Unit,
    contentBelow: @Composable (collapsed: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Persisted global state for collapsed header
    val persistedCollapsed by store.headerCollapsed.collectAsStateWithLifecycle(initialValue = false)
    // Default preference for landscape-only initial behavior
    val defaultCollapsedLand by store.headerCollapsedDefaultInLandscape.collectAsStateWithLifecycle(initialValue = true)
    var collapsed by rememberSaveable { mutableStateOf(persistedCollapsed) }

    // Keep local UI state in sync with persisted value
    LaunchedEffect(persistedCollapsed) {
        collapsed = persistedCollapsed
    }

    // One-time initial default in landscape if nothing persisted yet
    LaunchedEffect(Unit) {
        if (landscape && !persistedCollapsed && defaultCollapsedLand) {
            collapsed = true
            store.setHeaderCollapsed(true)
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            title()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                collapsed = !collapsed
                scope.launch { store.setHeaderCollapsed(collapsed) }
            }) {
                val icon = if (collapsed) android.R.drawable.arrow_down_float else android.R.drawable.arrow_up_float
                Icon(painterResource(icon), contentDescription = if (collapsed) "Header anzeigen" else "Header verbergen")
            }
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                headerContent()
                Spacer(Modifier.height(8.dp))
            }
        }

        contentBelow(collapsed)
    }
}
