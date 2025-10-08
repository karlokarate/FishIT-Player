// SettingsScreen.kt — bereinigt & kompatibel
package com.chris.m3usuite.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.ColumnScope
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import androidx.compose.runtime.snapshotFlow
import com.chris.m3usuite.ui.focus.FocusKit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.core.xtream.XtreamSeeder
import com.chris.m3usuite.work.SchedulingGateway
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.chris.m3usuite.telegram.TdLibReflection
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.ui.focus.tvClickable
import com.chris.m3usuite.ui.layout.FishFormSection
import com.chris.m3usuite.ui.layout.FishFormSelect
import com.chris.m3usuite.ui.layout.FishFormSwitch
import com.chris.m3usuite.ui.layout.FishFormTextField
import com.chris.m3usuite.ui.layout.TvKeyboard
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.core.net.toUri

// ---- Small helpers / constants ------------------------------------------------

private const val TAG_XTREAM_DIAG = "XtreamDiag"
private const val TAG_XTREAM_CAPS = "XtreamCaps"
private const val TAG_EPG_TEST = "XtreamEPGTest"
private const val TAG_IMPORT = "ImportUpdate"

private suspend fun SnackbarHostState.toast(message: String) {
    showSnackbar(message)
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    store: SettingsStore,
    onBack: () -> Unit,
    onOpenProfiles: (() -> Unit)? = null,
    onOpenGate: (() -> Unit)? = null,
    onOpenXtreamCfCheck: (() -> Unit)? = null,
    onGlobalSearch: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        com.chris.m3usuite.metrics.RouteTag.set("settings")
        com.chris.m3usuite.core.debug.GlobalDebug.logTree("settings:root")
    }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val isTv = remember { FocusKit.isTvDevice(ctx) }
    val xtRepo = remember(ctx) { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }

    // Permissions
    val permRepo = remember(ctx) { com.chris.m3usuite.data.repo.PermissionRepository(ctx, store) }
    var canChangeSources by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { canChangeSources = permRepo.current().canChangeSources }

    // Settings values (collected with lifecycle awareness)
    val mode by store.playerMode.collectAsStateWithLifecycle(initialValue = "internal")
    val pkg by store.preferredPlayerPkg.collectAsStateWithLifecycle(initialValue = "")
    val subScale by store.subtitleScale.collectAsStateWithLifecycle(initialValue = 0.06f)
    val subFg by store.subtitleFg.collectAsStateWithLifecycle(initialValue = 0xF2FFFFFF.toInt())
    val subBg by store.subtitleBg.collectAsStateWithLifecycle(initialValue = 0x66000000)
    val subFgOpacity by store.subtitleFgOpacityPct.collectAsStateWithLifecycle(initialValue = 90)
    val subBgOpacity by store.subtitleBgOpacityPct.collectAsStateWithLifecycle(initialValue = 40)
    val headerCollapsed by store.headerCollapsedDefaultInLandscape.collectAsStateWithLifecycle(initialValue = true)
    val rotationLocked by store.rotationLocked.collectAsStateWithLifecycle(initialValue = false)
    val autoplayNext by store.autoplayNext.collectAsStateWithLifecycle(initialValue = false)
    val hapticsEnabled by store.hapticsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val rememberLast by store.rememberLastProfile.collectAsStateWithLifecycle(initialValue = false)
    val pinSet by store.adultPinSet.collectAsStateWithLifecycle(initialValue = false)
    val m3u by store.m3uUrl.collectAsStateWithLifecycle(initialValue = "")
    val epg by store.epgUrl.collectAsStateWithLifecycle(initialValue = "")
    val xtHost by store.xtHost.collectAsStateWithLifecycle(initialValue = "")
    val xtPort by store.xtPort.collectAsStateWithLifecycle(initialValue = 80)
    val xtUser by store.xtUser.collectAsStateWithLifecycle(initialValue = "")
    val xtPass by store.xtPass.collectAsStateWithLifecycle(initialValue = "")
    val xtOut by store.xtOutput.collectAsStateWithLifecycle(initialValue = "m3u8")
    val ua by store.userAgent.collectAsStateWithLifecycle(initialValue = "IBOPlayer/1.4 (Android)")
    val referer by store.referer.collectAsStateWithLifecycle(initialValue = "")
    val m3uWorkersEnabled by store.m3uWorkersEnabled.collectAsStateWithLifecycle(initialValue = true)

    // Local edit buffers to avoid eager writes (debounced commit)
    var m3uLocal by rememberSaveable { mutableStateOf("") }
    var epgLocal by rememberSaveable { mutableStateOf("") }
    var uaLocal by rememberSaveable { mutableStateOf("") }
    var refererLocal by rememberSaveable { mutableStateOf("") }
    var xtHostLocal by rememberSaveable { mutableStateOf("") }
    var xtPortLocal by rememberSaveable { mutableStateOf(0) }
    var xtUserLocal by rememberSaveable { mutableStateOf("") }
    var xtPassLocal by rememberSaveable { mutableStateOf("") }
    var xtOutLocal by rememberSaveable { mutableStateOf("") }

    // Keep locals in sync when store changes (avoid feedback loops)
    LaunchedEffect(m3u) { if (m3uLocal != m3u) m3uLocal = m3u }
    LaunchedEffect(epg) { if (epgLocal != epg) epgLocal = epg }
    LaunchedEffect(ua) { if (uaLocal != ua) uaLocal = ua }
    LaunchedEffect(referer) { if (refererLocal != referer) refererLocal = referer }
    LaunchedEffect(xtHost) { if (xtHostLocal != xtHost) xtHostLocal = xtHost }
    LaunchedEffect(xtPort) { if (xtPortLocal != xtPort) xtPortLocal = xtPort }
    LaunchedEffect(xtUser) { if (xtUserLocal != xtUser) xtUserLocal = xtUser }
    LaunchedEffect(xtPass) { if (xtPassLocal != xtPass) xtPassLocal = xtPass }
    LaunchedEffect(xtOut) { if (xtOutLocal != xtOut) xtOutLocal = xtOut }

    // Debounced commits: prevents heavy imports firing on every keystroke
    LaunchedEffect(Unit) {
        snapshotFlow { m3uLocal.trim() }
            .debounce(800)
            .distinctUntilChanged()
            .collectLatest { if (canChangeSources) store.set(Keys.M3U_URL, it) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { epgLocal.trim() }
            .debounce(800).distinctUntilChanged()
            .collectLatest { if (canChangeSources) store.set(Keys.EPG_URL, it) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { uaLocal }
            .debounce(500).distinctUntilChanged()
            .collectLatest { store.set(Keys.USER_AGENT, it) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { refererLocal }
            .debounce(500).distinctUntilChanged()
            .collectLatest { store.set(Keys.REFERER, it) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { xtHostLocal }
            .debounce(600).distinctUntilChanged()
            .collectLatest { store.set(Keys.XT_HOST, it) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { xtPortLocal }
            .debounce(600).distinctUntilChanged()
            .collectLatest { p -> if (p in 1..65535) store.setInt(Keys.XT_PORT, p) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { xtUserLocal }
            .debounce(600).distinctUntilChanged()
            .collectLatest { store.set(Keys.XT_USER, it) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { xtPassLocal }
            .debounce(600).distinctUntilChanged()
            .collectLatest { store.set(Keys.XT_PASS, it) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { xtOutLocal.lowercase() }
            .debounce(500).distinctUntilChanged()
            .collectLatest { store.set(Keys.XT_OUTPUT, it) }
    }
    val httpLogEnabled by store.httpLogEnabled.collectAsStateWithLifecycle(initialValue = false)
    val globalDebugEnabled by store.globalDebugEnabled.collectAsStateWithLifecycle(initialValue = false)
    // Import diagnostics
    val lastImportAtMs by store.lastImportAtMs.collectAsStateWithLifecycle(initialValue = 0L)
    val lastSeedLive by store.lastSeedLive.collectAsStateWithLifecycle(initialValue = 0)
    val lastSeedVod by store.lastSeedVod.collectAsStateWithLifecycle(initialValue = 0)
    val lastSeedSeries by store.lastSeedSeries.collectAsStateWithLifecycle(initialValue = 0)
    val lastDeltaLive by store.lastDeltaLive.collectAsStateWithLifecycle(initialValue = 0)
    val lastDeltaVod by store.lastDeltaVod.collectAsStateWithLifecycle(initialValue = 0)
    val lastDeltaSeries by store.lastDeltaSeries.collectAsStateWithLifecycle(initialValue = 0)
    val obxCounts by produceState(initialValue = Triple(0L, 0L, 0L), lastImportAtMs, m3uWorkersEnabled) {
        value = runCatching { xtRepo.countTotals() }.getOrElse { Triple(0L, 0L, 0L) }
    }

    val favUseXtream by store.epgFavUseXtream.collectAsStateWithLifecycle(initialValue = true)
    val favSkipXmltv by store.epgFavSkipXmltvIfXtreamOk.collectAsStateWithLifecycle(initialValue = false)

    var pinDialogMode by remember { mutableStateOf<PinMode?>(null) }
    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("settings:main")
    val snackHost = remember { SnackbarHostState() }

    // Theme / input colors remembered once (prevents rebuilding on every recomposition)
    val accent = com.chris.m3usuite.ui.theme.DesignTokens.Accent
    val cs = MaterialTheme.colorScheme
    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.6f),
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White,
        focusedContainerColor = cs.surface,
        unfocusedContainerColor = cs.surface,
        disabledContainerColor = cs.surface,
        cursorColor = accent,
        focusedBorderColor = accent,
        unfocusedBorderColor = cs.outline.copy(alpha = 0.6f)
    )

    // Derived states
    val xtConfigured by remember(xtHost, xtUser, xtPass) {
        derivedStateOf { xtHost.isNotBlank() && xtUser.isNotBlank() && xtPass.isNotBlank() }
    }

    var xtSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var importSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var profileSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var playerSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var subtitleSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var pinSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var playbackSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var seedingSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var telegramSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var exportSectionExpanded by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun ColumnScope.XtreamSection() {
        var showEditM3u by remember { mutableStateOf(false) }
        var showEditEpg by remember { mutableStateOf(false) }
        var showEditUa by remember { mutableStateOf(false) }
        var showEditRef by remember { mutableStateOf(false) }
        var showEditHost by remember { mutableStateOf(false) }
        var showEditPort by remember { mutableStateOf(false) }
        var showEditUser by remember { mutableStateOf(false) }
        var showEditPass by remember { mutableStateOf(false) }
        var showEditOut by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = m3uLocal,
            onValueChange = { v -> m3uLocal = v },
            label = { Text("M3U / Xtream get.php Link") },
            singleLine = true,
            enabled = canChangeSources,
            readOnly = isTv,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isTv && canChangeSources) FocusKit.run { Modifier.tvClickable(onClick = { showEditM3u = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
            colors = tfColors
        )
        if (isTv && canChangeSources) {
            TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showEditM3u = true }) { Text("Bearbeiten…") }
        }
        OutlinedTextField(
            value = epgLocal,
            onValueChange = { v -> epgLocal = v },
            label = { Text("EPG XMLTV URL (optional)") },
            singleLine = true,
            enabled = canChangeSources,
            readOnly = isTv,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isTv && canChangeSources) FocusKit.run { Modifier.tvClickable(onClick = { showEditEpg = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
            colors = tfColors
        )
        if (isTv && canChangeSources) {
            TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showEditEpg = true }) { Text("Bearbeiten…") }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Favoriten-EPG via Xtream (falls konfiguriert)", modifier = Modifier.weight(1f))
            Switch(checked = favUseXtream, onCheckedChange = { v -> scope.launch { store.setEpgFavUseXtream(v) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aggressiv: XMLTV für Favoriten überspringen, wenn Xtream liefert", modifier = Modifier.weight(1f))
            Switch(checked = favSkipXmltv, onCheckedChange = { v -> scope.launch { store.setEpgFavSkipXmltvIfXtreamOk(v) } })
        }
        
        if (BuildConfig.SHOW_HEADER_UI) {
            OutlinedTextField(
                value = uaLocal,
                onValueChange = { v -> uaLocal = v },
                label = { Text("User-Agent") },
                singleLine = true,
                readOnly = isTv,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isTv) FocusKit.run { Modifier.tvClickable(onClick = { showEditUa = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
                colors = tfColors
            )
            if (isTv) { TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showEditUa = true }) { Text("Bearbeiten…") } }
        }
        if (BuildConfig.SHOW_HEADER_UI) {
            OutlinedTextField(
                value = refererLocal,
                onValueChange = { v -> refererLocal = v },
                label = { Text("Referer (optional)") },
                singleLine = true,
                readOnly = isTv,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isTv) FocusKit.run { Modifier.tvClickable(onClick = { showEditRef = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
                colors = tfColors
            )
            if (isTv) { TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showEditRef = true }) { Text("Bearbeiten…") } }
        }
        
        // Xtream (optional)
        Text("Xtream (optional)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = xtHostLocal,
                onValueChange = { v -> xtHostLocal = v },
                label = { Text("Host") },
                singleLine = true,
                readOnly = isTv,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isTv) FocusKit.run { Modifier.tvClickable(onClick = { showEditHost = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
                colors = tfColors
            )
            OutlinedTextField(
                value = xtPortLocal.toString(),
                onValueChange = { s -> s.toIntOrNull()?.let { xtPortLocal = it.coerceIn(1, 65535) } },
                label = { Text("Port") },
                singleLine = true,
                readOnly = isTv,
                modifier = Modifier
                    .width(120.dp)
                    .then(if (isTv) FocusKit.run { Modifier.tvClickable(onClick = { showEditPort = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
                colors = tfColors
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = xtUserLocal,
                onValueChange = { v -> xtUserLocal = v },
                label = { Text("Benutzername") },
                singleLine = true,
                readOnly = isTv,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isTv) FocusKit.run { Modifier.tvClickable(onClick = { showEditUser = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
                colors = tfColors
            )
            OutlinedTextField(
                value = xtPassLocal,
                onValueChange = { v -> xtPassLocal = v },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                readOnly = isTv,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isTv) FocusKit.run { Modifier.tvClickable(onClick = { showEditPass = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
                colors = tfColors
            )
        }
        Spacer(Modifier.height(8.dp))
        
        if (xtConfigured) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.focusScaleOnTv(),
                    onClick = { onOpenXtreamCfCheck?.invoke() },
                    enabled = m3uWorkersEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) { Text("Portal checken (WebView)") }
                TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { onOpenXtreamCfCheck?.invoke() }, enabled = m3uWorkersEnabled) { Text("Cloudflare lösen (WebView)") }
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = xtOutLocal,
                onValueChange = { v -> xtOutLocal = v },
                label = { Text("Output (ts|m3u8|mp4)") },
                singleLine = true,
                readOnly = isTv,
                modifier = Modifier
                    .width(200.dp)
                    .then(if (isTv) FocusKit.run { Modifier.tvClickable(onClick = { showEditOut = true }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) } else Modifier),
                colors = tfColors
            )
            val ctxLocal = LocalContext.current
            TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                scope.launch {
                    val m3uNow = store.m3uUrl.first()
                    val cfg = com.chris.m3usuite.core.xtream.XtreamConfig.fromM3uUrl(m3uNow)
                    if (cfg != null) {
                        store.setXtHost(cfg.host); store.setXtPort(cfg.port); store.setXtUser(cfg.username); store.setXtPass(cfg.password)
                        cfg.liveExtPrefs.firstOrNull()?.let { store.setXtOutput(it) }
                    }
                }
            }) { Text("Aus M3U ableiten") }
        
            TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                scope.launch {
                    val hostNow = store.xtHost.first(); val portNow = store.xtPort.first(); val userNow = store.xtUser.first(); val passNow = store.xtPass.first()
                    if (hostNow.isBlank() || userNow.isBlank() || passNow.isBlank()) { snackHost.toast("Xtream unvollständig."); return@launch }
                    val scheme = if (portNow == 443) "https" else "http"
                    val base = "$scheme://$hostNow:$portNow"
                    val url = "$base/player_api.php?username=${userNow}&password=${passNow}"
                    val client = HttpClientFactory.create(ctxLocal, store)
                    val headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)
                    val startNs = System.nanoTime()
                    val req = okhttp3.Request.Builder().url(url).get().apply { headers.forEach { (k, v) -> header(k, v) } }.build()
                    runCatching {
                        client.newCall(req).execute().use { res ->
                            val dur = (System.nanoTime() - startNs)/1_000_000
                            val code = res.code; val ctype = res.header("Content-Type").orEmpty()
                            val peek = res.peekBody(2048).string()
                            val jsonOk = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(peek); true }.getOrDefault(false)
                            Log.i(TAG_XTREAM_DIAG, "ua='${headers["User-Agent"]}' code=$code type='$ctype' jsonOk=$jsonOk durMs=$dur")
                            snackHost.toast("Xt: code=$code json=$jsonOk")
                        }
                    }.onFailure { e -> Log.w(TAG_XTREAM_DIAG, "fail: ${e.message}", e); snackHost.toast("Xt: Fehler ${e.message}") }
                }
            }, enabled = m3uWorkersEnabled) { Text("Xtream testen") }
        
            TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                scope.launch {
                    val hostNow = store.xtHost.first(); val portNow = store.xtPort.first(); val userNow = store.xtUser.first(); val passNow = store.xtPass.first()
                    if (hostNow.isBlank() || userNow.isBlank() || passNow.isBlank()) { snackHost.toast("Xtream unvollständig."); return@launch }
                    val scheme = if (portNow == 443) "https" else "http"
                    val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctxLocal, store)
                    val capsStore = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctxLocal)
                    val portStore = com.chris.m3usuite.core.xtream.EndpointPortStore(ctxLocal)
                    val disc = com.chris.m3usuite.core.xtream.CapabilityDiscoverer(http, capsStore, portStore)
                    val cfgExplicit = com.chris.m3usuite.core.xtream.XtreamConfig(
                        scheme = scheme,
                        host = hostNow,
                        port = portNow,
                        username = userNow,
                        password = passNow
                    )
                    val caps = runCatching { withContext(Dispatchers.IO) { disc.discover(cfgExplicit, false) } }.getOrElse { e ->
                        Log.w(TAG_XTREAM_CAPS, "discover failed: ${e.message}", e); snackHost.toast("Caps: Fehler ${e.message}"); return@launch
                    }
                    val info = buildString {
                        append("baseUrl=").append(caps.baseUrl).append('\n')
                        append("vodKind=").append(caps.resolvedAliases.vodKind ?: "-").append('\n')
                        append("actions=")
                        append(caps.actions.entries.joinToString { (k,v) -> "$k:${if (v.supported) '1' else '0'}" })
                    }
                    Log.i(TAG_XTREAM_CAPS, info)
                    snackHost.toast("Capabilities: ${caps.resolvedAliases.vodKind ?: "-"}")
                }
            }, enabled = m3uWorkersEnabled) { Text("Capabilities") }
        
            TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                scope.launch {
                    val tag = TAG_EPG_TEST
                    try {
                        val hostNow = store.xtHost.first(); val userNow = store.xtUser.first(); val passNow = store.xtPass.first(); val outNow = store.xtOutput.first(); val portNow = store.xtPort.first()
                        Log.d(tag, "Testing shortEPG with host=$hostNow:$portNow, user=$userNow, output=$outNow")
                        if (hostNow.isBlank() || userNow.isBlank() || passNow.isBlank()) {
                            Log.w(tag, "Xtream config missing; cannot test shortEPG")
                            snackHost.toast("EPG-Test: Xtream-Konfig fehlt")
                            return@launch
                        }
                        val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctxLocal, store)
                        val sid = withContext(Dispatchers.IO) { obx.livePaged(0, 1).firstOrNull()?.streamId }
                        if (sid == null) {
                            Log.w(tag, "No live streamId found in DB; import might be required")
                            snackHost.toast("EPG-Test: keine Live-StreamId gefunden")
                            return@launch
                        }
                        val scheme = if (portNow == 443) "https" else "http"
                        val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctxLocal, store)
                        val caps = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctxLocal)
                        val portStore = com.chris.m3usuite.core.xtream.EndpointPortStore(ctxLocal)
                        val client = com.chris.m3usuite.core.xtream.XtreamClient(http)
                        client.initialize(scheme, hostNow, userNow, passNow, basePath = null, store = caps, portStore = portStore, portOverride = portNow)
                        val list = sid.let { id ->
                            client.fetchShortEpg(id, 2)
                        }?.let { json ->
                            val root = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonArray
                            root.mapNotNull { el ->
                                val obj = el.jsonObject
                                com.chris.m3usuite.core.xtream.XtShortEPGProgramme(
                                    title = obj["title"]?.jsonPrimitive?.contentOrNull,
                                    start = obj["start"]?.jsonPrimitive?.contentOrNull,
                                    end = obj["end"]?.jsonPrimitive?.contentOrNull,
                                )
                            }
                        } ?: emptyList()
                        Log.d(tag, "shortEPG result count=${list.size}; entries=${list.map { it.title }}")
                        snackHost.toast("EPG-Test: ${list.getOrNull(0)?.title ?: "(leer)"}")
                    } catch (t: Throwable) {
                        Log.e(tag, "EPG test failed", t)
                        snackHost.toast("EPG-Test fehlgeschlagen: ${t.message}")
                    }
                }
            }, enabled = m3uWorkersEnabled) { Text("Test EPG (Debug)") }
        }
        
        // TV edit dialogs
        if (showEditM3u) TvEditDialog(initial = m3uLocal, label = "M3U / Xtream get.php Link", onDone = { m3uLocal = it }, onDismiss = { showEditM3u = false })
        if (showEditEpg) TvEditDialog(initial = epgLocal, label = "EPG XMLTV URL", onDone = { epgLocal = it }, onDismiss = { showEditEpg = false })
        if (showEditHost) TvEditDialog(initial = xtHostLocal, label = "Host", onDone = { xtHostLocal = it }, onDismiss = { showEditHost = false })
        if (showEditPort) TvEditDialog(initial = xtPortLocal.toString(), label = "Port", onDone = { s -> s.toIntOrNull()?.let { xtPortLocal = it.coerceIn(1, 65535) } }, onDismiss = { showEditPort = false })
        if (showEditUser) TvEditDialog(initial = xtUserLocal, label = "Benutzername", onDone = { xtUserLocal = it }, onDismiss = { showEditUser = false })
        if (showEditPass) TvEditDialog(initial = xtPassLocal, label = "Passwort", onDone = { xtPassLocal = it }, onDismiss = { showEditPass = false })
        if (showEditOut) TvEditDialog(initial = xtOutLocal, label = "Output (ts|m3u8|mp4)", onDone = { xtOutLocal = it }, onDismiss = { showEditOut = false })
        if (BuildConfig.SHOW_HEADER_UI) {
            if (showEditUa) TvEditDialog(initial = uaLocal, label = "User-Agent", onDone = { uaLocal = it }, onDismiss = { showEditUa = false })
            if (showEditRef) TvEditDialog(initial = refererLocal, label = "Referer", onDone = { refererLocal = it }, onDismiss = { showEditRef = false })
        }
        
        // EPG test (Repo, mit XMLTV‑Fallback)
        val ctxRepo = LocalContext.current
        Button(
            modifier = Modifier.focusScaleOnTv(),
            onClick = {
                scope.launch {
                    val tag = TAG_EPG_TEST
                    try {
                        val hostNow = store.xtHost.first(); val userNow = store.xtUser.first(); val passNow = store.xtPass.first(); val outNow = store.xtOutput.first(); val portNow = store.xtPort.first()
                        Log.d(tag, "Testing EPG via repo with host=$hostNow:$portNow, user=$userNow, output=$outNow")
                        if (hostNow.isBlank() || userNow.isBlank() || passNow.isBlank()) {
                            Log.w(tag, "Xtream config missing; cannot test EPG")
                            snackHost.toast("EPG-Test: Xtream-Konfig fehlt")
                            return@launch
                        }
                        val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctxRepo, store)
                        val firstLive = withContext(Dispatchers.IO) { obx.livePaged(0, 200).firstOrNull() }
                        val sid = firstLive?.streamId
                        Log.d(tag, "Selected sid=$sid tvg-id=${firstLive?.epgChannelId} name=${firstLive?.name}")
                        if (sid == null) {
                            Log.w(tag, "No live streamId found in DB; import might be required")
                            snackHost.toast("EPG-Test: keine Live-StreamId gefunden")
                            return@launch
                        }
                        val repo = com.chris.m3usuite.data.repo.EpgRepository(ctxRepo, store)
                        val list = repo.nowNext(sid, 2)
                        Log.d(tag, "repo EPG count=${list.size}; entries=${list.map { it.title }}")
                        snackHost.toast("EPG-Test: ${list.getOrNull(0)?.title ?: "(leer)"}")
                    } catch (t: Throwable) {
                        Log.e(tag, "EPG test failed", t)
                        snackHost.toast("EPG-Test fehlgeschlagen: ${t.message}")
                    }
                }
            },
            enabled = canChangeSources,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
        ) { Text("EPG testen (Debug)") }
        
        // (HTTP Debug/Logging switch moved to "Import & Diagnose" section)
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ctxLocal = LocalContext.current
        fun runManualImport(forceDiscovery: Boolean = true) {
            scope.launch {
                if (!m3uWorkersEnabled) { snackHost.toast("API deaktiviert"); return@launch }
                // Fire-and-forget background import so it continues even if user leaves the screen
                withContext(Dispatchers.IO) {
                    // Optionally do a quick discovery upfront; ignore failures silently
                    if (forceDiscovery) runCatching {
                        XtreamSeeder.ensureSeeded(
                            context = ctxLocal,
                            store = store,
                            reason = "settings:discovery",
                            force = false,
                            forceDiscovery = true
                        )
                    }
                    // Ensure all header lists are completely filled (heads-only delta)
                    runCatching { com.chris.m3usuite.data.repo.XtreamObxRepository(ctxLocal, store).importDelta(deleteOrphans = false, includeLive = true) }
                    // Then schedule a moderate detail chunk so posters/plots improve quickly
                    com.chris.m3usuite.work.XtreamDeltaImportWorker.triggerOnce(ctxLocal, includeLive = false, vodLimit = 50, seriesLimit = 30)
                    SchedulingGateway.scheduleAll(ctxLocal)
                }
                snackHost.toast("Import gestartet – läuft im Hintergrund")
            }
        }
        
        com.chris.m3usuite.ui.common.TvButton(
            modifier = Modifier.focusScaleOnTv(),
            onClick = { runManualImport(forceDiscovery = true) },
            enabled = canChangeSources && m3uWorkersEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) { Text("Import aktualisieren") }
        
            if (onOpenProfiles != null) {
                TextButton(onClick = onOpenProfiles) { Text("Profile verwalten…") }
            }
        }
        
        
    }



    HomeChromeScaffold(
        title = "Einstellungen",
        onSettings = null,
        onSearch = onGlobalSearch,
        onProfiles = null,
        listState = listState,
        onLogo = onBack,
        snackbarHost = snackHost,
        bottomBar = {}
    ) { pads ->
        // Hintergrund + Inhalt
        // (Der Fehler aus deinem Screenshot entstand hier nur, weil HomeChromeScaffold kein @Composable war.)
        Box(Modifier.fillMaxSize().padding(pads)) {
            // Gradient base
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background,
                            1f to MaterialTheme.colorScheme.surface
                        )
                    )
            )
            // Radial accent glow
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                            radius = with(LocalDensity.current) { 600.dp.toPx() }
                        )
                    )
            )
            // Center rotated app icon
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).size(520.dp),
                alpha = 0.06f,
                neutralizeUnderlay = true
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(pads)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            com.chris.m3usuite.ui.common.TvTextButton(onClick = onBack) { Text("Zurück") }

            if (onOpenProfiles != null) {
                val allowProfiles = remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { allowProfiles.value = permRepo.current().canEditWhitelist }
                if (allowProfiles.value) {
                    com.chris.m3usuite.ui.common.TvTextButton(onClick = onOpenProfiles) { Text("Profile verwalten…") }
                }
            }

            com.chris.m3usuite.ui.common.TvTextButton(onClick = {
                scope.launch {
                    store.setCurrentProfileId(-1)
                    onOpenGate?.invoke()
                }
            }) { Text("Zur Profilwahl…") }

            Spacer(Modifier.height(12.dp))

            CollapsibleSection(
                title = "Quelle (M3U/Xtream/EPG)",
                accent = accent,
                expanded = xtSectionExpanded,
                onExpandedChange = { xtSectionExpanded = it }
            ) {
                XtreamSection()
            }

            CollapsibleSection(
                title = "Import & Diagnose",
                accent = accent,
                expanded = importSectionExpanded,
                onExpandedChange = { importSectionExpanded = it }
            ) {
                // Debug / Logging
                Spacer(Modifier.height(8.dp))
                Text("Debug & Logging", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Globales Debugging (Navigation/OBX)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = globalDebugEnabled,
                        onCheckedChange = { v ->
                            scope.launch {
                                store.setGlobalDebugEnabled(v)
                                com.chris.m3usuite.core.debug.GlobalDebug.setEnabled(v)
                            }
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HTTP-Traffic-Log speichern (JSONL)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = httpLogEnabled,
                        onCheckedChange = { v ->
                            scope.launch {
                                store.setHttpLogEnabled(v)
                                com.chris.m3usuite.core.http.TrafficLogger.setEnabled(v)
                            }
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                        val dir = java.io.File(ctx.filesDir, "http-logs")
                        runCatching { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
                        scope.launch { snackHost.toast("HTTP-Logs gelöscht") }
                    }) { Text("HTTP-Logs löschen") }
                    Button(
                        modifier = Modifier.focusScaleOnTv(),
                        onClick = {
                            scope.launch {
                                val logsDir = java.io.File(ctx.filesDir, "http-logs")
                                val files = logsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
                                if (files.isEmpty()) {
                                    snackHost.toast("Keine HTTP-Logs vorhanden")
                                    return@launch
                                }
                                val exportDir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
                                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                                val zipFile = java.io.File(exportDir, "http-logs-$stamp.zip")
                                runCatching {
                                    java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(zipFile))).use { zos ->
                                        val buf = ByteArray(16 * 1024)
                                        for (f in files) {
                                            val entry = java.util.zip.ZipEntry(f.name)
                                            entry.time = f.lastModified()
                                            zos.putNextEntry(entry)
                                            java.io.BufferedInputStream(java.io.FileInputStream(f)).use { ins ->
                                                while (true) {
                                                    val n = ins.read(buf)
                                                    if (n <= 0) break
                                                    zos.write(buf, 0, n)
                                                }
                                            }
                                            zos.closeEntry()
                                        }
                                    }
                                }.onFailure {
                                    snackHost.toast("Export fehlgeschlagen: ${it.message ?: "Unbekannt"}")
                                    return@launch
                                }
                                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", zipFile)
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, zipFile.name)
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(android.content.Intent.createChooser(send, "HTTP-Logs exportieren"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                    ) { Text("HTTP-Logs exportieren/teilen…") }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("M3U/Xtream-Worker aktivieren", modifier = Modifier.weight(1f))
                    Switch(
                        checked = m3uWorkersEnabled,
                        onCheckedChange = { v -> scope.launch {
                            store.setM3uWorkersEnabled(v)
                            val ctxLocal = ctx
                            if (v) {
                                // Re-enable periodic scheduling immediately
                                // no periodic Xtream scheduling
                            } else {
                                // Cancel all existing Xtream work immediately
                                com.chris.m3usuite.work.SchedulingGateway.cancelXtreamWork(ctxLocal)
                            }
                        } }
                    )
                }
                // Adults global toggle
                val showAdults by store.showAdults.collectAsStateWithLifecycle(initialValue = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kategorie 'For Adults' anzeigen", modifier = Modifier.weight(1f))
                    Switch(
                        checked = showAdults,
                        onCheckedChange = { v -> scope.launch { store.setShowAdults(v) } }
                    )
                }
                val lastText = remember(lastImportAtMs) {
                    if (lastImportAtMs <= 0) "Noch kein Lauf"
                    else {
                        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        df.format(java.util.Date(lastImportAtMs))
                    }
                }
                Text("Letzter erfolgreicher Import: $lastText")
                Text("Seed: live=$lastSeedLive vod=$lastSeedVod series=$lastSeedSeries")
                val (currentLive, currentVod, currentSeries) = obxCounts
                Text("Aktuell (ObjectBox): live=$currentLive vod=$currentVod series=$currentSeries")
                Text("Delta: live=$lastDeltaLive vod=$lastDeltaVod series=$lastDeltaSeries")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.chris.m3usuite.ui.common.TvButton(
                        onClick = {
                        // Disabled: no automatic worker trigger; use manual import actions
                    }) { Text("Import jetzt (expedited)") }
                    com.chris.m3usuite.ui.common.TvTextButton(onClick = {
                        scope.launch {
                            val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                            com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive)
                        }
                    }) { Text("Favoriten‑EPG aktualisieren") }
                }
                
                
            }
            CollapsibleSection(
                title = "Profil & Gate",
                accent = accent,
                expanded = profileSectionExpanded,
                onExpandedChange = { profileSectionExpanded = it }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Letztes Profil merken", modifier = Modifier.weight(1f))
                    Switch(
                        checked = rememberLast,
                        onCheckedChange = { v -> scope.launch { store.setRememberLastProfile(v) } }
                    )
                }
                
                
            }
            CollapsibleSection(
                title = "Player",
                accent = accent,
                expanded = playerSectionExpanded,
                onExpandedChange = { playerSectionExpanded = it }
            ) {
                Column {
                    Radio("Immer fragen", mode == "ask") { scope.launch { store.setPlayerMode("ask") } }
                    Radio("Interner Player", mode == "internal") { scope.launch { store.setPlayerMode("internal") } }
                    Radio("Externer Player", mode == "external") { scope.launch { store.setPlayerMode("external") } }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pkg,
                        onValueChange = { scope.launch { store.set(Keys.PREF_PLAYER_PACKAGE, it) } },
                        label = { Text("Bevorzugtes externes Paket (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = tfColors
                    )
                    ExternalPlayerPickerButton(onPick = { packageName ->
                        scope.launch { store.set(Keys.PREF_PLAYER_PACKAGE, packageName) }
                    })
                }
                
            }
            CollapsibleSection(
                title = "Untertitel (interner Player)",
                accent = accent,
                expanded = subtitleSectionExpanded,
                onExpandedChange = { subtitleSectionExpanded = it }
            ) {
                Text("Größe")
                Slider(
                    value = subScale,
                    onValueChange = { v -> scope.launch { store.setFloat(Keys.SUB_SCALE, v) } },
                    valueRange = 0.04f..0.12f,
                    steps = 8
                )
                
                Text("Textfarbe")
                ColorRow(
                    selected = subFg,
                    onPick = { c -> scope.launch { store.setInt(Keys.SUB_FG, c) } },
                    palette = textPalette()
                )
                
                Text("Hintergrund")
                ColorRow(
                    selected = subBg,
                    onPick = { c -> scope.launch { store.setInt(Keys.SUB_BG, c) } },
                    palette = bgPalette()
                )
                
                Text("Text-Deckkraft: ${subFgOpacity}%")
                Slider(
                    value = subFgOpacity.toFloat(),
                    onValueChange = { v -> scope.launch { store.setSubtitleFgOpacityPct(v.toInt().coerceIn(0, 100)) } },
                    valueRange = 0f..100f,
                    steps = 10
                )
                Text("Hintergrund-Deckkraft: ${subBgOpacity}%")
                Slider(
                    value = subBgOpacity.toFloat(),
                    onValueChange = { v -> scope.launch { store.setSubtitleBgOpacityPct(v.toInt().coerceIn(0, 100)) } },
                    valueRange = 0f..100f,
                    steps = 10
                )
                
                // Vorschau
                OutlinedCard {
                    Box(Modifier.fillMaxWidth().height(180.dp)) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to MaterialTheme.colorScheme.surfaceVariant,
                                        1f to MaterialTheme.colorScheme.surface
                                    )
                                )
                        )
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val fg = Color(subFg)
                            val bg = Color(subBg)
                            val textSize = (subScale * 200f).coerceIn(10f, 28f).sp
                            Text(
                                "Untertitel-Vorschau",
                                color = fg.copy(alpha = (subFgOpacity / 100f)),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = textSize,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(bg.copy(alpha = (subBgOpacity / 100f)))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            CollapsibleSection(
                title = "App-PIN",
                accent = accent,
                expanded = pinSectionExpanded,
                onExpandedChange = { pinSectionExpanded = it }
            ) {
                if (!pinSet) {
                    Text("Es ist kein PIN gesetzt.")
                    Button(onClick = { pinDialogMode = PinMode.Set }) { Text("PIN festlegen…") }
                } else {
                    Text("PIN ist gesetzt.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pinDialogMode = PinMode.Change }) { Text("PIN ändern…") }
                        TextButton(onClick = { pinDialogMode = PinMode.Clear }) { Text("PIN entfernen") }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Landscape: Header standardmäßig eingeklappt", modifier = Modifier.weight(1f))
                    Switch(
                        checked = headerCollapsed,
                        onCheckedChange = { v -> scope.launch { store.setBool(Keys.HEADER_COLLAPSED_LAND, v) } }
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rotation in Player sperren (Landscape)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = rotationLocked,
                        onCheckedChange = { v -> scope.launch { store.setRotationLocked(v) } }
                    )
                }
                
            }
            CollapsibleSection(
                title = "Wiedergabe",
                accent = accent,
                expanded = playbackSectionExpanded,
                onExpandedChange = { playbackSectionExpanded = it }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Autoplay nächste Folge (Serie)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = autoplayNext,
                        onCheckedChange = { v -> scope.launch { store.setAutoplayNext(v) } }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Haptisches Feedback", modifier = Modifier.weight(1f))
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = { v -> scope.launch { store.setHapticsEnabled(v) } }
                    )
                }
                
            }
            CollapsibleSection(
                title = "Seeding (Regionen)",
                accent = accent,
                expanded = seedingSectionExpanded,
                onExpandedChange = { seedingSectionExpanded = it }
            ) {
                val ctx3 = LocalContext.current
                val obx = remember { com.chris.m3usuite.data.obx.ObxStore.get(ctx3) }
                // Build dynamic list of prefixes from current categories
                fun extractPrefix(name: String?): String? {
                    if (name.isNullOrBlank()) return null
                    var s = name.trim()
                    if (s.startsWith("[")) {
                        val idx = s.indexOf(']')
                        if (idx > 0) s = s.substring(1, idx)
                    }
                    val m = Regex("^([A-Z\\-]{2,6})").find(s.uppercase()) ?: return null
                    return m.groupValues[1].replace("-", "").trim().takeIf { it.isNotBlank() }
                }
                val availablePrefixes = remember {
                    val catBox = obx.boxFor(com.chris.m3usuite.data.obx.ObxCategory::class.java)
                    val cats = catBox.all.mapNotNull { extractPrefix(it.categoryName) }
                    (cats + listOf("DE","US","UK","VOD")).toSet().toList().sorted()
                }
                val seedCsv by store.seedPrefixesCsv.collectAsStateWithLifecycle(initialValue = "")
                val currentSet = remember(seedCsv) {
                    val def = setOf("DE","US","UK","VOD")
                    if (seedCsv.isBlank()) def else seedCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet().ifEmpty { def }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availablePrefixes.forEach { pfx ->
                        val checked = pfx in currentSet
                        FilterChip(
                            modifier = FocusKit.run { Modifier.tvClickable(onClick = {
                                val next = if (checked) currentSet - pfx else currentSet + pfx
                                scope.launch { store.setSeedPrefixesCsv(next.joinToString(",")) }
                            }, scaleFocused = 1f, scalePressed = 1f, brightenContent = false) },
                            selected = checked,
                            onClick = {
                                val next = if (checked) currentSet - pfx else currentSet + pfx
                                scope.launch { store.setSeedPrefixesCsv(next.joinToString(",")) }
                            },
                            label = { Text(pfx) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { scope.launch { store.setSeedPrefixesCsv("DE,US,UK,VOD,FOR") } }) { Text("Standard: DE/US/UK/VOD/FOR") }
                    TextButton(onClick = { scope.launch { store.setSeedPrefixesCsv(availablePrefixes.joinToString(",")) } }) { Text("Alle aktivieren") }
                }
                
                
                
            }
            CollapsibleSection(
                title = "Telegram",
                accent = accent,
                expanded = telegramSectionExpanded,
                onExpandedChange = { telegramSectionExpanded = it }
            ) {
                val tgEnabled by store.tgEnabled.collectAsStateWithLifecycle(initialValue = false)
                val ctx2 = LocalContext.current
                
                val bcApiId = remember {
                    runCatching {
                        val f = Class.forName(ctx2.packageName + ".BuildConfig").getDeclaredField("TG_API_ID"); f.isAccessible = true; (f.get(null) as? Int) ?: 0
                    }.getOrDefault(0)
                }
                val bcApiHash = remember {
                    runCatching {
                        val f = Class.forName(ctx2.packageName + ".BuildConfig").getDeclaredField("TG_API_HASH"); f.isAccessible = true; (f.get(null) as? String) ?: ""
                    }.getOrDefault("")
                }
                val overrideApiId by store.tgApiId.collectAsStateWithLifecycle(initialValue = 0)
                val overrideApiHash by store.tgApiHash.collectAsStateWithLifecycle(initialValue = "")
                val effApiId = if (overrideApiId > 0) overrideApiId else bcApiId
                val effApiHash = if (overrideApiHash.isNotBlank()) overrideApiHash else bcApiHash
                val authRepo = remember(effApiId, effApiHash) { com.chris.m3usuite.data.repo.TelegramAuthRepository(ctx2, effApiId, effApiHash) }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Telegram-Integration aktivieren (Beta)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = tgEnabled,
                        onCheckedChange = { v ->
                            scope.launch {
                                store.setTelegramEnabled(v)
                                if (v) {
                                    if (!authRepo.isAvailable()) {
                                        snackHost.toast("TDLib nicht verfügbar – Bibliotheken prüfen.")
                                    } else if (!authRepo.hasValidKeys()) {
                                        snackHost.toast("API-Keys fehlen – ID/HASH setzen.")
                                    } else {
                                        runCatching { authRepo.bindService(); authRepo.setInBackground(false); authRepo.start() }
                                            .onFailure { snackHost.toast("Start fehlgeschlagen: ${it.message ?: "Unbekannt"}") }
                                    }
                                } else {
                                    runCatching { authRepo.setInBackground(true); authRepo.unbindService() }
                                }
                            }
                        }
                    )
                }
                
                val tgChats by store.tgSelectedChatsCsv.collectAsStateWithLifecycle(initialValue = "")
                val tgVodSel by store.tgSelectedVodChatsCsv.collectAsStateWithLifecycle(initialValue = "")
                val tgSeriesSel by store.tgSelectedSeriesChatsCsv.collectAsStateWithLifecycle(initialValue = "")
                val tgCacheGb by store.tgCacheLimitGb.collectAsStateWithLifecycle(initialValue = 2)
                
                OutlinedTextField(
                    value = tgChats,
                    onValueChange = { scope.launch { store.setTelegramSelectedChatsCsv(it) } },
                    label = { Text("Chat-IDs (CSV), optional") },
                    singleLine = true,
                    enabled = tgEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tfColors
                )
                OutlinedTextField(
                    value = tgCacheGb.toString(),
                    onValueChange = { s -> s.toIntOrNull()?.let { g -> scope.launch { store.setTelegramCacheLimitGb(g.coerceIn(1, 20)) } } },
                    label = { Text("Cache-Limit (GB)") },
                    singleLine = true,
                    enabled = tgEnabled,
                    modifier = Modifier.width(220.dp),
                    colors = tfColors
                )
                
                if (tgEnabled) {
                    var showTgDialog by remember { mutableStateOf(false) }
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    val keysValid = effApiId > 0 && effApiHash.isNotBlank()
                
                    DisposableEffect(lifecycleOwner, tgEnabled) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (!tgEnabled) return@LifecycleEventObserver
                            when (event) {
                                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                                    authRepo.bindService()
                                    authRepo.setInBackground(false)
                                }
                                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                                    authRepo.setInBackground(true)
                                    authRepo.unbindService()
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        if (tgEnabled && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                            authRepo.bindService()
                            authRepo.setInBackground(false)
                        }
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            if (tgEnabled) runCatching { authRepo.setInBackground(true); authRepo.unbindService() }
                        }
                    }
                
                    val authState by authRepo.authState.collectAsStateWithLifecycle(initialValue = com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNKNOWN)
                
                    LaunchedEffect(tgEnabled) {
                        if (!tgEnabled) return@LaunchedEffect
                        authRepo.errors.collect { em -> snackHost.toast("Telegram: $em") }
                    }
                
                    var showLogout by remember { mutableStateOf(false) }
                    if (!keysValid) {
                        Spacer(Modifier.height(6.dp))
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
                                Text("API‑Keys fehlen – bitte API ID und API HASH setzen, um TDLib zu starten.")
                            }
                        }
                    }
                
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = keysValid,
                        onClick = {
                                if (!authRepo.isAvailable()) {
                                    scope.launch { snackHost.toast("TDLib nicht verfügbar – bitte Bibliotheken bündeln.") }
                                } else if (!authRepo.hasValidKeys()) {
                                    scope.launch { snackHost.toast("API-Keys fehlen – TG_API_ID/HASH setzen.") }
                                } else {
                                    val ok = runCatching { authRepo.start() }.getOrElse { e ->
                                        scope.launch { snackHost.toast("Telegram-Start fehlgeschlagen: ${e.message ?: "Unbekannter Fehler"}") }
                                        false
                                    }
                                    if (ok) showTgDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                        ) { Text("Telegram verbinden") }
                
                        TextButton(
                            enabled = keysValid,
                            onClick = {
                                if (!authRepo.hasValidKeys()) {
                                    scope.launch { snackHost.toast("API-Keys fehlen – bitte ID/HASH setzen.") }
                                } else {
                                    runCatching { authRepo.start() }
                                    authRepo.requestQrLogin(); showTgDialog = true
                                }
                            }
                        ) { Text("QR‑Login anfordern") }
                
                        TextButton(onClick = { showLogout = true }) { Text("Abmelden/Reset") }
                        TextButton(onClick = { val st = authRepo.authState.value; scope.launch { snackHost.toast("Telegram-Status: $st") } }) { Text("Status (Debug)") }
                    }
                
                    if (showLogout) {
                        var wipe by remember { mutableStateOf(true) }
                        AlertDialog(
                            onDismissRequest = { showLogout = false },
                            title = { Text("Telegram abmelden") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Willst du dich von Telegram abmelden?")
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = wipe, onCheckedChange = { wipe = it })
                                        Text("Lokalen Telegram-Cache löschen")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    authRepo.logout()
                                    if (wipe) com.chris.m3usuite.work.TelegramCacheCleanupWorker.wipeAll(ctx2)
                                    scope.launch { snackHost.toast("Abmeldung ausgelöst") }
                                    showLogout = false
                                }) { Text("Abmelden") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLogout = false }) { Text("Abbrechen") }
                            }
                        )
                    }
                
                    val maskedHash = if (effApiHash.isNotBlank()) effApiHash.take(4) + "…" else "(leer)"
                    Text("Status: $authState", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Text("Keys: ID=$effApiId  HASH=$maskedHash", style = MaterialTheme.typography.bodySmall, color = Color.White)
                
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = if (overrideApiId > 0) overrideApiId.toString() else "",
                            onValueChange = { s -> s.toIntOrNull()?.let { v -> scope.launch { store.setTelegramApiId(v) } } },
                            label = { Text("API ID (optional)") },
                            singleLine = true,
                            enabled = tgEnabled,
                            modifier = Modifier.width(200.dp)
                        )
                        OutlinedTextField(
                            value = overrideApiHash,
                            onValueChange = { s -> scope.launch { store.setTelegramApiHash(s.trim()) } },
                            label = { Text("API HASH (optional)") },
                            singleLine = true,
                            enabled = tgEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                
                    if (showTgDialog) TelegramLoginDialog(onDismiss = { showTgDialog = false }, repo = authRepo)
                
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        var showVodPicker by remember { mutableStateOf(false) }
                        var showSeriesPicker by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showVodPicker = true },
                            enabled = tgEnabled && authState == com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED,
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                        ) { Text("Film Sync") }
                        Button(
                            onClick = { showSeriesPicker = true },
                            enabled = tgEnabled && authState == com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED,
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                        ) { Text("Serien Sync") }
                        if (tgVodSel.isNotBlank()) {
                            TextButton(onClick = {
                                com.chris.m3usuite.work.SchedulingGateway.scheduleTelegramSync(
                                    ctx2,
                                    com.chris.m3usuite.work.TelegramSyncWorker.MODE_VOD,
                                    refreshHome = true
                                )
                            }) { Text("Filme synchronisieren") }
                        }
                        if (tgSeriesSel.isNotBlank()) {
                            TextButton(onClick = {
                                com.chris.m3usuite.work.SchedulingGateway.scheduleTelegramSync(
                                    ctx2,
                                    com.chris.m3usuite.work.TelegramSyncWorker.MODE_SERIES,
                                    refreshHome = true
                                )
                            }) { Text("Serien synchronisieren") }
                        }
                        if (showVodPicker) TelegramChatPickerDialog(onDismiss = { showVodPicker = false }, onSelect = { chatId ->
                            val cur = tgVodSel.split(',').filter { it.isNotBlank() }.toMutableSet()
                            cur.add(chatId.toString())
                            scope.launch { store.setTelegramSelectedVodChatsCsv(cur.joinToString(",")) }
                            showVodPicker = false
                        }, onRequestLogin = { showTgDialog = true })
                        if (showSeriesPicker) TelegramChatPickerDialog(onDismiss = { showSeriesPicker = false }, onSelect = { chatId ->
                            val cur = tgSeriesSel.split(',').filter { it.isNotBlank() }.toMutableSet()
                            cur.add(chatId.toString())
                            scope.launch { store.setTelegramSelectedSeriesChatsCsv(cur.joinToString(",")) }
                            showSeriesPicker = false
                        }, onRequestLogin = { showTgDialog = true })
                    }
                
                    if (tgVodSel.isNotBlank() || tgSeriesSel.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Quellen:", style = MaterialTheme.typography.labelLarge)
                    }
                    if (tgVodSel.isNotBlank()) {
                        val vodNamesState = remember(tgVodSel) { mutableStateOf<String?>(null) }
                        LaunchedEffect(tgVodSel) {
                            vodNamesState.value = resolveChatNamesCsv(tgVodSel, ctx2)
                        }
                        val label = vodNamesState.value?.takeIf { it.isNotBlank() } ?: tgVodSel
                        Text("• Filme: $label", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                    if (tgSeriesSel.isNotBlank()) {
                        val seriesNamesState = remember(tgSeriesSel) { mutableStateOf<String?>(null) }
                        LaunchedEffect(tgSeriesSel) {
                            seriesNamesState.value = resolveChatNamesCsv(tgSeriesSel, ctx2)
                        }
                        val label = seriesNamesState.value?.takeIf { it.isNotBlank() } ?: tgSeriesSel
                        Text("• Serien: $label", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                
                    val wm = remember { androidx.work.WorkManager.getInstance(ctx2) }
                    var vodProcessed by remember { mutableStateOf<Int?>(null) }
                    var seriesProcessed by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(tgEnabled) {
                        if (!tgEnabled) return@LaunchedEffect
                        while (true) {
                            try {
                                val vodInfos = withContext(Dispatchers.IO) { wm.getWorkInfosForUniqueWork("tg_sync_vod").get() }
                                val vi = vodInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                                vodProcessed = vi?.progress?.getInt("processed", -1)?.takeIf { it >= 0 }
                                val seriesInfos = withContext(Dispatchers.IO) { wm.getWorkInfosForUniqueWork("tg_sync_series").get() }
                                val si = seriesInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                                seriesProcessed = si?.progress?.getInt("processed", -1)?.takeIf { it >= 0 }
                            } catch (_: Throwable) {}
                            kotlinx.coroutines.delay(600)
                        }
                    }
                    if (vodProcessed != null || seriesProcessed != null) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        val vp = vodProcessed?.let { "Filme: $it" } ?: ""
                        val sp = seriesProcessed?.let { "Serien: $it" } ?: ""
                        Text(
                            "Telegram Sync läuft… ${listOf(vp, sp).filter { it.isNotBlank() }.joinToString("  ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
                
                com.chris.m3usuite.backup.QuickImportRow()
                
                com.chris.m3usuite.backup.BackupRestoreSection()
                
                // --- M3U Export ---
                
            }
            CollapsibleSection(
                title = "M3U Export",
                accent = accent,
                expanded = exportSectionExpanded,
                onExpandedChange = { exportSectionExpanded = it }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ctxLocal = LocalContext.current
            Button(
                modifier = Modifier.focusScaleOnTv(),
                onClick = {
                            scope.launch {
                                val dir = java.io.File(ctxLocal.cacheDir, "exports").apply { mkdirs() }
                                val file = java.io.File(dir, "playlist.m3u")
                                java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8).use { w ->
                                    com.chris.m3usuite.core.m3u.M3UExporter.stream(ctxLocal, store, w)
                                }
                                val uri = androidx.core.content.FileProvider.getUriForFile(ctxLocal, ctxLocal.packageName + ".fileprovider", file)
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/x-mpegURL"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "playlist.m3u")
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctxLocal.startActivity(android.content.Intent.createChooser(send, "M3U teilen"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                    ) { Text("Teilen…") }
                
                    Button(
                        onClick = {
                            scope.launch {
                                val dir = java.io.File(ctxLocal.cacheDir, "exports").apply { mkdirs() }
                                val file = java.io.File(dir, "playlist.m3u")
                                java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8).use { w ->
                                    com.chris.m3usuite.core.m3u.M3UExporter.stream(ctxLocal, store, w)
                                }
                                val uri = androidx.core.content.FileProvider.getUriForFile(ctxLocal, ctxLocal.packageName + ".fileprovider", file)
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/x-mpegURL"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "playlist.m3u")
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctxLocal.startActivity(android.content.Intent.createChooser(send, "M3U speichern/teilen"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                    ) { Text("Als Datei speichern…") }
                }
            }
        }
    }

    // PIN Dialog host
    pinDialogMode?.let { activePinMode ->
        PinDialogHost(store = store, mode = activePinMode) { pinDialogMode = null }
    }
}

@Composable
private fun TvEditDialog(
    initial: String,
    label: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun QrCodeBox(data: String, sizeDp: Int = 200) {
    val sizePx = with(LocalDensity.current) { sizeDp.dp.toPx().toInt() }
    val bmp = remember(data, sizePx) { generateQrBitmap(data, sizePx, sizePx) }
    if (bmp != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painter = BitmapPainter(bmp.asImageBitmap()), contentDescription = "QR Code", modifier = Modifier.size(sizeDp.dp))
        }
    }
}

private fun generateQrBitmap(text: String, width: Int, height: Int): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val bmp = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bmp
    } catch (_: Throwable) { null }
}

@Composable
private fun TelegramLoginDialog(onDismiss: () -> Unit, repo: com.chris.m3usuite.data.repo.TelegramAuthRepository) {
    val ctx = LocalContext.current
    val state by repo.authState.collectAsStateWithLifecycle(initialValue = com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNKNOWN)
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var didCheckDbKey by remember { mutableStateOf(false) }
    var qrLink by remember { mutableStateOf<String?>(null) }
    val hasTelegramApp = remember { isTelegramInstalled(ctx) }
    var useCurrentDevice by remember { mutableStateOf(hasTelegramApp) }
    var autoLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repo.qrLinks.collect { link -> qrLink = link }
    }
    val title = when (state) {
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_NUMBER,
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNAUTHENTICATED,
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> "Telegram verbinden"
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_CODE -> "Bestätigungscode"
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_PASSWORD -> "Passwort"
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_OTHER_DEVICE -> "QR‑Bestätigung"
        com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED -> "Verbunden"
        else -> "Telegram"
    }
    LaunchedEffect(state) {
        when (state) {
            com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> {
                if (!didCheckDbKey) {
                    busy = true
                    repo.checkDbKey()
                    didCheckDbKey = true
                }
            }
            com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_CODE,
            com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_PASSWORD,
            com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED -> busy = false
            else -> {}
        }
    }

    LaunchedEffect(state, qrLink) {
        if (state == com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_OTHER_DEVICE) {
            val link = qrLink
            if (!link.isNullOrBlank() && hasTelegramApp && !autoLaunched) {
                autoLaunched = true
                kotlin.runCatching {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
            }
        } else {
            autoLaunched = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_NUMBER, com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNAUTHENTICATED -> {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Telefonnummer") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = TextFieldDefaults.colors()
                        )
                        Text("Gib deine Telefonnummer im internationalen Format ein (z. B. +491701234567)", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text("Oder melde dich per QR an: In der Telegram‑App auf deinem Smartphone zu Einstellungen → Geräte → Gerät verbinden gehen.", style = MaterialTheme.typography.bodySmall)
                        if (hasTelegramApp) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = useCurrentDevice, onCheckedChange = { useCurrentDevice = it })
                                Spacer(Modifier.width(8.dp))
                                Text("Code direkt auf diesem Gerät bestätigen (Telegram-App installiert)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Datenbank‑Schlüssel wird überprüft…", style = MaterialTheme.typography.bodySmall)
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_CODE -> {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Bestätigungscode") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors()
                        )
                        Text("Den Code aus der Telegram-App eingeben.", style = MaterialTheme.typography.bodySmall)
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_PASSWORD -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Passwort") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = TextFieldDefaults.colors()
                        )
                        Text("Zwei-Faktor-Passwort eingeben (falls aktiviert).", style = MaterialTheme.typography.bodySmall)
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED -> {
                        Text("Erfolgreich verbunden.")
                    }
                    com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_OTHER_DEVICE -> {
                        Text("Warten auf Bestätigung auf einem anderen Gerät.", style = MaterialTheme.typography.bodySmall)
                        val link = qrLink
                        if (!link.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            QrCodeBox(data = link)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val ctx = LocalContext.current
                                Text(link, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    val clip = android.content.ClipData.newPlainText("tg_login", link)
                                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(clip)
                                }) { Text("Link kopieren") }
                                TextButton(onClick = {
                                    kotlin.runCatching {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        ctx.startActivity(intent)
                                    }
                                }) { Text("In Telegram öffnen") }
                                TextButton(onClick = { repo.requestQrLogin() }) { Text("Neu laden") }
                            }
                            if (hasTelegramApp) {
                                Spacer(Modifier.height(4.dp))
                                Text("Telegram ist auf diesem Gerät installiert – sobald du oben auf \"In Telegram öffnen\" tippst (oder der Dialog automatisch erscheint), kannst du die Anmeldung ohne zweites Gerät bestätigen.", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text("Öffne Telegram auf deinem Smartphone → Einstellungen → Geräte → QR‑Code scannen.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { repo.requestQrLogin() }) { Text("QR erneut anfordern") }
                        }
                    }
                    else -> {
                        Text("Warte auf Status…")
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Warte auf Antwort…", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
        },
        confirmButton = {
            when (state) {
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_NUMBER, com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNAUTHENTICATED -> {
                    TextButton(onClick = {
                        if (repo.start() && phone.isNotBlank()) { busy = true; repo.sendPhoneNumber(phone, useCurrentDevice) }
                    }) { Text("Weiter") }
                }
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_CODE -> {
                    TextButton(onClick = { if (code.isNotBlank()) { busy = true; repo.sendCode(code) } }) { Text("Bestätigen") }
                }
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_FOR_PASSWORD -> {
                    TextButton(onClick = { if (password.isNotBlank()) { busy = true; repo.sendPassword(password) } }) { Text("Bestätigen") }
                }
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY -> {
                    TextButton(onClick = { }) { Text("Weiter") }
                }
                com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED -> {
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
                else -> { TextButton(onClick = {}) { Text("OK") } }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    phone = ""; code = ""; password = ""; busy = false
                    onDismiss()
                }) { Text("Abbrechen") }
                if (state == com.chris.m3usuite.telegram.TdLibReflection.AuthState.WAIT_OTHER_DEVICE) {
                    TextButton(onClick = { repo.requestQrLogin() }) { Text("Erneut versuchen") }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramChatPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onRequestLogin: () -> Unit
) {
    val ctx = LocalContext.current
    val authState = remember { mutableStateOf(TdLibReflection.AuthState.UNKNOWN) }
    val chatsMain = remember { mutableStateListOf<Pair<Long, String>>() }
    val chatsArchive = remember { mutableStateListOf<Pair<Long, String>>() }
    var loading by remember { mutableStateOf(true) }
    var folder by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("main") }
    var search by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val svc = remember { com.chris.m3usuite.telegram.service.TelegramServiceClient(ctx) }
    DisposableEffect(Unit) {
        svc.bind()
        onDispose { svc.unbind() }
    }

    LaunchedEffect(Unit) {
        svc.getAuth()
        svc.authStates().collect { st ->
            runCatching { TdLibReflection.AuthState.valueOf(st) }
                .getOrNull()
                ?.let { authState.value = it }
        }
    }

    LaunchedEffect(authState.value) {
        if (authState.value == TdLibReflection.AuthState.AUTHENTICATED) {
            loading = true
            val main = runCatching { svc.listChats("main", 200) }.getOrDefault(emptyList())
            val archive = runCatching { svc.listChats("archive", 200) }.getOrDefault(emptyList())
            chatsMain.clear(); chatsMain.addAll(main)
            chatsArchive.clear(); chatsArchive.addAll(archive)
            loading = false
        } else {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Telegram – Chat-Liste", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (authState.value != TdLibReflection.AuthState.AUTHENTICATED) {
                Text("Bitte zuerst Telegram verbinden.", style = MaterialTheme.typography.bodySmall, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRequestLogin() },
                        colors = ButtonDefaults.buttonColors(containerColor = com.chris.m3usuite.ui.theme.DesignTokens.Accent, contentColor = Color.Black)
                    ) { Text("Jetzt verbinden") }
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(label = { Text("Hauptordner") }, onClick = { folder = "main" }, leadingIcon = null, enabled = !loading)
                    AssistChip(label = { Text("Archiv") }, onClick = { folder = "archive" }, leadingIcon = null, enabled = !loading)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Suchen…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Lade Chats…", style = MaterialTheme.typography.bodySmall)
                } else {
                    val itemsBase = if (folder == "archive") chatsArchive else chatsMain
                    val q = search.trim().lowercase()
                    val items = if (q.isBlank()) itemsBase else itemsBase.filter { it.second.lowercase().contains(q) || it.first.toString().contains(q) }
                    val chatListState = com.chris.m3usuite.ui.state.rememberRouteListState("settings:chatPicker:${folder}")
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), state = chatListState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(items, key = { it.first }) { (id, title) ->
                            ElevatedCard(onClick = { onSelect(id) }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(title)
                                    Text(id.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        }
    }
}

private fun isTelegramInstalled(context: android.content.Context): Boolean {
    val pm = context.packageManager
    val packages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "org.telegram.plus"
    )
    return packages.any { pkg ->
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
        }.isSuccess
    }
}

// --- External Player Picker UI ---
// Context-based resolver (actual implementation)
private suspend fun resolveChatNamesCsv(csv: String, context: android.content.Context): String {
    return try {
        val ids = csv.split(',').mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return csv
        val svc = com.chris.m3usuite.telegram.service.TelegramServiceClient(context)
        try {
            svc.bind()
            val store = com.chris.m3usuite.prefs.SettingsStore(context)
            val apiId = store.tgApiId.first().takeIf { it > 0 } ?: BuildConfig.TG_API_ID
            val apiHash = store.tgApiHash.first().ifBlank { BuildConfig.TG_API_HASH }
            if (apiId > 0 && apiHash.isNotBlank()) {
                svc.start(apiId, apiHash)
                svc.getAuth()
            }
            val titles = svc.resolveChatTitles(ids.toLongArray())
            titles.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.second }
                ?: csv
        } finally {
            svc.unbind()
        }
    } catch (_: Throwable) { csv }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalPlayerPickerButton(onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text("Externen Player auswählen…") }
    if (!show) return
    val pm = ctx.packageManager
    val list = remember(pm, ctx.packageName) {
        val intents = listOf(
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { type = "video/*" },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType("http://example.com/sample.m3u8".toUri(), "application/vnd.apple.mpegurl") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType("http://example.com/sample.m3u8".toUri(), "application/x-mpegurl") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType("http://example.com/sample.mpd".toUri(), "application/dash+xml") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType("http://example.com/sample.ts".toUri(), "video/MP2T") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType("http://example.com/sample.mp4".toUri(), "video/mp4") },
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { data = "http://example.com/stream".toUri() }
        )
        val flagSets = listOf(0, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val results = mutableListOf<android.content.pm.ResolveInfo>()
        for (intent in intents) {
            for (flags in flagSets) {
                try {
                    results += pm.queryIntentActivities(intent, flags)
                } catch (_: Throwable) { /* ignore */ }
            }
        }
        results
            .filter { it.activityInfo?.packageName != null && it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm)?.toString() ?: it.activityInfo.packageName }
    }
    ModalBottomSheet(onDismissRequest = { show = false }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Installierte Player")
            list.forEach { ri ->
                val label = ri.loadLabel(pm)?.toString() ?: ri.activityInfo.packageName
                Button(onClick = { onPick(ri.activityInfo.packageName); show = false }) { Text(label) }
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    accent: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionCard(accent = accent, modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .focusScaleOnTv()
                    .then(
                        Modifier.tvClickable(
                            role = Role.Button,
                            brightenContent = false,
                            autoBringIntoView = false
                        ) { onExpandedChange(!expanded) }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Einklappen" else "Ausklappen"
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier.border(BorderStroke(1.dp, accent.copy(alpha = 0.25f)), shape = MaterialTheme.shapes.medium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

// --- PIN dialogs ---
private enum class PinMode { Set, Change, Clear }

@Composable
private fun PinDialogHost(store: SettingsStore, mode: PinMode, onDismissed: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    var pin2 by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var old by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var error by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (!open) { onDismissed(); return }
    val dlgColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.6f),
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    )
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(when (mode) { PinMode.Set -> "PIN festlegen"; PinMode.Change -> "PIN ändern"; PinMode.Clear -> "PIN entfernen" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode != PinMode.Set) {
                    OutlinedTextField(value = old, onValueChange = { old = it }, label = { Text("Aktuelle PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = dlgColors)
                }
                if (mode != PinMode.Clear) {
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Neue PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = dlgColors)
                    OutlinedTextField(value = pin2, onValueChange = { pin2 = it }, label = { Text("Wiederholen") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = dlgColors)
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (mode) {
                        PinMode.Set -> {
                            if (pin.isBlank() || pin != pin2) { error = "PINs stimmen nicht"; return@launch }
                            val h = sha256(pin)
                            store.setAdultPinHash(h); store.setAdultPinSet(true)
                            open = false
                        }
                        PinMode.Change -> {
                            val current = store.adultPinHash.first()
                            val ok = sha256(old) == current
                            if (!ok) { error = "Falsche aktuelle PIN"; return@launch }
                            if (pin.isBlank() || pin != pin2) { error = "PINs stimmen nicht"; return@launch }
                            val h = sha256(pin)
                            store.setAdultPinHash(h); store.setAdultPinSet(true)
                            open = false
                        }
                        PinMode.Clear -> {
                            val current = store.adultPinHash.first()
                            val ok = sha256(old) == current
                            if (!ok) { error = "Falsche aktuelle PIN"; return@launch }
                            store.setAdultPinHash(""); store.setAdultPinSet(false)
                            open = false
                        }
                    }
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } }
    )
}

private fun sha256(s: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val dig = md.digest(s.toByteArray())
    return dig.joinToString("") { b -> "%02x".format(b) }
}

@Composable
private fun Radio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit, palette: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        palette.forEach { c ->
            val border = if (selected == c) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            if (border != null) {
                OutlinedCard(border = border, modifier = Modifier.size(36.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .selectable(selected = selected == c, role = Role.Button, onClick = { onPick(c) })
                    )
                }
            } else {
                OutlinedCard(modifier = Modifier.size(36.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .selectable(selected = selected == c, role = Role.Button, onClick = { onPick(c) })
                    )
                }
            }
        }
    }
}

private fun textPalette(): List<Int> = listOf(
    Color.White.copy(alpha = 0.95f).toArgb(),
    Color.Yellow.copy(alpha = 0.95f).toArgb(),
    Color.Cyan.copy(alpha = 0.95f).toArgb(),
    Color.Black.copy(alpha = 0.95f).toArgb(),
    Color.Red.copy(alpha = 0.95f).toArgb(),
    Color.Green.copy(alpha = 0.95f).toArgb(),
    Color.Blue.copy(alpha = 0.95f).toArgb(),
    Color.Magenta.copy(alpha = 0.95f).toArgb(),
)

private fun bgPalette(): List<Int> = listOf(
    Color.Black.copy(alpha = 0.50f).toArgb(),
    Color.White.copy(alpha = 0.50f).toArgb(),
    Color.Yellow.copy(alpha = 0.50f).toArgb(),
    Color.Cyan.copy(alpha = 0.50f).toArgb(),
    Color.Red.copy(alpha = 0.50f).toArgb(),
    Color.Green.copy(alpha = 0.50f).toArgb(),
    Color.Blue.copy(alpha = 0.50f).toArgb(),
    Color.Magenta.copy(alpha = 0.50f).toArgb(),
)
