package com.chris.m3usuite

import android.app.Application
import com.chris.m3usuite.core.telemetry.FrameTimeWatchdog
import com.chris.m3usuite.core.telemetry.Telemetry
import java.io.Closeable

class App : Application() {
    private var telemetryCloser: Closeable? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize debug tools in debug builds
        if (BuildConfig.DEBUG) {
            try {
                val debugClass = Class.forName("com.chris.m3usuite.DebugToolsInitializer")
                val initMethod = debugClass.getMethod("initialize", Application::class.java)
                initMethod.invoke(null, this)
            } catch (e: Exception) {
                // Debug initializer not available (e.g., in release builds)
            }
        }

        Telemetry.registerDefault(this)
        telemetryCloser = FrameTimeWatchdog.install()
    }

    // Global image loader: our AsyncImage wrappers use AppImageLoader directly.
}
