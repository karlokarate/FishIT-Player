# ObjectBox Runtime Introspection Dump

**Feature:** DEBUG-only schema extraction and export  
**Issue:** #612 Phase 2  
**Module:** `core:persistence`, `feature:settings`

## Overview

This debug-only feature extracts the complete ObjectBox schema at runtime, including:
- All entity types (tables) with row counts
- All properties (fields) with types and annotations
- All relations (ToOne/ToMany) with target entities
- ObjectBox version and database size

The schema is exported as JSON for debugging, migration analysis, and documentation purposes.

## Prerequisites

- **Debug build** of the app (feature is not available in release builds)
- Device or emulator with app installed
- Optional: ADB access for file extraction

## How to Use

### Option 1: Via DB Inspector UI (Recommended)

1. **Open the app** in a debug build
2. **Navigate to Settings** → Debug Menu → **"DB Inspector"**
3. At the top of the entity list, tap **"Export ObjectBox Schema"** card
4. Choose export method:
   - **Export to File**: Saves JSON to app files directory
   - **Export to Logcat**: Prints schema to Android logs
   - **Cancel**: Close dialog without exporting

#### After Exporting to File

The dump file is saved as:
```
/data/data/com.fishit.player/files/obx_dump_<timestamp>.json
```

**Extract via ADB:**
```bash
# List available dumps
adb shell ls -la /data/data/com.fishit.player/files/obx_dump_*.json

# Pull the latest dump
adb pull /data/data/com.fishit.player/files/obx_dump_<timestamp>.json .

# Example:
adb pull /data/data/com.fishit.player/files/obx_dump_20260108_113045.json ./schema_dump.json
```

#### After Exporting to Logcat

View the schema in logcat:
```bash
# Filter by tag
adb logcat -s ObjectBoxIntrospectionDump

# Or search for schema markers
adb logcat | grep "ObjectBox Schema Dump"
```

### Option 2: Programmatic Export (Advanced)

For automated testing or CI/CD integration:

```kotlin
import com.fishit.player.core.persistence.inspector.ObjectBoxIntrospectionDump
import io.objectbox.BoxStore

// In your test or debug code
val boxStore: BoxStore = // ... inject or obtain
val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)

// Export to file
val file = ObjectBoxIntrospectionDump.dumpToFile(context, dump)
println("Schema exported to: ${file.absolutePath}")

// Or export to logcat
ObjectBoxIntrospectionDump.dumpToLogcat(dump)
```

## Schema Dump Structure

The exported JSON has the following structure:

```json
{
  "timestamp": "2026-01-08T11:30:45.123Z",
  "objectBoxVersion": "5.0.1",
  "storeSize": 1048576,
  "entityCount": 23,
  "entities": [
    {
      "name": "ObxVod",
      "displayName": "VOD",
      "entityId": 12345678,
      "rowCount": 1523,
      "properties": [
        {
          "name": "id",
          "type": "Long",
          "fullType": "kotlin.Long",
          "isIndexed": false,
          "isUnique": false,
          "isId": true,
          "annotations": ["Id"]
        },
        {
          "name": "vodId",
          "type": "Int",
          "fullType": "kotlin.Int",
          "isIndexed": true,
          "isUnique": true,
          "annotations": ["Unique", "Index"]
        },
        {
          "name": "name",
          "type": "String",
          "fullType": "kotlin.String",
          "isIndexed": false,
          "isUnique": false,
          "isId": false,
          "annotations": []
        }
      ],
      "relations": [
        {
          "name": "episodes",
          "type": "ToMany",
          "targetEntity": "ObxEpisode"
        }
      ]
    }
  ]
}
```

### Field Descriptions

| Field | Description |
|-------|-------------|
| `timestamp` | ISO 8601 timestamp of when dump was generated |
| `objectBoxVersion` | ObjectBox library version (e.g., "5.0.1") |
| `storeSize` | Total database file size in bytes |
| `entityCount` | Total number of entities in schema |
| `entities` | Array of entity definitions |

#### Entity Fields

| Field | Description |
|-------|-------------|
| `name` | Entity class name (e.g., "ObxVod") |
| `displayName` | Human-friendly display name |
| `entityId` | ObjectBox internal entity ID |
| `rowCount` | Current number of rows in this table |
| `properties` | Array of property (column) definitions |
| `relations` | Array of relation definitions |
| `error` | Error message if extraction failed (optional) |

#### Property Fields

| Field | Description |
|-------|-------------|
| `name` | Property field name |
| `type` | Simple type name (e.g., "String", "Int") |
| `fullType` | Full generic type (e.g., "kotlin.String") |
| `isIndexed` | Whether property has `@Index` annotation |
| `isUnique` | Whether property has `@Unique` annotation |
| `isId` | Whether property is the `@Id` field |
| `annotations` | List of all annotations on this property |

#### Relation Fields

| Field | Description |
|-------|-------------|
| `name` | Relation field name |
| `type` | Relation type: "ToOne" or "ToMany" |
| `targetEntity` | Target entity class name |

## Use Cases

### 1. Schema Documentation
Generate up-to-date schema documentation for the wiki or README.

### 2. Migration Planning
Compare schema dumps before and after migrations to verify changes:
```bash
# Before migration
adb pull /data/data/com.fishit.player/files/obx_dump_before.json .

# After migration
adb pull /data/data/com.fishit.player/files/obx_dump_after.json .

# Compare
diff -u obx_dump_before.json obx_dump_after.json
```

### 3. Debugging Entity Issues
Verify that entities have expected indexes, relations, and annotations.

### 4. Performance Analysis
Check row counts and identify large tables that may need optimization.

## Troubleshooting

### Export button not visible
- **Cause**: You're using a release build
- **Solution**: Build and install the debug variant

### "Export failed" error
- **Cause**: Insufficient storage space or permissions issue
- **Solution**: Free up space or check logcat for detailed error message

### Cannot extract file via ADB
- **Cause**: App data directory is not accessible on non-rooted devices
- **Solution**: Use "Export to Logcat" option instead, or use `adb backup` to extract app data

### Schema is truncated in logcat
- **Cause**: Large schemas are split across multiple log statements
- **Solution**: Use `adb logcat -d > logcat.txt` to save full logs to file, then search for all chunks

## Technical Notes

### DEBUG-Only Enforcement
This feature is implemented in the main source set but is only accessible via the DB Inspector UI, which is:
- Only available in debug builds
- Hidden behind developer settings navigation
- Not surfaced in normal user flows

### Performance Impact
Schema introspection uses reflection, which can be slow for large schemas (100+ entities). The export operation runs on a background coroutine to avoid blocking the UI.

### Compatibility
Tested with ObjectBox 5.0.1. May work with other versions but behavior is not guaranteed.

## Related Documentation

- [DB Inspector UI Documentation](../../../feature/settings/README.md) (if exists)
- [ObjectBox Migration Guide](../migrations/OBJECTBOX_MIGRATIONS.md) (if exists)
- [Issue #612](https://github.com/karlokarate/FishIT-Player/issues/612) - Original feature request

## Changelog

**v1.0** (2026-01-08)
- Initial implementation
- Export to file and logcat support
- Complete schema introspection (entities, properties, relations)
- DB Inspector UI integration
