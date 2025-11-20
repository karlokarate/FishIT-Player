package com.chris.m3usuite.domain.usecases

import com.chris.m3usuite.data.repo.SettingsRepository

data class XtreamPrefs(
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val output: String, // e.g. "m3u8" | "ts" etc.
)

class SaveXtreamPrefs(
    private val repo: SettingsRepository,
) {
    suspend operator fun invoke(p: XtreamPrefs) {
        val safePort = p.port.coerceIn(1, 65535)
        repo.setXtream(
            host = p.host.trim(),
            port = safePort,
            user = p.user.trim(),
            pass = p.pass, // Store verschlüsselt selbst
            output = p.output.trim().ifBlank { "m3u8" },
        )
    }
}
