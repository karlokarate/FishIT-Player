package com.chris.m3usuite.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.chris.m3usuite.ui.theme.DesignTokens
import androidx.compose.foundation.layout.PaddingValues

@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    accent: Color = DesignTokens.Accent,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .shadow(elevation = 3.dp, shape = shape, clip = false)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), shape)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.25f)), shape)
            ,
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding)) { content() }
    }
}
