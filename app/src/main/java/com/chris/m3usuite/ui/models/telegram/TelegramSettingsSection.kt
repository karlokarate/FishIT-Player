
package com.chris.m3usuite.ui.models.telegram

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.telegram.service.TelegramServiceClient

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

    // Effects (snackbar)
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                is TelegramEffect.Snackbar -> snackbarHost.showSnackbar(eff.message)
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

            // Chat selector + selected chips
            ChatPickerBlock(vm = vm, selected = ui.selected)

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
                    OutlinedButton(onClick = { openTreePicker.launch(null) }) {
                        Text(if (ui.logDir.isBlank()) "Log-Verzeichnis wählen" else "Log-Verzeichnis ändern")
                    }
                    OutlinedButton(onClick = { vm.optimizeStorage() }) {
                        Text("Speicher optimieren")
                    }
                }
            }
        }
    }

    // local snackbar host to surface effects from VM
    Spacer(Modifier.height(4.dp))
    SnackbarHost(hostState = snackbarHost)
}

@Composable
private fun LoginBlock(ui: TelegramUiState, onIntent: (TelegramIntent) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Anmeldung", style = MaterialTheme.typography.titleSmall)

        when (ui.auth) {
            TelegramServiceClient.AuthState.Idle, TelegramServiceClient.AuthState.Error -> {
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Telefonnummer") }, supportingText = { Text("Mit +49… oder 0049…") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onIntent(TelegramIntent.RequestCode(phone)) }) { Text("Code anfordern") }
                    OutlinedButton(onClick = { onIntent(TelegramIntent.RequestQr) }) { Text("QR‑Login") }
                }
            }
            TelegramServiceClient.AuthState.CodeSent -> {
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    label = { Text("Bestätigungscode") }, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onIntent(TelegramIntent.SubmitCode(code)) }) { Text("Code senden") }
                    val left = ui.resendLeftSec
                    OutlinedButton(onClick = { onIntent(TelegramIntent.ResendCode) }, enabled = left <= 0) {
                        Text(if (left > 0) "Erneut senden (${left}s)" else "Erneut senden")
                    }
                }
            }
            TelegramServiceClient.AuthState.PasswordRequired -> {
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("2FA‑Passwort") }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(onClick = { onIntent(TelegramIntent.SubmitPassword(pass)) }) { Text("Passwort senden") }
            }
            TelegramServiceClient.AuthState.SignedIn -> {
                AssistChip(onClick = { onIntent(TelegramIntent.StartFullSync) }, label = { Text("Voll‑Sync ausführen") })
                Text("Angemeldet", style = MaterialTheme.typography.bodySmall)
            }
            TelegramServiceClient.AuthState.Error -> {
                // handled in snackbar/effects
            }
        }
    }
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
        OutlinedButton(onClick = { open = true }) { Text("Chats auswählen…") }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Proxy", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("none" to "Keiner", "socks5" to "SOCKS5", "http" to "HTTP", "mtproto" to "MTProto")
                .forEach { (value, label) ->
                    FilterChip(selected = state.type == value, onClick = { onChange(value, null, null, null, null, null, null) }, label = { Text(label) })
                }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = state.host, onValueChange = { onChange(null, it, null, null, null, null, null) }, label = { Text("Host") }, modifier = Modifier.weight(2f))
            OutlinedTextField(
                value = if (state.port == 0) "" else state.port.toString(),
                onValueChange = { it.toIntOrNull()?.let { p -> onChange(null, null, p, null, null, null, null) } },
                label = { Text("Port") }, modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = state.username, onValueChange = { onChange(null, null, null, it, null, null, null) }, label = { Text("User") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = state.password, onValueChange = { onChange(null, null, null, null, it, null, null) }, label = { Text("Passwort") }, modifier = Modifier.weight(1f), visualTransformation = PasswordVisualTransformation())
        }
        if (state.type == "mtproto") {
            OutlinedTextField(value = state.secret, onValueChange = { onChange(null, null, null, null, null, it, null) }, label = { Text("MTProto‑Secret") }, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.enabled, onCheckedChange = { onChange(null, null, null, null, null, null, it) })
                Spacer(Modifier.width(8.dp))
                Text(if (state.enabled) "Aktiv" else "Inaktiv")
            }
            Button(onClick = onApply, enabled = !state.isApplying) { Text(if (state.isApplying) "Übernehme…" else "Anwenden") }
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
