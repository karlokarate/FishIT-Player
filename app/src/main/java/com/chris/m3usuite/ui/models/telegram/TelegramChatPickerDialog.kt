package com.chris.m3usuite.ui.models.telegram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/**
 * Chat-Picker Dialog für den Telegram-Sync.
 * Nutzt die `TelegramSettingsViewModel`-APIs, um Chats/Folders live aus TDLib zu laden.
 */
@Composable
fun TelegramChatPickerDialog(
    vm: TelegramSettingsViewModel,
    selectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
    contentPadding: PaddingValues = PaddingValues(24.dp)
) {
    var selection by remember { mutableStateOf(selectedIds) }
    var query by rememberSaveable { mutableStateOf("") }
    var currentList by rememberSaveable { mutableStateOf("main") }
    var chats by remember { mutableStateOf<List<ChatUi>>(emptyList()) }
    var folders by remember { mutableStateOf(IntArray(0)) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var manualInputVisible by rememberSaveable { mutableStateOf(false) }
    var manualInput by rememberSaveable { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedIds) {
        selection = selectedIds
    }

    LaunchedEffect(Unit) {
        folders = runCatching { vm.listFolders() }.getOrElse { IntArray(0) }
    }

    LaunchedEffect(currentList, query) {
        isLoading = true
        errorMessage = null
        try {
            val results = vm.listChats(currentList, query.takeIf { it.isNotBlank() })
            chats = results
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            errorMessage = t.message ?: "Unbekannter Fehler beim Laden der Chats."
            chats = emptyList()
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Telegram-Chats auswählen") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Wähle die Chats aus, die automatisch synchronisiert werden sollen.",
                    style = MaterialTheme.typography.bodySmall
                )

                FilterSection(
                    currentList = currentList,
                    folders = folders,
                    onSelectList = { currentList = it }
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Suche nach Chat-Namen") },
                    singleLine = true
                )

                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 360.dp)
                ) {
                    if (!isLoading && chats.isEmpty() && errorMessage == null) {
                        Text("Keine Chats gefunden.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(chats, key = { it.id }) { chat ->
                                val checked = selection.contains(chat.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toggleSelection(chat.id, checked, selection) { selection = it } }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            toggleSelection(chat.id, checked, selection) { selection = it }
                                        }
                                    )
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(chat.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "ID: ${chat.id}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ausgewählt: ${selection.size}")
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { selection = emptySet() }, enabled = selection.isNotEmpty()) {
                        Text("Auswahl löschen")
                    }
                }

                if (manualInputVisible) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = {
                                manualInput = it
                                manualError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("IDs manuell hinzufügen (CSV)") },
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (manualError != null) {
                            Text(
                                manualError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                val parsed = manualInput
                                    .split(',', ';', '\n', '\r', '\t', ' ')
                                    .mapNotNull { token ->
                                        val trimmed = token.trim()
                                        if (trimmed.isEmpty()) null else trimmed.toLongOrNull()
                                    }
                                    .toSet()
                                if (parsed.isEmpty()) {
                                    manualError = "Keine gültigen Chat-IDs gefunden."
                                } else {
                                    selection = selection + parsed
                                    manualInput = parsed.joinToString(",")
                                    manualError = null
                                }
                            }) {
                                Text("IDs übernehmen")
                            }
                            TextButton(onClick = {
                                manualInput = ""
                                manualError = null
                                manualInputVisible = false
                            }) {
                                Text("Schließen")
                            }
                        }
                    }
                } else {
                    OutlinedButton(onClick = { manualInputVisible = true }) {
                        Text("IDs manuell eintragen")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selection) }) {
                Text("Übernehmen (${selection.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun toggleSelection(
    id: Long,
    wasChecked: Boolean,
    current: Set<Long>,
    commit: (Set<Long>) -> Unit
) {
    commit(if (wasChecked) current - id else current + id)
}

@Composable
private fun FilterSection(
    currentList: String,
    folders: IntArray,
    onSelectList: (String) -> Unit
) {
    val options = remember(folders) {
        buildList {
            add("main" to "Haupt")
            add("archive" to "Archiv")
            folders.sorted().forEach { add("folder:$it" to "Ordner $it") }
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = currentList == key,
                onClick = { onSelectList(key) },
                label = { Text(label) }
            )
        }
    }
}
