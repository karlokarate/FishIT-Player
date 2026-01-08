# ObjectBox Phase 0 - Intermediate Artifacts

This directory contains the raw discovery outputs from **Phase 0 of Issue #612** (ObjectBox Data Layers Map).

**Scope:** v2 only (core/persistence module)

## Contents

### Primary Deliverables

1. **`entity_inventory.json`** (~40KB)
   - Complete catalog of 23 v2 ObjectBox entities
   - Fields with types and annotations
   - Relations (ToOne/ToMany)
   - Package names and file paths

2. **`store_init_points.json`** (~1KB)
   - 2 BoxStore initialization points (v2)
   - DI framework details (Hilt/Manual)
   - Initialization patterns

3. **`db_inspector_components.json`** (7KB)
   - 8 DB Inspector components
   - Navigation flow
   - Architecture details

4. **`PHASE0_SUMMARY.md`** (~6KB)
   - Comprehensive findings report
   - Entity categorization
   - Architecture insights

## Usage

These JSON files are designed to be consumed by:
- **Task 2A:** Final documentation generation
- **Automated tooling:** Schema analysis, migration generation
- **Documentation generators:** Markdown/HTML reports
- **IDEs:** Code navigation and understanding

## Quick Stats

- **Total Entities:** 23 (v2 core/persistence only)
- **Relations:** 2 (canonical identity pattern)
- **Init Points:** 2 (v2 manual + Hilt)
- **DB Inspector Components:** 8 (full MVVM stack)

## Data Quality

✅ **All entries are backed by actual code**
- Every entity has a verified `@Entity` annotation
- Every field is extracted from actual Kotlin source
- Every path is validated and accessible
- No guesses or assumptions

## Examples

### Reading Entity Inventory

```python
import json

with open('entity_inventory.json', 'r') as f:
    data = json.load(f)
    
# All entities are from core/persistence
v2_entities = data['entities']

print(f"Found {len(v2_entities)} v2 entities")

# Find entity by name
canonical = next(e for e in v2_entities 
                 if e['className'] == 'ObxCanonicalMedia')
                 
print(f"Canonical media has {len(canonical['fields'])} fields")
print(f"Relations: {canonical['relations']}")
```

### Reading Store Init Points

```python
import json

with open('store_init_points.json', 'r') as f:
    data = json.load(f)
    
# Find Hilt init point
hilt_init = next(p for p in data['initPoints'] 
                 if p['diFramework'] == 'Hilt')
                 
print(f"Hilt init: {hilt_init['filePath']}")
print(f"Method: {hilt_init['methodName']}")
```

### Reading DB Inspector Components

```python
import json

with open('db_inspector_components.json', 'r') as f:
    data = json.load(f)
    
# Find all screens
screens = [c for c in data['components'] 
           if c['componentType'] == 'Screen']
           
print(f"Found {len(screens)} UI screens")
for screen in screens:
    print(f"  - {screen['filePath']}")
```

## Schema

### entity_inventory.json

```json
{
  "entities": [
    {
      "className": "ObxEntityName",
      "packageName": "com.example.package",
      "modulePath": "core/persistence",
      "filePath": "core/persistence/src/main/java/.../Entity.kt",
      "fields": [
        {
          "name": "fieldName",
          "type": "FieldType",
          "annotations": ["@Id", "@Index"]
        }
      ],
      "relations": [
        {
          "type": "ToOne|ToMany",
          "targetEntity": "TargetEntity",
          "fieldName": "relationField"
        }
      ]
    }
  ]
}
```

### store_init_points.json

```json
{
  "initPoints": [
    {
      "filePath": "path/to/Store.kt",
      "module": "core/persistence",
      "className": "ObxStore",
      "methodName": "get",
      "diFramework": "Hilt|Manual",
      "description": "...",
      "initPattern": "MyObjectBox.builder()...",
      "isLazy": true,
      "lineNumber": 30
    }
  ]
}
```

### db_inspector_components.json

```json
{
  "components": [
    {
      "filePath": "path/to/Component.kt",
      "module": "feature/settings",
      "componentType": "Screen|ViewModel|Repository|...",
      "description": "...",
      "navigationEntry": "..."
    }
  ],
  "navigationFlow": { ... },
  "architecture": { ... }
}
```

## Notes

- This is **read-only analysis** - no functional code changes
- All paths are relative to repository root
- JSON files are validated with `python3 -m json.tool`
- Entity field order matches source code order
- Annotations include `@` prefix for clarity
- **Scope:** v2 only - legacy v1 entities excluded per task requirements

## Next Steps

These artifacts feed into:
1. **Task 1B:** Compile-time artifact analysis
2. **Task 1C:** Runtime introspection feature
3. **Task 1D:** Query & relation extraction
4. **Task 2A:** Final documentation generation

---

**Generated:** 2026-01-08  
**Phase:** 0 (Discovery)  
**Scope:** v2 only (core/persistence)  
**Status:** ✅ Complete
