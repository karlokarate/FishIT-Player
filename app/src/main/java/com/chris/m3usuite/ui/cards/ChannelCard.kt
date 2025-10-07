package com.chris.m3usuite.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun ChannelCard(
    name: String,
    logoUrl: String?,
    nowNext: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val cfg = LocalConfiguration.current
    val isTablet = cfg.smallestScreenWidthDp >= 600
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val width = when {
        isTv -> 220.dp
        isTablet -> 200.dp
        else -> 180.dp
    }
    Column(
        modifier = modifier.semantics { this[SemanticsProperties.TestTag] = "Card-Channel" },
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(16f / 10f)
                .clip(shape)
                .focusScaleOnTv()
                .tvClickable(brightenContent = false, onClick = onClick)
        ) {
            AppAsyncImage(
                url = logoUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                crossfade = false
            )
            if (!nowNext.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                ) { Text(nowNext, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
