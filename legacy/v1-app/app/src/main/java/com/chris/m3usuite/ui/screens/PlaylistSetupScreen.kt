package com.chris.m3usuite.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.backup.QuickImportRow
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.core.xtream.XtreamDetect
import com.chris.m3usuite.core.xtream.XtreamSeeder
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.common.AccentCard
import com.chris.m3usuite.ui.common.TvButton
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import com.chris.m3usuite.ui.fx.FishBackground
import com.chris.m3usuite.ui.layout.FishFormButtonRow
import com.chris.m3usuite.ui.layout.FishFormSection
import com.chris.m3usuite.ui.layout.FishFormSelect
import com.chris.m3usuite.ui.layout.FishFormSwitch
import com.chris.m3usuite.ui.layout.FishFormTextField
import com.chris.m3usuite.ui.layout.TvKeyboard
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.work.SchedulingGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// Setup mode for first-run screen (top-level enum; local enums are not allowed)
enum class SetupMode { M3U, XTREAM }

/**
 * Optimized, single-file Compose screen for initial playlist setup.
 * - Robust value prefill via SettingsStore.snapshot()
 * - Strict input validation and normalization (host schema/port)
 * - Derived submit enablement (cheap recompositions)
 * - UX: password hiding, numeric port keyboard, IME actions
 * - No unused animations / allocations during recomposition
 * - Clean error surface (snackbar) + inline helper text
 * - Port/output fallback now marks verified flags on success
 */
@Composable
fun PlaylistSetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        com.chris.m3usuite.metrics.RouteTag
            .set("setup")
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTree("setup:root")
    }
    val focusManager = LocalFocusManager.current

    // Deep-Link (VIEW-Intent) als Initialwert
    val initialLink by remember {
        mutableStateOf((ctx as? Activity)?.intent?.dataString.orEmpty())
    }

    // Mode: M3U vs Xtream
    var mode by rememberSaveable { mutableStateOf(SetupMode.M3U) }

    // Form fields
    var m3u by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialLink)) }
    var epg by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var ua by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("IBOPlayer/1.4 (Android)")) }
    var ref by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    // Xtream fields (for XTREAM mode)
    var xtHost by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var xtPort by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("80")) }
    var xtHttps by rememberSaveable { mutableStateOf(false) }
    var xtUser by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var xtPass by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var xtOut by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("m3u8")) }

    // UI state
    var busy by rememberSaveable { mutableStateOf(false) }
    var inlineMsg by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    // Gespeicherte Werte beim Öffnen vorbefüllen (nutzt Snapshot aus SettingsStore)
    LaunchedEffect(Unit) {
        runCatching {
            val snap = store.snapshot()
            if (snap.m3uUrl.isNotBlank()) {
                m3u = TextFieldValue(snap.m3uUrl)
            } else if (initialLink.isNotBlank()) {
                m3u =
                    TextFieldValue(initialLink)
            }
            epg = TextFieldValue(snap.epgUrl)
            ua = TextFieldValue(snap.userAgent.ifBlank { "IBOPlayer/1.4 (Android)" })
            ref = TextFieldValue(snap.referer)
            // Xtream creds (if present)
            if (snap.xtHost.isNotBlank()) xtHost = TextFieldValue(snap.xtHost)
            xtPort = TextFieldValue(snap.xtPort.toString())
            xtHttps = snap.xtPort == 443
            if (snap.xtUser.isNotBlank()) xtUser = TextFieldValue(snap.xtUser)
            if (snap.xtPass.isNotBlank()) xtPass = TextFieldValue(snap.xtPass)
            if (snap.xtOutput.isNotBlank()) xtOut = TextFieldValue(snap.xtOutput)
        }.onFailure {
            inlineMsg = "Konnte gespeicherte Einstellungen nicht laden: ${it.message.orEmpty()}"
        }
    }

    // -------- Validation / Normalization --------
    val isM3uOk by remember(m3u) {
        derivedStateOf {
            val t = m3u.text.trim()
            val u = t.toHttpUrlOrNull()
            u != null && (u.scheme == "http" || u.scheme == "https")
        }
    }
    val isXtHostValid by remember(xtHost) {
        derivedStateOf { sanitizeHost(xtHost.text).first.isNotBlank() }
    }
    val isXtPortValid by remember(xtPort, xtHttps) {
        derivedStateOf {
            val p = xtPort.text.trim().toIntOrNull() ?: return@derivedStateOf false
            p in 1..65535 && (!(xtHttps) || p != 80)
        }
    }
    val isXtCredsOk by remember(xtUser, xtPass) {
        derivedStateOf { xtUser.text.isNotBlank() && xtPass.text.isNotBlank() }
    }
    val isXtOutValid by remember(xtOut) {
        derivedStateOf { normalizeOutput(xtOut.text).isNotBlank() }
    }

    val canSubmit by remember(mode, isM3uOk, isXtHostValid, isXtPortValid, isXtCredsOk, isXtOutValid, busy) {
        derivedStateOf {
            !busy &&
                when (mode) {
                    SetupMode.M3U -> isM3uOk
                    SetupMode.XTREAM -> isXtHostValid && isXtPortValid && isXtCredsOk && isXtOutValid
                }
        }
    }

    // Hintergrund-Brushes: einmalig berechnen (read composition locals outside remember)
    val cs = colorScheme
    val density = LocalDensity.current
    val bgV =
        remember(cs) {
            Brush.verticalGradient(
                0f to cs.background,
                1f to cs.surface,
            )
        }
    val bgR =
        remember(density) {
            val radius = with(density) { 640.dp.toPx() }
            Brush.radialGradient(
                colors = listOf(DesignTokens.Accent.copy(alpha = 0.12f), Color.Transparent),
                radius = radius,
            )
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Subtle background
            Box(Modifier.matchParentSize().background(bgV))
            Box(Modifier.matchParentSize().background(bgR))
            FishBackground(
                modifier = Modifier.align(Alignment.Center).size(520.dp),
                alpha = 0.05f,
                neutralizeUnderlay = true,
            )

            AccentCard(modifier = Modifier.padding(16.dp)) {
                Text("Setup", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                // Quick import (Drive/File) so users can pull settings before entering URLs
                QuickImportRow()
                Spacer(Modifier.height(8.dp))

                if (BuildConfig.TV_FORMS_V1) {
                    // TV Form Kit v1
                    FishFormSection(title = "Modus") {
                        FishFormSelect(
                            label = "Eingabe",
                            options = listOf(SetupMode.M3U, SetupMode.XTREAM),
                            selected = mode,
                            onSelected = { mode = it },
                            optionLabel = { if (it == SetupMode.M3U) "M3U Link" else "Xtream Login" },
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    if (mode == SetupMode.M3U) {
                        FishFormSection(title = "M3U Quelle") {
                            FishFormTextField(
                                label = "M3U / Xtream get.php Link",
                                value = m3u.text,
                                onValueChange = { m3u = TextFieldValue(it) },
                                placeholder = "https://…",
                                errorText = if (m3u.text.isNotBlank() && !isM3uOk) "Bitte gültige http/https URL eingeben" else null,
                                keyboard = TvKeyboard.Uri,
                            )
                            FishFormTextField(
                                label = "EPG XMLTV URL (optional)",
                                value = epg.text,
                                onValueChange = { epg = TextFieldValue(it) },
                                placeholder = "https://…",
                                keyboard = TvKeyboard.Uri,
                            )
                        }
                    } else {
                        FishFormSection(title = "Xtream Zugang") {
                            FishFormTextField(
                                label = "Xtream Host (ohne Schema)",
                                value = xtHost.text,
                                onValueChange = { raw ->
                                    val (hostNoScheme, httpsSuggested, explicitPort) = sanitizeHost(raw)
                                    if (httpsSuggested != xtHttps) xtHttps = httpsSuggested
                                    if (explicitPort != null &&
                                        xtPort.text != explicitPort.toString()
                                    ) {
                                        xtPort = TextFieldValue(explicitPort.toString())
                                    }
                                    xtHost = TextFieldValue(hostNoScheme)
                                },
                                helperText = "Nur Hostname oder Host:Port (ohne http/https)",
                                errorText =
                                    if (xtHost.text.isNotBlank() &&
                                        !isXtHostValid
                                    ) {
                                        "Ungültig: nur Hostname oder Host:Port"
                                    } else {
                                        null
                                    },
                                keyboard = TvKeyboard.Uri,
                            )
                            FishFormTextField(
                                label = "Port (1–65535)",
                                value = xtPort.text,
                                onValueChange = { nv -> xtPort = TextFieldValue(nv.filter { it.isDigit() }.take(5)) },
                                helperText = if (xtHttps) "HTTPS nutzt i. d. R. 443" else "HTTP nutzt i. d. R. 80",
                                errorText = if (xtPort.text.isNotBlank() && !isXtPortValid) "Ungültiger Port" else null,
                                keyboard = TvKeyboard.Number,
                            )
                            FishFormSwitch(
                                label = "HTTPS",
                                checked = xtHttps,
                                onCheckedChange = { v ->
                                    xtHttps = v
                                    val p = xtPort.text.toIntOrNull()
                                    if (p == null ||
                                        (v && p == 80)
                                    ) {
                                        xtPort = TextFieldValue("443")
                                    } else if (!v &&
                                        p == 443
                                    ) {
                                        xtPort = TextFieldValue("80")
                                    }
                                },
                            )
                            FishFormTextField(
                                label = "Benutzername",
                                value = xtUser.text,
                                onValueChange = { xtUser = TextFieldValue(it) },
                            )
                            FishFormTextField(
                                label = "Passwort",
                                value = xtPass.text,
                                onValueChange = { xtPass = TextFieldValue(it) },
                                keyboard = TvKeyboard.Password,
                            )
                            FishFormSelect(
                                label = "Output",
                                options = listOf("ts", "m3u8", "mp4"),
                                selected = normalizeOutput(xtOut.text),
                                onSelected = { xtOut = TextFieldValue(it) },
                                optionLabel = { it },
                                errorText = if (xtOut.text.isNotBlank() && !isXtOutValid) "Gültig: ts, m3u8 oder mp4" else null,
                            )
                        }
                    }

                    if (BuildConfig.SHOW_HEADER_UI) {
                        FishFormSection(title = "HTTP Header (optional)") {
                            FishFormTextField(
                                label = "User-Agent",
                                value = ua.text,
                                onValueChange = { ua = TextFieldValue(it) },
                            )
                            FishFormTextField(
                                label = "Referer (optional)",
                                value = ref.text,
                                onValueChange = { ref = TextFieldValue(it) },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    if (inlineMsg.isNotBlank()) {
                        Text(inlineMsg, color = colorScheme.secondary)
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    // Legacy (pre-forms) UI
                    // Mode selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = mode == SetupMode.M3U, onClick = { mode = SetupMode.M3U }, label = { Text("M3U Link") })
                        FilterChip(
                            selected = mode == SetupMode.XTREAM,
                            onClick = { mode = SetupMode.XTREAM },
                            label = { Text("Xtream Login") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    if (mode == SetupMode.M3U) {
                        OutlinedTextField(
                            value = m3u,
                            onValueChange = { m3u = it },
                            label = { Text("M3U / Xtream get.php Link") },
                            supportingText = {
                                AnimatedVisibility(
                                    visible = m3u.text.isNotBlank() && !isM3uOk,
                                ) { Text("Bitte gültige http/https URL eingeben") }
                            },
                            isError = m3u.text.isNotBlank() && !isM3uOk,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    capitalization = KeyboardCapitalization.None,
                                    keyboardType = KeyboardType.Uri,
                                ),
                        )
                        OutlinedTextField(
                            value = epg,
                            onValueChange = { epg = it },
                            label = { Text("EPG XMLTV URL (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Uri),
                        )
                    } else {
                        OutlinedTextField(
                            value = xtHost,
                            onValueChange = { v ->
                                xtHost = v
                                val (hostNoScheme, httpsSuggested, explicitPort) = sanitizeHost(v.text)
                                if (httpsSuggested != xtHttps) xtHttps = httpsSuggested
                                if (explicitPort != null &&
                                    xtPort.text != explicitPort.toString()
                                ) {
                                    xtPort = TextFieldValue(explicitPort.toString())
                                }
                                if (hostNoScheme != v.text) xtHost = TextFieldValue(hostNoScheme)
                            },
                            label = { Text("Xtream Host (ohne Schema)") },
                            supportingText = {
                                AnimatedVisibility(
                                    visible = xtHost.text.isNotBlank() && !isXtHostValid,
                                ) { Text("Nur Hostname oder Host:Port (ohne http/https)") }
                            },
                            isError = xtHost.text.isNotBlank() && !isXtHostValid,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = xtPort,
                                onValueChange = { nv ->
                                    val onlyDigits = nv.text.filter { it.isDigit() }.take(5)
                                    xtPort = nv.copy(text = onlyDigits)
                                },
                                label = { Text("Port (1–65535)") },
                                supportingText = {
                                    AnimatedVisibility(visible = xtPort.text.isNotBlank() && !isXtPortValid) {
                                        Text(if (xtHttps) "HTTPS nutzt i. d. R. 443" else "HTTP nutzt i. d. R. 80")
                                    }
                                },
                                isError = xtPort.text.isNotBlank() && !isXtPortValid,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Number),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(checked = xtHttps, onCheckedChange = { v ->
                                    xtHttps = v
                                    val p = xtPort.text.toIntOrNull()
                                    if (p == null || (v && p == 80)) {
                                        xtPort = TextFieldValue("443")
                                    } else if (!v && p == 443) {
                                        xtPort = TextFieldValue("80")
                                    }
                                })
                                Text("HTTPS")
                            }
                        }
                        OutlinedTextField(
                            value = xtUser,
                            onValueChange = { xtUser = it },
                            label = { Text("Benutzername") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = xtPass,
                            onValueChange = { xtPass = it },
                            label = { Text("Passwort") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Password),
                        )
                        OutlinedTextField(
                            value = xtOut,
                            onValueChange = { xtOut = it.copy(text = normalizeOutput(it.text)) },
                            label = { Text("Output (ts|m3u8|mp4)") },
                            supportingText = {
                                AnimatedVisibility(
                                    visible = xtOut.text.isNotBlank() && !isXtOutValid,
                                ) { Text("Gültig: ts, m3u8 oder mp4") }
                            },
                            isError = xtOut.text.isNotBlank() && !isXtOutValid,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
                        )
                    }

                    if (BuildConfig.SHOW_HEADER_UI) {
                        OutlinedTextField(
                            value = ua,
                            onValueChange = { ua = it },
                            label = { Text("User-Agent") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = ref,
                            onValueChange = { ref = it },
                            label = { Text("Referer (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Uri),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    if (inlineMsg.isNotBlank()) {
                        Text(inlineMsg, color = colorScheme.secondary)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (BuildConfig.TV_FORMS_V1) {
                    FishFormButtonRow(
                        primaryText = if (busy) "Bitte warten…" else "Speichern & Importieren",
                        onPrimary = {
                            focusManager.clearFocus(force = true)
                            scope.launch {
                                busy = true
                                inlineMsg = "Import läuft…"
                                val seedResult: Result<Triple<Int, Int, Int>>? =
                                    when (mode) {
                                        SetupMode.M3U -> {
                                            val m3uUrl = m3u.text.trim()
                                            val epgUrl = epg.text.trim()
                                            val uaStr = ua.text.trim()
                                            val refStr = ref.text.trim()
                                            try {
                                                store.setSources(m3uUrl, epgUrl, uaStr, refStr)
                                            } catch (t: Throwable) {
                                                inlineMsg = "Speichern fehlgeschlagen: ${t.message.orEmpty()}"
                                                busy = false
                                                return@launch
                                            }
                                            XtreamSeeder.ensureSeeded(
                                                context = ctx,
                                                store = store,
                                                reason = "setup:m3u",
                                                force = true,
                                                forceDiscovery = false,
                                            )
                                        }
                                        SetupMode.XTREAM -> {
                                            val (hostNoScheme, httpsFromHost, explicitPort) = sanitizeHost(xtHost.text)
                                            val scheme = if (xtHttps || httpsFromHost) "https" else "http"
                                            val user = xtUser.text.trim()
                                            val pass = xtPass.text.trim()
                                            val out = normalizeOutput(xtOut.text)
                                            val port =
                                                explicitPort ?: (xtPort.text.trim().toIntOrNull() ?: if (scheme == "https") 443 else 80)
                                            val portal = "$scheme://$hostNoScheme:$port"
                                            val m3uUrl = "$portal/get.php?username=$user&password=$pass&output=$out"
                                            val epgUrl = "$portal/xmltv.php?username=$user&password=$pass"
                                            try {
                                                store.setSources(m3uUrl, epgUrl, ua.text.trim(), ref.text.trim())
                                                store.setXtHost(hostNoScheme)
                                                store.setXtPort(port)
                                                store.setXtUser(user)
                                                store.setXtPass(pass)
                                                store.setXtOutput(out)
                                                store.setXtPortVerified(false)
                                            } catch (t: Throwable) {
                                                inlineMsg = "Speichern fehlgeschlagen: ${t.message.orEmpty()}"
                                                busy = false
                                                return@launch
                                            }
                                            XtreamSeeder.ensureSeeded(
                                                context = ctx,
                                                store = store,
                                                reason = "setup:xtream",
                                                force = true,
                                                forceDiscovery = true,
                                            )
                                        }
                                    }
                                busy = false
                                when {
                                    seedResult == null -> {
                                        inlineMsg = "Daten bereits vorhanden"
                                        snackbar.showSnackbar(inlineMsg)
                                        onDone()
                                    }
                                    seedResult.isSuccess -> {
                                        val (live, vod, series) = seedResult.getOrNull() ?: Triple(0, 0, 0)
                                        inlineMsg = "Import abgeschlossen: Live=$live · VOD=$vod · Serien=$series"
                                        SchedulingGateway.scheduleAll(ctx)
                                        snackbar.showSnackbar(inlineMsg)
                                        onDone()
                                    }
                                    else -> {
                                        val msg = seedResult.exceptionOrNull()?.message.orEmpty()
                                        inlineMsg = "Import fehlgeschlagen: $msg"
                                        snackbar.showSnackbar(inlineMsg)
                                    }
                                }
                            }
                        },
                        primaryEnabled = canSubmit,
                        isBusy = busy,
                    )
                } else {
                    TvButton(
                        enabled = canSubmit,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            scope.launch {
                                busy = true
                                inlineMsg = "Import läuft…"

                                val seedResult: Result<Triple<Int, Int, Int>>? =
                                    when (mode) {
                                        SetupMode.M3U -> {
                                            val m3uUrl = m3u.text.trim()
                                            val epgUrl = epg.text.trim()
                                            val uaStr = ua.text.trim()
                                            val refStr = ref.text.trim()

                                            try {
                                                store.setSources(m3uUrl, epgUrl, uaStr, refStr)
                                            } catch (t: Throwable) {
                                                inlineMsg = "Speichern fehlgeschlagen: ${t.message.orEmpty()}"
                                                busy = false
                                                return@launch
                                            }

                                            val cfg = XtreamConfig.fromM3uUrl(m3uUrl)
                                            if (cfg != null) {
                                                store.setXtHost(cfg.host)
                                                store.setXtPort(cfg.port)
                                                store.setXtUser(cfg.username)
                                                store.setXtPass(cfg.password)
                                                cfg.liveExtPrefs.firstOrNull()?.let { store.setXtOutput(it) }
                                            } else {
                                                val detected = XtreamDetect.detectCreds(m3uUrl)
                                                if (detected == null) {
                                                    inlineMsg = "Konnte Xtream-Zugangsdaten nicht ableiten."
                                                    busy = false
                                                    return@launch
                                                }
                                                store.setXtHost(detected.host)
                                                store.setXtPort(detected.port)
                                                store.setXtUser(detected.username)
                                                store.setXtPass(detected.password)
                                                store.setXtOutput(detected.output)
                                            }
                                            store.setXtPortVerified(false)
                                            XtreamSeeder.ensureSeeded(
                                                context = ctx,
                                                store = store,
                                                reason = "setup:m3u",
                                                force = true,
                                                forceDiscovery = true,
                                            )
                                        }
                                        SetupMode.XTREAM -> {
                                            val rawHost = xtHost.text.trim()
                                            val (hostNoScheme, httpsFromHost, explicitPort) = sanitizeHost(rawHost)
                                            if (hostNoScheme.isBlank()) {
                                                inlineMsg = "Host ungültig"
                                                busy = false
                                                return@launch
                                            }
                                            val scheme = if (xtHttps || httpsFromHost) "https" else "http"
                                            val user = xtUser.text.trim()
                                            val pass = xtPass.text.trim()
                                            val out = normalizeOutput(xtOut.text)
                                            val port =
                                                explicitPort ?: (xtPort.text.trim().toIntOrNull() ?: if (scheme == "https") 443 else 80)
                                            val portal = "$scheme://$hostNoScheme:$port"
                                            val m3uUrl = "$portal/get.php?username=$user&password=$pass&output=$out"
                                            val epgUrl = "$portal/xmltv.php?username=$user&password=$pass"

                                            try {
                                                store.setSources(m3uUrl, epgUrl, ua.text.trim(), ref.text.trim())
                                                store.setXtHost(hostNoScheme)
                                                store.setXtPort(port)
                                                store.setXtUser(user)
                                                store.setXtPass(pass)
                                                store.setXtOutput(out)
                                                store.setXtPortVerified(false)
                                            } catch (t: Throwable) {
                                                inlineMsg = "Speichern fehlgeschlagen: ${t.message.orEmpty()}"
                                                busy = false
                                                return@launch
                                            }

                                            XtreamSeeder.ensureSeeded(
                                                context = ctx,
                                                store = store,
                                                reason = "setup:xtream",
                                                force = true,
                                                forceDiscovery = true,
                                            )
                                        }
                                    }

                                busy = false

                                when {
                                    seedResult == null -> {
                                        inlineMsg = "Daten bereits vorhanden"
                                        snackbar.showSnackbar(inlineMsg)
                                        onDone()
                                    }
                                    seedResult.isSuccess -> {
                                        val (live, vod, series) = seedResult.getOrNull() ?: Triple(0, 0, 0)
                                        inlineMsg = "Import abgeschlossen: Live=$live · VOD=$vod · Serien=$series"
                                        SchedulingGateway.scheduleAll(ctx)
                                        snackbar.showSnackbar(inlineMsg)
                                        onDone()
                                    }
                                    else -> {
                                        val msg = seedResult.exceptionOrNull()?.message.orEmpty()
                                        inlineMsg = "Import fehlgeschlagen: $msg"
                                        snackbar.showSnackbar(inlineMsg)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.focusScaleOnTv(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = DesignTokens.Accent,
                                contentColor = Color.Black,
                            ),
                    ) {
                        if (busy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Bitte warten…")
                            }
                        } else {
                            Text("Speichern & Importieren")
                        }
                    }
                }
            }
        }
    }
}

/** Normalize host input: strips scheme, path and trailing slashes. Returns (host, https?, explicitPort?). */
private fun sanitizeHost(input: String): Triple<String, Boolean, Int?> {
    val t = input.trim()
    if (t.isEmpty()) return Triple("", false, null)
    val tmp = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    val url =
        tmp.toHttpUrlOrNull() ?: return Triple(t.removePrefix("http://").removePrefix("https://").trim('/'), t.startsWith("https://"), null)
    val host = url.host
    val isHttps = url.scheme == "https"
    val port =
        if (url.port != 80 && url.port != 443) {
            url.port
        } else if (url.port > 0) {
            url.port
        } else {
            null
        }
    if (port != null) {
        return Triple(host, isHttps, port)
    }
    return Triple(host, isHttps, null)
}

/** Restrict output to known valid extensions. */
private fun normalizeOutput(text: String): String {
    val t = text.trim().lowercase()
    return when (t) {
        "ts", "m3u8", "mp4" -> t
        else ->
            when {
                "m3u8" in t -> "m3u8"
                "mp4" in t -> "mp4"
                "ts" in t -> "ts"
                else -> "m3u8"
            }
    }
}
