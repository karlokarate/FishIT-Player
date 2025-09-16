package com.chris.m3usuite.ui.fx

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material Motion „Fade through“:
 * Enter: 90ms Delay, 220ms Fade + ScaleIn(0.92)
 * Exit :  90ms Fade + ScaleOut(1.02)
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FadeThrough(
  key: Any,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  AnimatedContent(
    targetState = key,
    transitionSpec = {
      (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
       scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90))) togetherWith
      (fadeOut(animationSpec = tween(90)) +
       scaleOut(targetScale = 1.02f, animationSpec = tween(90)))
    },
    modifier = modifier,
    label = "fadeThrough"
  ) { _ -> content() }
}
