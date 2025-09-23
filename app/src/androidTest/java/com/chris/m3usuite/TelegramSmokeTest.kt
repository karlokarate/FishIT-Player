package com.chris.m3usuite

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.TelegramTdlibDataSource
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramSmokeTest {

    // This is a smoke-test scaffold; it exercises the fallback path without requiring live Telegram.
    @Ignore("Scaffold only; requires environment to set tg_enabled and no live TDLib auth")
    @Test fun tg_datasource_fallback_when_not_authenticated() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsStore(appContext)
        // Ensure feature is ON to reach auth gate (real test could write via DataStore)
        // Run DataSource with a fake fallback that records invocation
        var fallbackOpened = false
        val fakeFallbackFactory = DataSource.Factory { object : DataSource {
            override fun open(dataSpec: DataSpec): Long { fallbackOpened = true; return -1 }
            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {}
            override fun close() {}
            override fun getUri(): Uri? = null
            override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int = -1
        } }
        var notified = false
        val notifier: (String) -> Unit = { notified = true }
        val ds = TelegramTdlibDataSource(appContext, fakeFallbackFactory, TelegramTdlibDataSource.Factory(appContext, fakeFallbackFactory), notifier)
        val spec = DataSpec.Builder().setUri(Uri.parse("tg://message?chatId=1&messageId=1")).build()
        try { ds.open(spec) } catch (_: Throwable) {}
        assertTrue("Fallback should be used when not authenticated", fallbackOpened)
        assertTrue("Notifier should be triggered on auth fallback", notified)
    }
}

