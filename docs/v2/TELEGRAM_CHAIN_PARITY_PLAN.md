# Telegram Chain Parity Plan — Xtream-Blueprint-Abgleich

<!-- markdownlint-disable MD024 MD013 -->

**Version:** 1.0  
**Erstellt:** 2026-02-12  
**Status:** 🚧 Audit abgeschlossen, Implementierung ausstehend  
**Referenz-Issue:** TDLib→Telethon Migration (Issue #703) + Chain Parity  
**Branch:** `architecture/v2-bootstrap`

---

## Motivation

Die Xtream-Chain ist die am weitesten fortgeschrittene Pipeline im Projekt und dient als **architektonisches Blueprint** für alle anderen Source-Chains. Die Telegram-Chain hat nach der TDLib→Telethon-Migration (5 Commits) zwar funktionale Transport-, Pipeline- und Playback-Layer, aber es fehlen wesentliche Infrastruktur-Komponenten, die bei Xtream vorhanden sind.

**Ziel:** Die Telegram-Chain soll **strukturell identisch** zur Xtream-Chain aufgebaut sein — gleiche Layer, gleiche Patterns, gleiche Lifecycle-Hooks.

---

## 1. Architektur-Vergleich (Layer-by-Layer)

| Layer | Xtream (Blueprint) | Telegram (Ist-Stand) | Status |
|-------|---------------------|----------------------|--------|
| **Transport** | `DefaultXtreamApiClient` (Facade → 3 Handler) | `DefaultTelegramClient` → `TelethonProxyClient` → Python proxy | ✅ COMPLETE |
| **Pipeline** | `PhaseScanOrchestrator` (4 Phasen parallel) | `TelegramCatalogPipelineImpl` (PLATINUM parallel) | ✅ COMPLETE |
| **Data (NX)** | `NxXtreamCatalogRepositoryImpl`, `NxCatalogWriter` | `NxTelegramMediaRepositoryImpl` (166 Zeilen) | ✅ COMPLETE |
| **Data (Legacy)** | — (kein Legacy mehr) | `ObxTelegramContentRepository` (413 Zeilen, **AKTIV im DI**) | ⚠️ CLEANUP |
| **Sync Service** | `XtreamSyncService` interface + `DefaultXtreamSyncService` (502 Z.) | **EXISTIERT NICHT** — nur `syncTelegram()` STUB im Worker | ❌ GAP |
| **Sync Checkpoint** | `XtreamSyncCheckpoint` (Phasen-basiert) | `TelegramSyncCheckpoint` (v3 mit HWM+userId) | ✅ COMPLETE |
| **Session Bootstrap** | `XtreamSessionBootstrap` (216 Zeilen, auto-init+retry+preload) | **EXISTIERT NICHT** | ❌ GAP |
| **Chat/Category Preloader** | `XtreamCategoryPreloader` (273 Z., StateFlow cache) | **EXISTIERT NICHT** | ❌ GAP |
| **Selection UI** | `CategorySelectionScreen` (382 Z.) + ViewModel | **EXISTIERT NICHT in v2** (nur v1-Legacy Dialog) | ❌ GAP |
| **Selection Repository** | `NxCategorySelectionRepository` (220 Z., sync gate) | **EXISTIERT NICHT** | ❌ GAP |
| **Playback** | `XtreamPlaybackSourceFactoryImpl` (696 Z.) | `TelegramPlaybackSourceFactoryImpl` | ✅ COMPLETE |
| **DI Modules** | 6 Module | 6 Module | ✅ COMPLETE |

---

## 2. Kritische Gaps (Priorisiert)

### GAP 1: `TelegramSyncService` — Kein Single Entry Point

**Xtream-Blueprint:**
- `XtreamSyncService` interface: `sync(config): Flow<SyncStatus>`
- `DefaultXtreamSyncService` (502 Zeilen): `ChannelSyncBuffer`, `IncrementalSyncDecider`
- Pfad: `core/catalog-sync/sources/xtream/`

**Telegram Ist-Stand:**
- `syncTelegram()` im `CatalogSyncOrchestratorWorker` ist ein **STUB**:
  ```kotlin
  private suspend fun syncTelegram(...): SyncResult {
      UnifiedLog.i(TAG) { "Telegram sync: TODO - awaiting TelegramSyncService" }
      return SyncResult(source = "TELEGRAM", itemsPersisted = 0)
  }
  ```
- Die eigentliche Sync-Logik steckt in `DefaultCatalogSyncService.syncTelegram()` (478 Zeilen),
  aber ohne sauberes Interface. Der Worker ruft diese Methode **nicht** auf.

**Aktion:**
- `TelegramSyncService` interface erstellen in `core/catalog-sync/sources/telegram/`
- `DefaultTelegramSyncService` implementieren (delegiert an bestehende Pipeline)
- Worker-Stub durch Service-Aufruf ersetzen
- Contract-Spec existiert bereits in `TELEGRAM_PIPELINE_REDESIGN_CONTRACT.md`

---

### GAP 2: `TelegramSessionBootstrap` — Kein Auto-Init bei App-Start

**Xtream-Blueprint:** `XtreamSessionBootstrap` (216 Zeilen):
1. Liest verschlüsselte Credentials aus `EncryptedXtreamCredentialsStore`
2. Optimistisch `setXtreamActive()`
3. Validiert mit Retry (3x, exponential backoff 5s→10s→20s)
4. Bei Erfolg: `XtreamCategoryPreloader.preloadCategories()`
5. Bei Fehler: `setXtreamInactive(reason)`

**Telegram:** Nichts. Kein Auto-Start des Telethon-Proxy, keine Auth-Validierung, kein `setTelegramActive()`.

**Aktion:** `TelegramSessionBootstrap` erstellen in `app-v2/.../bootstrap/`:
1. `TelethonProxyLifecycle.ensureRunning()` aufrufen
2. Auth-Status prüfen via `TelegramAuthClient.getAuthState()`
3. `setTelegramActive()` / `setTelegramInactive()` setzen
4. `TelegramChatPreloader.preloadChats()` triggern

---

### GAP 3: `TelegramChatPreloader` — Keine Chat-Liste gecacht

**Xtream-Blueprint:** `XtreamCategoryPreloader` (273 Zeilen):
- `StateFlow<PreloadState>` (Idle→Loading→Success→Error)
- Fetch via Pipeline → In-Memory Cache → Persist via `NxCategorySelectionRepository`
- Bestehende User-Selektion wird beim Re-Fetch preserved

**Telegram:** Chats werden nur "on-demand" in der Pipeline geholt, kein Cache, kein Pre-Load.

**Aktion:** `TelegramChatPreloader` erstellen in `core/catalog-sync/sources/telegram/`:
- Holt Chats via `TelegramClient.getChats()`
- Cached in `StateFlow<ChatPreloadState>`
- Persistiert in `NxTelegramChatSelectionRepository`

---

### GAP 4: Chat-Selection UI — Kein Chat-Picker in v2

**Xtream-Blueprint:** `CategorySelectionScreen` (382 Zeilen) + `CategorySelectionViewModel`:
- Full-Screen mit Tabs (VOD/Series/Live)
- Bulk Select All / Deselect All pro Tab
- "Speichern & Synchronisieren" Button
- **Sync-Gate:** `isCategorySelectionComplete()` — Sync startet NICHT ohne Selection

**Telegram v2:** Nichts. v1-Legacy hatte `TelegramChatPickerDialog` (AlertDialog), nicht portiert.

**Design-Entscheidung:**
- Shared UI-Komponenten wo möglich (z.B. `SelectableItemCard`, Bulk-Actions)
- Telegram-spezifisch: Statt 3 Tabs → einzelne Liste der verfügbaren Chats
- Jeder Chat: Titel, Typ (Channel/Group/Chat), Teilnehmeranzahl
- Sync-Gate analog: `isChatSelectionComplete()`
- Neuer Navigation-Route: `Routes.TELEGRAM_CHAT_SELECTION`

---

### GAP 5: Chat-Selection Repository — Kein Persistence-Layer

**Xtream:** `NxCategorySelectionRepository` → hart an `XtreamCategoryType` enum, `"xtream:"` Prefix.

**Aktion:** Neue `NxTelegramChatSelectionRepository` erstellen:
- Entity: `NX_TelegramChatSelection` (accountKey, chatId, chatTitle, chatType, isSelected, memberCount, sortOrder)
- Sync-Gate: `isChatSelectionComplete()` / `setChatSelectionComplete()`
- Persistence in ObjectBox
- **Nicht** die Xtream-Repo wiederverwenden — zu stark gekoppelt

---

## 3. Entity-Analyse: `remoteId` vs. `chatId`/`messageId`

### Ist-Stand

| Ort | `remoteId` Nutzung | `chatId`+`messageId` Nutzung |
|-----|--------------------|------------------------------|
| `ObxTelegramMessage` (Legacy) | `remoteId: String?`, `thumbRemoteId`, `posterRemoteId` | `chatId: Long`, `messageId: Long` |
| `TelegramRemoteId` (Transport) | Name enthält "Remote", intern chatId+messageId | `chatId: Long`, `messageId: Long` |
| `TelegramRawMetadataExtensions` | `PlaybackHintKeys.Telegram.REMOTE_ID` (optional) | `CHAT_ID`, `MESSAGE_ID` (primary) |
| `NX_WorkSourceRef` | — | `telegramChatId: Long?`, `telegramMessageId: Long?` + `sourceKey: "msg:{chatId}:{messageId}"` |
| `DefaultTelegramClient` | `resolveRemoteId()`, `parseRemoteIdParts()` | Intern resolved → chatId+messageId |
| `NxTelegramMediaRepositoryImpl` | `remoteId = null, // TODO` | Poster via `"tg:${it.remoteId}"` |

### Analyse

Der `remoteId`-Begriff wird in **zwei Bedeutungen** genutzt:

1. **Video/Media RemoteId** = chatId+messageId Komposit → Korrekt abstrahiert durch `TelegramRemoteId` value class.  
   `TelegramRemoteId.toSourceKey()` = `"msg:{chatId}:{messageId}"`. **Das ist richtig.**

2. **Thumbnail/Poster RemoteId** = Telegram File-Reference (Opaque String von der Telegram API) → **Anderer Zweck**.  
   Diese IDs kommen von `TgThumbnailRef.remoteId` und werden zum Download-Resolving gebraucht.  
   **Muss bleiben**, aber umbenannt werden zu `telegramFileRef` o.ä.

### Aktionen

| Was | Aktion |
|-----|--------|
| `ObxTelegramMessage` | Legacy, ReadOnly. Langfristig löschen. |
| `TelegramRemoteId` → `TelegramMessageId` | Umbenennen (chatId+messageId Value Class) |
| `DefaultTelegramClient.resolveRemoteId()` | → `resolveMessageMedia()` |
| `PlaybackHintKeys.Telegram.REMOTE_ID` | Prüfen ob noch gebraucht, ggf. entfernen |
| Thumbnail `remoteId` → `fileRef` | Separater Zweck, umbenennen |
| `NX_WorkSourceRef.telegramChatId/MessageId` | **Bereits korrekt** — keine Änderung |

---

## 4. Legacy Entity Cleanup

### `ObxTelegramContentRepository` — Noch aktiv im DI

**Aktive Referenzen außerhalb Legacy:**
- `ObxTelegramContentRepository.kt` (413 Zeilen) — Produktion in `infra/data-telegram/`
- `TelegramDataModule.kt` — DI Binding: `bindTelegramContentRepository(impl: ObxTelegramContentRepository)`
- `ObxTelegramMessage` — Entity in `core/persistence/.../ObxEntities.kt`

**NX-Alternative existiert:** `NxTelegramMediaRepositoryImpl` (166 Zeilen)

**Per Contract:** `TELEGRAM_PARSER_CONTRACT.md`: "Do NOT migrate legacy ObxTelegramMessage data"

**Aktion:**
1. `TelegramContentRepository` Interface-Nutzung auditieren
2. Wenn keine aktiven Consumer → DI Binding auf NX umstellen
3. `ObxTelegramContentRepository` → Deprecate → Delete in separatem PR

---

## 5. Timeout/Retry-Optimierung

### Ist-Stand vs. Empfehlung

| Parameter | Xtream | Telegram (Ist) | Empfehlung Telegram | Begründung |
|-----------|--------|----------------|---------------------|------------|
| Connect | 30s | 30s | **10s** | Localhost-Proxy, kein Netzwerk |
| Read (API) | 30s | 120s (alle) | **30s** | API-Calls schneller timeouten |
| Read (Streaming) | 120s | 120s | **120s** (OK) | File-Downloads brauchen Zeit |
| Write | 30s | 10s | **10s** (OK) | POST-Bodies sind klein |
| Health Check | — | 2s/2s | **OK** | Schnelle Prüfung |
| Retry DEFAULT | — | 5x, 500ms, 30s max | **OK** | |
| Retry AUTH | — | 7x, 1s, 60s max | **3x, 2s, 30s max** | Schneller fehlschlagen |
| Retry QUICK | — | 3x, 200ms, 2s max | **OK** | |

### Python-Seite (`tg_proxy.py`)

| Parameter | Ist | Empfehlung | Begründung |
|-----------|-----|------------|------------|
| Request line read | 10s | **5s** | Localhost, Header sofort |
| Telethon Client | Keine Timeouts | **`timeout=30` bei connect** | Default 10s zu kurz |
| FloodWait | `sleep(e.seconds + 1)` | **`min(e.seconds, 120) + 1`** | Schutz vor extremen Waits |
| File Chunks | 512KB | **1MB** | Weniger Round-Trips |

### Neue Config SSOT

Analog zu `XtreamTransportConfig`:

```kotlin
object TelegramTransportConfig {
    const val PROXY_CONNECT_TIMEOUT_SECONDS = 10L
    const val API_READ_TIMEOUT_SECONDS = 30L
    const val STREAMING_READ_TIMEOUT_SECONDS = 120L
    const val WRITE_TIMEOUT_SECONDS = 10L
    const val HEALTH_CONNECT_TIMEOUT_MS = 2000L
    const val HEALTH_READ_TIMEOUT_MS = 2000L
    const val MAX_FLOOD_WAIT_SECONDS = 120
    const val FILE_CHUNK_SIZE = 1024 * 1024  // 1MB
}
```

---

## 6. Pagination — Bewertung

### Ist-Stand

`TelegramMessageCursor` (235 Zeilen) — gut implementiert:
- Seiten-Größe: 100 Messages
- Cursor: `fromMessageId` (0 = neueste, dann letzte ID)
- Inkrementell: `stopAtMessageId` (High-Water-Mark)
- Retry: 3x bei leerer Seite (exponential backoff 300ms→600ms→1200ms)
- Quota: `maxMessages` Limit respektiert

### Verbesserungspotential

| Problem | Empfehlung |
|---------|------------|
| Kein Timeout pro Seite | `withTimeout(60.seconds) { fetchPage() }` |
| FloodWait nicht auf Cursor-Ebene behandelt | Cursor pausiert bei FloodWait vom Transport |
| Empty-Page-Retry 3x könnte zu wenig sein | Auf 5 erhöhen |
| `highestSeenMessageId`-Tracking | ✅ Korrekt für Checkpoint-Updates |

---

## 7. Naming-Alignment (Xtream → Telegram)

| Xtream Pattern | Telegram Equivalent |
|----------------|---------------------|
| `XtreamApiClient` | `TelegramClient` (existiert ✅) |
| `XtreamTransportConfig` | `TelegramTransportConfig` (**NEU**) |
| `XtreamCatalogPipelineImpl` | `TelegramCatalogPipelineImpl` (existiert ✅) |
| `XtreamSyncService` | `TelegramSyncService` (**NEU**) |
| `DefaultXtreamSyncService` | `DefaultTelegramSyncService` (**NEU**) |
| `XtreamSyncCheckpoint` | `TelegramSyncCheckpoint` (existiert ✅) |
| `XtreamCategoryPreloader` | `TelegramChatPreloader` (**NEU**) |
| `XtreamSessionBootstrap` | `TelegramSessionBootstrap` (**NEU**) |
| `CategorySelectionScreen` | `ChatSelectionScreen` (**NEU**) |
| `CategorySelectionViewModel` | `ChatSelectionViewModel` (**NEU**) |
| `NxCategorySelectionRepository` | `NxTelegramChatSelectionRepository` (**NEU**) |
| `XtreamPlaybackSourceFactoryImpl` | `TelegramPlaybackSourceFactoryImpl` (existiert ✅) |

---

## 8. Implementierungsplan

### Phase 1: Infrastruktur (Fundament)

> **Scope:** `telegram-transport`, `catalog-sync`  
> **Geschätzter Aufwand:** ~380 Zeilen neuer Code

| # | Aufgabe | Dateien | Zeilen |
|---|---------|---------|--------|
| 1.1 | `TelegramTransportConfig` — Timeout/Retry SSOT | `infra/transport-telegram/.../TelegramTransportConfig.kt` | ~50 |
| 1.2 | `TelegramTransportModule` — Config-Werte verwenden | `infra/transport-telegram/.../di/TelegramTransportModule.kt` (edit) | ~20 Δ |
| 1.3 | `TelegramSyncService` interface | `core/catalog-sync/.../telegram/TelegramSyncService.kt` | ~30 |
| 1.4 | `DefaultTelegramSyncService` implementation | `core/catalog-sync/.../telegram/DefaultTelegramSyncService.kt` | ~300 |
| 1.5 | Worker-Stub ersetzen | `app-v2/.../work/CatalogSyncOrchestratorWorker.kt` (edit) | ~20 Δ |

### Phase 2: Session & Bootstrap

> **Scope:** `app-v2`, `catalog-sync`  
> **Geschätzter Aufwand:** ~430 Zeilen neuer Code

| # | Aufgabe | Dateien | Zeilen |
|---|---------|---------|--------|
| 2.1 | `TelegramSessionBootstrap` | `app-v2/.../bootstrap/TelegramSessionBootstrap.kt` | ~150 |
| 2.2 | `TelegramChatPreloader` | `core/catalog-sync/.../telegram/TelegramChatPreloader.kt` | ~200 |
| 2.3 | `NxTelegramChatSelectionRepository` interface | `core/model/.../repository/NxTelegramChatSelectionRepository.kt` | ~80 |

### Phase 3: Persistence & Data

> **Scope:** `persistence`, `data-nx`  
> **Geschätzter Aufwand:** ~190 Zeilen neuer Code

| # | Aufgabe | Dateien | Zeilen |
|---|---------|---------|--------|
| 3.1 | `NX_TelegramChatSelection` Entity | `core/persistence/.../obx/NxEntities.kt` (edit) | ~40 Δ |
| 3.2 | `NxTelegramChatSelectionRepositoryImpl` | `infra/data-nx/.../telegram/NxTelegramChatSelectionRepositoryImpl.kt` | ~150 |

### Phase 4: Chat Selection UI

> **Scope:** `feature/settings`  
> **Geschätzter Aufwand:** ~550 Zeilen neuer Code

| # | Aufgabe | Dateien | Zeilen |
|---|---------|---------|--------|
| 4.1 | `ChatSelectionScreen` Composable | `feature/settings/.../ChatSelectionScreen.kt` | ~350 |
| 4.2 | `ChatSelectionViewModel` | `feature/settings/.../ChatSelectionViewModel.kt` | ~200 |
| 4.3 | Navigation-Route + Settings-Eintrag | `app-v2/.../navigation/AppNavHost.kt` (edit) | ~15 Δ |
| 4.4 | Sync-Gate in Worker | `app-v2/.../work/CatalogSyncOrchestratorWorker.kt` (edit) | ~10 Δ |

### Phase 5: Cleanup & Rename

> **Scope:** `telegram-transport`, `data-telegram`  
> **Geschätzter Aufwand:** ~100 Zeilen Δ (Edits/Renames)

| # | Aufgabe | Dateien | Zeilen |
|---|---------|---------|--------|
| 5.1 | Rename `TelegramRemoteId` → `TelegramMessageId` | Transport + Pipeline (repo-weit) | ~30 Δ |
| 5.2 | Rename `resolveRemoteId()` → `resolveMessageMedia()` | `DefaultTelegramClient.kt` | ~10 Δ |
| 5.3 | `ObxTelegramContentRepository` deprecieren | `infra/data-telegram/` | ~10 Δ |
| 5.4 | `PlaybackHintKeys.Telegram.REMOTE_ID` evaluieren | `core/model/PlaybackHintKeys.kt` | ~5 Δ |

### Phase 6: Timeout-Tuning

> **Scope:** `telegram-transport`, `tg_proxy.py`  
> **Geschätzter Aufwand:** ~50 Zeilen Δ

| # | Aufgabe | Dateien | Zeilen |
|---|---------|---------|--------|
| 6.1 | OkHttp-Clients auf Config umstellen | `TelegramTransportModule.kt` (edit) | ~15 Δ |
| 6.2 | Telethon connect timeout + FloodWait cap | `tg_proxy.py` (edit) | ~10 Δ |
| 6.3 | File chunk size erhöhen | `tg_proxy.py` (edit) | ~5 Δ |
| 6.4 | Pagination-Hardening (per-batch timeout) | `TelegramMessageCursor.kt` (edit) | ~20 Δ |

### Phase 7: Verifikation

| # | Aufgabe |
|---|---------|
| 7.1 | Full-Chain Test: App-Start → Bootstrap → Auth → Chats laden → Chat-Selection → Sync → Playback |
| 7.2 | Architektur-Tests aktualisieren (`TelegramIdArchitectureTest`) |
| 7.3 | Build-Verifikation (`:app-v2:assembleDebug` kompiliert) |

---

## 9. Dateien die erstellt werden müssen (Zusammenfassung)

| Datei | Layer | Zeilen (geschätzt) |
|-------|-------|---------------------|
| `infra/transport-telegram/.../TelegramTransportConfig.kt` | Transport | ~50 |
| `core/catalog-sync/.../telegram/TelegramSyncService.kt` | Sync | ~30 |
| `core/catalog-sync/.../telegram/DefaultTelegramSyncService.kt` | Sync | ~300 |
| `core/catalog-sync/.../telegram/TelegramChatPreloader.kt` | Sync | ~200 |
| `app-v2/.../bootstrap/TelegramSessionBootstrap.kt` | Bootstrap | ~150 |
| `core/model/.../repository/NxTelegramChatSelectionRepository.kt` | Model | ~80 |
| `infra/data-nx/.../telegram/NxTelegramChatSelectionRepositoryImpl.kt` | Data | ~150 |
| `feature/settings/.../ChatSelectionScreen.kt` | UI | ~350 |
| `feature/settings/.../ChatSelectionViewModel.kt` | UI | ~200 |
| **Gesamt neue Dateien** | | **~1510** |

---

## 10. Dateien die editiert werden (Zusammenfassung)

| Datei | Art der Änderung |
|-------|------------------|
| `TelegramTransportModule.kt` | Config-Konstanten verwenden |
| `CatalogSyncOrchestratorWorker.kt` | Stub ersetzen + Sync-Gate |
| `AppNavHost.kt` | Neue Route |
| `NxEntities.kt` | Neue Entity |
| `TelegramRemoteId.kt` | Rename → `TelegramMessageId` |
| `DefaultTelegramClient.kt` | Methoden-Rename |
| `tg_proxy.py` | Timeout-Tuning |
| `TelegramMessageCursor.kt` | Pagination-Hardening |
| `TelegramIdArchitectureTest.kt` | Test-Updates |

---

## Referenzen

| Dokument | Zweck |
|----------|-------|
| `contracts/GLOSSARY_v2_naming_and_modules.md` | Naming Rules |
| `contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` | Sync Worker Architecture |
| `docs/v2/TELEGRAM_PIPELINE_REDESIGN_CONTRACT.md` | TelegramSyncService Spec |
| `contracts/TELEGRAM_PARSER_CONTRACT.md` | Telegram Entity Rules |
| `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` | chatId/messageId Schema |
| `contracts/XTREAM_ONBOARDING_CATEGORY_SELECTION_CONTRACT.md` | Category UI Blueprint |
| `.scope/catalog-sync.scope.json` | Scope Guard für Sync |
| `.scope/telegram-transport.scope.json` | Scope Guard für Transport |
