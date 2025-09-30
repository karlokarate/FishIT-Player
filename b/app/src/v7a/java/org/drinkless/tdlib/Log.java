package org.drinkless.tdlib;

/**
 * Stub Log for armeabi-v7a flavor.
 * Provides the LogMessageHandler interface and no-op setters.
 */
public final class Log {
    private Log() {
    }

    public interface LogMessageHandler {
        void onLogMessage(int verbosityLevel, String message);
    }

    public static void setVerbosityLevel(int newVerbosityLevel) {
        // no-op
    }

    public static void setLogMessageHandler(int maxVerbosityLevel, LogMessageHandler handler) {
        // no-op
    }
}
