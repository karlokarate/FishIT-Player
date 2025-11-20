package com.chris.m3usuite.ui.screens

import android.app.Application
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
import com.chris.m3usuite.telegram.ui.TelegramSettingsViewModel
import com.chris.m3usuite.telegram.ui.TelegramSettingsState
import com.chris.m3usuite.telegram.ui.TelegramAuthState
import com.chris.m3usuite.telegram.ui.ChatInfo

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
    onOpenPortalCheck: (() -> Unit)? = null, // navigiert zu XtreamPortalCheckScreen
) {
    // --- ViewModels (bestehend) ---
    val playerVm: PlayerSettingsViewModel = viewModel(factory = PlayerSettingsViewModel.factory(app))
    val networkVm: NetworkSettingsViewModel = viewModel(factory = NetworkSettingsViewModel.factory(app))
    val xtreamVm: XtreamSettingsViewModel = viewModel(factory = XtreamSettingsViewModel.factory(app))
    val epgVm: EpgSettingsViewModel = viewModel(factory = EpgSettingsViewModel.factory(app))
    val generalVm: GeneralSettingsViewModel = viewModel(factory = GeneralSettingsViewModel.factory(app))
    val telegramVm: TelegramSettingsViewModel = viewModel(factory = TelegramSettingsViewModel.factory(app))

    // --- States (bestehend) ---
    val playerState by playerVm.state.collectAsStateWithLifecycle()
    val networkState by networkVm.state.collectAsStateWithLifecycle()
    val xtreamState by xtreamVm.state.collectAsStateWithLifecycle()
    val epgState by epgVm.state.collectAsStateWithLifecycle()
    val generalState by generalVm.state.collectAsStateWithLifecycle()
    val telegramState by telegramVm.state.collectAsStateWithLifecycle()

    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon =
                    if (onBack != null) {
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
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // --- Network ---
            SettingsCard(title = "Netzwerk (M3U / EPG / Header)") {
                OutlinedTextField(
                    value = networkState.m3uUrl,
                    onValueChange = { networkVm.onChange(m3u = it) },
                    label = { Text("M3U URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = networkState.epgUrl,
                    onValueChange = { networkVm.onChange(epg = it) },
                    label = { Text("EPG URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = networkState.userAgent,
                    onValueChange = { networkVm.onChange(ua = it) },
                    label = { Text("User-Agent") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = networkState.referer,
                    onValueChange = { networkVm.onChange(ref = it) },
                    label = { Text("Referer") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = networkState.extraHeadersJson,
                    onValueChange = { networkVm.onChange(headersJson = it) },
                    label = { Text("Zusätzliche Header (JSON)") },
                    supportingText = { Text("Beispiel: {\"X-Auth\":\"abc\",\"X-Token\":\"123\"}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 5,
                )
            }

            // --- Xtream ---
            SettingsCard(title = "Xtream") {
                OutlinedTextField(
                    value = xtreamState.host,
                    onValueChange = { xtreamVm.onChange(host = it) },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = xtreamState.port.toString(),
                        onValueChange = { it.toIntOrNull()?.let { p -> xtreamVm.onChange(port = p) } },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = xtreamState.output,
                        onValueChange = { xtreamVm.onChange(output = it) },
                        label = { Text("Output (z.B. m3u8)") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = xtreamState.user,
                    onValueChange = { xtreamVm.onChange(user = it) },
                    label = { Text("User") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = xtreamState.pass,
                    onValueChange = { xtreamVm.onChange(pass = it) },
                    label = { Text("Passwort") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { xtreamVm.onOpenPortalCookieBridge { onOpenPortalCheck?.invoke() } }) {
                        Text("Portal-Cookie-Bridge öffnen")
                    }
                    Button(
                        onClick = { xtreamVm.onTriggerDeltaImport(includeLive = false) },
                        enabled = !xtreamState.isImportInFlight,
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
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = playerState.preferredPkg,
                    onValueChange = { playerVm.onChangePreferredPkg(it) },
                    label = { Text("Externes Paket (Package-Name)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Untertitel
                Spacer(Modifier.height(8.dp))
                Text("Untertitel-Stil")
                Slider(
                    value = playerState.subScale,
                    onValueChange = { playerVm.onChangeSubtitle(scale = it) },
                    valueRange = 0.04f..0.12f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = "0x${playerState.subFgArgb.toUInt().toString(16).uppercase()}",
                        onValueChange = {
                            runCatching { it.removePrefix("0x").toUInt(16).toInt() }
                                .onSuccess { v -> playerVm.onChangeSubtitle(fgArgb = v) }
                        },
                        label = { Text("FG (ARGB)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = "0x${playerState.subBgArgb.toUInt().toString(16).uppercase()}",
                        onValueChange = {
                            runCatching { it.removePrefix("0x").toUInt(16).toInt() }
                                .onSuccess { v -> playerVm.onChangeSubtitle(bgArgb = v) }
                        },
                        label = { Text("BG (ARGB)") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = playerState.subFgOpacityPct.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> playerVm.onChangeSubtitle(fgPct = v) } },
                        label = { Text("FG-Deckkraft (%)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = playerState.subBgOpacityPct.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> playerVm.onChangeSubtitle(bgPct = v) } },
                        label = { Text("BG-Deckkraft (%)") },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Toggles
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = playerState.rotationLocked,
                        onClick = { playerVm.onToggleRotation(!playerState.rotationLocked) },
                        label = { Text(if (playerState.rotationLocked) "Rotation gesperrt" else "Rotation frei") },
                    )
                    FilterChip(
                        selected = playerState.autoplayNext,
                        onClick = { playerVm.onToggleAutoplay(!playerState.autoplayNext) },
                        label = { Text(if (playerState.autoplayNext) "Autoplay an" else "Autoplay aus") },
                    )
                }
            }

            // --- EPG ---
            SettingsCard(title = "EPG (Favoriten)") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = epgState.useXtreamForFavorites,
                        onClick = { epgVm.onToggleUseXtream(!epgState.useXtreamForFavorites) },
                        label = { Text(if (epgState.useXtreamForFavorites) "Xtream bevorzugen" else "Xtream aus") },
                    )
                    FilterChip(
                        selected = epgState.skipXmltvIfXtreamOk,
                        onClick = { epgVm.onToggleSkipXmltv(!epgState.skipXmltvIfXtreamOk) },
                        label = { Text("XMLTV überspringen, wenn Xtream OK") },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { epgVm.onRefreshFavoritesNow() },
                    enabled = !epgState.isRefreshing,
                ) { Text(if (epgState.isRefreshing) "Aktualisiere …" else "Favoriten-EPG jetzt aktualisieren") }
            }

            // --- Telegram ---
            TelegramSettingsSection(
                state = telegramState,
                onToggleEnabled = telegramVm::onToggleEnabled,
                onUpdateCredentials = telegramVm::onUpdateCredentials,
                onConnectWithPhone = telegramVm::onConnectWithPhone,
                onSendCode = telegramVm::onSendCode,
                onSendPassword = telegramVm::onSendPassword,
                onLoadChats = telegramVm::onLoadChats,
                onUpdateSelectedChats = telegramVm::onUpdateSelectedChats,
                onUpdateCacheLimit = telegramVm::onUpdateCacheLimit,
                onDisconnect = telegramVm::onDisconnect,
            )

            // --- Allgemein ---
            SettingsCard(title = "Allgemein") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Kategorie 'For Adults' anzeigen",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = generalState.showAdults,
                        onCheckedChange = { value -> generalVm.onToggleShowAdults(value) },
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
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/**
 * Telegram settings section with login, chat selection, and configuration.
 */
@Composable
private fun TelegramSettingsSection(
    state: TelegramSettingsState,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateCredentials: (String, String) -> Unit,
    onConnectWithPhone: (String) -> Unit,
    onSendCode: (String) -> Unit,
    onSendPassword: (String) -> Unit,
    onLoadChats: () -> Unit,
    onUpdateSelectedChats: (List<String>) -> Unit,
    onUpdateCacheLimit: (Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    var showChatPicker by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Local state for API credentials to allow smooth typing
    var apiIdLocal by remember { mutableStateOf(state.apiId) }
    var apiHashLocal by remember { mutableStateOf(state.apiHash) }

    // Sync local state with incoming state (but don't overwrite while user is typing)
    LaunchedEffect(state.apiId, state.apiHash) {
        if (apiIdLocal.isEmpty() && state.apiId.isNotEmpty()) {
            apiIdLocal = state.apiId
        }
        if (apiHashLocal.isEmpty() && state.apiHash.isNotEmpty()) {
            apiHashLocal = state.apiHash
        }
    }

    SettingsCard(title = "Telegram Integration") {
        // Enable/Disable Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Telegram aktivieren")
            Switch(
                checked = state.enabled,
                onCheckedChange = onToggleEnabled,
            )
        }

        if (state.enabled) {
            HorizontalDivider()

            // API Credentials
            if (state.authState == TelegramAuthState.DISCONNECTED) {
                Text(
                    "API Zugangsdaten",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = apiIdLocal,
                        onValueChange = { apiIdLocal = it },
                        label = { Text("API ID") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = apiHashLocal,
                        onValueChange = { apiHashLocal = it },
                        label = { Text("API Hash") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Connection Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Status: ${state.authState.name}")
                if (state.authState == TelegramAuthState.READY) {
                    Button(onClick = onDisconnect) {
                        Text("Trennen")
                    }
                }
            }

            // Error Message
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Authentication Flow
            when (state.authState) {
                TelegramAuthState.DISCONNECTED -> {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Telefonnummer (+49...)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            // Save credentials before connecting
                            onUpdateCredentials(apiIdLocal, apiHashLocal)
                            onConnectWithPhone(phoneNumber)
                        },
                        enabled = phoneNumber.isNotBlank() && !state.isConnecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isConnecting) "Verbinde..." else "Mit Telegram verbinden")
                    }
                }

                TelegramAuthState.WAITING_FOR_CODE -> {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Verifizierungscode") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onSendCode(code) },
                        enabled = code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Code senden")
                    }
                }

                TelegramAuthState.WAITING_FOR_PASSWORD -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("2FA Passwort") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onSendPassword(password) },
                        enabled = password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Passwort senden")
                    }
                }

                TelegramAuthState.READY -> {
                    HorizontalDivider()

                    // Chat Selection
                    Text(
                        "Ausgewählte Chats (${state.selectedChats.size})",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    if (state.selectedChats.isNotEmpty()) {
                        Text(
                            state.selectedChats.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = { showChatPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Chats auswählen")
                    }

                    // Cache Limit
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Cache-Limit")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${state.cacheLimitGb} GB")
                            Button(
                                onClick = { onUpdateCacheLimit((state.cacheLimitGb - 1).coerceAtLeast(1)) },
                            ) {
                                Text("-")
                            }
                            Button(
                                onClick = { onUpdateCacheLimit((state.cacheLimitGb + 1).coerceAtMost(20)) },
                            ) {
                                Text("+")
                            }
                        }
                    }
                }

                else -> {
                    // Other states handled by status display
                }
            }
        }
    }

    // Chat Picker Dialog
    if (showChatPicker) {
        TelegramChatPickerDialog(
            availableChats = state.availableChats,
            selectedChatIds = state.selectedChats,
            isLoading = state.isLoadingChats,
            onLoadChats = onLoadChats,
            onConfirm = { selected ->
                onUpdateSelectedChats(selected)
                showChatPicker = false
            },
            onDismiss = { showChatPicker = false },
        )
    }
}

/**
 * Dialog for selecting Telegram chats to parse for content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramChatPickerDialog(
    availableChats: List<ChatInfo>,
    selectedChatIds: List<String>,
    isLoading: Boolean,
    onLoadChats: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(selectedChatIds.toSet()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (availableChats.isEmpty() && !isLoading) {
            onLoadChats()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chats auswählen") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Suchen...") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val filteredChats =
                        availableChats.filter { chat ->
                            searchQuery.isBlank() || chat.title.contains(searchQuery, ignoreCase = true)
                        }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        filteredChats.forEach { chat ->
                            val chatIdStr = chat.id.toString()
                            val isSelected = selection.contains(chatIdStr)

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chat.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = chat.type,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selection =
                                            if (checked) {
                                                selection + chatIdStr
                                            } else {
                                                selection - chatIdStr
                                            }
                                    },
                                )
                            }
                        }

                        if (filteredChats.isEmpty()) {
                            Text(
                                text = "Keine Chats gefunden",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selection.toList()) },
                enabled = !isLoading,
            ) {
                Text("Übernehmen (${selection.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}
