package com.chris.m3usuite.data.repo

import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow

/**
 * Repository-Schicht über dem SettingsStore.
 * - Liefert klar abgegrenzte Flows
 * - Enthält nur Settings-bezogene Schreibvorgänge (keine UI/Worker-Logik)
 */
class SettingsRepository(
    private val store: SettingsStore
) {

    // --- Network ---
    val m3uUrl: Flow<String> = store.m3uUrl
    val epgUrl: Flow<String> = store.epgUrl
    val userAgent: Flow<String> = store.userAgent
    val referer: Flow<String> = store.referer
    val extraHeadersJson: Flow<String> = store.extraHeadersJson

    suspend fun saveNetworkBases(
        m3u: String,
        epg: String,
        ua: String,
        ref: String
    ) {
        store.setM3uUrl(m3u)
        store.setEpgUrl(epg)
        store.setUserAgent(ua)
        store.setReferer(ref)
    }

    suspend fun setExtraHeadersJson(json: String) {
        store.setExtraHeadersJson(json)
    }

    // --- Player ---
    val playerMode: Flow<String> = store.playerMode                // "ask" | "internal" | "external"
    val preferredPlayerPkg: Flow<String> = store.preferredPlayerPkg
    val rotationLocked: Flow<Boolean> = store.rotationLocked
    val autoplayNext: Flow<Boolean> = store.autoplayNext

    val subtitleScale: Flow<Float> = store.subtitleScale
    val subtitleFg: Flow<Int> = store.subtitleFg
    val subtitleBg: Flow<Int> = store.subtitleBg
    val subtitleFgOpacityPct: Flow<Int> = store.subtitleFgOpacityPct
    val subtitleBgOpacityPct: Flow<Int> = store.subtitleBgOpacityPct

    suspend fun setPlayerMode(mode: String) = store.setPlayerMode(mode)
    suspend fun setPreferredPlayerPackage(pkg: String) = store.setPreferredPlayerPackage(pkg)
    suspend fun setRotationLocked(locked: Boolean) = store.setRotationLocked(locked)
    suspend fun setAutoplayNext(enabled: Boolean) = store.setAutoplayNext(enabled)

    suspend fun setSubtitleStyle(scale: Float, fgArgb: Int, bgArgb: Int) =
        store.setSubtitleStyle(scale, fgArgb, bgArgb)

    suspend fun setSubtitleFgOpacityPct(value: Int) = store.setSubtitleFgOpacityPct(value)
    suspend fun setSubtitleBgOpacityPct(value: Int) = store.setSubtitleBgOpacityPct(value)

    // --- Xtream ---
    val xtHost: Flow<String> = store.xtHost
    val xtPort: Flow<Int> = store.xtPort
    val xtUser: Flow<String> = store.xtUser
    val xtPass: Flow<String> = store.xtPass // bereits entschlüsselt vom Store
    val xtOutput: Flow<String> = store.xtOutput

    suspend fun setXtream(
        host: String,
        port: Int,
        user: String,
        pass: String,
        output: String
    ) {
        store.setXtHost(host)
        store.setXtPort(port)
        store.setXtUser(user)
        store.setXtPass(pass)
        store.setXtOutput(output)
    }

    // --- EPG ---
    val epgFavUseXtream: Flow<Boolean> = store.epgFavUseXtream
    val epgFavSkipXmltvIfXtreamOk: Flow<Boolean> = store.epgFavSkipXmltvIfXtreamOk

    suspend fun setEpgFavUseXtream(value: Boolean) = store.setEpgFavUseXtream(value)
    suspend fun setEpgFavSkipXmltvIfXtreamOk(value: Boolean) = store.setEpgFavSkipXmltvIfXtreamOk(value)
}
