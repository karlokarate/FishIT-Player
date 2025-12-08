package com.chris.m3usuite.ui.util

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

/**
 * Turns a stored avatar path into a model accepted by Coil:
 * - null/blank -> null (use fallback icon)
 * - absolute path ("/…") -> File(path)
 * - file:// or content:// -> Uri.parse(path)
 * - everything else -> raw string (e.g., http/https)
 */
@Composable
fun rememberAvatarModel(path: String?): Any? =
    remember(path) {
        when {
            path.isNullOrBlank() -> null
            path.startsWith("/") -> File(path)
            path.startsWith("file://") || path.startsWith("content://") -> Uri.parse(path)
            else -> path
        }
    }
