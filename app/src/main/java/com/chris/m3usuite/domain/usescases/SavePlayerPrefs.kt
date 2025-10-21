package com.chris.m3usuite.domain.usecases

import com.chris.m3usuite.data.repo.SettingsRepository

data class PlayerPrefs(
    val mode: String,              // "ask" | "internal" | "external"
    val preferredPkg: String,
    val rotationLocked: Boolean,
    val autoplayNext: Boolean,
    val subScale: Float,
    val subFgArgb: Int,
    val subBgArgb: Int,
    val subFgOpacityPct: Int,
    val subBgOpacityPct: Int
)

class SavePlayerPrefs(
    private val repo: SettingsRepository
) {
    suspend operator fun invoke(p: PlayerPrefs) {
        repo.setPlayerMode(p.mode)
        repo.setPreferredPlayerPackage(p.preferredPkg)
        repo.setRotationLocked(p.rotationLocked)
        repo.setAutoplayNext(p.autoplayNext)
        // Reihenfolge: erst Style, dann Opacity-Pcts (damit UI-Flicker minimiert bleibt)
        repo.setSubtitleStyle(p.subScale, p.subFgArgb, p.subBgArgb)
        repo.setSubtitleFgOpacityPct(p.subFgOpacityPct.coerceIn(0, 100))
        repo.setSubtitleBgOpacityPct(p.subBgOpacityPct.coerceIn(0, 100))
    }
}
