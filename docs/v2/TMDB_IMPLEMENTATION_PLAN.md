# TMDB Canonical Identity & Imaging Implementation Plan

> **Datum:** 2025-12-16  
> **Basiert auf:** TMDB Canonical Identity & Imaging SSOT Contract (v2)  
> **Status:** In Planung

---

## 📋 Zusammenfassung

Dieses Dokument beschreibt alle benötigten Komponenten, Module und Änderungen, um den **TMDB Canonical Identity & Imaging SSOT Contract** vollständig zu implementieren. TMDB wird als **Single Source of Truth (SSOT)** für:

- **Canonical Identity** (TMDB ID = global eindeutige Medien-ID)
- **Bilder** (Poster, Backdrop, Thumbnail URLs)
- **Metadata-Anreicherung** (Official Titles, Year, Plot, Genres)

---

## 🏗️ Vorhandene Infrastruktur (IST-Zustand)

### ✅ Bereits vorhanden

| Komponente | Ort | Status |
|------------|-----|--------|
| `tmdb-java` Library | `core/metadata-normalizer/build.gradle.kts` | ✅ Dependency vorhanden (v2.11.0) |
| `TmdbMetadataResolver` Interface | `core/metadata-normalizer/.../TmdbMetadataResolver.kt` | ✅ Definiert |
| `DefaultTmdbMetadataResolver` | `core/metadata-normalizer/.../DefaultTmdbMetadataResolver.kt` | ⚠️ No-Op Stub |
| `MediaMetadataNormalizer` Interface | `core/metadata-normalizer/.../MediaMetadataNormalizer.kt` | ✅ Definiert |
| `SceneNameParser` | `core/metadata-normalizer/.../parser/` | ✅ Implementiert (Regex-basiert) |
| `ParsedSceneInfo` | `core/metadata-normalizer/.../parser/ParsedSceneInfo.kt` | ✅ Vollständig |
| `ImageRef` Sealed Interface | `core/model/.../ImageRef.kt` | ✅ Mit Http, TelegramThumb, LocalFile, InlineBytes |
| `RawMediaMetadata` | `core/model/.../RawMediaMetadata.kt` | ✅ Mit ExternalIds (tmdbId, imdbId, tvdbId) |
| `NormalizedMediaMetadata` | `core/model/.../NormalizedMediaMetadata.kt` | ✅ Mit tmdbId, ImageRef Feldern |
| `GlobalImageLoader` | `core/ui-imaging/` | ✅ Coil 3 Integration |

### ❌ Fehlt / Muss implementiert werden

| Komponente | Beschreibung |
|------------|--------------|
| TMDB API Key Management | Sichere Speicherung und Zugriff |
| TMDB Client Wrapper | Kotlin-Wrapper um tmdb-java |
| TMDB Search Service | Title → TMDB ID Resolution |
| TMDB Metadata Fetcher | ID → Details/Images |
| TMDB Image URL Builder | Configuration + path → Full URL |
| TMDB ID Cache | Persistierung für Offline/Performance |
| Canonical Upgrade Mechanism | Fallback → TMDB Migration |

---

## 📦 Benötigte Module & Komponenten

### 1. TMDB Service Module

**Neuer Subordner in:** `core/metadata-normalizer/`

```
core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/tmdb/
├── TmdbClient.kt                    # Wrapper um tmdb-java Tmdb Klasse
├── TmdbSearchService.kt             # Suche nach Title/Year
├── TmdbDetailsService.kt            # Fetch Details by TMDB ID
├── TmdbImageUrlBuilder.kt           # Configuration + poster_path → Full URL
├── TmdbConfigurationCache.kt        # Cached TMDB Configuration
├── TmdbRateLimiter.kt               # Rate Limiting (40 req/10s)
└── di/
    └── TmdbModule.kt                # Hilt DI Bindings
```

---

## ✅ Implementierungs-Checkliste

### Phase 1: Basis-Infrastruktur

#### 1.1 API Key Management

- [ ] **`TmdbApiKeyProvider` Interface erstellen**
  ```kotlin
  interface TmdbApiKeyProvider {
      val apiKey: String
      val isValid: Boolean
  }
  ```

- [ ] **`BuildConfigTmdbApiKeyProvider` für Debug/Release**
  - [ ] API Key in `local.properties` oder `gradle.properties`
  - [ ] BuildConfig Field in `build.gradle.kts`

- [ ] **Encrypted DataStore Option für User-Provided Keys** (optional)
  - [ ] Settings Screen Integration für eigenen API Key

#### 1.2 TMDB Client Wrapper

- [ ] **`TmdbClient` Kotlin Wrapper erstellen**
  ```kotlin
  @Singleton
  class TmdbClient @Inject constructor(
      private val apiKeyProvider: TmdbApiKeyProvider,
      private val okHttpClient: OkHttpClient,
  ) {
      private val tmdb: Tmdb by lazy { ... }
      
      fun searchService(): SearchService
      fun moviesService(): MoviesService
      fun tvService(): TvService
      fun configurationService(): ConfigurationService
  }
  ```

- [ ] **Custom OkHttpClient mit:**
  - [ ] Shared App-OkHttpClient (nicht Tmdb-eigenen)
  - [ ] Caching Header Support
  - [ ] Logging Interceptor (Debug builds)

#### 1.3 Rate Limiting

- [ ] **`TmdbRateLimiter` implementieren**
  - [ ] TMDB Limit: 40 Requests / 10 Sekunden
  - [ ] Semaphore oder Token Bucket Pattern
  - [ ] Coroutine-basiert (`suspend fun acquire()`)

---

### Phase 2: TMDB Configuration & Image URL Building

#### 2.1 Configuration Caching

- [ ] **`TmdbConfigurationCache` erstellen**
  ```kotlin
  @Singleton
  class TmdbConfigurationCache @Inject constructor(
      private val tmdbClient: TmdbClient,
      private val dataStore: DataStore<Preferences>,
  ) {
      suspend fun getConfiguration(): Configuration
      suspend fun refreshIfNeeded()
  }
  ```

- [ ] **Configuration Response speichern:**
  - [ ] `base_url` / `secure_base_url`
  - [ ] `poster_sizes` (z.B. w92, w154, w185, w342, w500, w780, original)
  - [ ] `backdrop_sizes` (z.B. w300, w780, w1280, original)
  - [ ] `profile_sizes`, `logo_sizes`, `still_sizes`

- [ ] **TTL für Refresh (empfohlen: 24h)**

#### 2.2 Image URL Builder

- [ ] **`TmdbImageUrlBuilder` implementieren**
  ```kotlin
  class TmdbImageUrlBuilder(
      private val configCache: TmdbConfigurationCache,
  ) {
      suspend fun buildPosterUrl(path: String, size: TmdbPosterSize): String
      suspend fun buildBackdropUrl(path: String, size: TmdbBackdropSize): String
      fun bestPosterSize(targetWidth: Int): TmdbPosterSize
      fun bestBackdropSize(targetWidth: Int): TmdbBackdropSize
  }
  ```

- [ ] **Enum für Sizes:**
  ```kotlin
  enum class TmdbPosterSize { W92, W154, W185, W342, W500, W780, ORIGINAL }
  enum class TmdbBackdropSize { W300, W780, W1280, ORIGINAL }
  ```

- [ ] **Size Selection Logic:**
  - [ ] Wähle nächstgrößere Size für Target-Dimension
  - [ ] Fallback zu ORIGINAL wenn keine passt

---

### Phase 3: TMDB Search & ID Resolution

#### 3.1 Search Service

- [ ] **`TmdbSearchService` implementieren**
  ```kotlin
  class TmdbSearchService @Inject constructor(
      private val tmdbClient: TmdbClient,
      private val rateLimiter: TmdbRateLimiter,
  ) {
      suspend fun searchMovie(title: String, year: Int?): List<TmdbMovieResult>
      suspend fun searchTv(title: String, year: Int?): List<TmdbTvResult>
      suspend fun searchMulti(query: String): List<TmdbSearchResult>
  }
  ```

- [ ] **Result Ranking/Scoring:**
  - [ ] Exact title match > Partial match
  - [ ] Year match bonus
  - [ ] Popularity als Tiebreaker
  - [ ] Confidence Score (0.0-1.0)

- [ ] **Ambiguity Handling:**
  - [ ] Threshold für Auto-Match (z.B. confidence > 0.85)
  - [ ] Logging bei niedrigem Confidence
  - [ ] Skip bei zu vielen gleich-guten Matches

#### 3.2 Find by External ID

- [ ] **IMDB ID → TMDB ID Resolution**
  ```kotlin
  suspend fun findByImdbId(imdbId: String): TmdbFindResult?
  suspend fun findByTvdbId(tvdbId: String): TmdbFindResult?
  ```

- [ ] **Verwendet `findService().find(id, ExternalSource.IMDB_ID, ...)`**

---

### Phase 4: TMDB Metadata Fetching

#### 4.1 Details Service

- [ ] **`TmdbDetailsService` implementieren**
  ```kotlin
  class TmdbDetailsService @Inject constructor(
      private val tmdbClient: TmdbClient,
      private val rateLimiter: TmdbRateLimiter,
  ) {
      suspend fun getMovieDetails(tmdbId: Int, language: String = "de"): TmdbMovieDetails?
      suspend fun getTvDetails(tmdbId: Int, language: String = "de"): TmdbTvDetails?
      suspend fun getSeasonDetails(tvId: Int, season: Int): TmdbSeasonDetails?
      suspend fun getEpisodeDetails(tvId: Int, season: Int, episode: Int): TmdbEpisodeDetails?
  }
  ```

- [ ] **`append_to_response` für Batch-Fetching:**
  - [ ] `images` - Alle verfügbaren Bilder
  - [ ] `credits` - Cast & Crew
  - [ ] `external_ids` - IMDB, TVDB IDs
  - [ ] `videos` - Trailer Links

#### 4.2 Domain DTOs

- [ ] **`TmdbMovieDetails` Data Class:**
  ```kotlin
  data class TmdbMovieDetails(
      val tmdbId: Int,
      val imdbId: String?,
      val title: String,
      val originalTitle: String,
      val releaseYear: Int?,
      val overview: String?,
      val posterPath: String?,
      val backdropPath: String?,
      val genres: List<String>,
      val runtime: Int?,
      val voteAverage: Double?,
      val adult: Boolean,
  )
  ```

- [ ] **`TmdbTvDetails`, `TmdbSeasonDetails`, `TmdbEpisodeDetails` analog**

---

### Phase 5: TmdbMetadataResolver Implementierung

#### 5.1 Core Resolver

- [ ] **`DefaultTmdbMetadataResolver` ersetzen durch echte Implementierung:**
  ```kotlin
  class TmdbMetadataResolverImpl @Inject constructor(
      private val searchService: TmdbSearchService,
      private val detailsService: TmdbDetailsService,
      private val imageUrlBuilder: TmdbImageUrlBuilder,
      private val idCache: TmdbIdCache,
  ) : TmdbMetadataResolver {
      
      override suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata {
          // 1. Check if tmdbId already present → Skip search
          // 2. Check cache for canonicalTitle + year
          // 3. Search TMDB if needed
          // 4. Fetch details
          // 5. Build image URLs
          // 6. Return enriched metadata
      }
  }
  ```

- [ ] **Flow:**
  ```
  Input: NormalizedMediaMetadata (from parser)
    │
    ├─ Has tmdbId? → Skip search, use existing
    │
    ├─ Check TmdbIdCache(canonicalTitle, year) → Cache hit? Use cached ID
    │
    ├─ Search TMDB by canonicalTitle + year
    │   ├─ High confidence match → Use ID, cache it
    │   ├─ Low confidence → Log warning, skip enrichment
    │   └─ No match → Return input unmodified
    │
    └─ Fetch details + Build ImageRefs
        └─ Return enriched NormalizedMediaMetadata
  ```

#### 5.2 TMDB ID Cache

- [ ] **`TmdbIdCache` für Persistierung:**
  ```kotlin
  interface TmdbIdCache {
      suspend fun get(canonicalTitle: String, year: Int?, mediaType: MediaType): Int?
      suspend fun put(canonicalTitle: String, year: Int?, mediaType: MediaType, tmdbId: Int)
      suspend fun invalidate(canonicalTitle: String, year: Int?, mediaType: MediaType)
  }
  ```

- [ ] **ObjectBox Entity für Cache:**
  ```kotlin
  @Entity
  data class ObxTmdbIdCache(
      @Id var id: Long = 0,
      var canonicalTitle: String = "",
      var year: Int? = null,
      var mediaType: String = "",
      var tmdbId: Int = 0,
      var cachedAt: Long = 0,
      var confidence: Float = 0f,
  )
  ```

- [ ] **TTL für Cache-Invalidierung (z.B. 30 Tage)**

---

### Phase 6: ImageRef Integration für TMDB

#### 6.1 ImageRef.TmdbImage Variant (Optional)

- [ ] **Entscheidung:** Separate `ImageRef.TmdbImage` oder `ImageRef.Http` verwenden?

  **Option A:** `ImageRef.Http` verwenden (empfohlen)
  - Pro: Kein neuer Typ, TMDB-URLs sind normale HTTP URLs
  - Con: Keine spezielle Cache-Behandlung

  **Option B:** Neuer `ImageRef.TmdbImage` Typ
  ```kotlin
  data class TmdbImage(
      val posterPath: String,  // z.B. "/abc123.jpg"
      val size: TmdbPosterSize,
      override val preferredWidth: Int?,
      override val preferredHeight: Int?,
  ) : ImageRef
  ```
  - Pro: Lazy URL Resolution, Resize bei Bedarf
  - Con: Zusätzlicher Typ, komplexerer Fetcher

- [ ] **Empfehlung:** `ImageRef.Http` mit vollständiger URL aus `TmdbImageUrlBuilder`

#### 6.2 Integration in NormalizedMediaMetadata

- [ ] **TMDB Bilder → ImageRef Konvertierung:**
  ```kotlin
  fun TmdbMovieDetails.toImageRefs(urlBuilder: TmdbImageUrlBuilder): ImageRefs {
      return ImageRefs(
          poster = posterPath?.let { 
              ImageRef.Http(urlBuilder.buildPosterUrl(it, TmdbPosterSize.W500))
          },
          backdrop = backdropPath?.let {
              ImageRef.Http(urlBuilder.buildBackdropUrl(it, TmdbBackdropSize.W1280))
          },
      )
  }
  ```

---

### Phase 7: Canonical Upgrade Mechanism

#### 7.1 Fallback → TMDB Migration

- [ ] **`CanonicalUpgradeService` implementieren:**
  ```kotlin
  class CanonicalUpgradeService @Inject constructor(
      private val tmdbResolver: TmdbMetadataResolver,
  ) {
      suspend fun upgradeToCanonical(item: RawMediaMetadata): NormalizedMediaMetadata {
          // 1. Normalisieren (Title Cleanup, Scene Parsing)
          // 2. TMDB Enrichment
          // 3. Return kanonische Metadata
      }
      
      suspend fun batchUpgrade(items: List<RawMediaMetadata>): List<NormalizedMediaMetadata>
  }
  ```

- [ ] **Batch Processing mit Rate Limiting:**
  - [ ] Max parallel Requests respektieren
  - [ ] Delay zwischen Batches
  - [ ] Progress Callback für UI

#### 7.2 Merge Policy

- [ ] **TMDB Wins für:**
  - [ ] Official Title (überschreibt canonicalTitle)
  - [ ] Year (wenn TMDB genauer)
  - [ ] Poster/Backdrop (TMDB priorisiert)

- [ ] **Merge (kombinieren):**
  - [ ] External IDs (TMDB + vorhandene IMDB/TVDB)
  - [ ] Genres (Union)

- [ ] **Keep Original:**
  - [ ] sourceId (Pipeline-spezifisch)
  - [ ] sourceType
  - [ ] sourceLabel
  - [ ] durationMinutes (wenn Quelle genauer)

---

### Phase 8: Tests & Validation

#### 8.1 Unit Tests

- [ ] **`TmdbSearchServiceTest`**
  - [ ] Mock TMDB API Responses
  - [ ] Confidence Scoring Tests
  - [ ] Ambiguity Handling Tests

- [ ] **`TmdbImageUrlBuilderTest`**
  - [ ] Size Selection Logic
  - [ ] URL Construction

- [ ] **`TmdbMetadataResolverImplTest`**
  - [ ] Already has TMDB ID → Skip
  - [ ] Successful search → Enrich
  - [ ] No match → Return unchanged
  - [ ] Cache hit → Skip API call

#### 8.2 Integration Tests

- [ ] **Real TMDB API Tests (CI mit API Key Secret)**
  - [ ] Search for known movies
  - [ ] Handle rate limiting gracefully

---

## 🔧 Externe Tools & Libraries

### Bereits integriert

| Library | Version | Zweck |
|---------|---------|-------|
| `tmdb-java` | 2.11.0 | TMDB API Client (Retrofit-basiert) |
| Coil 3 | 3.x | Image Loading & Caching |
| OkHttp 5 | 5.x | HTTP Client |
| Hilt | 2.x | Dependency Injection |

### Empfohlene Upgrades

- [ ] **`tmdb-java` auf 2.13.0 upgraden** (neueste Version)
  - Bessere Exception Handling
  - Mehr Endpoints

### Alternative Libraries (evaluiert)

| Library | Bewertung | Entscheidung |
|---------|-----------|--------------|
| `tmdb-java` | ⭐⭐⭐⭐⭐ | ✅ Verwenden - gut maintained, Kotlin-kompatibel |
| `themoviedbapi` | ⭐⭐⭐ | ❌ Weniger aktiv |
| Custom Retrofit Client | ⭐⭐⭐⭐ | ❌ Mehr Aufwand, tmdb-java reicht |

### Online Tools für Entwicklung

| Tool | URL | Zweck |
|------|-----|-------|
| TMDB API Docs | https://developer.themoviedb.org/docs | Offizielle Dokumentation |
| TMDB API Testing | https://developer.themoviedb.org/reference | API Playground |
| TMDB Image Tester | https://image.tmdb.org/t/p/w500/test.jpg | URL Format Test |

---

## 📐 Architecture Compliance

### Contract-konforme Architektur

```
                    ┌─────────────────────────────────────────┐
                    │            Pipeline Layer               │
                    │                                         │
                    │  Telegram  │  Xtream   │  IO  │ Audiobook│
                    │            │           │      │          │
                    │       ⬇️ RawMediaMetadata ⬇️              │
                    └─────────────────────────────────────────┘
                                        │
                    ┌───────────────────▼─────────────────────┐
                    │      :core:metadata-normalizer          │
                    │                                         │
                    │  ┌───────────────────────────────────┐  │
                    │  │   MediaMetadataNormalizer         │  │
                    │  │   (Title Cleanup, Scene Parsing)  │  │
                    │  └───────────────┬───────────────────┘  │
                    │                  │                      │
                    │  ┌───────────────▼───────────────────┐  │
                    │  │   TmdbMetadataResolver            │  │
                    │  │   (Search, Details, Images)       │  │
                    │  │                                   │  │
                    │  │   ┌─────────────────────────┐     │  │
                    │  │   │ TmdbSearchService       │     │  │
                    │  │   │ TmdbDetailsService      │     │  │
                    │  │   │ TmdbImageUrlBuilder     │     │  │
                    │  │   │ TmdbIdCache             │     │  │
                    │  │   └─────────────────────────┘     │  │
                    │  └───────────────┬───────────────────┘  │
                    │                  │                      │
                    │       ⬇️ NormalizedMediaMetadata ⬇️       │
                    └─────────────────────────────────────────┘
                                        │
                    ┌───────────────────▼─────────────────────┐
                    │           Data Layer                    │
                    │   (Repository, ObjectBox Persistence)   │
                    └─────────────────────────────────────────┘
```

### Hard Rules (aus Contract)

| Rule | Status |
|------|--------|
| Pipelines MUST NOT call TMDB | ⚠️ Sicherstellen |
| TMDB calls ONLY in `:core:metadata-normalizer` | ✅ Geplant |
| ImageRef is SSOT for images | ✅ Vorhanden |
| No raw URLs past Pipeline Layer | ✅ ImageRef enforced |

---

## 📅 Priorisierte Reihenfolge

### Sprint 1: Foundation (1-2 Wochen)
1. [ ] API Key Management
2. [ ] TmdbClient Wrapper
3. [ ] Rate Limiter
4. [ ] Configuration Cache

### Sprint 2: Search & Resolution (1-2 Wochen)
5. [ ] TmdbSearchService
6. [ ] Confidence Scoring
7. [ ] TmdbIdCache (ObjectBox)

### Sprint 3: Details & Images (1-2 Wochen)
8. [ ] TmdbDetailsService
9. [ ] TmdbImageUrlBuilder
10. [ ] Domain DTOs

### Sprint 4: Integration (1 Woche)
11. [ ] TmdbMetadataResolverImpl
12. [ ] CanonicalUpgradeService
13. [ ] Tests

### Sprint 5: Polish & Optimization (1 Woche)
14. [ ] Batch Processing
15. [ ] Performance Tuning
16. [ ] Documentation

---

## 📝 Offene Fragen

1. **API Key Distribution:**
   - Eigener TMDB API Key in App eingebettet?
   - User muss eigenen Key eingeben?
   - Backend-Proxy für Key-Hiding?

2. **Offline Support:**
   - Wie lange sollen TMDB-Daten gecached werden?
   - Fallback wenn offline und Cache leer?

3. **Adult Content:**
   - TMDB `adult` Flag auswerten?
   - Profile-basierte Filterung?

4. **Sprache:**
   - TMDB Requests in `de-DE` oder User-Locale?
   - Fallback zu `en-US` wenn kein deutscher Content?

---

## 📚 Referenzen

- [TMDB API Documentation](https://developer.themoviedb.org/docs)
- [tmdb-java GitHub](https://github.com/UweTrottmann/tmdb-java)
- [docs/v2/IMAGING_SYSTEM.md](./IMAGING_SYSTEM.md)
- [docs/v2/MEDIA_NORMALIZER_DESIGN.md](./MEDIA_NORMALIZER_DESIGN.md)
- [core/model/ImageRef.kt](../../core/model/src/main/java/com/fishit/player/core/model/ImageRef.kt)
