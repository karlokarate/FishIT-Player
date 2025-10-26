
package com.chris.m3usuite.ui.models.telegram

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import java.util.EnumMap
import com.chris.m3usuite.ui.layout.FishFormSection
import com.chris.m3usuite.ui.layout.FishFormSelect
import com.chris.m3usuite.ui.layout.FishFormSwitch
import com.chris.m3usuite.ui.layout.FishFormTextField
import com.chris.m3usuite.ui.layout.TvKeyboard
import com.chris.m3usuite.ui.common.TvButton
import com.chris.m3usuite.ui.common.TvOutlinedButton

@Composable
fun TelegramSettingsSection(
    vm: TelegramSettingsViewModel = viewModel(factory = TelegramSettingsViewModel.Factory(LocalContext.current.applicationContext as Application)),
    proxyVm: TelegramProxyViewModel = viewModel(factory = TelegramProxyViewModel.Factory(LocalContext.current.applicationContext as Application)),
    autoVm: TelegramAutoDownloadViewModel = viewModel(factory = TelegramAutoDownloadViewModel.Factory(LocalContext.current.applicationContext as Application))
) {
    LocalContext.current
    val ui by vm.state.collectAsStateWithLifecycle()
    val proxy by proxyVm.state.collectAsStateWithLifecycle()
    val auto by autoVm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    rememberCoroutineScope()
    var qrLink by remember { mutableStateOf<String?>(null) }

    var apiIdText by remember { mutableStateOf("") }
    var apiHashText by remember { mutableStateOf("") }
    LaunchedEffect(ui.overrideApiId) {
        apiIdText = if (ui.overrideApiId > 0) ui.overrideApiId.toString() else ""
    }
    LaunchedEffect(ui.overrideApiHash) {
        apiHashText = ui.overrideApiHash
    }

    // Effects (snackbar)
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                is TelegramEffect.Snackbar -> snackbarHost.showSnackbar(eff.message)
                is TelegramEffect.ShowQr -> qrLink = eff.link
            }
        }
    }

    val openTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.onIntent(TelegramIntent.SetLogDir(uri))
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Telegram", style = MaterialTheme.typography.titleMedium)

            // Enabled
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Aktivieren")
                    Text("Telegram-Integration aktivieren/deaktivieren", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = ui.enabled,
                    onCheckedChange = { vm.onIntent(TelegramIntent.SetEnabled(it)) }
                )
            }

            Divider()

            // Login block
            LoginBlock(ui = ui, onIntent = vm::onIntent)

            Divider()

            // API Keys override
            FishFormSection(
                title = "API-Schlüssel (Override)",
                description = "Überschreibt die BuildConfig-Werte falls eigene Schlüssel benötigt werden."
            ) {
                if (ui.apiKeysMissing) {
                    Text(
                        "Aktuelle API-Schlüssel fehlen – ohne gültige ID/HASH verweigert TDLib die Anmeldung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                FishFormTextField(
                    label = "API ID",
                    value = apiIdText,
                    onValueChange = {
                        apiIdText = it
                        vm.onIntent(TelegramIntent.SetApiId(it))
                    },
                    keyboard = TvKeyboard.Number,
                    helperText = "BuildConfig: ${if (ui.buildApiId > 0) ui.buildApiId else "leer"}"
                )
                FishFormTextField(
                    label = "API HASH",
                    value = apiHashText,
                    onValueChange = {
                        apiHashText = it
                        vm.onIntent(TelegramIntent.SetApiHash(it))
                    },
                    keyboard = TvKeyboard.Password,
                    helperText = "BuildConfig: ${if (ui.buildApiHash.isNotBlank()) ui.buildApiHash else "leer"}"
                )
                Text(
                    "Effektiv aktiv: ID=${ui.effectiveApiId} · HASH=${if (ui.effectiveApiHash.isNotBlank()) ui.effectiveApiHash.take(4) + "…" else "leer"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.apiKeysMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Status: ${ui.auth}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.auth == TelegramServiceClient.AuthState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider()

            // Chat selector + selected chips
            ChatPickerBlock(vm = vm, selected = ui.selected)

            SyncStatusBlock(isRunning = ui.isSyncRunning, progress = ui.syncProgress)

            Divider()

            // Proxy
            ProxyBlock(state = proxy, onChange = proxyVm::onChange, onApply = proxyVm::applyNow)

            Divider()

            // Auto-Download
            AutoDownloadBlock(state = auto, onToggleWifi = { e, l, n, s, c -> autoVm.setWifi(e, l, n, s, c) },
                onToggleMobile = { e, l, n, s, c -> autoVm.setMobile(e, l, n, s, c) },
                onToggleRoaming = { e, l, n, s, c -> autoVm.setRoaming(e, l, n, s, c) }
            )

            Divider()

            // Cache/Tools
            Text("Cache / Tools", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HTTP Log Level", modifier = Modifier.weight(1f))
                    Slider(
                        value = ui.httpLogLevel.toFloat(),
                        onValueChange = { vm.onIntent(TelegramIntent.SetHttpLogLevel(it.toInt())) },
                        valueRange = 0f..5f,
                        steps = 4,
                        modifier = Modifier.weight(2f)
                    )
                    Text("${ui.httpLogLevel}", modifier = Modifier.padding(start = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvOutlinedButton(onClick = { openTreePicker.launch(null) }) {
                        Text(if (ui.logDir.isBlank()) "Log-Verzeichnis wählen" else "Log-Verzeichnis ändern")
                    }
                    TvOutlinedButton(onClick = { vm.optimizeStorage() }) {
                        Text("Speicher optimieren")
                    }
                }
                TvOutlinedButton(onClick = { vm.onIntent(TelegramIntent.ProbeVersion) }) {
                    Text("TDLib-Version prüfen")
                }
                val versionInfo = ui.versionInfo
                val versionSummary = remember(versionInfo) {
                    when (versionInfo) {
                        null -> "Noch nicht abgefragt."
                        else -> {
                            val appValue = versionInfo.version ?: versionInfo.versionError ?: "n/a"
                            val nativeValue = versionInfo.nativeVersion ?: versionInfo.nativeError ?: "n/a"
                            val commitValue = versionInfo.commit ?: versionInfo.commitError ?: "-"
                            "Version: $appValue • Native: $nativeValue • Commit: $commitValue"
                        }
                    }
                }
                Text(versionSummary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // local snackbar host to surface effects from VM
    Spacer(Modifier.height(4.dp))
    SnackbarHost(hostState = snackbarHost)

    if (qrLink != null) {
        TelegramQrDialog(link = qrLink!!, onDismiss = { qrLink = null })
    }
}

@Composable
private fun LoginBlock(ui: TelegramUiState, onIntent: (TelegramIntent) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    FishFormSection(title = "Anmeldung") {
        when (ui.auth) {
            TelegramServiceClient.AuthState.Idle, TelegramServiceClient.AuthState.Error -> {
                FishFormTextField(
                    label = "Telefonnummer",
                    value = phone,
                    onValueChange = { phone = it },
                    placeholder = "+49… / 0049…",
                    helperText = "Mit Landesvorwahl eingeben",
                    keyboard = TvKeyboard.Default
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        onClick = { onIntent(TelegramIntent.RequestCode(phone.trim())) },
                        enabled = phone.isNotBlank()
                    ) { Text("Code anfordern") }
                    TvOutlinedButton(onClick = { onIntent(TelegramIntent.RequestQr) }) { Text("QR‑Login") }
                }
            }
            TelegramServiceClient.AuthState.CodeSent -> {
                FishFormTextField(
                    label = "Bestätigungscode",
                    value = code,
                    onValueChange = { code = it },
                    keyboard = TvKeyboard.Number
                )
                val left = ui.resendLeftSec
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        onClick = { onIntent(TelegramIntent.SubmitCode(code.trim())) },
                        enabled = code.isNotBlank()
                    ) { Text("Code senden") }
                    TvOutlinedButton(
                        onClick = { onIntent(TelegramIntent.ResendCode) },
                        enabled = left <= 0
                    ) {
                        Text(if (left > 0) "Erneut senden (${left}s)" else "Erneut senden")
                    }
                }
            }
            TelegramServiceClient.AuthState.PasswordRequired -> {
                FishFormTextField(
                    label = "2FA‑Passwort",
                    value = pass,
                    onValueChange = { pass = it },
                    keyboard = TvKeyboard.Password
                )
                TvButton(
                    onClick = { onIntent(TelegramIntent.SubmitPassword(pass)) },
                    enabled = pass.isNotBlank()
                ) { Text("Passwort senden") }
            }
            TelegramServiceClient.AuthState.SignedIn -> {
                TvButton(
                    onClick = { onIntent(TelegramIntent.StartFullSync) },
                    enabled = !ui.isSyncRunning
                ) { Text("Voll‑Sync ausführen") }
                Text(
                    if (ui.isSyncRunning) "Synchronisation läuft …" else "Angemeldet",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TelegramServiceClient.AuthState.Error -> {
                // handled in snackbar/effects
            }
        }
    }
}

@Composable
private fun TelegramQrDialog(link: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val qrBitmap = remember(link) { generateQrBitmap(link, 512, 512) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Telegram-QR") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Scanne den Code in der Telegram-App oder kopiere den Link.", style = MaterialTheme.typography.bodyMedium)
                if (qrBitmap != null) {
                    Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "Telegram QR", modifier = Modifier.size(220.dp))
                } else {
                    Text("QR konnte nicht erzeugt werden.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard?.setText(AnnotatedString(link))
                onDismiss()
            }) { Text("Link kopieren") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

private fun generateQrBitmap(data: String, width: Int, height: Int): Bitmap? {
    return runCatching {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
        }
        val bitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, width, height, hints)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    }.getOrElse { null }
}

@Composable
private fun SyncStatusBlock(isRunning: Boolean, progress: TelegramSyncProgressUi?) {
    if (!isRunning && progress == null) return
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Synchronisierung", style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        val status = when {
            progress != null && progress.totalChats > 0 ->
                "Telegram Sync läuft… ${progress.processedChats} / ${progress.totalChats}"
            isRunning -> "Telegram Sync läuft…"
            else -> ""
        }
        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChatPickerBlock(vm: TelegramSettingsViewModel, selected: List<ChatUi>) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ausgewählte Chats", style = MaterialTheme.typography.titleSmall)
        if (selected.isEmpty()) {
            Text("Keine Chats ausgewählt.")
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { chat -> AssistChip(onClick = {}, label = { Text(chat.title) }) }
            }
        }
        TvOutlinedButton(onClick = { open = true }) { Text("Chats auswählen…") }
    }
    if (open) {
        ChatPickerDialog(vm = vm, onDismiss = { open = false }) { ids ->
            vm.onIntent(TelegramIntent.ConfirmChats(ids))
            open = false
        }
    }
}

@Composable
private fun ChatPickerDialog(
    vm: TelegramSettingsViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    rememberCoroutineScope()
    var listTag by remember { mutableStateOf("main") } // "main"| "archive" | "folder:ID"
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<ChatUi>()) }
    var folders by remember { mutableStateOf(intArrayOf()) }
    val selection = remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(listTag, query) {
        items = vm.listChats(list = listTag, query = query)
    }
    LaunchedEffect(Unit) {
        folders = vm.listFolders()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(selection.value) }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text("Chats auswählen") },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                // folders + lists
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = listTag == "main", onClick = { listTag = "main" }, label = { Text("Haupt") })
                    FilterChip(selected = listTag == "archive", onClick = { listTag = "archive" }, label = { Text("Archiv") })
                    folders.forEach { id ->
                        val tag = "folder:$id"
                        FilterChip(selected = listTag == tag, onClick = { listTag = tag }, label = { Text("Ordner $id") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Suchen…") }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 300.dp)) {
                    items(items) { chat ->
                        val checked = selection.value.contains(chat.id)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = {
                                selection.value = if (it) selection.value + chat.id else selection.value - chat.id
                            })
                            Text(chat.title, modifier = Modifier.weight(1f))
                            Text("#${chat.id}")
                        }
                    }
                }
            }
        }
    )
}

// Simple FlowRow replacement (Compose 1.6+ has androidx.compose.foundation.layout.FlowRow)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = horizontalArrangement) {
            content()
        }
    }
}

@Composable
private fun ProxyBlock(
    state: ProxyUiState,
    onChange: (String?, String?, Int?, String?, String?, String?, Boolean?) -> Unit,
    onApply: () -> Unit
) {
    val typeOptions = listOf("none", "socks5", "http", "mtproto")
    val selectedType = state.type.takeIf { it in typeOptions } ?: "none"
    val editable = selectedType != "none"

    FishFormSection(title = "Proxy") {
        FishFormSelect(
            label = "Proxy-Typ",
            options = typeOptions,
            selected = selectedType,
            onSelected = { newType -> onChange(newType, null, null, null, null, null, null) },
            optionLabel = { opt ->
                when (opt) {
                    "socks5" -> "SOCKS5"
                    "http" -> "HTTP"
                    "mtproto" -> "MTProto"
                    else -> "Kein Proxy"
                }
            }
        )

        FishFormTextField(
            label = "Proxy-Host",
            value = state.host,
            onValueChange = { onChange(null, it.trim(), null, null, null, null, null) },
            helperText = "Hostname oder IP-Adresse",
            enabled = editable && !state.isApplying
        )

        FishFormTextField(
            label = "Proxy-Port",
            value = state.port.takeIf { it > 0 }?.toString().orEmpty(),
            onValueChange = { newValue ->
                val port = newValue.toIntOrNull()?.coerceIn(0, 65535) ?: 0
                onChange(null, null, port, null, null, null, null)
            },
            keyboard = TvKeyboard.Number,
            helperText = "0–65535",
            enabled = editable && !state.isApplying
        )

        FishFormTextField(
            label = "Benutzername",
            value = state.username,
            onValueChange = { onChange(null, null, null, it.trim(), null, null, null) },
            enabled = editable && !state.isApplying
        )

        FishFormTextField(
            label = "Passwort",
            value = state.password,
            onValueChange = { onChange(null, null, null, null, it, null, null) },
            keyboard = TvKeyboard.Password,
            enabled = editable && !state.isApplying
        )

        if (selectedType == "mtproto") {
            FishFormTextField(
                label = "MTProto-Secret",
                value = state.secret,
                onValueChange = { onChange(null, null, null, null, null, it.trim(), null) },
                keyboard = TvKeyboard.Password,
                enabled = editable && !state.isApplying
            )
        }

        FishFormSwitch(
            label = "Proxy aktiv",
            checked = state.enabled,
            onCheckedChange = { onChange(null, null, null, null, null, null, it) },
            enabled = editable && !state.isApplying
        )

        TvButton(
            onClick = onApply,
            enabled = editable && !state.isApplying
        ) {
            Text(if (state.isApplying) "Übernehme…" else "Anwenden")
        }
    }
}

@Composable
private fun AutoDownloadBlock(
    state: AutoUiState,
    onToggleWifi: (Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onToggleMobile: (Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onToggleRoaming: (Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Auto‑Download", style = MaterialTheme.typography.titleSmall)
        NetworkAutoCard(
            title = "WLAN",
            enabled = state.wifiEnabled, preloadLarge = state.wifiPreloadLarge, preloadNext = state.wifiPreloadNextAudio,
            preloadStories = state.wifiPreloadStories, lessDataCalls = state.wifiLessDataCalls,
            onEnabled = { onToggleWifi(it, null, null, null, null) },
            onPreloadLarge = { onToggleWifi(null, it, null, null, null) },
            onPreloadNext = { onToggleWifi(null, null, it, null, null) },
            onPreloadStories = { onToggleWifi(null, null, null, it, null) },
            onLessDataCalls = { onToggleWifi(null, null, null, null, it) }
        )
        NetworkAutoCard(
            title = "Mobil",
            enabled = state.mobileEnabled, preloadLarge = state.mobilePreloadLarge, preloadNext = state.mobilePreloadNextAudio,
            preloadStories = state.mobilePreloadStories, lessDataCalls = state.mobileLessDataCalls,
            onEnabled = { onToggleMobile(it, null, null, null, null) },
            onPreloadLarge = { onToggleMobile(null, it, null, null, null) },
            onPreloadNext = { onToggleMobile(null, null, it, null, null) },
            onPreloadStories = { onToggleMobile(null, null, null, it, null) },
            onLessDataCalls = { onToggleMobile(null, null, null, null, it) }
        )
        NetworkAutoCard(
            title = "Roaming",
            enabled = state.roamingEnabled, preloadLarge = state.roamingPreloadLarge, preloadNext = state.roamingPreloadNextAudio,
            preloadStories = state.roamingPreloadStories, lessDataCalls = state.roamingLessDataCalls,
            onEnabled = { onToggleRoaming(it, null, null, null, null) },
            onPreloadLarge = { onToggleRoaming(null, it, null, null, null) },
            onPreloadNext = { onToggleRoaming(null, null, it, null, null) },
            onPreloadStories = { onToggleRoaming(null, null, null, it, null) },
            onLessDataCalls = { onToggleRoaming(null, null, null, null, it) }
        )
    }
}

@Composable
private fun NetworkAutoCard(
    title: String,
    enabled: Boolean,
    preloadLarge: Boolean,
    preloadNext: Boolean,
    preloadStories: Boolean,
    lessDataCalls: Boolean,
    onEnabled: (Boolean) -> Unit,
    onPreloadLarge: (Boolean) -> Unit,
    onPreloadNext: (Boolean) -> Unit,
    onPreloadStories: (Boolean) -> Unit,
    onLessDataCalls: (Boolean) -> Unit
) {
    ElevatedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = enabled, onCheckedChange = onEnabled)
                Spacer(Modifier.width(8.dp)); Text(if (enabled) "Aktiv" else "Inaktiv")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = preloadLarge, onClick = { onPreloadLarge(!preloadLarge) }, label = { Text("Große Videos vorladen") })
                FilterChip(selected = preloadNext, onClick = { onPreloadNext(!preloadNext) }, label = { Text("Nächste Audios vorladen") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = preloadStories, onClick = { onPreloadStories(!preloadStories) }, label = { Text("Stories vorladen") })
                FilterChip(selected = lessDataCalls, onClick = { onLessDataCalls(!lessDataCalls) }, label = { Text("Low‑Data Anrufe") })
            }
        }
    }
}
