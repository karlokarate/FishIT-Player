package com.fishit.player.feature.settings.dbinspector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.persistence.inspector.DbEntityDump
import com.fishit.player.core.persistence.inspector.DbEntityTypeInfo
import com.fishit.player.core.persistence.inspector.DbFieldValue
import com.fishit.player.core.persistence.inspector.DbPage
import com.fishit.player.core.persistence.inspector.DbRowPreview
import com.fishit.player.core.persistence.inspector.ObxDatabaseInspector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// =============================================================================
// ViewModels
// =============================================================================

@HiltViewModel
class DbInspectorEntityTypesViewModel
    @Inject
    constructor(
        private val inspector: ObxDatabaseInspector,
    ) : ViewModel() {
        data class State(
            val isLoading: Boolean = true,
            val entityTypes: List<DbEntityTypeInfo> = emptyList(),
            val error: String? = null,
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                runCatching { inspector.listEntityTypes() }
                    .onSuccess { types -> _state.update { it.copy(isLoading = false, entityTypes = types) } }
                    .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            }
        }
    }

@HiltViewModel
class DbInspectorRowsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val inspector: ObxDatabaseInspector,
    ) : ViewModel() {
        val entityTypeId: String = savedStateHandle[DbInspectorNavArgs.ARG_ENTITY_TYPE] ?: ""

        data class State(
            val isLoading: Boolean = true,
            val page: DbPage<DbRowPreview>? = null,
            val error: String? = null,
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        private var currentOffset = 0L
        private val pageSize = 50L

        init {
            loadPage(0)
        }

        fun loadPage(offset: Long) {
            currentOffset = offset
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                runCatching { inspector.listRows(entityTypeId, offset, pageSize) }
                    .onSuccess { page -> _state.update { it.copy(isLoading = false, page = page) } }
                    .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            }
        }

        fun nextPage() {
            val p = _state.value.page ?: return
            if (p.offset + p.limit < p.total) {
                loadPage(p.offset + p.limit)
            }
        }

        fun prevPage() {
            val p = _state.value.page ?: return
            if (p.offset > 0) {
                loadPage(maxOf(0, p.offset - p.limit))
            }
        }
    }

@HiltViewModel
class DbInspectorDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val inspector: ObxDatabaseInspector,
    ) : ViewModel() {
        val entityTypeId: String = savedStateHandle[DbInspectorNavArgs.ARG_ENTITY_TYPE] ?: ""
        val rowId: Long = savedStateHandle[DbInspectorNavArgs.ARG_ROW_ID] ?: 0L

        data class State(
            val isLoading: Boolean = true,
            val dump: DbEntityDump? = null,
            val error: String? = null,
            val snackbar: String? = null,
            val deleted: Boolean = false,
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                runCatching { inspector.getEntity(entityTypeId, rowId) }
                    .onSuccess { dump ->
                        if (dump == null) {
                            _state.update { it.copy(isLoading = false, error = "Row not found") }
                        } else {
                            _state.update { it.copy(isLoading = false, dump = dump) }
                        }
                    }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            }
        }

        fun saveChanges(patch: Map<String, String?>) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val result = inspector.updateFields(entityTypeId, rowId, patch)
                if (result.isSuccess) {
                    _state.update { it.copy(snackbar = "Saved ${result.applied} field(s)") }
                    load()
                } else {
                    _state.update { it.copy(isLoading = false, snackbar = result.errors.joinToString("; ")) }
                }
            }
        }

        fun deleteRow(onDeleted: () -> Unit) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val ok = inspector.deleteEntity(entityTypeId, rowId)
                if (ok) {
                    _state.update { it.copy(deleted = true, snackbar = "Row deleted") }
                    onDeleted()
                } else {
                    _state.update { it.copy(isLoading = false, snackbar = "Delete failed") }
                }
            }
        }

        fun clearSnackbar() {
            _state.update { it.copy(snackbar = null) }
        }
    }

// =============================================================================
// Screens
// =============================================================================

@Composable
fun DbInspectorEntityTypesScreen(
    onBack: () -> Unit,
    onOpenEntity: (String) -> Unit,
    viewModel: DbInspectorEntityTypesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            InspectorTopBar(title = "DB Inspector", onBack = onBack, onRefresh = viewModel::load)
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Text(
                        text = "Error: ${state.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.entityTypes) { entity ->
                            EntityTypeCard(entity, onClick = { onOpenEntity(entity.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DbInspectorRowsScreen(
    onBack: () -> Unit,
    onOpenRow: (Long) -> Unit,
    viewModel: DbInspectorRowsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            InspectorTopBar(title = viewModel.entityTypeId, onBack = onBack, onRefresh = { viewModel.loadPage(0) })
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Text(
                        text = "Error: ${state.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                state.page != null -> {
                    val page = state.page!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Pagination header
                        PaginationRow(
                            offset = page.offset,
                            limit = page.limit,
                            total = page.total,
                            onPrev = viewModel::prevPage,
                            onNext = viewModel::nextPage,
                        )
                        HorizontalDivider()
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(page.items, key = { it.id }) { row ->
                                RowPreviewCard(row, onClick = { onOpenRow(row.id) })
                            }
                            if (page.items.isEmpty()) {
                                item {
                                    Text(
                                        text = "No rows",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DbInspectorDetailScreen(
    onBack: () -> Unit,
    viewModel: DbInspectorDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Local edits: fieldName -> edited value
    val edits = remember { mutableStateMapOf<String, String?>() }
    var editMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // When dump changes, reset edits
    LaunchedEffect(state.dump) {
        edits.clear()
    }

    Scaffold(
        topBar = {
            InspectorTopBar(
                title = "${viewModel.entityTypeId} #${viewModel.rowId}",
                onBack = onBack,
                onRefresh = viewModel::load,
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Text(
                        text = "Error: ${state.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                state.dump != null -> {
                    val dump = state.dump!!
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                    ) {
                        // Action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = { editMode = !editMode },
                            ) {
                                Icon(
                                    imageVector = if (editMode) Icons.Default.Save else Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (editMode) "Cancel" else "Edit")
                            }
                            if (editMode && edits.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        viewModel.saveChanges(edits.toMap())
                                        editMode = false
                                    },
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save")
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Fields
                        dump.fields.forEach { field ->
                            FieldRow(
                                field = field,
                                editMode = editMode,
                                editedValue = edits[field.name],
                                onValueChange = { newValue -> edits[field.name] = newValue },
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            // Snackbar
            state.snackbar?.let { msg ->
                Snackbar(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearSnackbar) {
                            Text("OK")
                        }
                    },
                ) {
                    Text(msg)
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Row?") },
            text = { Text("This will permanently remove this row from the database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteRow(onDeleted = onBack)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// =============================================================================
// Reusable Components
// =============================================================================

@Composable
private fun InspectorTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Icon(
            Icons.Default.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}

@Composable
private fun EntityTypeCard(
    entity: DbEntityTypeInfo,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${entity.count} rows",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowPreviewCard(
    row: DbRowPreview,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = "#${row.id}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaginationRow(
    offset: Long,
    limit: Long,
    total: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val start = offset + 1
    val end = minOf(offset + limit, total)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrev, enabled = offset > 0) {
            Text("← Prev")
        }
        Text(
            text = "$start–$end of $total",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onNext, enabled = end < total) {
            Text("Next →")
        }
    }
}

@Composable
private fun FieldRow(
    field: DbFieldValue,
    editMode: Boolean,
    editedValue: String?,
    onValueChange: (String?) -> Unit,
) {
    val displayValue = editedValue ?: field.value ?: "(null)"
    val isEdited = editedValue != null

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.name,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = if (field.editable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = field.type,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (!field.editable) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(read-only)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (editMode && field.editable) {
            OutlinedTextField(
                value = editedValue ?: field.value ?: "",
                onValueChange = { onValueChange(it) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                singleLine = false,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(keyboardType = inferKeyboardType(field.type)),
            )
        } else {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (isEdited) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                        ).padding(8.dp),
            )
        }
    }
}

private fun inferKeyboardType(typeStr: String): KeyboardType =
    when {
        typeStr.startsWith(
            "Long",
        ) ||
            typeStr.startsWith("Int") ||
            typeStr.startsWith("Short") ||
            typeStr.startsWith("Byte") -> KeyboardType.Number
        typeStr.startsWith("Double") || typeStr.startsWith("Float") -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
