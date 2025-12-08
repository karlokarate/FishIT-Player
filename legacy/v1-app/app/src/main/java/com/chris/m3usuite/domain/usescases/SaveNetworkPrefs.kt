package com.chris.m3usuite.domain.usecases

import com.chris.m3usuite.data.repo.SettingsRepository

data class NetworkPrefs(
    val m3uUrl: String,
    val epgUrl: String,
    val userAgent: String,
    val referer: String,
    val extraHeadersJson: String,
)

class SaveNetworkPrefs(
    private val repo: SettingsRepository,
) {
    suspend operator fun invoke(p: NetworkPrefs) {
        repo.saveNetworkBases(p.m3uUrl.trim(), p.epgUrl.trim(), p.userAgent.trim(), p.referer.trim())
        repo.setExtraHeadersJson(p.extraHeadersJson.trim())
    }
}
