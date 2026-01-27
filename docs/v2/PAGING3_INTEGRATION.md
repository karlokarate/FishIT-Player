# Jetpack Paging 3 Integration

**Status:** ✅ Implemented  
**Date:** January 2025  
**Version:** 3.3.6

---

## Overview

The FishIT-Player v2 Library screen now supports **Jetpack Paging 3** for efficient infinite scroll browsing of large media catalogs (10,000+ items).

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (feature/library)                                  │
│  ├── LibraryScreen.kt                                        │
│  │   ├── PagingMediaGrid (LazyPagingItems)                  │
│  │   └── MediaGrid (legacy List-based)                      │
│  └── LibraryViewModel.kt                                     │
│      ├── vodPagingFlow: Flow<PagingData<LibraryMediaItem>>  │
│      └── seriesPagingFlow: Flow<PagingData<...>>            │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Domain Layer (core/library-domain)                          │
│  └── LibraryContentRepository                                │
│      ├── getVodPagingData(options, config)                  │
│      ├── getSeriesPagingData(options, config)               │
│      ├── getVodCount(options)                               │
│      └── getSeriesCount(options)                            │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Data Layer (infra/data-nx)                                  │
│  └── NxLibraryContentRepositoryImpl                          │
│      └── Uses Pager + ObjectBoxPagingSource                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Repository Layer (core/model)                               │
│  └── NxWorkRepository                                        │
│      ├── pagingSourceFactory(options): () -> PagingSource   │
│      └── count(options): Int                                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Persistence Layer (core/persistence)                        │
│  └── ObjectBoxPagingSource<T, R>                            │
│      └── Uses query.find(offset, limit) for DB pagination   │
└─────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. ObjectBoxPagingSource (`core/persistence`)

Generic `PagingSource` implementation for ObjectBox:

```kotlin
class ObjectBoxPagingSource<T, R : Any>(
    private val queryFactory: () -> Query<T>,
    private val mapper: (T) -> R?,
) : PagingSource<Int, R>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, R> {
        val page = params.key ?: 0
        val items = queryFactory()
            .find(offset.toLong(), pageSize.toLong())
            .mapNotNull(mapper)
        return LoadResult.Page(data = items, prevKey, nextKey)
    }
}
```

**Performance:**
- O(1) memory per page
- Native database offset/limit (no full-table scan)
- Automatic invalidation support

### 2. LibraryPagingConfig (`core/library-domain`)

```kotlin
data class LibraryPagingConfig(
    val pageSize: Int = 50,
    val prefetchDistance: Int = pageSize,
    val initialLoadSize: Int = pageSize * 3,
)

companion object {
    val DEFAULT = LibraryPagingConfig()      // 50/page for TV grids
    val GRID = LibraryPagingConfig(30)       // 30/page for larger tiles
    val LIST = LibraryPagingConfig(100)      // 100/page for compact lists
}
```

### 3. ViewModel Paging Flows (`feature/library`)

```kotlin
val vodPagingFlow: Flow<PagingData<LibraryMediaItem>> =
    combine(sortOption, filterConfig) { sort, filter -> ... }
        .flatMapLatest { (sort, filter) ->
            libraryContentRepository.getVodPagingData(
                options = LibraryQueryOptions(sort, filter),
                config = LibraryPagingConfig.DEFAULT,
            )
        }
        .cachedIn(viewModelScope)  // ← Critical for config changes!
```

### 4. Compose UI Integration

```kotlin
@Composable
fun LibraryScreen(
    usePaging: Boolean = true,  // Toggle between Paging/legacy
    ...
) {
    val vodPagingItems = viewModel.vodPagingFlow.collectAsLazyPagingItems()
    
    PagingMediaGrid(
        pagingItems = vodPagingItems,
        onItemClick = onItemClick,
    )
}

@Composable
private fun PagingMediaGrid(pagingItems: LazyPagingItems<LibraryMediaItem>) {
    when (pagingItems.loadState.refresh) {
        is LoadState.Loading -> CircularProgressIndicator()
        is LoadState.Error -> ErrorState(...)
        else -> LazyVerticalGrid(...) {
            items(pagingItems.itemCount) { index ->
                pagingItems[index]?.let { MediaCard(it) }
            }
        }
    }
}
```

## Dependencies

| Module | Dependency | Scope |
|--------|-----------|-------|
| `core:model` | `paging-common:3.3.6` | api |
| `core:persistence` | `paging-common:3.3.6` | api |
| `core:library-domain` | `paging-common:3.3.6` | api |
| `infra:data-nx` | `paging-runtime-ktx:3.3.6` | implementation |
| `feature:library` | `paging-runtime-ktx:3.3.6` | implementation |
| `feature:library` | `paging-compose:3.3.6` | implementation |

## Usage Example

```kotlin
// In your Screen
LibraryScreen(
    onItemClick = { item -> navController.navigateToDetail(item.id) },
    onBack = { navController.popBackStack() },
    usePaging = true,  // Enable infinite scroll
)
```

## Migration Path

**From List-based to Paging:**

1. The `usePaging` parameter allows gradual migration
2. Search results still use legacy List-based approach
3. Set `usePaging = false` to use the original implementation

## Performance Comparison

| Metric | Legacy (List) | Paging 3 |
|--------|--------------|----------|
| Initial Load | All items | 150 items (3 pages) |
| Memory | O(n) | O(page size) |
| Scroll Latency | High (large lists) | Constant |
| Sort/Filter Change | Full reload | Automatic invalidation |

## Best Practices

1. **Always use `cachedIn(viewModelScope)`** to survive configuration changes
2. **Handle all LoadState values** (Loading, Error, NotLoading)
3. **Use stable keys** for item identity (`key = { it.id }`)
4. **Prefer page size ~50** for TV grids (balances memory vs network)

## References

- [Jetpack Paging 3 Documentation](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)
- [Paging with Compose](https://developer.android.com/jetpack/compose/lists#paging)
- [ObjectBox Query Pagination](https://docs.objectbox.io/queries#offset-and-limit)
