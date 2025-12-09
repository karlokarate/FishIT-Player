# Gold: Xtream Pipeline

Curated patterns from the v1 Xtream Codes API integration.

## Documentation

✅ **`GOLD_XTREAM_CLIENT.md`** (403 lines) - Complete extraction of Xtream patterns

## Key Patterns Extracted

1. **Per-Host Rate Limiting** – 120ms minimum interval with Mutex
2. **Dual-TTL Cache** – 60s for catalog, 15s for EPG (LRU eviction at 512 entries)
3. **VOD Alias Rotation** – Try "vod", "movie", "movies" until one works
4. **Multi-Port Discovery** – Parallel probing of common ports (80, 8080, 25461, etc.)
5. **Capability Detection** – Probe for EPG, series, catchup, XMLTV support
6. **Parallel EPG Prefetch** – Semaphore(4) for controlled concurrency
7. **Category Fallback** – Try category_id=* first, fall back to category_id=0
8. **Graceful Degradation** – Empty results instead of crashes

## v2 Target Modules

- `pipeline/xtream/client/` - API client with rate limiting and caching
- `pipeline/xtream/discovery/` - Multi-port and capability detection
- `pipeline/xtream/repository/` - Catalog and EPG repository

## v2 Status

❌ **Not Started** - Scheduled for Phase 3 (near future)

See `/docs/v2/XTREAM_PIPELINE_V2_REUSE_ANALYSIS.md` for detailed analysis.

## Porting Checklist

- [ ] Port rate limiting to XtreamApiClient
- [ ] Port dual-TTL cache
- [ ] Port URL generation with alias rotation
- [ ] Port multi-port discovery
- [ ] Port capability detection
- [ ] Port parallel EPG prefetch
- [ ] Port category fallback strategy
- [ ] Write unit tests for all patterns

## References

- **Gold Doc:** `GOLD_XTREAM_CLIENT.md` (this folder)
- **v1 Source:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/core/xtream/`
- **v2 Analysis:** `/docs/v2/XTREAM_PIPELINE_V2_REUSE_ANALYSIS.md`
