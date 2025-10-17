package com.chris.m3usuite.feature_tg_auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthError

@Composable
fun PasswordScreen(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    error: TgAuthError? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Text("Zwei-Faktor-Passwort eingeben (falls aktiviert).", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onSubmit, enabled = password.isNotBlank()) {
            Text("Bestätigen")
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(error.userMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
