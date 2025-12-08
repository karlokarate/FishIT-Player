package com.chris.m3usuite.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.common.TvButton
import com.chris.m3usuite.ui.common.TvOutlinedButton
import com.chris.m3usuite.ui.theme.DesignTokens

/**
 * Horizontal bar of actions with TV focus visuals.
 * Primary actions use filled buttons; secondary actions outlined/text.
 */
@Composable
fun MediaActionBar(
    actions: List<MediaAction>,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        actions.forEachIndexed { index, a ->
            val tag = MediaActionDefaults.testTagFor(a.id)
            val base =
                Modifier
                    .semantics(mergeDescendants = false) {
                        this.role = Role.Button
                        // Set TestTag semantics directly (avoid ui-test dependency)
                        this[SemanticsProperties.TestTag] = tag
                    }.then(Modifier)
            val labelText = a.badge?.let { "${a.label} ($it)" } ?: a.label
            when {
                a.primary ->
                    TvButton(
                        onClick = a.onClick,
                        enabled = a.enabled,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = DesignTokens.Accent,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        modifier = base,
                    ) { Text(labelText) }
                else ->
                    TvOutlinedButton(
                        onClick = a.onClick,
                        enabled = a.enabled,
                        modifier = base,
                    ) { Text(labelText) }
            }
        }
    }
    // Initial focus auto-request disabled to avoid stealing focus from sections below.
}
