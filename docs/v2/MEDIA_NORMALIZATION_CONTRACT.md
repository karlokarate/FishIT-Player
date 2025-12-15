# MEDIA_NORMALIZATION_CONTRACT (v2)

Authoritative rules for raw pipeline outputs and centralized normalization.

## RawMediaMetadata definition

```
data class RawMediaMetadata(
    val originalTitle: String,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val durationMinutes: Int? = null,
    val externalIds: ExternalIds = ExternalIds(),
    val sourceType: SourceType,
    val sourceLabel: String,
    val sourceId: String,
    val pipelineIdTag: PipelineIdTag = PipelineIdTag.UNKNOWN,
    val globalId: String = ""  // MUST remain empty in pipelines; assigned centrally
)
```

### Pipeline responsibilities (raw-only)
- Provide unmodified titles and IDs from the upstream source (no cleaning, no heuristics).
- Do not attempt TMDB/IMDB lookups or canonical identity generation.
- Leave `globalId` empty; it is assigned by `:core:metadata-normalizer` during unification.

### Normalizer responsibilities
- Normalize titles, resolve TMDB IDs, and compute canonical identity.
- Assign `globalId` as the single source of truth for deduplication across pipelines.

## Violations
- Pipeline sets `RawMediaMetadata.globalId` or calls any canonical-id generator (`GlobalIdUtil/generateCanonicalId`).
- Pipeline performs title normalization, TMDB lookups, or cross-pipeline matching.
