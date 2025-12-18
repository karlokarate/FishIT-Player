# TMDB Integration Migration - Implementation Summary

**Status:** ✅ Complete  
**Date:** 2024-12-18

## Overview

Successfully migrated TMDB integration from direct tmdb-java usage to a single, type-safe infra gateway pattern. This enforces architectural boundaries and contract compliance.

## What Was Done

### 1. Created :infra:transport-tmdb Module

**New module structure:**
```
infra/transport-tmdb/
├── src/main/java/.../infra/transport/tmdb/
│   ├── api/                      # Public gateway API
│   │   ├── TmdbGateway.kt        # Interface (ID-first methods only)
│   │   ├── TmdbRequestParams.kt  # Request parameters
│   │   ├── TmdbResult.kt         # Result wrapper with typed errors
│   │   └── TmdbDtos.kt           # Internal DTOs (no tmdb-java types)
│   ├── internal/                 # Implementation (tmdb-java contained here)
│   │   ├── TmdbGatewayImpl.kt    # Gateway implementation
│   │   └── TmdbMappers.kt        # tmdb-java → DTO mappers
│   ├── di/                       # Dependency injection
│   │   └── TmdbTransportModule.kt
│   └── testing/                  # Test utilities
│       └── FakeTmdbGateway.kt    # Fake for unit tests
```

**Key Features:**
- ✅ ID-first API (no search methods)
- ✅ No type leakage (tmdb-java types never exposed)
- ✅ Typed error handling (TmdbResult/TmdbError)
- ✅ Retry policy (network/5xx retryable, 401/404 never)
- ✅ Rate limiting support (429 with retryAfter)

### 2. Updated :core:metadata-normalizer

**Changes:**
- ✅ Removed `com.uwetrottmann.tmdb2:tmdb-java` dependency
- ✅ Added dependency on `:infra:transport-tmdb`
- ✅ Updated `DefaultTmdbMetadataResolver` to use `TmdbGateway`
- ✅ Implements ID-first contract (only enriches when tmdbId exists)
- ✅ Tests use `FakeTmdbGateway` (no real network calls)

### 3. Guardrails

**Created:** `scripts/quality/check_tmdb_leaks.sh`

Enforces:
- ✅ tmdb-java dependency only in :infra:transport-tmdb
- ✅ No Moviebase dependencies anywhere
- ✅ tmdb-java imports only in transport-tmdb/internal
- ✅ No Moviebase imports anywhere

**Verification:**
```bash
./scripts/quality/check_tmdb_leaks.sh
# ✅ All checks passed - no TMDB library boundary violations
```

## Contract Compliance

### MEDIA_NORMALIZATION_CONTRACT.md
- ✅ Pipelines don't call TMDB (unchanged)
- ✅ Normalizer uses ID-first approach
- ✅ No search when tmdbId missing (returns input unmodified)

### TMDB Canonical Identity & Imaging SSOT Contract (v2)
- ✅ TMDB ID is preferred canonical ID
- ✅ ID-first resolution (no title search in gateway)
- ✅ No library type leakage
- ✅ Gateway enforces architectural boundaries

### AGENTS.md Layer Boundaries
- ✅ Core doesn't depend on infra implementation details
- ✅ tmdb-java isolated in infra/transport-tmdb
- ✅ Public API uses only internal DTOs

## Test Results

### Unit Tests
```bash
./gradlew :core:metadata-normalizer:test
# ✅ BUILD SUCCESSFUL
# All tests pass using FakeTmdbGateway
```

### Code Quality
```bash
./gradlew :infra:transport-tmdb:ktlintCheck :core:metadata-normalizer:ktlintCheck
# ✅ BUILD SUCCESSFUL

./gradlew :infra:transport-tmdb:detekt :core:metadata-normalizer:detekt
# ✅ BUILD SUCCESSFUL
```

### Guardrails
```bash
./scripts/quality/check_tmdb_leaks.sh
# ✅ All checks passed
```

## Benefits

1. **Type Safety:** tmdb-java exceptions/types never leak
2. **Testability:** Easy to mock with FakeTmdbGateway
3. **Maintainability:** Single place to update TMDB logic
4. **Contract Enforcement:** ID-first approach enforced by API design
5. **Error Handling:** Typed errors (Network, Timeout, NotFound, etc.)
6. **CI/Testing:** No real TMDB key required (uses fake)

## Migration Status

- [x] No Moviebase dependencies (verified)
- [x] tmdb-java only in :infra:transport-tmdb (enforced)
- [x] Core uses gateway API only (verified)
- [x] Tests pass without real API key (verified)
- [x] Guardrails in place (scripted)
- [x] Code quality checks pass (ktlint, detekt)

## Future Enhancements

While not in scope for this migration, future work could include:

1. **Search Service:** Higher-level service for title-based TMDB search (policy decisions)
2. **Caching:** Add cache directory injection for HTTP cache
3. **Metrics:** Track TMDB API usage/errors
4. **Image Service:** Dedicated service for TMDB image URL building/selection

## Files Changed

- `settings.gradle.kts` - Added :infra:transport-tmdb module
- `infra/transport-tmdb/` - New module (11 files)
- `core/metadata-normalizer/build.gradle.kts` - Updated dependencies
- `core/metadata-normalizer/.../DefaultTmdbMetadataResolver.kt` - Reimplemented with gateway
- `core/metadata-normalizer/.../di/MetadataNormalizerModule.kt` - Added resolver binding
- `scripts/quality/check_tmdb_leaks.sh` - New guardrail script

## Acceptance Criteria ✅

- ✅ No Moviebase dependency remains
- ✅ tmdb-java is only referenced inside :infra:transport-tmdb
- ✅ Core + pipelines compile and tests pass
- ✅ Normalizer uses TMDB strictly ID-first when tmdbId exists
- ✅ No tmdb-java DTO types or exceptions leak outside infra gateway
- ✅ Guardrails enforce boundaries
- ✅ Code quality checks pass
