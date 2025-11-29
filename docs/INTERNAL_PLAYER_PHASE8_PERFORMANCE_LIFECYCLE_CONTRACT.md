

INTERNAL PLAYER – PHASE 8 CONTRACT

Performance, Lifecycle & Stability for Unified PlaybackSession + MiniPlayer

Applies To: SIP Player, MiniPlayer, Global TV Input, Background Workers

Legacy InternalPlayerScreen: Reference-Only (MUST remain untouched)


---

1. Purpose

Phase 8 ensures that the unified PlaybackSession + In-App MiniPlayer behave robustly and efficiently under real-world conditions:

App background/foreground

Rotation & configuration changes

Process death / Activity recreation

Background workers (Xtream, Telegram, DB, EPG)

Memory pressure & resource reuse


The goal is to make playback stutter-free, leak-free, and resilient,
while keeping performance predictable and testbar.


---

2. Non-Goals

Phase 8 does not:

Introduce new user-facing features (no new buttons, no new flows)

Change the Phase 7 UX semantics (MiniPlayer, Resize, Focus Toggle etc.)

Extend TV input or FocusKit beyond what Phase 6/7 defined

Replace or modify the legacy InternalPlayerScreen


Phase 8 is strictly about stability, performance and lifecycle hygiene.


---

3. Core Principles

1. One Playback Session per app

No second ExoPlayer / second player instance for SIP or MiniPlayer.



2. Warm Resume, Cold Start only when necessary

Lifecycle events dürfen PlaybackSession/ExoPlayer nicht unnötig neu bauen.



3. Playback-Aware Resource Usage

Worker/Network/DB/CPU-Intensive Tasks drosseln, wenn Playback aktiv ist.



4. No UI Jank

Keine Blockierungen auf dem Main-Thread, keine unnötigen Recomposition-Bursts.



5. Full Observability

Fehler und Performance-Symptome sind test- und debugbar, nicht „magisch“.





---

4. PlaybackSession Lifecycle Contract

4.1 Ownership & Lifetime

PlaybackSession hält die einzige ExoPlayer-Instanz für die SIP-Welt:

Full Player

MiniPlayer

Optional: System PiP Binding


PlaybackSession darf nicht direkt von Activity/Fragment/View gebaut oder destroyed werden.


4.2 Lifecycle States

PlaybackSession must maintain an internal state machine, z. B.:

enum class SessionLifecycleState {
    IDLE,        // no media loaded
    PREPARED,    // media loaded, ready to play
    PLAYING,     // actively playing
    PAUSED,      // paused but retained
    BACKGROUND,  // app in background, still allowed to play
    STOPPED,     // playback stopped, resources mostly freed
    RELEASED     // ExoPlayer released, session not usable
}

4.3 Lifecycle Rules

onResume() (App foreground)

If SessionLifecycleState in {PREPARED, PLAYING, PAUSED, BACKGROUND}:

Re-bind UI surfaces (PlayerSurface, MiniPlayerOverlay)

Do not recreate ExoPlayer



onPause()

If playing video and device allows background audio:

Optional: keep playing (BACKGROUND)


Else:

Pause playback; stay in PREPARED or PAUSED



onStop()

Should not immediately release ExoPlayer

Session goes to BACKGROUND only when:

No UI is bound

Playback is still active (e.g. audio)



onDestroy()

Only release ExoPlayer when:

No route wants the session anymore (no full player, no mini, no PiP)

SessionLifecycleState == STOPPED




4.4 Rotation / Configuration Changes

Rotation / configuration change (e.g. locale, UI mode) MUST NOT:

Reset playback position

Reset aspect ratio

Reset subtitle/audio track selection


UI components must re-bind to the existing PlaybackSession, not recreate it.



---

5. Navigation & Backstack Stability

5.1 Full ↔ Mini ↔ Home

Full → Mini

PIP Button (UI) → MiniPlayerManager.enterMiniPlayer(fromRoute, mediaId, indices)

SIP Player screen removed (popBackStack)

PlaybackSession unchanged


Mini → Full

Expand/Maximize → navigate to full player route derived from returnRoute/mediaId

MiniPlayerState.visible = false


EXIT_TO_HOME (Double BACK)

From any screen:

Single BACK: normal overlay/stack navigation

Double BACK within threshold: TvAction.EXIT_TO_HOME → Start/Home route

MiniPlayer: bleibt sichtbar, wenn Playback läuft (je nach Contract).




5.2 Process Death & State Rehydration

Auf Process-Death muss der Player (optional) so rehydratisierbar sein, dass:

Zuletzt geschaute MediaId + Position persistiert sind

Optional: Nutzer erhält „Continue watching from X:XX?“


PlaybackSession darf nie „half-initialized“ übrig bleiben (keine Zombie-Player).



---

6. System PiP vs In-App MiniPlayer

6.1 In-App MiniPlayer

Vollständig in Phase 7 definiert.

NICHT mit enterPictureInPictureMode() gekoppelt.

Wird nur durch Actions in deiner UI/TV Input Pipeline gesteuert.


6.2 System PiP (Phone/Tablet Only)

May be triggered by:

Home button

Recents

OS events (auto-enter PiP for video activities)


Phase-8 Zusatzregeln:


1. System PiP darf nie vom PIP-Button im UI ausgelöst werden.


2. System PiP darf nicht aktiviert werden, wenn:

MiniPlayer sichtbar ist

Kids Mode diese Funktion verhindern soll (optional)



3. Beim Rückkehren aus System PiP:

PlaybackSession muss re-ge-bound werden (Full oder Mini)

Position, Tracks, Aspect bleiben erhalten





---

7. Background Workers & Playback Priority

7.1 PlaybackPriority

Introduce:

object PlaybackPriority {
    val isPlaybackActive: StateFlow<Boolean> // derived from PlaybackSession.isPlaying && SessionLifecycleState
}

7.2 Worker Behavior

Xtream, Telegram, EPG, DB-Prozesse, Log-Upload:

Wenn isPlaybackActive == true:

Worker müssen:

ihre Task-Rate drosseln

keine langen CPU/IO-Bursts erzeugen


Praktisches Beispiel:

delay(BACKGROUND_THROTTLE_MS) zwischen heavy network calls

keine großvolumigen DB-Migrationen während Playback





7.3 Threading & Main-Thread Rules

DB & Netzwerk-Arbeit immer in Dispatchers.IO / geeigneten Worker-Scope.

Kein Netzwerk/IO-Aufruf im Compose-Composable selbst.

Kein schwerer Code in onDraw, drawWithContent, graphicsLayer.



---

8. Memory & Resource Management

8.1 ExoPlayer

ExoPlayer darf nur durch PlaybackSession erstellt und released werden.

Activity/Composables dürfen nie ExoPlayer.Builder aufrufen.


8.2 Leak-Schutz

In Debug-Builds:

LeakCanary aktivieren (oder vergleichbare Lösung)

PlaybackSession + MiniPlayer + FocusKit-Row/Zone intensiv beobachten


Keine static-Refs auf Activity/Context/etc. aus Session/Manager-Schichten.


8.3 Cache & Bitmaps

Bild-Loading (Cover/Thumbnails/Posters) nur über:

AsyncImage/Coil/Glide mit Cache


Keine manuellen Bitmaps im UI, wenn vermeidbar.

Player-intern: CacheDataSource für HTTP/Xtream, um Re-Requests zu vermeiden.



---

9. Compose & FocusKit Performance

9.1 Recomposition Hygiene

InternalPlayerUiState darf nicht alles in einem Objekt packen, wenn nur Teile sich ändern.

Hot-Paths (Progress, isPlaying, Buffering) in separate, kleine Composables, die kaum Layout kosten.


9.2 FocusKit

Focus-Effekte (tvFocusFrame, tvClickable, tvFocusGlow) so konsolidieren, dass:

maximal eine graphicsLayer + drawWithContent Kette pro UI-Element läuft

unnötige doppelte Effekte vermieden werden



9.3 MiniPlayerOverlay

Animationen kurz, state-driven, testfreundlich (abschaltbar in Tests).

Keine übermäßige Layout-Sprünge beim Ein-/Ausblenden.



---

10. Error Handling & Recovery

10.1 Netz- / Streaming-Fehler

PlaybackSession muss Fehler signalisieren via:

error: StateFlow<PlayerError?>


UI (Full Player/MiniPlayer) muss:

den Fehler „soft“ anzeigen (Overlay, Message)

nicht hart crashen


Optional:

Auto-Retry bei transienten Fehlern (Connection Reset, Timeout)

Logging an Diagnostics / TV Input Inspector



10.2 Worker-Fehler

Worker dürfen niemals die PlaybackSession killen.

Schwere Fehler (z. B. DB defekt) müssen:

UI-kompatibel (Fehlermeldung),

aber Playback-bewusst sein (z. B. erst nach Ende der Session anzeigen).




---

11. Quality-Tooling & Test Requirements

11.1 Tools

Detekt:

Max Complexity pro Funktion ≤ 10

Keine „God-Classes“ im Session/Manager-Bereich


Ktlint:

Einheitlicher Stil – wichtig für Review/Langlebigkeit


Android Lint:

Lifecycle, Threading, Context-Leaks, PiP-Warnings ernst nehmen


LeakCanary (Debug):

Fokus auf PlaybackSession/MiniPlayer



11.2 Tests

Unit-Tests:

PlaybackSessionLifecycleTest:

onPause/onStop/onResume Simulation

keine unnötige Player-Recreation


MiniPlayerLifecycleTest:

Full↔Mini↔Home mit Lifecycle-Wechseln


WorkerThrottleTest:

Worker-Rate reduziert bei isPlaybackActive == true



Integration-/Robolectric-Tests:

App in Background/Foreground mit laufendem Playback (TV & Phone-Konfiguration)

Rotation mit laufendem Player + MiniPlayer

System PiP (Phone): Home → PiP → zurück zur App


Regression-Tests:

Prüfen, dass Phasen 4–7-Verhalten unverändert bleibt:

Subtitles/CC

PlayerSurface & Aspect Ratio

TV Input & Focus (inkl. EXIT_TO_HOME, MiniPlayer-Inputs)

Live TV/EPG




---

12. Ownership

PlaybackSessionController:

alleiniger Owner von ExoPlayer + PlaybackLifecycle


MiniPlayerManager:

alleiniger Owner von MiniPlayerState


TvInputController:

Owner der Input-Routing-Logik, aber NICHT des Lifecycle


FocusKit:

Owner aller Fokus-/Zonen-Entscheidungen


Workers:

an PlaybackPriority gebunden


UI-Screens (Library, Player, Settings, ProfileGate, Start):

reine Konsumenten dieser Systeme, ohne eigene Lifecycle-/Playback-Logik




---

13. Evolution / Amendments

Alle Änderungen an:

PlaybackSession-Lifecycle

MiniPlayer-Lifecycle

System PiP Verhalten

Worker/Playback-Interaktion


müssen zuerst diesen Contract aktualisieren und danach in:

INTERNAL_PLAYER_PHASE8_CHECKLIST.md

INTERNAL_PLAYER_REFACTOR_ROADMAP.md


übernommen werden, bevor Implementierung erfolgt.


---

Filename:
docs/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md