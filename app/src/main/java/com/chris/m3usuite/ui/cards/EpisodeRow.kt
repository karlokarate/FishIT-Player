package com.chris.m3usuite.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.util.AppAsyncImage

@Composable
fun EpisodeRow(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val cfg = LocalConfiguration.current
    val isTablet = cfg.smallestScreenWidthDp >= 600
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val height = when {
        isTv -> 120.dp
        isTablet -> 110.dp
        else -> 96.dp
    }
    Row(
        modifier = modifier
            .semantics { this[SemanticsProperties.TestTag] = "Card-Episode" }
            .fillMaxWidth()
            .height(height)
            .focusScaleOnTv()
            .tvClickable(brightenContent = false, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .clip(shape)
        ) {
            AppAsyncImage(
                url = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                crossfade = false
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
