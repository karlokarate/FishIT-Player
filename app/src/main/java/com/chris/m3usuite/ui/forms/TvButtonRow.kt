package com.chris.m3usuite.ui.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.common.TvButton
import com.chris.m3usuite.ui.theme.DesignTokens

@Composable
fun TvButtonRow(
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        TvButton(
            onClick = onPrimary,
            enabled = enabled && !busy,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = DesignTokens.Accent,
                contentColor = Color.Black
            )
        ) {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Bitte warten…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(primaryText)
            }
        }
        if (secondaryText != null && onSecondary != null) {
            com.chris.m3usuite.ui.common.TvOutlinedButton(onClick = onSecondary) {
                Text(secondaryText)
            }
        }
    }
}

