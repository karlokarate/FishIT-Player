package com.chris.m3usuite.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

/**
 * Safely obtain a Painter from a drawable resource id.
 * Avoids try/catch around composable calls by validating the id up-front.
 */
@Composable
fun safePainter(
    id: Int,
    label: String? = null,
): Painter {
    val ctx = LocalContext.current
    val validId =
        remember(id) {
            try {
                if (id != 0) {
                    // Validate resource existence; throws if invalid
                    ctx.resources.getResourceName(id)
                    id
                } else {
                    0
                }
            } catch (_: Throwable) {
                android.R.drawable.ic_menu_report_image
            }
        }
    return painterResource(id = if (validId != 0) validId else android.R.drawable.ic_menu_report_image)
}
