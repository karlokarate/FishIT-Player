# Hybrid Parallel/Sequential Sync Strategy

## Problem mit Current Sequential Approach

**Aktueller Code (Zeile 100-226):**
- Sequentielle Verarbeitung: LIVE → VOD → SERIES
- Grund: Memory Pressure (220MB → 120MB)
- Trade-off: 10% langsamer, aber keine Lags

**Aber mit neuen Fixes:**
- Socket Timeout jetzt 120s (kein Timeout mehr!)
- Flow throttling (weniger Memory Pressure)
- Streaming mit 500er Batches (weniger Peak Memory)

**Neuer Bottleneck:**
- VOD dauert 150s
- SERIES muss warten bis VOD fertig
- User sieht nichts während VOD-Sync

---

## Hybrid Solution: **Throttled Parallel Sync**

### Konzept

Statt ALLE 3 gleichzeitig oder ALLE sequentiell:
→ **2 Parallel (Limited Memory), 3. wartet**

```
[LIVE] ─────┐
            ├──> Parallel (beide laden gleichzeitig)
[VOD]  ─────┘

[SERIES] ──> Startet wenn einer der oberen fertig ist
```

### Implementation

```kotlin
// File: pipeline/xtream/.../XtreamCatalogPipelineImpl.kt

coroutineScope {
    // Bounded Parallelism: Max 2 gleichzeitig
    val semaphore = Semaphore(permits = 2)
    
    val jobs = listOf(
        async {
            if (config.includeLive) {
                semaphore.withPermit { scanLive() }
            }
        },
        async {
            if (config.includeVod) {
                semaphore.withPermit { scanVod() }
            }
        },
        async {
            if (config.includeSeries) {
                semaphore.withPermit { scanSeries() }
            }
        }
    )
    
    jobs.awaitAll()
}
```

### Memory Analysis

**Sequential (Current):**
- Peak: 1 × 70MB = 70MB ✅
- Zeit: 103s + 150s + 10s = 263s ❌

**Full Parallel:**
- Peak: 3 × 70MB = 210MB ❌
- Zeit: max(103s, 150s, 10s) = 150s ✅

**Hybrid (2 Parallel):**
- Peak: 2 × 70MB = 140MB ⚠️ (acceptable!)
- Zeit: max(103s, 150s) + 10s = 160s ✅ (-40%)

### Benefits

1. **40% schneller** als sequential
2. **33% weniger Memory** als full parallel
3. **SERIES wird garantiert gesynct** (nicht mehr timeout!)
4. **User sieht Live + Movies gleichzeitig**

---

## Advanced: Channel-based Pipeline

Für **maximale Performance** (Phase 2):

```kotlin
// Producer-Consumer mit bounded channel
val itemChannel = Channel<CatalogItem>(capacity = 1000)
val batchChannel = Channel<List<CatalogItem>>(capacity = 3)

// Producers (parallel sources)
launch { streamLive(itemChannel) }
launch { streamVod(itemChannel) }
launch { streamSeries(itemChannel) }

// Batcher (groups items for efficient DB writes)
launch {
    var batch = mutableListOf<CatalogItem>()
    for (item in itemChannel) {
        batch.add(item)
        if (batch.size >= 500) {
            batchChannel.send(batch.toList())
            batch.clear()
        }
    }
}

// Consumers (parallel DB writes)
repeat(3) {
    launch {
        for (batch in batchChannel) {
            persistBatch(batch)
        }
    }
}
```

**Expected Performance:**
- Zeit: ~60s (-76%)
- Memory: 140MB (controlled)
- DB Throughput: 300+/sec
