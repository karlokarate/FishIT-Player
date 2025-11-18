package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.prefs.SettingsStore

// --- Bestehende Settings-VMs ---
import com.chris.m3usuite.ui.screens.PlayerSettingsViewModel
import com.chris.m3usuite.ui.screens.NetworkSettingsViewModel
import com.chris.m3usuite.ui.screens.XtreamSettingsViewModel
import com.chris.m3usuite.ui.screens.EpgSettingsViewModel
import com.chris.m3usuite.ui.screens.GeneralSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: SettingsStore? = null,
    onBack: (() -> Unit)? = null,
    onOpenProfiles: (() -> Unit)? = null,
    onOpenGate: (() -> Unit)? = null,
    onOpenXtreamCfCheck: (() -> Unit)? = null,
    onGlobalSearch: (() -> Unit)? = null,
    app: Application = LocalContext.current.applicationContext as Application,
    onOpenPortalCheck: (() -> Unit)? = null,             // navigiert zu XtreamPortalCheckScreen
) {
    // --- ViewModels (bestehend) ---
    val playerVm: PlayerSettingsViewModel = viewModel(factory = PlayerSettingsViewModel.factory(app))
    val networkVm: NetworkSettingsViewModel = viewModel(factory = NetworkSettingsViewModel.factory(app))
    val xtreamVm: XtreamSettingsViewModel = viewModel(factory = XtreamSettingsViewModel.factory(app))
    val epgVm: EpgSettingsViewModel = viewModel(factory = EpgSettingsViewModel.factory(app))
    val generalVm: GeneralSettingsViewModel = viewModel(factory = GeneralSettingsViewModel.factory(app))

    // --- States (bestehend) ---
    val playerState by playerVm.state.collectAsStateWithLifecycle()
    val networkState by networkVm.state.collectAsStateWithLifecycle()
    val xtreamState by xtreamVm.state.collectAsStateWithLifecycle()
    val epgState by epgVm.state.collectAsStateWithLifecycle()
    val generalState by generalVm.state.collectAsStateWithLifecycle()

    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = if (onBack != null) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                } else {
                    {}
                },
                actions = {
                    onOpenProfiles?.let { handler ->
                        TextButton(onClick = handler) { Text("Profile") }
                    }
                    onGlobalSearch?.let { handler ->
                        TextButton(onClick = handler) { Text("Suche") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // --- Network ---
            SettingsCard(title = "Netzwerk (M3U / EPG / Header)") {
                OutlinedTextField(
                    value = networkState.m3uUrl, onValueChange = { networkVm.onChange(m3u = it) },
                    label = { Text("M3U URL") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = networkState.epgUrl, onValueChange = { networkVm.onChange(epg = it) },
                    label = { Text("EPG URL") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = networkState.userAgent, onValueChange = { networkVm.onChange(ua = it) },
                    label = { Text("User-Agent") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = networkState.referer, onValueChange = { networkVm.onChange(ref = it) },
                    label = { Text("Referer") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = networkState.extraHeadersJson,
                    onValueChange = { networkVm.onChange(headersJson = it) },
                    label = { Text("Zusätzliche Header (JSON)") },
                    supportingText = { Text("Beispiel: {\"X-Auth\":\"abc\",\"X-Token\":\"123\"}") },
                    modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 5
                )
            }

            // --- Xtream ---
            SettingsCard(title = "Xtream") {
                OutlinedTextField(
                    value = xtreamState.host, onValueChange = { xtreamVm.onChange(host = it) },
                    label = { Text("Host") }, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = xtreamState.port.toString(),
                        onValueChange = { it.toIntOrNull()?.let { p -> xtreamVm.onChange(port = p) } },
                        label = { Text("Port") }, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = xtreamState.output, onValueChange = { xtreamVm.onChange(output = it) },
                        label = { Text("Output (z.B. m3u8)") }, modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = xtreamState.user, onValueChange = { xtreamVm.onChange(user = it) },
                    label = { Text("User") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = xtreamState.pass, onValueChange = { xtreamVm.onChange(pass = it) },
                    label = { Text("Passwort") }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { xtreamVm.onOpenPortalCookieBridge { onOpenPortalCheck?.invoke() } }) {
                        Text("Portal-Cookie-Bridge öffnen")
                    }
                    Button(
                        onClick = { xtreamVm.onTriggerDeltaImport(includeLive = false) },
                        enabled = !xtreamState.isImportInFlight
                    ) { Text(if (xtreamState.isImportInFlight) "Import läuft…" else "Delta-Import starten") }
                }
            }

            // --- Player ---
            SettingsCard(title = "Player") {
                // Modus
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("ask" to "Fragen", "internal" to "Intern", "external" to "Extern").forEach { (value, label) ->
                        FilterChip(
                            selected = playerState.mode == value,
                            onClick = { playerVm.onChangeMode(value) },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = playerState.preferredPkg,
                    onValueChange = { playerVm.onChangePreferredPkg(it) },
                    label = { Text("Externes Paket (Package-Name)") },
                    modifier = Modifier.fillMaxWidth()
                )
                // Untertitel
                Spacer(Modifier.height(8.dp))
                Text("Untertitel-Stil")
                Slider(
                    value = playerState.subScale, onValueChange = { playerVm.onChangeSubtitle(scale = it) },
                    valueRange = 0.04f..0.12f
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = "0x${playerState.subFgArgb.toUInt().toString(16).uppercase()}",
                        onValueChange = { runCatching { it.removePrefix("0x").toUInt(16).toInt() }
                            .onSuccess { v -> playerVm.onChangeSubtitle(fgArgb = v) } },
                        label = { Text("FG (ARGB)") }, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = "0x${playerState.subBgArgb.toUInt().toString(16).uppercase()}",
                        onValueChange = { runCatching { it.removePrefix("0x").toUInt(16).toInt() }
                            .onSuccess { v -> playerVm.onChangeSubtitle(bgArgb = v) } },
                        label = { Text("BG (ARGB)") }, modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = playerState.subFgOpacityPct.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> playerVm.onChangeSubtitle(fgPct = v) } },
                        label = { Text("FG-Deckkraft (%)") }, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = playerState.subBgOpacityPct.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> playerVm.onChangeSubtitle(bgPct = v) } },
                        label = { Text("BG-Deckkraft (%)") }, modifier = Modifier.weight(1f)
                    )
                }
                // Toggles
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = playerState.rotationLocked, onClick = { playerVm.onToggleRotation(!playerState.rotationLocked) },
                        label = { Text(if (playerState.rotationLocked) "Rotation gesperrt" else "Rotation frei") }
                    )
                    FilterChip(
                        selected = playerState.autoplayNext, onClick = { playerVm.onToggleAutoplay(!playerState.autoplayNext) },
                        label = { Text(if (playerState.autoplayNext) "Autoplay an" else "Autoplay aus") }
                    )
                }
            }

            // --- EPG ---
            SettingsCard(title = "EPG (Favoriten)") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = epgState.useXtreamForFavorites,
                        onClick = { epgVm.onToggleUseXtream(!epgState.useXtreamForFavorites) },
                        label = { Text(if (epgState.useXtreamForFavorites) "Xtream bevorzugen" else "Xtream aus") }
                    )
                    FilterChip(
                        selected = epgState.skipXmltvIfXtreamOk,
                        onClick = { epgVm.onToggleSkipXmltv(!epgState.skipXmltvIfXtreamOk) },
                        label = { Text("XMLTV überspringen, wenn Xtream OK") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { epgVm.onRefreshFavoritesNow() },
                    enabled = !epgState.isRefreshing
                ) { Text(if (epgState.isRefreshing) "Aktualisiere …" else "Favoriten-EPG jetzt aktualisieren") }
            }

            // --- Allgemein ---
            SettingsCard(title = "Allgemein") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Kategorie 'For Adults' anzeigen",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = generalState.showAdults,
                        onCheckedChange = { value -> generalVm.onToggleShowAdults(value) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
