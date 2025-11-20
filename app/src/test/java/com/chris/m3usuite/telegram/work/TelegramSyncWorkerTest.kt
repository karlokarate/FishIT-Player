package com.chris.m3usuite.telegram.work

import com.chris.m3usuite.telegram.work.TelegramSyncWorker
import org.junit.Test

/**
 * Unit tests for TelegramSyncWorker.
 * Tests worker structure and API compatibility.
 * 
 * Note: Full worker testing requires WorkManager test framework and Android context.
 * These tests verify the worker's basic structure and exposed API.
 */
class TelegramSyncWorkerTest {

    @Test
    fun `TelegramSyncWorker class exists and extends CoroutineWorker`() {
        // Verify the class structure
        val clazz = TelegramSyncWorker::class
        val superClass = clazz.java.superclass?.name
        assert(superClass?.contains("Worker") == true) {
            "TelegramSyncWorker should extend a Worker class, got: $superClass"
        }
    }

    @Test
    fun `TelegramSyncWorker has doWork method`() {
        val clazz = TelegramSyncWorker::class
        val methods = clazz.java.methods.map { it.name }
        assert("doWork" in methods) {
            "TelegramSyncWorker should have doWork method"
        }
    }
}
