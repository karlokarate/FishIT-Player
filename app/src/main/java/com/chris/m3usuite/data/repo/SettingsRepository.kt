package com.chris.m3usuite.data.repo

import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow

/**
 * Repository-Schicht über dem SettingsStore.
 * - Liefert klar abgegrenzte Flows
 * - Enthält nur Settings-bezogene Schreibvorgänge (keine UI/Worker-Logik)
 */
class SettingsRepository(
    private val store: SettingsStore,
) {
    // --- Network ---
    val m3uUrl: Flow<String> = store.m3uUrl
    val epgUrl: Flow<String> = store.epgUrl
    val userAgent: Flow<String> = store.userAgent
    val referer: Flow<String> = store.referer
    val extraHeadersJson: Flow<String> = store.extraHeadersJson
    val showAdults: Flow<Boolean> = store.showAdults

    suspend fun saveNetworkBases(
        m3u: String,
        epg: String,
        ua: String,
        ref: String,
    ) {
        store.setM3uUrl(m3u)
        store.setEpgUrl(epg)
        store.setUserAgent(ua)
        store.setReferer(ref)
    }

    suspend fun setExtraHeadersJson(json: String) {
        store.setExtraHeadersJson(json)
    }

    suspend fun setShowAdults(value: Boolean) = store.setShowAdults(value)

    // --- Player ---
    val playerMode: Flow<String> = store.playerMode // "ask" | "internal" | "external"
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

    suspend fun setSubtitleStyle(
        scale: Float,
        fgArgb: Int,
        bgArgb: Int,
    ) = store.setSubtitleStyle(scale, fgArgb, bgArgb)

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
        output: String,
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

    // --- Telegram Advanced Settings ---
    // Engine settings
    val tgMaxGlobalDownloads: Flow<Int> = store.tgMaxGlobalDownloads
    val tgMaxVideoDownloads: Flow<Int> = store.tgMaxVideoDownloads
    val tgMaxThumbDownloads: Flow<Int> = store.tgMaxThumbDownloads
    val tgShowEngineOverlay: Flow<Boolean> = store.tgShowEngineOverlay

    suspend fun setTgMaxGlobalDownloads(value: Int) = store.setTgMaxGlobalDownloads(value)

    suspend fun setTgMaxVideoDownloads(value: Int) = store.setTgMaxVideoDownloads(value)

    suspend fun setTgMaxThumbDownloads(value: Int) = store.setTgMaxThumbDownloads(value)

    suspend fun setTgShowEngineOverlay(value: Boolean) = store.setTgShowEngineOverlay(value)

    // Streaming / buffering settings
    val tgInitialPrefixBytes: Flow<Long> = store.tgInitialPrefixBytes
    val tgSeekMarginBytes: Flow<Long> = store.tgSeekMarginBytes
    val tgEnsureFileReadyTimeoutMs: Flow<Long> = store.tgEnsureFileReadyTimeoutMs
    val tgShowStreamingOverlay: Flow<Boolean> = store.tgShowStreamingOverlay

    suspend fun setTgInitialPrefixBytes(value: Long) = store.setTgInitialPrefixBytes(value)

    suspend fun setTgSeekMarginBytes(value: Long) = store.setTgSeekMarginBytes(value)

    suspend fun setTgEnsureFileReadyTimeoutMs(value: Long) = store.setTgEnsureFileReadyTimeoutMs(value)

    suspend fun setTgShowStreamingOverlay(value: Boolean) = store.setTgShowStreamingOverlay(value)

    // Thumbnail / poster prefetch settings
    val tgThumbPrefetchEnabled: Flow<Boolean> = store.tgThumbPrefetchEnabled
    val tgThumbPrefetchBatchSize: Flow<Int> = store.tgThumbPrefetchBatchSize
    val tgThumbMaxParallel: Flow<Int> = store.tgThumbMaxParallel
    val tgThumbPauseWhileVodBuffering: Flow<Boolean> = store.tgThumbPauseWhileVodBuffering
    val tgThumbFullDownload: Flow<Boolean> = store.tgThumbFullDownload

    suspend fun setTgThumbPrefetchEnabled(value: Boolean) = store.setTgThumbPrefetchEnabled(value)

    suspend fun setTgThumbPrefetchBatchSize(value: Int) = store.setTgThumbPrefetchBatchSize(value)

    suspend fun setTgThumbMaxParallel(value: Int) = store.setTgThumbMaxParallel(value)

    suspend fun setTgThumbPauseWhileVodBuffering(value: Boolean) = store.setTgThumbPauseWhileVodBuffering(value)

    suspend fun setTgThumbFullDownload(value: Boolean) = store.setTgThumbFullDownload(value)

    // ExoPlayer buffer settings
    val exoMinBufferMs: Flow<Int> = store.exoMinBufferMs
    val exoMaxBufferMs: Flow<Int> = store.exoMaxBufferMs
    val exoBufferForPlaybackMs: Flow<Int> = store.exoBufferForPlaybackMs
    val exoBufferForPlaybackAfterRebufferMs: Flow<Int> = store.exoBufferForPlaybackAfterRebufferMs
    val exoExactSeek: Flow<Boolean> = store.exoExactSeek

    suspend fun setExoMinBufferMs(value: Int) = store.setExoMinBufferMs(value)

    suspend fun setExoMaxBufferMs(value: Int) = store.setExoMaxBufferMs(value)

    suspend fun setExoBufferForPlaybackMs(value: Int) = store.setExoBufferForPlaybackMs(value)

    suspend fun setExoBufferForPlaybackAfterRebufferMs(value: Int) = store.setExoBufferForPlaybackAfterRebufferMs(value)

    suspend fun setExoExactSeek(value: Boolean) = store.setExoExactSeek(value)

    // Diagnostics / logging settings
    val tgAppLogLevel: Flow<Int> = store.tgAppLogLevel
    val jankTelemetrySampleRate: Flow<Int> = store.jankTelemetrySampleRate

    suspend fun setTgAppLogLevel(value: Int) = store.setTgAppLogLevel(value)

    suspend fun setJankTelemetrySampleRate(value: Int) = store.setJankTelemetrySampleRate(value)
}
