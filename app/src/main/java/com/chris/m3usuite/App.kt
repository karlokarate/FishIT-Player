package com.chris.m3usuite

import android.app.Application
import com.chris.m3usuite.core.telemetry.FrameTimeWatchdog
import com.chris.m3usuite.core.telemetry.Telemetry
import java.io.Closeable

class App : Application() {
    private var telemetryCloser: Closeable? = null

    override fun onCreate() {
        super.onCreate()
        Telemetry.registerDefault(this)
        telemetryCloser = FrameTimeWatchdog.install()
    }

    // Global image loader: our AsyncImage wrappers use AppImageLoader directly.
}
