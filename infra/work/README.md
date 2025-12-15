# infra:work

**Status:** RESERVED (stub module)  
**Purpose:** WorkManager scheduling/orchestration (catalog sync, background fetch)

## Overview

This module is reserved for future WorkManager-based scheduling implementation. It will handle background work coordination, including catalog synchronization and periodic content updates.

## Responsibilities

- WorkManager scheduler API for catalog sync
- Background sync worker implementations
- Work constraints and policies
- Integration with core:catalog-sync contracts

## Contract Rules

1. **Consumes core:catalog-sync contracts (domain/types)**
2. app-v2 only triggers via interfaces
3. MUST NOT contain WorkManager scheduling in core:catalog-sync
4. Scheduler implementation lives here, contracts live in core

## TODO

- [ ] Implement WorkManager configuration
- [ ] Create scheduler API for catalog sync triggers
- [ ] Implement background sync workers
- [ ] Define work constraints and retry policies

## References

- `docs/v2/FROZEN_MODULE_MANIFEST.md` - Module manifest and rules
- `core/catalog-sync` - Catalog sync contracts (domain/types)
