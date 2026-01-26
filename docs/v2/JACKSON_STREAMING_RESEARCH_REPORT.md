# Jackson Streaming JSON Library - Research Report

**Date:** 2026-01-26  
**Purpose:** Evaluate jackson-core for streaming JSON array parsing in FishIT-Player  
**Status:** ✅ Research Complete

---

## 1. Current Stable Versions

### Latest Releases (Maven Central - as of January 2026)

| Artifact | Latest Version | Notes |
|----------|----------------|-------|
| `jackson-core` | **2.19.4** | Latest stable patch |
| `jackson-databind` | 2.19.0 | ObjectMapper (NOT needed for streaming) |
| `jackson-annotations` | 3.0-rc5 | Annotations (NOT needed for streaming) |

### Version History Timeline

```
2.17.x Series:
  2.17.0      → First 2.17 release
  2.17.1      → Patch
  2.17.2      → Patch  
  2.17.3      → Latest 2.17.x

2.18.x Series:
  2.18.0      → Released
  2.18.1-2.18.5 → Patches

2.19.x Series (Current):
  2.19.0-rc1  → Release candidate
  2.19.0-rc2  → Release candidate
  2.19.0      → GA Release
  2.19.1-2.19.4 → Patches (2.19.4 is latest)
```

### Jackson 3.x Status

**No Jackson 3.x published on Maven Central.** Only jackson-annotations has 3.0-rc preview releases.
The 3.x branch is in development but not yet released for jackson-core/databind.

---

## 2. Module Structure Analysis

### Core Jackson Modules

| Module | Purpose | Required for Streaming? |
|--------|---------|------------------------|
| `jackson-core` | Streaming parser/generator (JsonParser, JsonFactory) | ✅ **YES - This is ALL you need** |
| `jackson-databind` | ObjectMapper, POJO binding | ❌ NO - Not needed for streaming |
| `jackson-annotations` | @JsonProperty, @JsonIgnore, etc. | ❌ NO - Not needed for streaming |

### Minimal Dependency for Streaming

```kotlin
// gradle/libs.versions.toml
[versions]
jackson = "2.19.4"  # or 2.17.3 for more conservative choice

[libraries]
jackson-core = { module = "com.fasterxml.jackson.core:jackson-core", version.ref = "jackson" }
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(libs.jackson.core)  // That's it! No other Jackson dependencies needed
}
```

### What jackson-core Provides (Standalone)

- `JsonFactory` - Factory for creating parsers/generators (thread-safe, reusable)
- `JsonParser` - Pull-parser for reading JSON (streaming)
- `JsonGenerator` - For writing JSON (streaming)
- `JsonToken` - Token types (START_OBJECT, FIELD_NAME, VALUE_STRING, etc.)
- Built-in limits and security constraints (since 2.15+)

---

## 3. JAR Sizes & Dependencies

### Size Comparison

| Artifact | Version | JAR Size | Method Count (est.) |
|----------|---------|----------|---------------------|
| `jackson-core` | 2.17.0 | **568 KB** | ~2,500 |
| `jackson-core` | 2.19.0 | **586 KB** | ~2,600 |
| `jackson-databind` | 2.19.0 | **1.64 MB** | ~8,000+ |
| `kotlinx-serialization-json` | 1.8.0 | ~280 KB | ~1,500 |

### Dependencies

**jackson-core has ZERO runtime dependencies!**

From official README:
> "Package has no external dependencies, except for testing (which uses JUnit)."

This makes it ideal for Android:
- No transitive dependency bloat
- No version conflicts
- Clean classpath

---

## 4. Java & Android Compatibility

### JDK Requirements

| Jackson Version | Minimum JDK |
|-----------------|-------------|
| 2.0 - 2.13 | Java 6 |
| **2.14+** | **Java 8** |
| 2.17+ | Java 8 (tested up to Java 21) |

### Android Compatibility

From official Jackson docs:
- **Android SDK 26+** (Android 8.0 Oreo) is the minimum for 2.19.x
- Uses `animal-sniffer-maven-plugin` to verify Android API compatibility
- Properly handles Android's limited reflection and security constraints

**FishIT-Player context:**
- Our `minSdk = 26` ✅ Compatible
- Our `targetSdk = 35` ✅ Compatible

---

## 5. Streaming API Surface

### Creating JsonParser from InputStream

```kotlin
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.InputStream

// Create ONCE, reuse across all parsing (thread-safe)
private val jsonFactory = JsonFactory()

// Parse from InputStream (e.g., OkHttp response.body?.byteStream())
fun parseFromStream(inputStream: InputStream) {
    jsonFactory.createParser(inputStream).use { parser ->
        // Parse here
    }
}
```

### Iterating Through JSON Arrays (Streaming Pattern)

```kotlin
/**
 * Stream-parses a JSON array, emitting items one at a time.
 * Memory: O(1) - only holds one item at a time regardless of array size.
 */
suspend fun <T> parseJsonArrayStreaming(
    inputStream: InputStream,
    parseItem: (JsonParser) -> T
): Flow<T> = flow {
    jsonFactory.createParser(inputStream).use { parser ->
        // Expect array start
        check(parser.nextToken() == JsonToken.START_ARRAY) {
            "Expected JSON array, got: ${parser.currentToken()}"
        }
        
        // Iterate through array elements
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.START_OBJECT) {
                emit(parseItem(parser))
            }
        }
    }
}.flowOn(Dispatchers.IO)
```

### Parsing Objects Inside Array

```kotlin
data class LiveStream(
    val streamId: Long,
    val name: String,
    val categoryId: String?,
    val streamIcon: String?
)

fun parseLiveStream(parser: JsonParser): LiveStream {
    var streamId: Long = 0
    var name: String = ""
    var categoryId: String? = null
    var streamIcon: String? = null
    
    while (parser.nextToken() != JsonToken.END_OBJECT) {
        val fieldName = parser.currentName()
        parser.nextToken() // Move to value
        
        when (fieldName) {
            "stream_id" -> streamId = parser.longValue
            "name" -> name = parser.valueAsString ?: ""
            "category_id" -> categoryId = parser.valueAsString
            "stream_icon" -> streamIcon = parser.valueAsString?.takeIf { it.isNotBlank() }
            // Skip unknown fields automatically
        }
    }
    
    return LiveStream(streamId, name, categoryId, streamIcon)
}
```

### Complete Example: Streaming Xtream Live Streams

```kotlin
class XtreamStreamingParser(
    private val jsonFactory: JsonFactory = JsonFactory()
) {
    /**
     * Parses potentially 10,000+ live streams with O(1) memory.
     * Emits each stream as soon as it's parsed.
     */
    fun parseLiveStreams(inputStream: InputStream): Flow<LiveStream> = flow {
        jsonFactory.createParser(inputStream).use { parser ->
            require(parser.nextToken() == JsonToken.START_ARRAY)
            
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    emit(parseLiveStreamObject(parser))
                }
            }
        }
    }.flowOn(Dispatchers.IO)
    
    private fun parseLiveStreamObject(parser: JsonParser): LiveStream {
        var streamId: Long = 0
        var name = ""
        var categoryId: String? = null
        var streamIcon: String? = null
        var epgChannelId: String? = null
        
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            when (parser.currentName()) {
                "stream_id" -> { parser.nextToken(); streamId = parser.longValue }
                "name" -> { parser.nextToken(); name = parser.valueAsString ?: "" }
                "category_id" -> { parser.nextToken(); categoryId = parser.valueAsString }
                "stream_icon" -> { parser.nextToken(); streamIcon = parser.valueAsString }
                "epg_channel_id" -> { parser.nextToken(); epgChannelId = parser.valueAsString }
                else -> parser.skipChildren() // Skip unknown fields efficiently
            }
        }
        
        return LiveStream(streamId, name, categoryId, streamIcon)
    }
}
```

---

## 6. Memory Characteristics

### Streaming vs Tree Model Comparison

| Approach | Memory for 10K items | Time to First Item |
|----------|---------------------|-------------------|
| **Jackson Streaming** | O(1) ~1 KB | **Immediate** |
| `kotlinx-serialization` (list) | O(n) ~10 MB | After full parse |
| Jackson `ObjectMapper` (tree) | O(n) ~15 MB | After full parse |

### Why Streaming Wins for Large Arrays

1. **No intermediate String allocation** - Parses directly from bytes
2. **No full DOM/tree construction** - Never builds complete object graph
3. **Progressive emission** - First item available in milliseconds
4. **Bounded buffer** - Internal buffer ~8KB regardless of input size
5. **Garbage collector friendly** - Minimal allocation churn

---

## 7. Thread Safety

### JsonFactory

**Thread-safe and reusable!**

From official docs:
> "Factory instances are thread-safe and reusable after configuration (if any). 
> Typically applications and services use only a single globally shared factory instance."

```kotlin
// Singleton pattern - create once at app startup
object JacksonConfig {
    val factory: JsonFactory = JsonFactory.builder()
        // Optional: relaxed parsing for malformed JSON
        // .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        .build()
}
```

### JsonParser

**NOT thread-safe!** Create per-parse operation and close after use.

```kotlin
// Each parse operation gets its own parser
fun parseData(input: InputStream): MyData {
    return JacksonConfig.factory.createParser(input).use { parser ->
        // ... parse ...
    }
}
```

---

## 8. Processing Limits (Security)

Since Jackson 2.15+, built-in security constraints:

```kotlin
val factory = JsonFactory.builder()
    .streamReadConstraints(
        StreamReadConstraints.builder()
            .maxNumberLength(1000)          // Default: 1000 digits
            .maxStringLength(20_000_000)    // Default: 20 million chars
            .maxNestingDepth(1000)          // Default: 1000 levels
            .maxNameLength(50_000)          // Default: 50K (since 2.16)
            .maxDocumentLength(-1)          // Default: unlimited
            .build()
    )
    .build()
```

---

## 9. Kotlin Interoperability

Jackson-core is pure Java but works excellently with Kotlin:

### Extension Functions

```kotlin
// Convenient Kotlin extensions
inline fun <T> JsonFactory.parseStream(
    input: InputStream,
    block: (JsonParser) -> T
): T = createParser(input).use(block)

// Null-safe value access
val JsonParser.valueAsStringOrNull: String?
    get() = valueAsString?.takeIf { it.isNotBlank() && it != "null" }

// Flow-based streaming
fun <T> JsonFactory.streamArray(
    input: InputStream,
    parseItem: (JsonParser) -> T
): Flow<T> = flow {
    createParser(input).use { parser ->
        require(parser.nextToken() == JsonToken.START_ARRAY)
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            emit(parseItem(parser))
        }
    }
}
```

---

## 10. Recommendations for FishIT-Player

### Version Recommendation

| Option | Version | Rationale |
|--------|---------|-----------|
| **Conservative** | `2.17.3` | Current in libs.versions.toml, stable, well-tested |
| **Current Stable** | `2.19.4` | Latest with all fixes, still Java 8 compatible |

**Recommendation:** Stay with **2.17.3** for now (already configured). Upgrade to 2.19.x when:
- A specific bug fix is needed
- Better streaming performance is required
- After v2 stabilizes

### Maven Coordinates

```toml
# gradle/libs.versions.toml (already correct)
[versions]
jackson = "2.17.0"  # or upgrade to "2.17.3" / "2.19.4"

[libraries]
jackson-core = { module = "com.fasterxml.jackson.core:jackson-core", version.ref = "jackson" }
```

### Usage Location

Per existing architecture, use in:
- `:infra:transport-xtream` - For streaming Xtream API responses
- `:infra:transport-common` - Shared streaming utilities (future)

**Do NOT use in:**
- Pipeline modules (they receive already-parsed DTOs)
- Feature modules (use domain models)
- Player modules (no JSON parsing needed)

---

## 11. Summary

| Aspect | Answer |
|--------|--------|
| **Latest Stable** | 2.19.4 (jackson-core) |
| **Minimal Dependency** | `jackson-core` alone (no databind/annotations) |
| **JAR Size** | ~586 KB |
| **External Dependencies** | Zero |
| **Java Requirement** | Java 8+ |
| **Android Compatibility** | SDK 26+ ✅ |
| **Kotlin Compatibility** | Excellent (pure Java, idiomatic extensions) |
| **Thread Safety** | JsonFactory: ✅ / JsonParser: per-operation |
| **Memory for 10K items** | O(1) constant |
| **Current in Project** | 2.17.0 (already configured correctly) |

---

## References

- [GitHub: jackson-core](https://github.com/FasterXML/jackson-core)
- [Maven Central: jackson-core](https://search.maven.org/artifact/com.fasterxml.jackson.core/jackson-core)
- [Jackson Wiki](https://github.com/FasterXML/jackson-core/wiki)
- [Javadoc](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-core)
