package com.fishit.player.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishShapes

/**
 * Start/Onboarding Screen for FishIT Player v2
 *
 * Allows users to:
 * - Connect Telegram account
 * - Connect Xtream via URL
 * - Continue to Home when at least one source is connected
 */
@Composable
fun StartScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            StartScreenHeader()

            Spacer(modifier = Modifier.height(48.dp))

            // Source Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                // Telegram Card
                SourceCard(
                    title = "Telegram",
                    icon = Icons.Default.Send,
                    iconTint = FishColors.SourceTelegram,
                    isConnected = state.telegramState == TelegramAuthState.Connected,
                    modifier = Modifier.weight(1f, fill = false).width(360.dp),
                ) {
                    TelegramLoginContent(
                        state = state,
                        onStartLogin = viewModel::startTelegramLogin,
                        onPhoneChange = viewModel::updatePhoneNumber,
                        onSubmitPhone = viewModel::submitPhoneNumber,
                        onCodeChange = viewModel::updateCode,
                        onSubmitCode = viewModel::submitCode,
                        onPasswordChange = viewModel::updatePassword,
                        onSubmitPassword = viewModel::submitPassword,
                        onDisconnect = viewModel::disconnectTelegram,
                    )
                }

                // Xtream Card
                SourceCard(
                    title = "Xtream / IPTV",
                    icon = Icons.Default.Tv,
                    iconTint = FishColors.SourceXtream,
                    isConnected = state.xtreamState == XtreamConnectionState.Connected,
                    modifier = Modifier.weight(1f, fill = false).width(360.dp),
                ) {
                    XtreamLoginContent(
                        state = state,
                        onUrlChange = viewModel::updateXtreamUrl,
                        onConnect = viewModel::connectXtream,
                        onDisconnect = viewModel::disconnectXtream,
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Continue Button
            AnimatedVisibility(
                visible = state.canContinue,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Button(
                    onClick = onContinue,
                    enabled = state.canContinue,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = FishColors.Primary,
                        ),
                    modifier =
                        Modifier
                            .width(280.dp)
                            .height(56.dp),
                ) {
                    Text(
                        text = "Continue to Home",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartScreenHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🐟",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "FishIT Player",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Connect your media sources",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SourceCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = FishColors.Surface,
            ),
        shape = FishShapes.Large,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            // Header with icon and status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isConnected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Connected",
                        tint = FishColors.Primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            content()
        }
    }
}

@Composable
private fun TelegramLoginContent(
    state: OnboardingState,
    onStartLogin: () -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmitPhone: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitPassword: () -> Unit,
    onDisconnect: () -> Unit,
) {
    when (state.telegramState) {
        TelegramAuthState.Idle, TelegramAuthState.Disconnected -> {
            Button(
                onClick = onStartLogin,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = FishColors.SourceTelegram,
                    ),
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect with Phone Number")
            }
        }

        TelegramAuthState.WaitingForPhone -> {
            PhoneInputField(
                phone = state.telegramPhoneNumber,
                onPhoneChange = onPhoneChange,
                onSubmit = onSubmitPhone,
                error = state.telegramError,
            )
        }

        TelegramAuthState.WaitingForCode -> {
            CodeInputField(
                code = state.telegramCode,
                onCodeChange = onCodeChange,
                onSubmit = onSubmitCode,
                error = state.telegramError,
            )
        }

        TelegramAuthState.WaitingForPassword -> {
            PasswordInputField(
                password = state.telegramPassword,
                onPasswordChange = onPasswordChange,
                onSubmit = onSubmitPassword,
                error = state.telegramError,
            )
        }

        TelegramAuthState.Connected -> {
            ConnectedState(
                message = "Telegram connected",
                onDisconnect = onDisconnect,
            )
        }

        is TelegramAuthState.Error -> {
            ErrorState(
                message = (state.telegramState as TelegramAuthState.Error).message,
                onRetry = onStartLogin,
            )
        }
    }
}

@Composable
private fun XtreamLoginContent(
    state: OnboardingState,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    when (state.xtreamState) {
        XtreamConnectionState.Disconnected -> {
            Column {
                OutlinedTextField(
                    value = state.xtreamUrl,
                    onValueChange = onUrlChange,
                    label = { Text("Xtream URL or M3U Link") },
                    placeholder = { Text("http://host:port/get.php?username=X&password=Y") },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { onConnect() }),
                    isError = state.xtreamError != null,
                    supportingText = state.xtreamError?.let { { Text(it, color = FishColors.Error) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.xtreamUrl.isNotBlank(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = FishColors.SourceXtream,
                        ),
                ) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect")
                }
            }
        }

        XtreamConnectionState.Connecting -> {
            LoadingState("Connecting to server...")
        }

        XtreamConnectionState.Connected -> {
            ConnectedState(
                message = "Xtream connected",
                onDisconnect = onDisconnect,
            )
        }

        is XtreamConnectionState.Error -> {
            ErrorState(
                message = (state.xtreamState as XtreamConnectionState.Error).message,
                onRetry = onConnect,
            )
        }
    }
}

@Composable
private fun PhoneInputField(
    phone: String,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    error: String?,
) {
    Column {
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone Number") },
            placeholder = { Text("+49 123 456789") },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = error != null,
            supportingText = error?.let { { Text(it, color = FishColors.Error) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = phone.isNotBlank(),
        ) {
            Text("Send Code")
        }
    }
}

@Composable
private fun CodeInputField(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    error: String?,
) {
    Column {
        Text(
            text = "Enter the code sent to your phone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Verification Code") },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = error != null,
            supportingText = error?.let { { Text(it, color = FishColors.Error) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = code.isNotBlank(),
        ) {
            Text("Verify")
        }
    }
}

@Composable
private fun PasswordInputField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    error: String?,
) {
    Column {
        Text(
            text = "Enter your 2FA password",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("2FA Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = error != null,
            supportingText = error?.let { { Text(it, color = FishColors.Error) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = password.isNotBlank(),
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectedState(
    message: String,
    onDisconnect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = FishColors.Primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = FishColors.Primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDisconnect) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Disconnect",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = FishColors.Error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Retry")
        }
    }
}
