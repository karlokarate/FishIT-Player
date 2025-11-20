package com.chris.m3usuite.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.repo.SettingsRepository
import com.chris.m3usuite.domain.usecases.NetworkPrefs
import com.chris.m3usuite.domain.usecases.SaveNetworkPrefs
import com.chris.m3usuite.domain.usecases.SaveXtreamPrefs
import com.chris.m3usuite.domain.usecases.XtreamPrefs
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.Locale

data class NetworkSettingsState(
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val userAgent: String = "IBOPlayer/1.4 (Android)",
    val referer: String = "",
    val extraHeadersJson: String = "",
    val isSaving: Boolean = false,
)

class NetworkSettingsViewModel(
    app: Application,
    private val repo: SettingsRepository,
    private val saveNetwork: SaveNetworkPrefs,
    private val saveXtream: SaveXtreamPrefs,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(NetworkSettingsState())
    val state: StateFlow<NetworkSettingsState> = _state

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                repo.m3uUrl,
                repo.epgUrl,
                repo.userAgent,
                repo.referer,
                repo.extraHeadersJson,
            ) { values: Array<Any?> ->
                NetworkSettingsState(
                    m3uUrl = values[0] as String,
                    epgUrl = values[1] as String,
                    userAgent = values[2] as String,
                    referer = values[3] as String,
                    extraHeadersJson = values[4] as String,
                )
            }.collect { _state.value = it }
        }
    }

    /**
     * UI -> Persistenz (Domain-Usecases). Bei m3u-Änderung:
     *  - SaveNetworkPrefs: M3U/EPG/UA/Referer/Headers
     *  - Automatisch Xtream aus M3U ableiten -> SaveXtreamPrefs (host/port/user/pass/output)
     */
    fun onChange(
        m3u: String? = null,
        epg: String? = null,
        ua: String? = null,
        ref: String? = null,
        headersJson: String? = null,
    ) = viewModelScope.launch {
        // 1) UI-State aktualisieren
        val s =
            _state.value.copy(
                m3uUrl = m3u ?: _state.value.m3uUrl,
                epgUrl = epg ?: _state.value.epgUrl,
                userAgent = ua ?: _state.value.userAgent,
                referer = ref ?: _state.value.referer,
                extraHeadersJson = headersJson ?: _state.value.extraHeadersJson,
                isSaving = true,
            )
        _state.value = s

        // 2) Persistieren (Network) via Usecase
        saveNetwork(NetworkPrefs(s.m3uUrl, s.epgUrl, s.userAgent, s.referer, s.extraHeadersJson))

        // 3) Automatisches Ableiten der Xtream-Felder (wie früher)
        if (m3u != null) {
            runCatching {
                deriveXtreamFromM3uIfPossible(m3u)?.let { prefs ->
                    viewModelScope.launch(Dispatchers.IO) {
                        saveXtream(prefs) // <- Domain-Usecase, einheitlicher Pfad
                    }
                }
            }
        }

        _state.update { it.copy(isSaving = false) }
    }

    /**
     * Wenn die M3U wie Xtream aussieht (get.php, username, password), parse
     * host/port/user/pass/output. Sonst: null (kein Ableiten).
     *
     * Beispiele:
     * http://example.com:8080/get.php?username=USER&password=PASS&type=m3u_plus&output=ts
     * https://portal.xyz/get.php?username=u&pAsS=...&output=m3u8
     */
    private suspend fun deriveXtreamFromM3uIfPossible(m3uUrl: String): XtreamPrefs? =
        withContext(Dispatchers.Default) {
            val url = m3uUrl.trim()
            if (url.isEmpty()) return@withContext null

            val lower = url.lowercase(Locale.ROOT)
            if (!lower.contains("get.php") && !lower.contains("username=")) return@withContext null

            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return@withContext null

            // Host & Port
            val host = uri.host?.trim().orEmpty()
            if (host.isEmpty()) return@withContext null
            val isHttps = (uri.scheme?.equals("https", true) == true)
            val port =
                when {
                    uri.port > 0 -> uri.port
                    isHttps -> 443
                    else -> 80
                }

            // Query-Parameter robust lesen
            fun qp(key: String): String? {
                val all = uri.query?.split('&').orEmpty()
                val keyLower = key.lowercase(Locale.ROOT)
                val hit =
                    all.firstOrNull {
                        val k = it.substringBefore('=', "").lowercase(Locale.ROOT)
                        k == keyLower
                    } ?: return null
                val raw = hit.substringAfter('=', "")
                return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: raw
            }

            val user = qp("username") ?: qp("user") ?: ""
            val pass = qp("password") ?: qp("pass") ?: qp("pwd") ?: ""
            val output = qp("output") ?: "m3u8"

            if (user.isEmpty() || pass.isEmpty()) return@withContext null

            XtreamPrefs(
                host = host,
                port = port,
                user = user,
                pass = pass,
                output = output,
            )
        }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SettingsStore(app)
                    val repo = SettingsRepository(store)
                    val saveNetwork = SaveNetworkPrefs(repo) // :contentReference[oaicite:4]{index=4}
                    val saveXtream = SaveXtreamPrefs(repo) // :contentReference[oaicite:5]{index=5}
                    @Suppress("UNCHECKED_CAST")
                    return NetworkSettingsViewModel(app, repo, saveNetwork, saveXtream) as T
                }
            }
        }
    }
}
