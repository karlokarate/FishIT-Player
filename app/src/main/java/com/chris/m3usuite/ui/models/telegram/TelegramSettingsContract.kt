
package com.chris.m3usuite.ui.models.telegram

import androidx.compose.runtime.Immutable

@Immutable
data class TelegramSettingsState(
    val enabled: Boolean = false,
    val isLoggedIn: Boolean = false,
    val statusText: String = "",
    val selectedChatsCsv: String = "",
    val selectedChatsCount: Int = 0,
    val cacheLimitGb: Int = 4,
    val logLevel: Int = 1, // 0..5
    val preferIpv6: Boolean = false,
    val keepOnline: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null
)

sealed interface TelegramSettingsEvent {
    data class Toast(val message: String): TelegramSettingsEvent
    data class Failure(val message: String): TelegramSettingsEvent
    object RequestChatPicker: TelegramSettingsEvent
    object RequestLoginFlow: TelegramSettingsEvent
}

@Immutable
data class TelegramProxyState(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.None,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val secret: String = "" // for MTProto
)

enum class ProxyType { None, Socks5, Http, MtProto }

@Immutable
data class TelegramAutoDownloadState(
    val wifi: AutoCategory = AutoCategory(),
    val mobile: AutoCategory = AutoCategory(),
    val roaming: AutoCategory = AutoCategory()
)

@Immutable
data class AutoCategory(
    val autoDownload: Boolean = true,
    val bigVideos: Boolean = false,
    val nextAudios: Boolean = true,
    val stories: Boolean = false,
    val lowDataCalls: Boolean = true
)
