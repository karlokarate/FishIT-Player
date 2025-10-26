
package com.chris.m3usuite.ui.models.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Simple fallback CSV editor dialog, until a full chat-picker is wired.
 */
@Composable
fun TelegramChatPickerDialog(
    currentCsv: String,
    onConfirmCsv: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var csv by remember { mutableStateOf(currentCsv) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Telegram Chats auswählen") },
        text = {
            Column(Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gib die Chat-IDs als CSV an (z. B. 12345,-67890)")
                OutlinedTextField(value = csv, onValueChange = { csv = it }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmCsv(csv.trim()) }) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
