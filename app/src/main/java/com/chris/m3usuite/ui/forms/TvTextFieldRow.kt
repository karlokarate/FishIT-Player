package com.chris.m3usuite.ui.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.tvClickable

enum class TvKeyboard { Default, Uri, Number, Password, Email }

@Composable
fun TvTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    keyboard: TvKeyboard = TvKeyboard.Default,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val display = if (keyboard == TvKeyboard.Password && value.isNotEmpty()) "•".repeat(value.length.coerceAtMost(12)).ifEmpty { "•••" } else value

    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(debugTag = "TvTextFieldRow:$label", onClick = { showDialog = true }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            val hint = when {
                display.isNotBlank() -> display
                !placeholder.isNullOrBlank() -> placeholder
                else -> ""
            }
            Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        ValidationHint(helperText = helperText, errorText = errorText)
    }

    if (showDialog) {
        val focus = LocalFocusManager.current
        var tmp by remember(value) { mutableStateOf(value) }

        val (kt, cap, ime) = when (keyboard) {
            TvKeyboard.Uri -> Triple(KeyboardType.Uri, KeyboardCapitalization.None, ImeAction.Done)
            TvKeyboard.Number -> Triple(KeyboardType.Number, KeyboardCapitalization.None, ImeAction.Done)
            TvKeyboard.Password -> Triple(KeyboardType.Password, KeyboardCapitalization.None, ImeAction.Done)
            TvKeyboard.Email -> Triple(KeyboardType.Email, KeyboardCapitalization.None, ImeAction.Done)
            else -> Triple(KeyboardType.Text, KeyboardCapitalization.None, ImeAction.Done)
        }
        val visual: VisualTransformation? = if (keyboard == TvKeyboard.Password) PasswordVisualTransformation() else null

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = tmp,
                    onValueChange = { tmp = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = visual ?: VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(capitalization = cap, keyboardType = kt, imeAction = ime)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(tmp)
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Abbrechen") }
            }
        )

        LaunchedEffect(Unit) {
            // Clear any incidental in-row focus when showing the dialog
            focus.clearFocus(force = true)
        }
    }
}
