package com.chris.m3usuite.telegram.core

/**
 * Central configuration for Telegram streaming & downloading.
 *
 * This config is intentionally small and focused:
 *
 * - TDLib is responsible for buffering and caching.
 * - The app side only controls *how much* we ask TDLib to download and
 *   *wie aggressiv* wir für Streaming nachladen.
 *
 * No ringbuffers, no windowing, no RandomAccessFile – we always stream
 * directly from TDLib's cached file on disk (zero-copy friendly).
 */
object StreamingConfigRefactor {
    // --- Prefix / Read-Ahead Strategy ---------------------------------------

    /**
     * Minimal prefix we consider "safe" to start playback, in bytes.
     *
     * Wird z. B. genutzt, wenn ein Player von 0 startet und wir eine
     * gewisse Menge vorn im File haben wollen, bevor es richtig losgeht.
     */
    const val DEFAULT_PREFIX_BYTES: Long = 512 * 1024 // 512 KiB

    /**
     * Standard-Read-Ahead für Streams, in Bytes.
     *
     * Beispiel: Wenn ExoPlayer gerade bei Position X liest, stellen wir
     * sicher, dass X + MIN_READ_AHEAD_BYTES bereits von TDLib geladen ist.
     *
     * Der konkrete Wert ist ein Trade-Off:
     * - zu klein = mehr Nachlade-Peaks
     * - zu groß  = mehr IO / Speicherverbrauch
     */
    const val MIN_READ_AHEAD_BYTES: Long = 2L * 1024 * 1024 // 2 MiB

    // --- Timeouts / Polling -------------------------------------------------

    /**
     * Maximale Wartezeit für ensureFileReady() & ähnliche Operationen.
     */
    const val ENSURE_READY_TIMEOUT_MS: Long = 30_000L

    /**
     * Polling-Intervall, mit dem wir TDLib nach frischem File-State fragen,
     * solange wir auf Prefix-Download warten.
     */
    const val ENSURE_READY_POLL_INTERVAL_MS: Long = 100L

    // --- Download Priorities ------------------------------------------------

    /**
     * Hohe Priorität für aktive Playback-Streams.
     *
     * Orientiert sich an den Empfehlungen der tdlib-coroutines-Doku:
     * Stream-Jobs sollten klar von Hintergrund-Downloads getrennt sein.
     */
    const val DOWNLOAD_PRIORITY_STREAMING: Int = 32

    /**
     * Niedrigere Priorität für Prefetch / Hintergrund-Downloads.
     */
    const val DOWNLOAD_PRIORITY_BACKGROUND: Int = 8

    // --- Progress & Logging -------------------------------------------------

    /**
     * Wie häufig wir Fortschritt-Events nach oben durchreichen.
     * Hilft, UI-Flackern zu vermeiden und Logs zu reduzieren.
     */
    const val PROGRESS_DEBOUNCE_MS: Long = 250L

    /**
     * Optionaler Schalter, um sehr detailliertes Streaming-Logging zu aktivieren.
     * Sollte im Release normalerweise false sein.
     */
    const val ENABLE_VERBOSE_LOGGING: Boolean = false
}
