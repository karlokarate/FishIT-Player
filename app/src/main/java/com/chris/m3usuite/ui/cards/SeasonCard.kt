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
fun SeasonCard(
    name: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val cfg = LocalConfiguration.current
    val isTablet = cfg.smallestScreenWidthDp >= 600
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val width = when {
        isTv -> 160.dp
        isTablet -> 150.dp
        else -> 140.dp
    }
    Column(
        modifier = modifier.semantics { this[SemanticsProperties.TestTag] = "Card-Season" },
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(2f / 3f)
                .clip(shape)
                .focusScaleOnTv()
                .tvClickable(brightenContent = false, onClick = onClick)
        ) {
            AppAsyncImage(
                url = posterUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                crossfade = false
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
