package com.chris.m3usuite.backup

import android.content.Intent
import android.net.Uri

object BackupSaf {
    const val MIME_MSBX = "application/octet-stream"

    fun createExportIntent(suggested: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_MSBX
            putExtra(Intent.EXTRA_TITLE, suggested)
        }

    fun createImportIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
}
