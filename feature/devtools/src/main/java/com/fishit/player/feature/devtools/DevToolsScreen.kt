package com.fishit.player.feature.devtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fishit.player.infra.transport.telegram.TelegramAuthState

/**
 * DevTools Screen
 *
 * Minimal UI for testing login flows:
 * - Telegram authentication
 * - Xtream configuration
 */
@Composable
fun DevToolsScreen(
    viewModel: DevToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "DevTools - Login Testing",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Debug-only UI for testing Telegram and Xtream login flows",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Telegram Section
        TelegramAuthSection(
            authState = uiState.telegramAuthState,
            error = uiState.telegramError,
            onStartAuth = viewModel::startTelegramAuth,
            onSubmitPhone = viewModel::submitPhoneNumber,
            onSubmitCode = viewModel::submitCode,
            onSubmitPassword = viewModel::submitPassword,
            onClearError = viewModel::clearTelegramError
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Xtream Section
        XtreamConfigSection(
            config = uiState.xtreamConfig,
            status = uiState.xtreamStatus,
            error = uiState.xtreamError,
            onParseUrl = viewModel::parseXtreamUrl,
            onClearConfig = viewModel::clearXtreamConfig,
            onClearError = viewModel::clearXtreamError
        )
    }
}

@Composable
private fun TelegramAuthSection(
    authState: TelegramAuthState,
    error: String?,
    onStartAuth: () -> Unit,
    onSubmitPhone: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onSubmitPassword: (String) -> Unit,
    onClearError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Telegram Authentication",
                style = MaterialTheme.typography.titleLarge
            )

            // Status display
            Text(
                text = "Status: ${authState.toDisplayString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (authState) {
                    is TelegramAuthState.Ready -> MaterialTheme.colorScheme.primary
                    is TelegramAuthState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            // Error display
            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            // Auth controls based on state
            when (authState) {
                TelegramAuthState.Idle -> {
                    Button(
                        onClick = onStartAuth,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Telegram Auth")
                    }
                }
                TelegramAuthState.WaitingForPhone -> {
                    PhoneInputForm(onSubmit = onSubmitPhone)
                }
                TelegramAuthState.WaitingForCode -> {
                    CodeInputForm(onSubmit = onSubmitCode)
                }
                TelegramAuthState.WaitingForPassword -> {
                    PasswordInputForm(onSubmit = onSubmitPassword)
                }
                TelegramAuthState.Ready -> {
                    Text(
                        text = "✓ Authenticated successfully",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TelegramAuthState.Connecting -> {
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is TelegramAuthState.Error -> {
                    Button(
                        onClick = onStartAuth,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry Auth")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneInputForm(onSubmit: (String) -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            placeholder = { Text("+1234567890") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (phoneNumber.isNotBlank()) onSubmit(phoneNumber) }
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = { onSubmit(phoneNumber) },
            enabled = phoneNumber.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Phone")
        }
    }
}

@Composable
private fun CodeInputForm(onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Verification Code") },
            placeholder = { Text("12345") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (code.isNotBlank()) onSubmit(code) }
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = { onSubmit(code) },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Code")
        }
    }
}

@Composable
private fun PasswordInputForm(onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (password.isNotBlank()) onSubmit(password) }
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = { onSubmit(password) },
            enabled = password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Password")
        }
    }
}

@Composable
private fun XtreamConfigSection(
    config: com.fishit.player.infra.transport.xtream.XtreamApiConfig?,
    status: XtreamStatus,
    error: String?,
    onParseUrl: (String) -> Unit,
    onClearConfig: () -> Unit,
    onClearError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Xtream Configuration",
                style = MaterialTheme.typography.titleLarge
            )

            // Status display
            Text(
                text = "Status: ${status.toDisplayString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (status) {
                    is XtreamStatus.Connected -> MaterialTheme.colorScheme.primary
                    is XtreamStatus.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            // Error display
            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            // URL input or config display
            if (config == null) {
                XtreamUrlInputForm(
                    onSubmit = onParseUrl,
                    isProcessing = status == XtreamStatus.Parsing
                )
            } else {
                XtreamConfigDisplay(
                    config = config,
                    status = status,
                    onClear = onClearConfig
                )
            }
        }
    }
}

@Composable
private fun XtreamUrlInputForm(
    onSubmit: (String) -> Unit,
    isProcessing: Boolean
) {
    var url by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Paste full Xtream URL:",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Example: http://host:8080/get.php?username=USER&password=PASS&type=m3u",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Xtream URL") },
            placeholder = { Text("http://...") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (url.isNotBlank()) onSubmit(url) }
            ),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
        Button(
            onClick = { onSubmit(url) },
            enabled = url.isNotBlank() && !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isProcessing) "Parsing..." else "Parse & Connect")
        }
    }
}

@Composable
private fun XtreamConfigDisplay(
    config: com.fishit.player.infra.transport.xtream.XtreamApiConfig,
    status: XtreamStatus,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "✓ Configuration parsed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ConfigRow("Server", "${config.scheme}://${config.host}")
                config.port?.let { ConfigRow("Port", it.toString()) }
                ConfigRow("Username", config.username)
                ConfigRow("Password", "•".repeat(config.password.length))
                if (status is XtreamStatus.Connected) {
                    ConfigRow("Resolved Port", status.port.toString())
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onClear,
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear Config")
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ========== Display Helpers ==========

private fun TelegramAuthState.toDisplayString(): String = when (this) {
    TelegramAuthState.Idle -> "Idle"
    TelegramAuthState.Connecting -> "Connecting"
    TelegramAuthState.WaitingForPhone -> "Waiting for phone number"
    TelegramAuthState.WaitingForCode -> "Waiting for verification code"
    TelegramAuthState.WaitingForPassword -> "Waiting for password"
    TelegramAuthState.Ready -> "Ready"
    is TelegramAuthState.Error -> "Error: ${this.message}"
}

private fun XtreamStatus.toDisplayString(): String = when (this) {
    XtreamStatus.NotConfigured -> "Not configured"
    XtreamStatus.Parsing -> "Parsing URL..."
    XtreamStatus.Parsed -> "Parsed"
    XtreamStatus.Testing -> "Testing connectivity..."
    is XtreamStatus.Connected -> "Connected (port ${this.port})"
    XtreamStatus.Error -> "Error"
}
