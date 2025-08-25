# m3uSuite – Roadmap (auf Basis des aktuellen Projektstands)

## Grundsätze
- Einmal-anpacken-Prinzip: Settings & DB → Repos/Services → UI.
- Rückwärtskompatibel: EPG/Xtream, Listen/Detail, bestehende Playerpfade bleiben erhalten.
- Profile: Adult vs. Kids (Playerwahl, Untertitel global, Freigaben/Filter, Screen-Time).
- Keine Repos in Modul-Buildfiles; zentrale Repos in `settings.gradle.kts`.

---

## Phase 1 — Fundament & Datenmodell (IST erweitern, keine Brüche)
**Module:** `prefs/SettingsStore.kt`, `data/db/AppDatabase.kt`, `data/db/Entities.kt`, `settings.gradle.kts`

- [ ] `SettingsStore.kt`: neue Flows
    - `player_mode` = `ask|internal|external`
    - `rotation_locked` (bool)
    - `header_collapsed` (bool)
    - Untertitel global:
        - `subtitle_scale` (Float/Int)
        - `subtitle_fg_color` (ARGB)
        - `subtitle_bg_color` (ARGB)
        - `subtitle_fg_opacity_pct` (0–100)
        - `subtitle_bg_opacity_pct` (0–100)
    - Profile/PIN:
        - `current_profile_id` (Adult/Kid)
        - `adult_pin_set` (bool)
        - `adult_pin_hash` (String, Hash)
    - Defaults: Text 90 % Opacity, Hintergrund 40 %.

- [ ] DB: Tabellen ergänzen (in **`Entities.kt`** + Migrationen in **`AppDatabase.kt`**)
    - `profiles (id, name, type {adult,kid}, avatar_path, created_at, updated_at)`
    - `kid_content (kid_profile_id, content_type {live,vod,series}, content_id, UNIQUE(kid_profile_id, content_type, content_id))`
    - `screen_time (kid_profile_id, day_yyyymmdd, used_minutes, limit_minutes, UNIQUE(kid_profile_id, day_yyyymmdd))`
    - Resume-UNIQUE: VOD per `media_id`, Serien per `episode_id`.
    - Seeding: Wenn keine Profile → Adult anlegen, `current_profile_id` auf Adult.

- [ ] `settings.gradle.kts`: stabile Repos (ohne Incubating-Flags).

**Abnahme:** Settings-Keys les-/schreibbar per Flow, Migrationen idempotent, Adult als Startprofil.

---

## Phase 2 — ProfileGate & Profilverwaltung
**Module:** `MainActivity.kt` (Nav-Entry), **neu** `ui/auth/ProfileGate.kt`, **neu** `ui/profile/ProfileManagerScreen.kt`, Link in `ui/screens/LibraryScreen.kt` (nur Navigation)

- [ ] `ProfileGate.kt` (neu):
    - Startscreen mit: „Ich bin ein Kind“ (Avatare der Kids) und „Ich bin ein Erwachsener“ (PIN).
    - Kind-Tap ⇒ `current_profile_id = kidId` ⇒ Library.
    - Adult-Tap ⇒ PIN (Setup, falls nicht gesetzt) ⇒ `current_profile_id = adultId` ⇒ Library.
    - Optional: „Zuletzt genutztes Profil merken“ (Setting), Gate bleibt erreichbar.

- [ ] `ProfileManagerScreen.kt` (neu, nur Adult):
    - Kinder anlegen/umbenennen/löschen.
    - Avatar Aufnahme/Picker → Datei unter `/files/avatars/{kidId}/avatar.jpg`, Thumbnail erzeugen.
    - Screen-Time-Limit je Kind (Tageslimit, Min/Max/Step).

- [ ] `MainActivity.kt`:
    - Gate vor Library vorschalten, Routing nach Profil.

**Abnahme:** Mehrere Kids inkl. Avatare/Limit, Gate erscheint und routet korrekt.

---

## Phase 3 — Repositories & Filter/Limit-Logik
**Module:** `data/repo/PlaylistRepository.kt`, `data/repo/XtreamRepository.kt`, `data/db/*`

- [ ] Freigabe-API:
    - `allow(kidId, type, contentId)`, `disallow(...)`, `isAllowed(...)`, Bulk-Varianten.
    - Indizes auf `kid_content`.

- [ ] Profil-Filter:
    - Adult → unverändert (bestehende Queries).
    - Kid → Queries erweitern: nur Einträge, die in `kid_content` für dieses Kid freigegeben sind.

- [ ] Screen-Time:
    - `remainingMinutes(kidId)` (legt „heute“ an, falls fehlt).
    - `tickUsageIfPlaying(kidId, deltaSecs)` (nur bei Play).
    - Tages-Reset (lokale TZ) vor jedem Tick & bei Appstart.

- [ ] Resume:
    - Upsert/Get/Delete → UNIQUE je Inhalt greifen.

**Abnahme:** Repo-Methoden liefern gefilterte Resultate; Limit-Funktionen arbeiten nachvollziehbar.

---

## Phase 4 — Player-Grundlogik (UI unverändert halten)
**Module:** `player/PlayerChooser.kt` (neu klein), `player/InternalPlayerScreen.kt`

- [ ] `PlayerChooser`: Kids ⇒ immer Internal (Extern/Ask ignorieren); Adults ⇒ Setting `player_mode` respektieren.
- [ ] `InternalPlayerScreen.kt`:
    - Vor Start (Kids): `remainingMinutes` prüfen, bei 0 ⇒ Blockdialog.
    - Während Wiedergabe (Kids): zyklisch `tickUsageIfPlaying`; bei 0 ⇒ Pause/Stop + Hinweis.
    - Untertitel: globale Settings (`SettingsStore`) anwenden.
    - Live **nicht** in Resume aufnehmen; bestehende Logik beibehalten.
    - Keine Untertitel-/Player-Settings-UI, wenn aktives Profil Kid.

**Abnahme:** Kids: nur internal & Zeitlimit greift; Adults: unverändert mit globalen Untertiteln.

---

## Phase 5 — SettingsScreen (nur Adult sichtbar)
**Module:** `ui/screens/SettingsScreen.kt` (neu), `prefs/SettingsStore.kt` (Konsum)

- [ ] Sektionen:
    - Player: ask|internal|external
    - Untertitel: Größe, Textfarbe, Hintergrundfarbe, Opacity Text/BG
    - Wiedergabe: Autoplay nächste Folge (Serie), Haptik
    - UI: Header default „eingeklappt“, Rotation-Lock
    - Profile: Link „Profile verwalten…“ (öffnet `ProfileManagerScreen`)

- [ ] Sichtbarkeit: Settings nur im Adult-Profil.

**Abnahme:** Änderungen persistieren und wirken sofort (z. B. Untertitel).

---

## Phase 6 — Untertitel-On-Screen-Menü (nur Adult)
**Module:** `player/InternalPlayerScreen.kt`

- [ ] Overlay (CC-Button nur für Adult):
    - Spurwahl (falls mehrere Spuren)
    - Größe/Farben/Opacity, „Als Standard speichern“ ⇒ `SettingsStore`
    - Sofort anwenden; TV-Fokus beachten.

**Abnahme:** Menü nur Adult sichtbar; Änderungen wirken sofort; Persist optional.

---

## Phase 7 — Universal-Header & Rotation-Lock
**Module:** `ui/screens/LibraryScreen.kt` (+ ggf. `ui/components/CollapsibleHeader*.kt` neu)

- [ ] Collapsible Header (sticky Pfeil) in Portrait & Landscape:
    - Pfeil „hoch“ ⇒ Header slide-up; Pfeil bleibt sichtbar (zeigt dann „runter“).
    - State in `SettingsStore.header_collapsed` global persistieren.
- [ ] Rotation-Lock-Button in TopBar spiegeln.

**Abnahme:** Togglen mit Animation, Zustand bleibt über Appstart/Rotation.

---

## Phase 8 — LibraryScreen: Profil-Filter & Freigaben
**Module:** `ui/screens/LibraryScreen.kt`, ggf. `ui/common/*` (Kontextmenü)

- [ ] Adult: „Für Kind(er) freigeben…“ (Multi-Select).
- [ ] Adult: „Aus Kinderprofil entfernen…“.
- [ ] Kid: zeigt nur freigegebene Inhalte (Repo-Filter).
- [ ] „Weiter-schauen“: dedupliziert; Klick = Play; Long-Press = entfernen.

**Abnahme:** Sichtbare Wirkung der Freigaben ohne Neustart; deduplizierte Weiter-schauen-Row.

---

## Phase 9 — DetailScreens (Live/VOD/Series)
**Module:** `ui/screens/LiveDetailScreen.kt`, `VodDetailScreen.kt`, `SeriesDetailScreen.kt`

- [ ] Playerwahl via `PlayerChooser` (Kids intern erzwingen).
- [ ] Freigabe-Menü wie in Library (nur Adult).
- [ ] Live: weiter unverändert; nicht in Resume.

**Abnahme:** Konsistente Startpfade; keine Regressionen bei EPG/Details.

---

## Phase 10 — Resume finalisieren
**Module:** `data/db/Entities.kt` (UNIQUE), Repo-Methoden, Startseite/Library

- [ ] UNIQUE je Inhalt erzwungen; Upsert statt Duplikate.
- [ ] Interaktionen: Klick = Resume; Long-Press = Entfernen.
- [ ] Serien-Resume pro Episode; VOD pro `media_id`; Live ignorieren.

**Abnahme:** stabil, dedupliziert, erwartetes Abspielverhalten.

---

## Phase 11 — Feinschliff & QA
- [ ] Berechtigungen: Kamera nur bei Avatar-Nutzung; ansonsten Photo Picker.
- [ ] Zeitzone: täglicher Reset (Europe/Berlin) — auch nach App-Sleep.
- [ ] A11y & UI: TalkBack-Labels, Untertitel-Kontrast (Preset-Palette).
- [ ] Performance: Poster-Caching, Scroll-Smoothness, Player-State-Transitions.
- [ ] Fehlerfälle: Netz down beim EPG, kaputte Poster-URLs ⇒ Platzhalter.

**Testmatrix (Auszug):**
- ProfileGate: PIN-Setup, falsche PIN, mehrere Kids/Avatare
- Screen-Time: zählt nur bei `playWhenReady==true`, Reset 00:00
- Player: Kids intern, Adult CC-Overlay
- Header: Toggle & Persist in beiden Orientierungen
- Settings: wirken sofort; Kids sehen keine Settings
- Freigaben: Kid-Sichten unterscheiden sich
- Resume: keine Duplikate; Remove per Long-Press

---
