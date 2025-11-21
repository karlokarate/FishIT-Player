package com.chris.m3usuite.logs.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.logs.LogViewerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: LogViewerViewModel = viewModel(factory = LogViewerViewModel.factory(app))
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var exportPendingFile by remember { mutableStateOf<File?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(exportErrorMessage) {
        exportErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            exportErrorMessage = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = CreateDocument("text/plain"),
    ) { uri: Uri? ->
        val file = exportPendingFile
        exportPendingFile = null
        if (uri != null && file != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { `in` ->
                        `in`.copyTo(out)
                    }
                }
            }.onFailure { e ->
                exportErrorMessage = "Export fehlgeschlagen: ${e.message}"
            }.onSuccess {
                exportErrorMessage = "Export erfolgreich"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Viewer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Zurück",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshLogFiles() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Aktualisieren",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp),
        ) {
            // Datei-Auswahl
            if (state.logFiles.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    TextField(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        value = state.selectedFile?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Logfile auswählen") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        state.logFiles.forEach { file ->
                            DropdownMenuItem(
                                text = { Text(file.name) },
                                onClick = {
                                    expanded = false
                                    viewModel.selectFile(file)
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            state.selectedFile?.let { file ->
                                exportPendingFile = file
                                exportLauncher.launch(file.name)
                            }
                        },
                        enabled = state.selectedFile != null,
                    ) {
                        Text("Als .txt exportieren")
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Keine Logfiles gefunden",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Es wurden noch keine Logs geschrieben.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Filter: Suchfeld + Source-Chips
            if (state.entries.isNotEmpty()) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Textfilter (z. B. 'ERROR', 'TelegramDataSource')") },
                    singleLine = true,
                )

                Spacer(Modifier.height(8.dp))

                if (state.availableSources.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.availableSources.forEach { source ->
                            val selected = source in state.activeSources
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleSourceFilter(source) },
                                label = { Text(source) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            state.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Log-Text: gefiltert, scroll- & selektierbar
            val scrollState = rememberScrollState()
            SelectionContainer {
                Text(
                    text = state.filteredContent,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}
