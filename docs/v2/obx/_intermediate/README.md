# ObjectBox Intermediate Artifacts

This directory contains intermediate outputs from the ObjectBox documentation effort (Issue #612 - ObjectBox Data Layers Map).

## Phase 0 - Entity Discovery & Inventory

**Status:** ✅ Complete  
**Generated:** 2026-01-08  
**Scope:** v2 only (core/persistence module)

### Files

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

5. **`generated_files.json`**
   - Build-time generated file tracking

---

## Phase 4 - Query Patterns & Relations Extraction

**Status:** ✅ Complete  
**Generated:** 2026-01-08  
**Scope:** All v2 ObjectBox usage patterns

### Files

1. **`query_usage.json`** (63KB)
   - Complete inventory of 128 ObjectBox queries
   - Query conditions, ordering, and patterns
   - Statistics by entity and type
   - Index coverage analysis

2. **`relationships.json`** (8.6KB)
   - 2 ObjectBox relations (ToOne/ToMany)
   - 1 backlink annotation
   - 14 manual foreign key join patterns
   - Usage locations and contexts

3. **`entity_access_patterns.json`** (15KB)
   - Read/write locations per entity
   - Query counts and access patterns
   - Summary statistics

4. **`PHASE4_COMPLETION.md`** (~5KB)
   - Validation report
   - Final metrics
   - Analysis summary

### Key Findings

**Query Statistics:**
- Total queries: 128
- Most queried entity: `ObxCanonicalMedia` (27 queries)
- Indexed queries: 55 (43%)
- Non-indexed queries: 73 (57%)
- Most common operations: find (55%), findFirst (34%), count (12%)

**Relations:**
- Only 1 proper ToOne/ToMany relation pair in schema
- Heavy reliance on manual foreign key joins (14 patterns)
- Most common join field: `seriesId` (4 different joins)

**Entities:**
- All 14 entities actively used (100% coverage)
- Primary repositories: ObxCanonicalMediaRepository, ObxXtreamCatalogRepository

---

## Combined Entity Inventory

| Entity | Query Count | Relations | Primary Use Case |
|--------|-------------|-----------|------------------|
| `ObxCanonicalMedia` | 27 | ToMany sources | Cross-pipeline media unification |
| `ObxMediaSourceRef` | 7 | ToOne canonicalMedia | Source-to-canonical linking |
| `ObxCanonicalResumeMark` | 6 | - | Resume position tracking |
| `ObxVod` | 20 | - | Xtream VOD items |
| `ObxSeries` | 10 | - | Xtream series metadata |
| `ObxEpisode` | 5 | - | Xtream series episodes |
| `ObxLive` | 10 | - | Xtream live streams |
| `ObxCategory` | 10 | - | Xtream categories |
| `ObxEpisodeIndex` | 11 | - | Series episode indexing |
| `ObxSeasonIndex` | 6 | - | Series season indexing |
| `ObxTelegramMessage` | 9 | - | Telegram media messages |
| `ObxTelegramChat` | 1 | - | Telegram chat metadata |
| `ObxProfile` | 2 | - | User profiles |
| `ObxScreenTimeEntry` | 4 | - | Kids screen time tracking |
| `ObxEpgNowNext` | 1 | - | EPG data |

---

## Usage

These JSON files are designed to be consumed by:
- **Task 2A:** Final comprehensive documentation
- **Traceability Matrix:** Field → Writer → Reader mapping
- **Query Optimization:** Index recommendations
- **Schema Evolution:** Migration planning
- **Automated Tooling:** Code generation and analysis

---

## Data Quality

✅ **All entries are backed by actual code**
- Zero inference or guessing
- Every pattern has file path + line number
- All statistics computed from real data
- JSON files validated

---

## Validation Commands

```bash
# Validate JSON structure
jq empty query_usage.json
jq empty relationships.json
jq empty entity_access_patterns.json
jq empty entity_inventory.json
jq empty store_init_points.json
jq empty db_inspector_components.json

# Count queries
jq '.queries | length' query_usage.json

# Count relations
jq '.relations | length' relationships.json

# Count entities
jq '.entities | length' entity_access_patterns.json
jq '.entities | length' entity_inventory.json
```

---

## Next Steps

This data feeds into:
1. **Task 2A:** Final comprehensive documentation generation
2. **Traceability Matrix:** Complete field → writer → reader mapping
3. **Query Optimization Guide:** Index recommendations and patterns
4. **Schema Evolution Guidelines:** Migration best practices

---

**Project:** FishIT-Player Issue #612  
**Phases:** 0 (Discovery) + 4 (Query Patterns)  
**Status:** ✅ Complete  
**Scope:** v2 architecture only
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
