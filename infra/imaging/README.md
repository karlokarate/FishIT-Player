# infra:imaging

**Status:** RESERVED (stub module)  
**Purpose:** Global Coil/ImageLoader/OkHttp cache + provisioning (source-agnostic)

## Overview

This module is reserved for future imaging infrastructure implementation. It will provide centralized image loading and caching capabilities for the entire application.

## Responsibilities

- Global Coil ImageLoader configuration
- OkHttp cache setup for images
- Source-specific fetcher integration via narrow interfaces
- ImageLoader DI provisioning

## Contract Rules

1. **Transport layers MUST NOT own Coil/ImageLoader configuration**
2. May accept source-specific fetchers via narrow interfaces
3. Remains source-agnostic (no TDLib/Xtream direct dependencies)

## TODO

- [ ] Implement global ImageLoader configuration
- [ ] Set up OkHttp cache for images
- [ ] Create narrow interfaces for source-specific fetchers
- [ ] Provide DI bindings for ImageLoader

## References

- `docs/v2/FROZEN_MODULE_MANIFEST.md` - Module manifest and rules
- `core/ui-imaging` - UI imaging primitives (render helpers, ImageRef usage)
