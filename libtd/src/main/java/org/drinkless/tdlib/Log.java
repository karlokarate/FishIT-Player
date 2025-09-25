//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2025
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
package org.drinkless.tdlib;

/**
 * TDLib logging control API. This class is required by tdjni to register native methods.
 */
public final class Log {
    /**
     * Callback interface invoked when TDLib encounters a fatal error. None of TDLib APIs can be called
     * from this callback. The process will be terminated after the callback returns.
     */
    public interface FatalErrorCallback {
        void onFatalError(String errorMessage);
    }

    /** Sets the verbosity level of the internal TDLib log. */
    public static native void setVerbosityLevel(int newVerbosityLevel);

    /** Returns the current verbosity level of the internal TDLib log. */
    public static native int getVerbosityLevel();

    /**
     * Sets the path to the file where the internal TDLib log will be written.
     * Pass an empty path to disable file logging.
     *
     * Note: Some tdjni builds expect a boolean return here; align with JNI.
     * Returns true on success, false otherwise.
     */
    public static native boolean setFilePath(String filePath);

    /** Sets the maximum size of the file to where the internal TDLib log is written. */
    public static native void setMaxFileSize(long maxFileSize);

    /** Sets the callback that will be called on a fatal error. Pass null to remove the callback. */
    public static native void setFatalErrorCallback(FatalErrorCallback callback);

    private Log() {}
}
