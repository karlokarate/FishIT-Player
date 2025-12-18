# :infra:transport-tmdb

**TMDB Transport Gateway**

This module provides a single, type-safe gateway to The Movie Database (TMDB) API using tmdb-java. It enforces strict architectural boundaries: no TMDB library types leak outside this module.

## Contract Compliance

This module implements the requirements from:
- `contracts/TMDB Canonical Identity & Imaging SSOT Contract (v2).md`
- `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`

## Key Design Principles

1. **ID-First API**: All methods require TMDB IDs. No search methods exposed.
2. **No Type Leakage**: tmdb-java DTOs never appear in public API signatures.
3. **Error Handling**: All exceptions converted to typed `TmdbResult.Err` variants.
4. **Retry Policy**: Deterministic retry on network/5xx errors; never on 401/404.
5. **Rate Limiting**: 429 responses surface `TmdbError.RateLimited(retryAfter)`.

## Public API

### Gateway Interface
```kotlin
interface TmdbGateway {
    suspend fun getMovieDetails(movieId: Int, params: TmdbRequestParams): TmdbResult<TmdbMovieDetails>
    suspend fun getTvDetails(tvId: Int, params: TmdbRequestParams): TmdbResult<TmdbTvDetails>
    suspend fun getMovieImages(movieId: Int, params: TmdbRequestParams): TmdbResult<TmdbImages>
    suspend fun getTvImages(tvId: Int, params: TmdbRequestParams): TmdbResult<TmdbImages>
}
```

### Result Types
- `TmdbResult.Ok<T>` - Success with data
- `TmdbResult.Err` - Failure with typed error

### Error Types
- `TmdbError.Network` - Network connectivity issues
- `TmdbError.Timeout` - Request timeout
- `TmdbError.Unauthorized` - Invalid API key (401)
- `TmdbError.NotFound` - Resource not found (404)
- `TmdbError.RateLimited(retryAfter?)` - Rate limit exceeded (429)
- `TmdbError.Unknown` - Other errors

## Dependencies

- tmdb-java 2.11.0
- OkHttp 5.x (project version)
- Retrofit 2.x
- Hilt for DI

## Usage

Inject `TmdbGateway` in your service:

```kotlin
@Inject lateinit var tmdbGateway: TmdbGateway

suspend fun enrichMetadata(tmdbId: Int) {
    val params = TmdbRequestParams(language = "en-US", region = null)
    when (val result = tmdbGateway.getMovieDetails(tmdbId, params)) {
        is TmdbResult.Ok -> handleDetails(result.value)
        is TmdbResult.Err -> handleError(result.error)
    }
}
```

## Testing

Unit tests should use `FakeTmdbGateway` (no real network calls).
Integration tests use MockWebServer with canned responses.

## Not Allowed

- Search methods (title search is higher-level policy)
- Direct tmdb-java usage outside this module
- TMDB library types in return values
