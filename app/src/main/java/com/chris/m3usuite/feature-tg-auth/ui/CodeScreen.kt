package com.chris.m3usuite.feature_tg_auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthError

@Composable
fun CodeScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    canResend: Boolean,
    resendSeconds: Long,
    isBusy: Boolean,
    error: TgAuthError? = null
) {
    val focusRequester = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text("Bestätigungscode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Text("Den Code aus der Telegram-App eingeben.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onSubmit, enabled = !isBusy && code.isNotBlank()) {
            Text("Bestätigen")
        }
        Button(onClick = onResend, enabled = !isBusy && canResend) {
            Text(if (canResend) "Code erneut senden" else "Code erneut senden (${resendSeconds}s)")
        }
        if (!canResend && resendSeconds > 0) {
            Text(
                text = "Bitte warte ${resendSeconds}s, bevor du einen neuen Code anforderst.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(error.userMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
}
