package com.chris.m3usuite.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Route-scoped Assign (multi-select) controller.
 *
 * While active, tiles may opt-in to toggle selection using the exposed API.
 * Selected IDs are stored as encoded media IDs (OBX bridge).
 */
@Immutable
class AssignSelectionState internal constructor(
    private val _active: MutableState<Boolean>,
    private val _selected: MutableList<Long>,
) {
    val active: Boolean get() = _active.value
    val selectedCount: Int get() = _selected.size
    val selectedSnapshot: List<Long> get() = _selected.toList()

    fun toggleActive() {
        _active.value = !_active.value
        if (!_active.value) clear()
    }

    fun select(id: Long) {
        if (!_selected.contains(id)) {
            _selected.add(id)
        }
    }

    fun deselect(id: Long) {
        _selected.remove(id)
    }

    fun toggle(id: Long) {
        if (_selected.contains(id)) _selected.remove(id) else _selected.add(id)
    }

    fun clear() {
        _selected.clear()
    }
}

private val AssignSelectionSaver: Saver<AssignSelectionState, Any> =
    listSaver(
        save = {
            val ids = it.selectedSnapshot
            val active = it.active
            listOf(active) + ids
        },
        restore = { list ->
            val active = (list.firstOrNull() as? Boolean) ?: false
            val ids = list.drop(1).mapNotNull { v -> (v as? Number)?.toLong() }.toMutableList()
            AssignSelectionState(mutableStateOf(active), ids)
        },
    )

@Composable
fun rememberAssignSelectionState(): AssignSelectionState =
    rememberSaveable(saver = AssignSelectionSaver) {
        AssignSelectionState(mutableStateOf(false), mutableStateListOf())
    }

val LocalAssignSelection = staticCompositionLocalOf<AssignSelectionState?> { null }

@Composable
fun ProvideAssignSelection(
    state: AssignSelectionState,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAssignSelection provides state, content = content)
}

/**
 * A lightweight controller bar to toggle assign-mode and apply selection.
 * Place near screen headers (Start/Library/Search).
 */
@Composable
fun AssignModeBar(
    state: AssignSelectionState,
    modifier: Modifier = Modifier,
    onPickProfileAndApply: suspend (selectedEncodedIds: List<Long>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Button(onClick = { state.toggleActive() }) {
            Text(text = if (state.active) "Zuweisen beenden" else "Zuweisen")
        }
        OutlinedButton(
            enabled = state.active && state.selectedCount > 0,
            onClick = {
                if (!state.active || state.selectedCount == 0) return@OutlinedButton
                scope.launch {
                    onPickProfileAndApply(state.selectedSnapshot)
                    // Reset after apply
                    state.clear()
                    state.toggleActive()
                }
            },
        ) {
            Text(text = "Profil wählen • ${state.selectedCount} ausgewählt")
        }
    }
}

/**
 * Optional selection badge for tiles. Call from within a tile to render a subtle
 * selection overlay when assign-mode is active.
 */
@Composable
fun AssignSelectionBadge(
    encodedId: Long,
    modifier: Modifier = Modifier,
) {
    val sel = LocalAssignSelection.current
    if (sel?.active == true) {
        val selected = sel.selectedSnapshot.contains(encodedId)
        Box(
            modifier =
                modifier
                    .alpha(if (selected) 1f else 0.6f)
                    .background(
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                Color.Black.copy(alpha = 0.35f)
                            },
                        shape = RoundedCornerShape(6.dp),
                    ).padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = if (selected) "Ausgewählt" else "Wählen",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * Tiles can call this in their click handler to toggle selection when assign-mode is active.
 * Return true if the event was consumed by selection.
 * Use the overload with explicit state for non-Composable contexts (e.g., onClick lambdas).
 */
fun handleAssignSelectionClick(
    encodedId: Long,
    state: AssignSelectionState?,
): Boolean {
    val sel = state ?: return false
    if (!sel.active) return false
    sel.toggle(encodedId)
    return true
}

@Composable
fun handleAssignSelectionClick(encodedId: Long): Boolean {
    val sel = LocalAssignSelection.current
    if (sel == null || !sel.active) return false
    sel.toggle(encodedId)
    return true
}
