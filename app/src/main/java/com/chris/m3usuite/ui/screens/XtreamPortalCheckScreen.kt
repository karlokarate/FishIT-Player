package com.chris.m3usuite.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import com.chris.m3usuite.ui.common.TvButton
import com.chris.m3usuite.ui.common.TvTextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.core.http.CookieBridge
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XtreamPortalCheckScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    var portal by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val host = store.xtHost.first()
        val port = store.xtPort.first()
        val scheme = if (port == 443) "https" else "http"
        val base = if (port == 80 || port == 443) "$scheme://$host" else "$scheme://$host:$port"
        portal = base
        info = "Lade $base … bitte ggf. Challenge abwarten."
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Portal prüfen (Cloudflare)")
        Spacer(Modifier.height(8.dp))
        Text(info)
        Spacer(Modifier.height(8.dp))
        if (portal.isNotBlank()) {
            AndroidView(
                modifier = Modifier.weight(1f),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                info = "Seite geladen. Tippe auf ‘Cookies übernehmen’."
                            }
                        }
                        loadUrl(portal)
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(onClick = {
                    // Import cookies into OkHttp and finish
                    CookieBridge.importFromWebView(ctx, portal)
                    onDone()
                }) { Text("Cookies übernehmen") }
                com.chris.m3usuite.ui.common.TvTextButton(onClick = onDone) { Text("Abbrechen") }
            }
        }
    }
}
