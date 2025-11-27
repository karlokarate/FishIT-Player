package com.chris.m3usuite.logs.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chris.m3usuite.logs.LogViewerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: LogViewerViewModel = viewModel(factory = LogViewerViewModel.factory(app))
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val exporter = remember { LogExporter(context) }

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
                    IconButton(
                        onClick = {
                            exporter.export(state.entries)
                        },
                        enabled = state.entries.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = "Export",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(8.dp),
        ) {
            if (state.isLoading) {
                Text("Loading logs …")
            } else if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                SelectionContainer {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        state.entries.forEach { entry ->
                            Text(
                                text = entry.raw,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class LogExporter(
    private val context: android.content.Context,
) {
    private val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun export(entries: List<com.chris.m3usuite.logs.LogEntry>) {
        if (entries.isEmpty()) return
        val name = "applog_${sdf.format(Date())}.txt"
        // Best-effort: write to cache dir so user can pull via LogViewer or share externally
        runCatching {
            val file = java.io.File(context.cacheDir, name)
            file.printWriter().use { out ->
                entries.forEach { out.println(it.raw) }
            }
        }
    }
}
