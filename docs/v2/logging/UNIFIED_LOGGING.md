# Unified Logging System (v2)

**Version:** 1.1  
**Last Updated:** 2025-12-11

This document describes the logging system for FishIT-Player v2 and provides usage guidelines for all modules.

---

## Overview

The v2 logging system provides a single, unified façade (`UnifiedLog`) that abstracts away the logging backend (currently Timber). This design ensures:

- **Consistent API** across all modules
- **Backend flexibility** – swap Timber for another backend without changing call sites
- **Performance optimization** via lazy logging
- **Structured approach** to log levels, tags, and message formatting

---

## Primary API: Lambda-based (Lazy)

The **recommended** logging API uses inline lambdas to defer message construction:

```kotlin
UnifiedLog.d(TAG) { "loading item $id took ${measureMs()} ms" }
UnifiedLog.i(TAG) { "Fetched ${items.size} items from Telegram" }
UnifiedLog.w(TAG) { "Token expired, will re-authenticate" }
UnifiedLog.e(TAG, throwable) { "Failed to load stream for contentId=$contentId" }
UnifiedLog.v(TAG) { "Frame rendered in ${frameTimeNs}ns" }
```

### Why Lambda-based?

Lambda-based logging defers message construction until **after** the log level check. This means:

- String interpolation (`"value is $x"`) is not evaluated if the log level is disabled
- Expensive `toString()` calls are skipped
- Computations inside the message (`${measureMs()}`) don't run unnecessarily

### Performance Impact

In hot paths (player, transport, pipelines), this can make a significant difference:

```kotlin
// BAD: measureMs() runs even if DEBUG is disabled
UnifiedLog.d(TAG, "operation took ${measureMs()} ms")

// GOOD: measureMs() only runs if DEBUG is enabled
UnifiedLog.d(TAG) { "operation took ${measureMs()} ms" }
```

---

## Convenience API: String-based

For constant messages or non-critical paths, string-based overloads are available:

```kotlin
UnifiedLog.d(TAG, "Initialization complete")
UnifiedLog.i(TAG, "Application started")
```

### When to Use String-based

- The message is a compile-time constant with no interpolation
- The code path is not performance-sensitive (e.g., app startup, settings changes)
- You're logging a simple constant for debugging

### When NOT to Use String-based

- Any string interpolation (`$variable`, `${expression}`)
- Any expensive `toString()` calls (collections, complex objects)
- Player, transport, pipeline, or other hot paths

---

## Log Levels

| Level | Purpose | Example |
|-------|---------|---------|
| `VERBOSE` | Very detailed diagnostic info; temporary debugging only | Frame timing, byte-level operations |
| `DEBUG` | Development-time diagnostics | State changes, non-critical flows |
| `INFO` | High-level application events | App start, successful syncs |
| `WARN` | Recoverable issues | Token expired, retry needed |
| `ERROR` | Failures affecting user experience | Exceptions, critical misconfigurations |

### Level Filtering

Set the minimum log level to control verbosity:

```kotlin
UnifiedLog.setMinLevel(UnifiedLog.Level.INFO) // Filter out DEBUG and VERBOSE
```

Check if a level is enabled (for expensive preparations):

```kotlin
if (UnifiedLog.isEnabled(UnifiedLog.Level.DEBUG)) {
    val expensiveData = computeDebugInfo()
    UnifiedLog.d(TAG) { expensiveData }
}
```

---

## Tags

Use consistent, meaningful tags:

```kotlin
private const val TAG = "TelegramRepo"
private const val TAG = "XtreamClient"
private const val TAG = "SIPPlayer"
```

Guidelines:
- Keep tags short (15-20 characters max)
- Use `ModuleName` or `ClassName` format
- Tags should be stable (don't include dynamic values)

---

## Message Content

**DO:**
- Describe what happened clearly
- Include relevant identifiers (`contentId`, `chatId`, `streamId`)
- Keep messages concise

**DON'T:**
- Log secrets (tokens, passwords, private keys)
- Log excessive PII (personal information)
- Log full URLs with sensitive query parameters

```kotlin
// Good
UnifiedLog.e(TAG) { "Failed to fetch media for chatId=$chatId" }

// Bad
UnifiedLog.e(TAG) { "Failed with token=$authToken" }
```

---

## Exception Logging

Include throwables in error and warning logs:

```kotlin
try {
    loadStream()
} catch (e: IOException) {
    UnifiedLog.e(TAG, e) { "Failed to load stream" }
}
```

---

## Module Dependencies

Only `:infra:logging` may depend on:
- `Timber`
- `android.util.Log`
- External logging backends (Crashlytics, Sentry, etc.)

All other modules use only `UnifiedLog`.

---

## Future Extensibility

The `UnifiedLog` façade is designed to support:

- **Structured logging** – attach key-value metadata to log events
- **External sinks** – forward logs to OpenSearch, Loki, Sentry, Crashlytics
- **Ring buffer** – in-memory log buffer for debugging UI
- **Kotlin Multiplatform** – swap Timber for Kermit or custom backend

Lazy evaluation via lambdas helps minimize overhead when logs are forwarded to external tools.

---

## Contract Reference

See [/contracts/LOGGING_CONTRACT_V2.md](/contracts/LOGGING_CONTRACT_V2.md) for the binding contract that governs all logging in v2.
