package com.chris.m3usuite.feature_tg_auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthError

@Composable
fun PhoneScreen(
    phone: String,
    onPhoneChange: (String) -> Unit,
    useCurrentDevice: Boolean,
    onUseCurrentDeviceChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onRequestQr: () -> Unit,
    showCurrentDeviceSwitch: Boolean,
    error: TgAuthError? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Telefonnummer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Text(
            text = "Bitte im internationalen Format eingeben (z. B. +491701234567).",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = onSubmit, enabled = phone.isNotBlank()) {
            Text("Weiter")
        }
        Button(onClick = onRequestQr) {
            Text("QR-Login anfordern")
        }
        Text(
            text = "Der QR-Login steht nur zur Verfügung, wenn Telegram ihn aktuell anbietet. Der Telefon-/Code-Flow bleibt parallel verfügbar.",
            style = MaterialTheme.typography.bodySmall
        )
        if (showCurrentDeviceSwitch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = useCurrentDevice, onCheckedChange = onUseCurrentDeviceChange)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Code auf diesem Gerät bestätigen (Telegram-App installiert)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(error.userMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
