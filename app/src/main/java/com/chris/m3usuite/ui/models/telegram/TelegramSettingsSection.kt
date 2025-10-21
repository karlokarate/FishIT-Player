package com.chris.m3usuite.ui.models.telegram

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthState
import com.chris.m3usuite.feature_tg_auth.ui.CodeScreen
import com.chris.m3usuite.feature_tg_auth.ui.PasswordScreen
import com.chris.m3usuite.feature_tg_auth.ui.PhoneScreen
import com.chris.m3usuite.feature_tg_auth.ui.TgAuthViewModel
import com.chris.m3usuite.feature_tg_auth.ui.TgAuthViewModel.KeysStatus
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun TelegramSettingsSection(
    vm: TelegramSettingsViewModel = viewModel(factory = TelegramSettingsViewModel.Factory(LocalContext.current.applicationContext as Application)),
    proxyVm: TelegramProxyViewModel = viewModel(factory = TelegramProxyViewModel.Factory(LocalContext.current.applicationContext as Application)),
    autoVm: TelegramAutoDownloadViewModel = viewModel(factory = TelegramAutoDownloadViewModel.Factory(LocalContext.current.applicationContext as Application)),
    authVm: TgAuthViewModel = viewModel(factory = TgAuthViewModel.Factory(LocalContext.current.applicationContext as Application))
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    val ui by vm.state.collectAsStateWithLifecycle()
    val proxy by proxyVm.state.collectAsStateWithLifecycle()
    val auto by autoVm.state.collectAsStateWithLifecycle()

    val authState by authVm.authState.collectAsStateWithLifecycle()
    val authBusy by authVm.busy.collectAsStateWithLifecycle()
    val hasKeys by authVm.hasKeys.collectAsStateWithLifecycle()
    val phone by authVm.phone.collectAsStateWithLifecycle()
    val code by authVm.code.collectAsStateWithLifecycle()
    val password by authVm.password.collectAsStateWithLifecycle()
    val useCurrentDevice by authVm.useCurrentDevice.collectAsStateWithLifecycle()
    val qrLink by authVm.qrLink.collectAsStateWithLifecycle()
    val apiIdInput by authVm.apiIdInput.collectAsStateWithLifecycle()
    val apiHashInput by authVm.apiHashInput.collectAsStateWithLifecycle()
    val keysStatus by authVm.keysStatus.collectAsStateWithLifecycle()
    val orchestratorToken by authVm.orchestratorToken.collectAsStateWithLifecycle(0)

    val snackbarHost = remember { SnackbarHostState() }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authVm.handleConsentResult(result)
    }

    LaunchedEffect(Unit) {
        launch {
            vm.effects.collect { eff ->
                if (eff is TelegramEffect.Snackbar) {
                    snackbarHost.showSnackbar(eff.message)
                }
            }
        }
        launch {
            authVm.snackbar.collect { message -> snackbarHost.showSnackbar(message) }
        }
    }

    LaunchedEffect(activity, lifecycleOwner, orchestratorToken) {
        if (activity != null) {
            authVm.attachConsent(activity, lifecycleOwner, consentLauncher)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> authVm.setInBackground(false)
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> authVm.setInBackground(true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            authVm.setInBackground(true)
            authVm.detachConsent()
        }
    }

    val openTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.onIntent(TelegramIntent.SetLogDir(uri))
    }

    val openQr: (String) -> Unit = remember(context, scope) {
        { link ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure { scope.launch { snackbarHost.showSnackbar("Telegram-App nicht gefunden.") } }
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Telegram", style = MaterialTheme.typography.titleMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Aktivieren")
                    Text("Telegram-Integration aktivieren/deaktivieren", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = ui.enabled,
                    onCheckedChange = { vm.onIntent(TelegramIntent.SetEnabled(it)) },
                    enabled = !authBusy
                )
            }

            Divider()

            CredentialsBlock(
                apiId = apiIdInput,
                apiHash = apiHashInput,
                status = keysStatus,
                onApiIdChange = authVm::onApiIdChange,
                onApiHashChange = authVm::onApiHashChange,
                onSave = authVm::saveApiCredentials,
                onClear = authVm::clearApiCredentials,
                busy = authBusy
            )

            Divider()

            AuthBlock(
                state = authState,
                busy = authBusy,
                hasKeys = hasKeys,
                phone = phone,
                onPhoneChange = authVm::onPhoneChange,
                useCurrentDevice = useCurrentDevice,
                onUseCurrentDeviceChange = authVm::onUseCurrentDeviceChange,
                onSubmitPhone = authVm::submitPhone,
                onRequestQr = authVm::requestQr,
                code = code,
                onCodeChange = authVm::onCodeChange,
                onSubmitCode = authVm::submitCode,
                onResend = authVm::resendCode,
                password = password,
                onPasswordChange = authVm::onPasswordChange,
                onSubmitPassword = authVm::submitPassword,
                onCancel = authVm::cancelAuth,
                onStartFullSync = { vm.onIntent(TelegramIntent.StartFullSync) },
                qrLink = qrLink,
                onOpenQr = openQr
            )

            Divider()

            ChatPickerBlock(vm = vm, selected = ui.selected, isResolving = ui.isResolvingChats)

            Divider()

            ProxyBlock(state = proxy, onChange = proxyVm::onChange, onApply = proxyVm::applyNow)

            Divider()

            AutoDownloadBlock(
                state = auto,
                onToggleWifi = { e, l, n, s, c -> autoVm.setWifi(e, l, n, s, c) },
                onToggleMobile = { e, l, n, s, c -> autoVm.setMobile(e, l, n, s, c) },
                onToggleRoaming = { e, l, n, s, c -> autoVm.setRoaming(e, l, n, s, c) }
            )

            Divider()

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

    Spacer(Modifier.height(4.dp))
    SnackbarHost(hostState = snackbarHost)
}

@Composable
private fun CredentialsBlock(
    apiId: String,
    apiHash: String,
    status: KeysStatus,
    onApiIdChange: (String) -> Unit,
    onApiHashChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    busy: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("API-Zugang", style = MaterialTheme.typography.titleSmall)
        Text(
            "Für TDLib werden API-ID und API-Hash benötigt. Diese erhältst du unter https://my.telegram.org.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = apiId,
                onValueChange = onApiIdChange,
                modifier = Modifier.weight(1f),
                label = { Text("API-ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = apiHash,
                onValueChange = onApiHashChange,
                modifier = Modifier.weight(1f),
                label = { Text("API-Hash") },
                singleLine = true
            )
        }
        val statusText = when (status) {
            KeysStatus.Missing -> "Es sind keine Schlüssel hinterlegt. Ohne Schlüssel ist keine Anmeldung möglich."
            KeysStatus.BuildConfig -> "Verwendet die im Build hinterlegten API-Schlüssel."
            KeysStatus.Custom -> "Verwendet benutzerdefinierte API-Schlüssel."
        }
        Text(statusText, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave, enabled = !busy) { Text("Speichern") }
            OutlinedButton(
                onClick = onClear,
                enabled = !busy && (apiId.isNotBlank() || apiHash.isNotBlank())
            ) { Text("Zurücksetzen") }
        }
    }
}

@Composable
private fun AuthBlock(
    state: TgAuthState,
    busy: Boolean,
    hasKeys: Boolean,
    phone: String,
    onPhoneChange: (String) -> Unit,
    useCurrentDevice: Boolean,
    onUseCurrentDeviceChange: (Boolean) -> Unit,
    onSubmitPhone: () -> Unit,
    onRequestQr: () -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onResend: () -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmitPassword: () -> Unit,
    onCancel: () -> Unit,
    onStartFullSync: () -> Unit,
    qrLink: String?,
    onOpenQr: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Anmeldung", style = MaterialTheme.typography.titleSmall)
        if (!hasKeys) {
            Text(
                "Bitte zuerst API-ID und Hash speichern, damit TDLib gestartet werden kann.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        when (state) {
            is TgAuthState.WaitPhone -> {
                PhoneScreen(
                    phone = phone,
                    onPhoneChange = onPhoneChange,
                    useCurrentDevice = useCurrentDevice,
                    onUseCurrentDeviceChange = onUseCurrentDeviceChange,
                    onSubmit = onSubmitPhone,
                    onRequestQr = onRequestQr,
                    showCurrentDeviceSwitch = true,
                    isBusy = busy || !hasKeys,
                    error = null
                )
            }
            TgAuthState.Unauthenticated -> {
                PhoneScreen(
                    phone = phone,
                    onPhoneChange = onPhoneChange,
                    useCurrentDevice = useCurrentDevice,
                    onUseCurrentDeviceChange = onUseCurrentDeviceChange,
                    onSubmit = onSubmitPhone,
                    onRequestQr = onRequestQr,
                    showCurrentDeviceSwitch = true,
                    isBusy = busy || !hasKeys,
                    error = null
                )
            }
            is TgAuthState.WaitCode -> {
                CodeScreen(
                    code = code,
                    onCodeChange = onCodeChange,
                    onSubmit = onSubmitCode,
                    onResend = onResend,
                    canResend = state.canResend,
                    resendSeconds = state.remainingSeconds,
                    isBusy = busy,
                    error = state.lastError
                )
                OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Abbrechen") }
            }
            is TgAuthState.WaitPassword -> {
                state.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                PasswordScreen(
                    password = password,
                    onPasswordChange = onPasswordChange,
                    onSubmit = onSubmitPassword,
                    isBusy = busy,
                    error = state.lastError
                )
                OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Abbrechen") }
            }
            is TgAuthState.Qr -> {
                Text(
                    "Telegram wartet auf eine Bestätigung über ein anderes Gerät.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { qrLink?.let(onOpenQr) },
                    enabled = !busy && !qrLink.isNullOrBlank()
                ) { Text("In Telegram öffnen") }
                OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Abbrechen") }
            }
            TgAuthState.Ready -> {
                AssistChip(
                    onClick = onStartFullSync,
                    enabled = !busy,
                    label = { Text("Voll-Sync ausführen") }
                )
                OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Abmelden") }
            }
            TgAuthState.LoggingOut -> {
                Text("Abmeldung läuft…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChatPickerBlock(vm: TelegramSettingsViewModel, selected: List<ChatUi>, isResolving: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ausgewählte Chats", style = MaterialTheme.typography.titleSmall)
        if (selected.isEmpty() && !isResolving) {
            Text("Keine Chats ausgewählt.")
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { chat -> AssistChip(onClick = {}, label = { Text(chat.title) }) }
            }
        }
        if (isResolving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick = { open = true }, enabled = !isResolving) {
            Text(if (isResolving) "Chats werden geladen…" else "Chats auswählen…")
        }
    }
    if (open) {
        ChatPickerDialog(
            vm = vm,
            selectedIds = selected.map { it.id }.toSet(),
            onDismiss = { open = false }
        ) { ids ->
            vm.onIntent(TelegramIntent.ConfirmChats(ids))
            open = false
        }
    }
}

@Composable
private fun ChatPickerDialog(
    vm: TelegramSettingsViewModel,
    selectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    TelegramChatPickerDialog(
        vm = vm,
        selectedIds = selectedIds,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
