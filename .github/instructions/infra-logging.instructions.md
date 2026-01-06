---
applyTo: 
  - infra/logging/**
---

# 🏆 PLATIN Instructions:  infra/logging

> **PLATIN STANDARD** - Unified Logging System (SSOT).
>
> **Purpose:** Central logging facade for ALL modules.  Every log statement in the codebase
> MUST use UnifiedLog.  Zero exceptions. 
>
> **Critical Principle:** UnifiedLog is the SINGLE SOURCE OF TRUTH for logging. 
> println, Log. d, Timber, System.out are FORBIDDEN everywhere. 

---

## 🔴 ABSOLUTE HARD RULES

### 1. UnifiedLog is MANDATORY Everywhere

```kotlin
// ✅ CORRECT:  UnifiedLog with lazy evaluation
import com.fishit.player.infra.logging.UnifiedLog

private const val TAG = "MyComponent"

UnifiedLog. d(TAG) { "Starting operation:  $operationId" }
UnifiedLog.i(TAG) { "User logged in: ${user.id}" }
UnifiedLog.w(TAG) { "Cache miss for key: $key" }
UnifiedLog.e(TAG, exception) { "Failed to load data: ${exception.message}" }

// ❌ FORBIDDEN: ANY other logging mechanism
println("Debug:  $value")                        // WRONG
Log.d(TAG, "message")                           // WRONG
Log.e(TAG, "error", exception)                  // WRONG
Timber. d("message")                             // WRONG
System.out.println("debug")                     // WRONG
android.util.Log.v(TAG, "verbose")              // WRONG
```

**Why:**
- Single point of control for log filtering
- Consistent format across all modules
- Lazy evaluation (no string building unless logged)
- Structured logging support
- Backend-agnostic (can route to Logcat, Firebase, Sentry, etc.)

---

### 2. Lazy Evaluation is MANDATORY

```kotlin
// ✅ CORRECT: Lazy lambda (only evaluated if logged)
UnifiedLog.d(TAG) { "Expensive computation:  ${expensiveFunction()}" }

// ❌ WRONG: Eager evaluation (always computed, even if not logged)
UnifiedLog.d(TAG, "Expensive computation:  ${expensiveFunction()}")  // WRONG - no lambda! 
```

**Why:**
- Zero performance cost when log level is disabled
- String interpolation only happens if log is actually written
- Critical for release builds where debug logs are disabled

---

### 3. TAG Convention (MANDATORY)

```kotlin
// ✅ CORRECT:  Companion object constant
class MyViewModel @Inject constructor() : ViewModel() {
    companion object {
        private const val TAG = "MyViewModel"
    }
    
    fun doSomething() {
        UnifiedLog.d(TAG) { "Doing something" }
    }
}

// ✅ CORRECT: Package-level constant
private const val TAG = "TelegramSync"

suspend fun syncChat(chatId: Long) {
    UnifiedLog.i(TAG) { "Starting chat sync: $chatId" }
}

// ❌ WRONG:  Inline TAG string
UnifiedLog.d("MyClass") { "message" }  // WRONG - no constant! 

// ❌ WRONG:  Class. simpleName (runtime overhead)
UnifiedLog.d(this::class.simpleName) { "message" }  // WRONG - runtime computation! 
```

**TAG Naming Rules:**
- Use PascalCase (no spaces, underscores, or special chars)
- Max 23 characters (Android Logcat limit)
- Descriptive of component (not file name)
- Stable across refactors

---

### 4. Exception Logging Pattern

```kotlin
// ✅ CORRECT: Exception with context message
try {
    riskyOperation()
} catch (e: Exception) {
    UnifiedLog.e(TAG, e) { "Failed to process item: itemId=$itemId" }
    // Optional: rethrow or handle
}

// ✅ CORRECT: Warning without exception
if (invalidState) {
    UnifiedLog.w(TAG) { "Invalid state detected:  state=$currentState" }
}

// ❌ WRONG: Exception without context
UnifiedLog.e(TAG, e) { e.message }  // WRONG - no context! 

// ❌ WRONG:  Exception in message string
UnifiedLog.e(TAG) { "Error: ${e.message}" }  // WRONG - exception not in API!
```

---

### 5. Structured Logging (Advanced)

```kotlin
// ✅ CORRECT: Structured data for analytics
UnifiedLog.i(TAG) {
    "Playback started: " +
    "canonicalId=$canonicalId, " +
    "sourceType=$sourceType, " +
    "resumeMs=$resumeMs"
}

// ✅ CORRECT:  Key-value pairs for debugging
UnifiedLog. d(TAG) {
    "State transition: " +
    "from=$oldState, " +
    "to=$newState, " +
    "trigger=$trigger"
}

// ❌ WRONG: Unstructured messages (hard to parse)
UnifiedLog.i(TAG) { "Playing $canonicalId from $sourceType" }  // WRONG - no keys!
```

**Why:**
- Enables log parsing and analytics
- Easier to filter and search
- Better debugging experience

---

## 📋 Log Level Guidelines

### DEBUG (`UnifiedLog.d`)

**Use for:**
- Method entry/exit points
- State transitions
- Configuration values
- Internal flow tracking

**Example:**
```kotlin
UnifiedLog.d(TAG) { "resolve:  canonicalId=$canonicalId, sourceType=$sourceType" }
UnifiedLog.d(TAG) { "State changed: $oldState -> $newState" }
```

**NOT for:**
- Sensitive data (credentials, tokens, PII)
- High-frequency loops (pollutes logs)

---

### INFO (`UnifiedLog.i`)

**Use for:**
- Significant events (playback started, sync completed)
- User actions (login, logout, settings changed)
- Success states

**Example:**
```kotlin
UnifiedLog.i(TAG) { "Catalog sync complete:  sources=TELEGRAM, items=$itemCount" }
UnifiedLog.i(TAG) { "User authenticated: userId=$userId, source=TELEGRAM" }
```

---

### WARN (`UnifiedLog.w`)

**Use for:**
- Recoverable errors (fallback used)
- Deprecated API usage
- Configuration issues (non-fatal)
- Performance issues

**Example:**
```kotlin
UnifiedLog.w(TAG) { "No factory for sourceType=$sourceType, using fallback" }
UnifiedLog. w(TAG) { "TMDB enrichment skipped: no API key configured" }
```

---

### ERROR (`UnifiedLog.e`)

**Use for:**
- Unrecoverable errors (operation failed)
- Exception caught (with exception object)
- Data corruption detected
- Critical failures

**Example:**
```kotlin
UnifiedLog.e(TAG, exception) { "Failed to load canonical media: id=$canonicalId" }
UnifiedLog.e(TAG) { "Database migration failed: schema=$schemaVersion" }
```

**MUST include exception object when available:**
```kotlin
// ✅ CORRECT
UnifiedLog.e(TAG, exception) { "Context message" }

// ❌ WRONG
UnifiedLog.e(TAG) { "Error:  ${exception.message}" }  // Exception lost!
```

---

### VERBOSE (`UnifiedLog.v`)

**Use for:**
- High-frequency events (frame rendering, network packets)
- Detailed internal state dumps
- Performance profiling data

**Example:**
```kotlin
UnifiedLog.v(TAG) { "Buffer updated: position=$position, size=$size" }
```

**Warning:** Verbose logs are NEVER shipped in release builds.

---

## 📋 Module Implementation

### infra/logging Structure

```
infra/logging/
├── src/main/java/com/fishit/player/infra/logging/
│   ├── UnifiedLog.kt              # Main facade
│   ├── LogLevel.kt                # Log level enum
│   ├── LogBackend.kt              # Backend abstraction
│   ├── LogcatBackend.kt           # Default backend
│   ├── LogBufferProvider.kt      # In-memory buffer for debug UI
│   └── di/
│       └── LoggingModule.kt       # Hilt DI
└── build.gradle.kts
```

---

### UnifiedLog API (Current Implementation)

```kotlin
object UnifiedLog {
    /**
     * Debug log with lazy evaluation. 
     * 
     * @param tag Stable TAG constant (max 23 chars)
     * @param message Lazy lambda that builds the log message
     */
    fun d(tag: String, message: () -> String)
    
    /**
     * Info log with lazy evaluation.
     */
    fun i(tag: String, message: () -> String)
    
    /**
     * Warning log with lazy evaluation.
     */
    fun w(tag: String, message: () -> String)
    
    /**
     * Error log with optional exception.
     * 
     * @param tag Stable TAG constant
     * @param exception Optional throwable (ALWAYS include if available)
     * @param message Lazy lambda with context message
     */
    fun e(tag: String, exception:  Throwable?  = null, message: () -> String)
    
    /**
     * Verbose log with lazy evaluation. 
     * NEVER shipped in release builds.
     */
    fun v(tag: String, message: () -> String)
    
    /**
     * Check if a log level is enabled (for expensive operations).
     * 
     * Example:
     * if (UnifiedLog.isLoggable(LogLevel.DEBUG)) {
     *     val expensiveData = computeExpensiveDebugData()
     *     UnifiedLog.d(TAG) { "Debug:  $expensiveData" }
     * }
     */
    fun isLoggable(level: LogLevel): Boolean
}
```

---

### LogBackend Interface (Extension Point)

```kotlin
interface LogBackend {
    /**
     * Write a log entry to the backend.
     * 
     * @param level Log level
     * @param tag TAG constant
     * @param message Evaluated message string
     * @param exception Optional throwable
     */
    fun log(
        level: LogLevel,
        tag: String,
        message:  String,
        exception: Throwable? = null,
    )
    
    /**
     * Whether this backend supports the given log level.
     * 
     * Used for performance optimization (skip expensive message building).
     */
    fun isLoggable(level: LogLevel): Boolean
}
```

**Current Backends:**
- `LogcatBackend` - Writes to Android Logcat (default)
- `LogBufferProvider` - In-memory buffer for debug UI

**Future Backends:**
- Firebase Crashlytics (ERROR only)
- Sentry (ERROR only)
- File logging (for bug reports)

---

### LogBufferProvider (Debug UI)

```kotlin
interface LogBufferProvider {
    /**
     * Observe recent logs for debug UI display.
     * 
     * @param limit Maximum number of recent logs
     * @return Flow of buffered log entries
     */
    fun observeLogs(limit: Int = 100): Flow<List<BufferedLogEntry>>
    
    /**
     * Clear all buffered logs.
     */
    fun clearLogs()
}

data class BufferedLogEntry(
    val level: LogLevel,
    val tag: String,
    val message:  String,
    val exception:  Throwable?,
    val timestampMs: Long,
)
```

**Usage in Debug Screen:**
```kotlin
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val logBufferProvider: LogBufferProvider,
) : ViewModel() {
    
    val recentLogs:  StateFlow<List<BufferedLogEntry>> =
        logBufferProvider
            .observeLogs(limit = 500)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

---

## ⚠️ Critical Anti-Patterns

### Anti-Pattern 1: String Building Before Log

```kotlin
// ❌ WRONG: String built even if DEBUG disabled
val debugMsg = "Processing $count items with $size bytes"
UnifiedLog.d(TAG, debugMsg)  // No lambda!

// ✅ CORRECT: String built only if DEBUG enabled
UnifiedLog.d(TAG) { "Processing $count items with $size bytes" }
```

---

### Anti-Pattern 2: Exception as String

```kotlin
// ❌ WRONG: Exception lost (no stack trace)
try {
    riskyOp()
} catch (e: Exception) {
    UnifiedLog.e(TAG) { "Error: ${e.message}" }  // Stack trace lost!
}

// ✅ CORRECT: Exception object preserved
try {
    riskyOp()
} catch (e: Exception) {
    UnifiedLog.e(TAG, e) { "Operation failed: context=$context" }
}
```

---

### Anti-Pattern 3: Multiple Logging Facades

```kotlin
// ❌ WRONG:  Mixing logging systems
class MyClass {
    fun doSomething() {
        Log.d(TAG, "Starting")           // Android Log
        UnifiedLog.i(TAG) { "Middle" }   // UnifiedLog
        Timber.d("Ending")               // Timber
    }
}

// ✅ CORRECT: UnifiedLog exclusively
class MyClass {
    companion object {
        private const val TAG = "MyClass"
    }
    
    fun doSomething() {
        UnifiedLog.d(TAG) { "Starting" }
        UnifiedLog.i(TAG) { "Middle" }
        UnifiedLog. d(TAG) { "Ending" }
    }
}
```

---

### Anti-Pattern 4: Sensitive Data in Logs

```kotlin
// ❌ WRONG: PII, credentials, tokens logged
UnifiedLog. d(TAG) { "User email: ${user.email}" }           // GDPR violation! 
UnifiedLog.d(TAG) { "API key: ${credentials.apiKey}" }      // Security issue! 
UnifiedLog.d(TAG) { "Password: ${credentials.password}" }   // CRITICAL!

// ✅ CORRECT: Redact or use IDs
UnifiedLog.d(TAG) { "User: userId=${user.id}" }
UnifiedLog.d(TAG) { "API key: configured=${credentials.apiKey != null}" }
UnifiedLog.d(TAG) { "Auth:  hasPassword=${credentials.password. isNotEmpty()}" }
```

---

## 📐 Architecture Position

```
ALL MODULES
    ↓
UnifiedLog (facade) ← YOU ARE HERE
    ↓
LogBackend (interface)
    ├── LogcatBackend (Android Logcat)
    ├── LogBufferProvider (in-memory for debug UI)
    ├── FirebaseBackend (future - Crashlytics)
    └── SentryBackend (future - error tracking)
```

---

## 🔍 CI Enforcement

### Pre-Change Verification

```bash
# 1. No println anywhere
grep -rn "println(" --include="*.kt" . 

# 2. No android.util.Log anywhere
grep -rn "import android.util.Log" --include="*.kt" . 
grep -rn "Log\.[diwev](" --include="*.kt" . 

# 3. No Timber anywhere
grep -rn "import timber. log. Timber" --include="*.kt" .
grep -rn "Timber\." --include="*.kt" . 

# 4. No System.out anywhere
grep -rn "System\. out\.print" --include="*. kt" . 

# All should return empty! 
```

### CI Guard (scripts/ci/check-arch-guardrails.sh)

```bash
# Logging compliance check
echo "Checking logging compliance..."

# Check for println
violations=$(grep -rn "println(" --include="*.kt" . 2>/dev/null | grep -v "build/" | grep -v ". gradle/" || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: println() forbidden (use UnifiedLog)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for android.util. Log
violations=$(grep -rn "Log\.[diwev](" --include="*.kt" . 2>/dev/null | grep -v "build/" | grep -v "infra/logging/" || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: android.util.Log forbidden (use UnifiedLog)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for Timber
violations=$(grep -rn "Timber\." --include="*.kt" . 2>/dev/null | grep -v "build/" || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Timber forbidden (use UnifiedLog)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi
```

---

## ✅ PLATIN Checklist

### For ALL Modules
- [ ] Uses UnifiedLog exclusively (no println, Log.d, Timber)
- [ ] Lazy evaluation with lambda (`{ "message" }`)
- [ ] Stable TAG constant (companion object or package-level)
- [ ] TAG max 23 characters (Android Logcat limit)
- [ ] Exception passed as parameter (not in message string)
- [ ] No sensitive data (PII, credentials, tokens)
- [ ] Structured logging with key-value pairs
- [ ] Appropriate log level (DEBUG, INFO, WARN, ERROR)

### For infra/logging Module
- [ ] UnifiedLog facade is thread-safe
- [ ] LogBackend abstraction for extensibility
- [ ] LogcatBackend as default implementation
- [ ] LogBufferProvider for debug UI integration
- [ ] Hilt DI module provides all components
- [ ] Zero Android framework dependencies (beyond Logcat)
- [ ] Lazy evaluation enforced by API design

---

## 📚 Reference Documents

1. **`/AGENTS. md`** - Section 4. 5 (Layer Boundaries - logging SSOT)
2. **`/contracts/LOGGING_CONTRACT_V2.md`** - Complete logging contract
3. **`/docs/dev/ARCH_GUARDRAILS.md`** - CI enforcement rules
4. **`/infra/logging/README.md`** - Module-specific documentation
5. Android Logcat documentation

---

## 🚨 Common Violations & Solutions

### Violation 1: println in Debug Code

```kotlin
// ❌ WRONG
fun debugOperation() {
    println("Debug:  operation started")  // WRONG! 
}

// ✅ CORRECT
fun debugOperation() {
    UnifiedLog.d(TAG) { "Operation started" }
}
```

---

### Violation 2: android.util.Log Direct Import

```kotlin
// ❌ WRONG
import android.util.Log

fun logSomething() {
    Log.d(TAG, "message")  // WRONG!
}

// ✅ CORRECT
import com.fishit.player.infra.logging.UnifiedLog

fun logSomething() {
    UnifiedLog.d(TAG) { "message" }
}
```

---

### Violation 3: No Lazy Evaluation

```kotlin
// ❌ WRONG
UnifiedLog.d(TAG, "Computed:  ${expensiveFunction()}")  // Always computed!

// ✅ CORRECT
UnifiedLog. d(TAG) { "Computed: ${expensiveFunction()}" }  // Only if logged!
```

---

### Violation 4: Exception as String

```kotlin
// ❌ WRONG
catch (e: Exception) {
    UnifiedLog.e(TAG) { "Error: ${e.message}" }  // Stack trace lost! 
}

// ✅ CORRECT
catch (e: Exception) {
    UnifiedLog.e(TAG, e) { "Operation failed" }  // Stack trace preserved! 
}
```

---

## 🎯 Migration from Legacy Logging

### Step 1: Find All Log Statements

```bash
# Find all android.util.Log usage
git grep "Log\.[diwev](" -- "*.kt"

# Find all println
git grep "println(" -- "*.kt"

# Find all Timber
git grep "Timber\." -- "*.kt"
```

### Step 2: Replace Pattern

```kotlin
// OLD
Log.d(TAG, "Message:  $value")

// NEW
UnifiedLog.d(TAG) { "Message: $value" }

// OLD
Log.e(TAG, "Error", exception)

// NEW
UnifiedLog.e(TAG, exception) { "Error" }
```

### Step 3: Verify

```bash
./gradlew compileDebugKotlin
./scripts/ci/check-arch-guardrails.sh
```

---

**End of PLATIN Instructions for infra/logging**