package org.drinkless.tdlib;

/**
 * Lightweight Java stub for armeabi-v7a builds to avoid native loading.
 * This class mirrors the public surface needed by app code but performs no TDLib operations.
 *
 * Note:
 * - No System.loadLibrary call here.
 * - Methods are no-ops or return null.
 * - BuildConfig.TG_TDLIB_ENABLED in the app is set to false for v7a, so app code should gate usage.
 */
@SuppressWarnings({"unused"})
public final class Client {

    public interface ResultHandler {
        void onResult(TdApi.Object object);
    }

    public interface ErrorHandler {
        void onError(int errorCode, String errorMessage);
    }

    public interface ExceptionHandler {
        void onException(Throwable e);
    }

    private final ResultHandler updatesHandler;
    private final ErrorHandler errorHandler;
    private final ExceptionHandler exceptionHandler;

    private Client(ResultHandler updatesHandler,
                   ErrorHandler errorHandler,
                   ExceptionHandler exceptionHandler) {
        this.updatesHandler = updatesHandler;
        this.errorHandler = errorHandler;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Creates a stub client that never connects to TDLib.
     */
    public static Client create(ResultHandler updatesHandler,
                                ErrorHandler errorHandler,
                                ExceptionHandler exceptionHandler) {
        return new Client(updatesHandler, errorHandler, exceptionHandler);
    }

    /**
     * No-op send. Immediately returns without invoking native code.
     */
    public void send(TdApi.Function query, ResultHandler resultHandler) {
        // Best-effort: signal "not supported" via error handler if present.
        if (errorHandler != null) {
            errorHandler.onError(-1, "TDLib is disabled for v7a build (stub).");
        }
        // Also callback result handler with null to avoid hangs in simple flows.
        if (resultHandler != null) {
            resultHandler.onResult(null);
        }
    }

    /**
     * No-op execute. Always returns null.
     */
    public static TdApi.Object execute(TdApi.Function query) {
        return null;
    }

    /**
     * No-op update handler setter.
     */
    public void setUpdatesHandler(ResultHandler updatesHandler, int delayMs) {
        // ignore
    }

    /**
     * No-op destroy.
     */
    public void destroy() {
        // ignore
    }

    // No static initializer; critical to avoid System.loadLibrary on v7a
}
