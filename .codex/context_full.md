CONTEXT FOR SOLVER — Telegram Integration Fix (Phase-2), based on 4 incoming diffs

Goal
- Stabilize and complete the Telegram integration end-to-end: correct message dating, reliable caption/filename parsing, proper VOD vs. Series partitioning for UI rows, and clear “TG” source tagging on tiles.
- Integrate the four provided diffs where useful, fix issues they contain, and adapt them to our architecture (ObjectBox-first, TDLib service process, Settings gating, centralized rows).
- Produce one coherent PR that adds two new modules and patches two existing ones without breaking current Xtream or UI flows.

Repository state (relevant)
- Telegram infrastructure exists: dedicated service process (TelegramTdlibService), reflection bridge (TdLibReflection), ObjectBox entity ObxTelegramMessage with fields (chatId, messageId, fileId, uniqueId, caption, supportsStreaming, date, localPath, durationSecs, mimeType, width/height, thumbFileId).
- UI rows already support Telegram tiles and badges (blue “T”) according to recent CHANGELOG entries; per-chat rows in Library and Start are planned/partially present.
- Gating: settings.tg_enabled must be true AND TDLib must be authenticated before the Telegram worker/service or DataSource do work. 
- ID bridging: live=1e12+, vod=2e12+, series=3e12+. Telegram is not formally assigned but 5e12+ is free.

Incoming diffs — quick assessment
1) New file: app/src/main/java/com/chris/m3usuite/telegram/TelegramHeuristics.kt
- Intent: Best-effort parser for captions/filenames, extracting normalized title, year, SxxEyy, language, quality.
- Pros:
  - Lightweight, non-throwing
  - Good base token sets (quality/language/junk)
  - Reasonable cleaning pipeline (replace _, ., trim, drop junk)
- Issues to fix before use:
  - Regex escaping bugs (Kotlin String requires escaping backslashes or raw strings). Current literals like "\d", "\s", "\b" are wrong and will not match. Use raw strings """...""" or double-escape (\\d, \\s, \\b).
  - Language detection is over-broad (upper.contains("DE") also matches “MODE”); we need token boundaries or bracket/delimiter checks. Better approach: scan for [DE]/(DE)/ DELIMITED tokens, or split tokens and compare upper token-in-set. MULTI/DUAL are fine as top-level flags; GERMAN/ENGLISH tokens are okay; “DE”/“EN” must be treated as whole tokens.
  - Quality regex is fine semantically but use raw string and ensure case-insensitive + word boundaries are honored on token basis.
  - Clean title: ensure we strip bracket pairs intelligently (avoid wiping meaningful text) and collapse repeated separators.
  - Optional: Recognize 1x02 or E02 patterns for series in addition to SxxEyy, and “Folge/Staffel” DE tokens (low-priority).
- Verdict: Adopt with fixes. This is the canonical class to be used in service indexing and UI mapping for Telegram rows.

2) New file: app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt
- Intent: Provide per-chat, newest-first lists of Telegram items mapped to MediaItem for UI rows (separate VOD vs Series).
- Pros:
  - Uses ObjectBox (ObxStore, ObxTelegramMessage)
  - Maps to model.MediaItem with source="TG" and tg* fields filled
  - Generates encoded IDs (5e12 + obxId) — good for stable UI keys
  - Applies parsing for clean display titles
- Issues to fix before use:
  - Kotlin generics missing: all List return types must be List<MediaItem>.
  - Unused SettingsStore param; either remove or gate with it (tg_enabled/auth).
  - Type partitioning omitted: recentByChatInternal fetches all messages and assigns type param blindly. Must filter by heuristics:
    - series row: keep messages where parse(..) detects season+episode
    - vod row: keep messages where no SE pattern detected
    - prevents duplicates appearing in both rows
  - Performance: parsing every row per call is fine for small limits (60 default). If we add paging later, we can precompute a “kind” flag or cache parse results (not required now).
  - Date: uses ObxTelegramMessage.date but the service currently sometimes used “now()” fallback; we will fix service to persist real message dates to support correct ordering.
  - ID domain: 5e12 offset must be documented and shared to avoid collisions.
  - Poster/thumb: currently returns logo=null/posters=null; we may leverage thumbFileId later to show thumbs. Not in scope for this PR beyond ensuring fields are persisted.
- Verdict: Adopt with fixes (types, gating, partitioning, ID offset constant, optional date null fallback).

3) Patch existing: app/src/main/java/com/chris/m3usuite/telegram/service/TelegramTdlibService.kt
- Intent: Pass message date into indexMessageContent and persist correct date; patch update handlers to include date.
- Pros:
  - Fixes incorrect dating (“now()” fallback) by using TdLibReflection.extractMessageDate on message objects.
  - Keeps UpdateFile handling unchanged.
- Issues to fix before use:
  - API shape: indexMessageContent signature changes; ensure all internal call sites are updated. Provide a default param (messageDate: Long? = null) to minimize breakage inside the class. All 3 call sites should pass a date:
    - UpdateMessageContent handler
    - UpdateNewMessage handler
    - History fetch code paths (if they funnel through the same method)
  - Fallback ordering: messageDate ?: extractMessageDate(content) ?: (now). This is OK but ensure extractMessageDate(content) does not throw when content is null — the code currently does extractMessageDate(content ?: Any()) which is odd; better to check nulls before calling.
  - Verify TdLibReflection has extractMessageDate(obj) for both message and content nodes (adapt if not).
- Verdict: Adopt with cautious signature change, proper null-safety, and tests. This is critical to make “newest” ordering meaningful in UI Telegram rows.

4) Patch existing: app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt
- Intent: Add “T” blue badge overlay for Telegram-sourced items.
- Pros:
  - Clear marker for TG origin
- Risks:
  - We already introduced TelegramRow/TelegramTileCard with a “blue T” badge (per CHANGELOG). Double-tagging must be avoided. We must centralize TG badge rendering in a single shared tile component or wrap it with a feature flag.
- Verdict: Integrate conditionally. If HomeRows already renders TG tiles via shared component (PosterCard/TelegramTileCard), skip this ad-hoc overlay. Otherwise, extract this overlay into a reusable small composable and call it consistently from our tile components (PosterCard, TelegramTileCard). The tile should not render multiple T badges.

Files to create/update
New (missing)
- app/src/main/java/com/chris/m3usuite/telegram/TelegramHeuristics.kt
  - Implement with corrected regex escaping and boundary-safe language detection.
  - Provide a small unit-test suite (see test plan).

- app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt
  - Implement with:
    - Return types List<MediaItem>
    - SettingsStore gating: early return empty when tg_enabled=false
    - Optional: require AUTH state or allow stale rows; at minimum obey tg_enabled
    - Per-type filtering using heuristics (series vs vod)
    - Null-safe date fallback; newest-first sorting by (date desc, messageId desc)
    - Encoded TG media ID offset constant (5e12L)

Existing (to patch)
- app/src/main/java/com/chris/m3usuite/telegram/service/TelegramTdlibService.kt
  - Change indexMessageContent signature to include messageDate: Long? (default null).
  - Update all call sites to pass dates from UpdateNewMessage/UpdateMessageContent/explicit fetch code.
  - Inside, use date = messageDate ?: TdLibReflection.extractMessageDate(msgOrContent) ?: now.
  - Ensure no reflection call throws when given nulls; guard with try/catch as in other parts of the class.

- app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt
  - Audit existing TG badge usage. If already present (TelegramTileCard with badge), do not duplicate.
  - If missing on some tile types, refactor to a shared badge overlay composable (TelegramSourceBadge) and add to the standard tile.
  - Respect TV focus visuals and not clip halos.

Integration points and architectural alignment
- Gating
  - Repository methods should respect settings.tg_enabled. The UI already gates chat picker and sync; rows should not attempt to read from TDLib when disabled. Reads from OBX are fine even when TDLib is disabled (they render cached content), but we prefer to show TG rows only when tg_enabled is ON to reduce confusion.
  - AUTH state gating is stronger. However, for display of previously indexed messages, UI can render even if service is not running.

- Data source
  - No change required in streaming for this PR. The TelegramHeuristics is used only at indexing-time and mapping-time. The TelegramTdlibDataSource remains as-is.

- OBX entities
  - ObxTelegramMessage already has date/duration/mime/thumb id. Service fix ensures correct date is persisted. No schema changes required.

- UI rows
  - Library and Start: Pull per-chat rows via TelegramContentRepository.recentVodByChat and recentSeriesByChat (wired in viewmodels/adapters).
  - Ensure sorting uses date desc + messageId desc tie-breaker.

- ID offsets
  - Define a shared constant (e.g., MediaIdDomain.TELEGRAM = 5_000_000_000_000L) so routing and detail screens can filter/identify TG items if needed. No behavior change required when opening TG items (player routes tg:// URIs already).

Specific adaptations required for our project (beyond diffs)
- Regex correctness: Use raw strings and tokenized matching to avoid false positives (“DE” in “MODE”).
- Type partitioning: Only list VOD vs Series in their respective rows. Without this, both rows show the same content.
- TG badge centralization: Use a single source for TG visual marking, not inline snippets across files.
- Date persistence: Adopt the service patch and test UpdateNewMessage + UpdateMessageContent + history backfill all store the real TD date.
- Settings gating: Return empty lists when tg_enabled=false; rows can be hidden by their callers when lists are empty.
- Unit tests: Add tests for heuristics and for TdLib date extraction mapping to OBX.

Risks and mitigations
- Regex regressions
  - Risk: Incorrect escaping or overly aggressive token stripping could blank titles.
  - Mitigation: Unit tests for typical captions and token patterns (DE/EN, 1080p/2160p, x265, WEB-DL, REMUX, etc.). Keep a conservative junk list and assert title is never blank; fallback to raw caption when cleaning empties out.

- Duplicate items across VOD/Series rows
  - Risk: If heuristics fail to detect SxxEyy correctly, items may appear in VOD rather than Series or vice versa.
  - Mitigation: Allow only strict SxxEyy (and optionally 1x02) to mark Series. Everything else defaults to VOD. This is consistent with user expectations for Telegram channels.

- Date correctness
  - Risk: If extractMessageDate fails for some TDLib update types, items may jump to top as “now”.
  - Mitigation: Use provided message object to get date; only fall back to content; as last resort, now(). Add logging to the service when date resolution falls back.

- TG badge duplication
  - Risk: Multiple “T” badges if both tile and row add overlays.
  - Mitigation: Centralize TG badge in a single tile component. If we must keep a HomeRows overlay, guard it behind a check that the tile doesn’t already add one.

- Performance
  - Risk: Parsing at mapping time on large queries.
  - Mitigation: Default limit is small (≤60). If we expand, consider caching parse results or computing a “kind” flag during indexing (not in this PR).

- UI stability (TV focus)
  - Risk: Adding overlays can interfere with focus overlays or clip bounds.
  - Mitigation: Keep badge within poster area and respect current focus halo z-index and padding.

Assumptions
- ObxTelegramMessage fields exist as documented (id, chatId, messageId, fileId, caption, date, durationSecs, mimeType, thumbFileId). No schema migration needed.
- TdLibReflection has extractMessageDate(obj) that can read from TdApi.Message and likely from message content nodes. If not present for content, we add it or pass only from the message object.
- The app already has Screen/Home rows wired for TG per-chat lists or placeholders for them; integrating the repository will be straightforward.
- SettingsStore exposes tg_enabled. Authentication state can be read from service client if we need to conditionally hide UI elements, but repository methods only need to check tg_enabled.

Step-by-step solver-prep plan (what to implement in the PR)
1) Add TelegramHeuristics (new file)
- Implement the parser with:
  - Raw-string regexes ("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""", """(?i)\bS(\d{1,2})[ ._-]*E(\d{1,2})\b""", etc.)
  - Optional secondary pattern for 1x02 (“(?i)\b(\d{1,2})x(\d{1,2})\b”) treated as season/episode
  - Quality tokens consistent with the provided list; consider both standalone tokens and folder-like tokens
  - Language detection based on tokenization and bracketed tags ([DE], (DE), trailing flags), not just contains()
  - Junk token stripping and safe title fallback to raw on empty
- Add unit tests:
  - “Movie.Title.2023.1080p.GERMAN.x265” → title “Movie Title”, year 2023, lang de, quality 1080p
  - “Show.S01E02.WEB-DL.ENG” → title “Show”, season 1, episode 2, lang en, quality web-dl
  - “Serie 1x03 HDR DV MULTI” → season 1, episode 3, lang multi, quality hdr or dv
  - “Film (1999) [DE] Remux” → title “Film”, year 1999, lang de, quality remux
  - Non-matching captions → no crash, sensible title

2) Add TelegramContentRepository (new file)
- Provide:
  - recentVodByChat(chatId, limit, offset): List<MediaItem>
  - recentSeriesByChat(chatId, limit, offset): List<MediaItem>
  - Internally: early-exit empty when chatId==0 or tg_enabled=false
  - Query ObxTelegramMessage by chatId, sort date desc (nulls last) then messageId desc
  - For each row, parse caption; filter to type:
    - Series: parsed.season!=null && parsed.episode!=null
    - VOD: else
  - Map to MediaItem:
    - id: TG_OFFSET + obxRow.id
    - name: title + “ SxxEyy” (for Series) + optional “ (year)”
    - sortTitle = base title lowercased
    - source = “TG”
    - tgChatId/messageId/fileId; durationSecs; plot=caption; year=parsed.year
  - Document TG_OFFSET (5_000_000_000_000L) and ensure it doesn’t collide
- Add minimal unit test for type partitioning on a mocked list (if feasible).

3) Patch TelegramTdlibService
- Change indexMessageContent(chatId, messageId, content, messageDate: Long? = null)
- Update call sites:
  - UpdateMessageContent → pass TdLibReflection.extractMessageDate(msg)
  - UpdateNewMessage → pass TdLibReflection.extractMessageDate(obj)
  - Any explicit message processing paths → pass extracted date
- Inside indexMessageContent:
  - Resolve date = messageDate ?: TdLibReflection.extractMessageDate(content) ?: (System.currentTimeMillis()/1000)
  - Persist ObxTelegramMessage.date accordingly
- Add safe try/catch around reflection calls (as elsewhere in service), no throws.
- Log a warning when both messageDate and content-date are null and we fallback to now.

4) UI badge integration in HomeRows (and/or central tile)
- Inspect current HomeRows/PosterCard/TelegramTileCard:
  - If a TG badge is already present centrally, do not add another overlay; ensure it shows for any tile from source="TG".
  - If missing in some tiles, extract a TelegramSourceBadge composable and place it in the shared tile.
  - Ensure overlay respects focus halos (zIndex) and TV DPAD visually.

5) Wire repository to UI (if not already)
- Wherever Library/Start build Telegram rows per chat:
  - Use TelegramContentRepository.recentVodByChat for the VOD row
  - Use TelegramContentRepository.recentSeriesByChat for the Series row
- Gate rows on tg_enabled and selected chat presence; hide empty rows.

6) Documentation and constants
- Add a comment in model or a shared constants file for TG_OFFSET so future code can decode/recognize TG IDs if needed.
- Update docs:
  - AGENTS.md: mention Telegram Heuristics, TG ID offset, and that per-chat rows use this repo.
  - CHANGELOG.md: summarize “fix: Tdlib message date is now persisted; feat: Telegram heuristics and per-chat rows partition series/vod correctly; UI: blue T badge centralized”
  - ROADMAP.md: move the relevant Telegram row/badge tasks to completed.

7) Test plan
- Unit tests for TelegramHeuristics (as above).
- Manual verification:
  - Authenticate Telegram; select chat(s) in Settings; trigger sync; confirm TG rows appear under Library/Start (VOD and Series per chat).
  - Check ordering: newest messages appear first; editing dates (by adding new content to a chat) pushes items to top.
  - Check series detection: S01E02 items only appear in Series row; movies only in VOD row.
  - Check badge: blue T shows once per tile; no duplication; TV focus looks correct.
  - Playback: TG item opens via tg:// using existing DataSource; seek works; localPath updates in OBX on download completion.
- Edge checks:
  - tg_enabled=false: repository returns empty; UI rows hidden.
  - No caption provided: title falls back to “Telegram” or “Telegram <messageId>”, no crash.
  - Non-latin characters: heuristics keep title; no throw.

8) Risk handling and rollback
- If heuristics cause regressions, we can scope them to repository only (do not alter service indexing), making rollback easy by replacing parse() calls with a trivial parser. Keep service date changes as they are safe and isolated.
- If TG badge conflicts with existing tile layers, we can disable overlay via a feature flag and iterate.

File targets summary (existing vs missing)
- Missing (to add):
  - app/src/main/java/com/chris/m3usuite/telegram/TelegramHeuristics.kt
  - app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt
- Existing (to patch):
  - app/src/main/java/com/chris/m3usuite/telegram/service/TelegramTdlibService.kt
  - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt
- Likely already present (no code changes required, but referenced):
  - com.chris.m3usuite.data.obx.ObxStore / ObxTelegramMessage
  - com.chris.m3usuite.telegram.TdLibReflection (must expose extractMessageDate on message; optional content fallback)
  - com.chris.m3usuite.model.MediaItem
  - prefs/SettingsStore (tg_enabled)

Why this solves “alle Probleme auf einmal”
- Wrong/unstable dates: service now stores TD message date; newest-first ordering works.
- Missing Telegram title normalization: heuristics produce clean, human-readable names and year/quality/language fields for UI.
- VOD vs Series duplication/mixture: repository filters by season/episode presence; rows show only relevant items.
- Inconsistent TG badges: centralized overlay ensures consistent “T” marking without duplicates.
- Compatibility and safety: no schema changes; isolated classes; no effect on Xtream flows; gating respected.

Acceptance criteria
- After login and sync, Library/Start render Telegram rows per selected chat:
  - VOD row shows only movie-like items (no SxxEyy), titled cleanly, sorted newest-first.
  - Series row shows only episode-like items (SxxEyy or 1x02), titled “Title SxxEyy”, sorted newest-first.
  - Tiles show a single blue “T”.
- Debug logs show that UpdateNewMessage/Content assign real message dates and OBX entries reflect those timestamps.
- Heuristics unit tests pass; no crashes on odd captions; blank input produces a non-empty title fallback.

Scope control
- No changes to streaming/DownloadFile logic or DataSource; no new workers; no DB migrations; no dependency upgrades.
- Changes are confined to: one service method signature, two new Kotlin files, one UI overlay check.

This is the complete context and plan for the solver to produce a single, cohesive PR that integrates the provided diffs, fixes their issues, and aligns them with our codebase and architecture.