package com.chris.m3usuite.ui.fx
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.chris.m3usuite.R
import com.chris.m3usuite.ui.debug.safePainter
@Composable
fun FishBackground(
    modifier: Modifier = Modifier,
    alpha: Float = 0.05f,
    neutralizeUnderlay: Boolean = false,
    neutralizeColor: Color = MaterialTheme.colorScheme.background
) {
    Box(modifier = modifier) {
        if (neutralizeUnderlay) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = neutralizeColor)
            }
        }
        Image(
            painter = safePainter(id = R.drawable.fisch_bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = alpha),
            contentScale = ContentScale.Fit
        )
    }
}
