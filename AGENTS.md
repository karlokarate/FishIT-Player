# Codex Agent Rules – m3uSuite (Android/Kotlin)

- Zusätzlich immer AGENTS_NEW.md lesen. sie bietet einen Komplettüberblick
- zusätzlich zur Roadmap immmer auch ROADMAP_NEW.md beachten
- Regelmäßiges update und Anpassung von Agents.md und Roadmap.md Beide Dateien stets pflegen und beide Roadmaps zu einer immer aktuellen zusammenfassen.
- Ab jetzt auch Changelog schreiben zu allen Vorgängen. Initial für Changelog einmal alle merges im repo durchgehen und erstes vollständiges Changelog anhand der Beschreibungen der commits erstellen 
## Build & Test
- JDK **17**, Build via Gradle Wrapper.
- Initial: `./gradlew --version`
- Build: `./gradlew build`
- Tests: `./gradlew test`
- (Optional, falls konfiguriert) Lint/Format: `./gradlew lint ktlintFormat detekt`

## Projektstruktur (IST)
- Modul: `app`
- Manifest: `app/src/main/AndroidManifest.xml`
- Package Root: `app/src/main/java/com/chris/m3usuite`
- Core:
    - `core/http/HttpClient.kt`
    - `core/m3u/M3UParser.kt`
    - `core/xtream/XtreamClient.kt`, `XtreamConfig.kt`, `XtreamModels.kt`
- Datenbank:
    - `data/db/AppDatabase.kt`
    - `data/db/Entities.kt` (konsolidierte Entities/Views, keine getrennten MediaItem/Episode-Dateien)
- Repositories:
    - `data/repo/PlaylistRepository.kt`
    - `data/repo/XtreamRepository.kt`
- Preferences:
    - `prefs/SettingsStore.kt`
- Player:
    - `player/InternalPlayerScreen.kt`, `player/ExternalPlayer.kt`
- UI Screens:
    - `ui/screens/LibraryScreen.kt`, `LiveDetailScreen.kt`, `VodDetailScreen.kt`, `SeriesDetailScreen.kt`, `PlaylistSetupScreen.kt`

## Policies (Do/Don't)
- **Do:** Diffs anzeigen, **erst nach Freigabe** anwenden.
- **Do:** Bestehendes Verhalten (EPG/Xtream, Playerpfade, Listen/Detail) erhalten.
- **Don't:** nichts unter `.gradle/`, `.idea/`, `app/build/` verändern.
- **Don't:** Dependencies upgraden, außer wenn zwingend für Build-Fix.

## Profilregeln (Kontext für künftige Tasks)
- Adult/Kids:
    - Adult: Playerwahl (ask/internal/external), CC-Overlay sichtbar.
    - Kids: immer Internal Player, keine Player-/CC-UI, nur freigegebene Inhalte, Screen-Time Limit.
- Live wird nicht in Resume aufgenommen.

## Typische Aufgaben
- „Baue & teste Projekt; behebe offensichtliche Fehler; zeige Diffs; wende nur nach Freigabe an.“
- „Implementiere ProfileGate + ProfileManager nach Roadmap Phase 2; Navigation in `MainActivity` ergänzen.“
- „Repos um Freigaben/Filter/Screen-Time erweitern (Phase 3), ohne bestehende Abrufe zu brechen.“
- „PlayerChooser (neu) + Limitlogik im `InternalPlayerScreen` (Phase 4), CC-Settings aus `SettingsStore` anwenden.“
- „SettingsScreen (nur Adult) nach Phase 5; State live anwenden.“

## Umgebung & Ausführung
- WSL/Ubuntu empfohlen (Sandbox stabil, Netzwerkzugriff für Gradle ok).
- Netz: erlaubt (Dependencies laden).
- Unit-Tests priorisieren; UI-/Instrumented-Tests nur bei gestarteten Emulatoren.

