package com.chris.m3usuite.player.internal.system

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chris.m3usuite.R

/**
 * Kapselt:
 * - Back-Handling
 * - Screen-On
 * - (optionales) Fullscreen-Hiding
 * - PiP-Helfer
 */
@Composable
fun InternalPlayerSystemUi(
    isPlaying: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current

    // Back geht nach außen
    BackHandler { onClose() }

    // Screen-On solange gespielt wird
    DisposableEffect(isPlaying) {
        val old = view.keepScreenOn
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = old }
    }

    // Optional: Systembars ausblenden (kannst du konfigurieren)
    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController =
            window?.let { WindowInsetsControllerCompat(it, it.decorView) }

        insetsController?.let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * PiP-EntryPoint, kann direkt vom Pip-Button aufgerufen werden.
 */
fun requestPictureInPicture(activity: Activity?) {
    activity ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val params =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        activity.enterPictureInPictureMode(params)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        @Suppress("DEPRECATION")
        activity.enterPictureInPictureMode()
    } else {
        runCatching {
            Toast
                .makeText(activity, R.string.pip_not_available, Toast.LENGTH_SHORT)
                .show()
        }
    }
}