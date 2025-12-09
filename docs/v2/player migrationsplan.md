Hier kommt ein ausführlicher Migrationsplan, der:

deine v2-Struktur konsequent respektiert,

den bestehenden SIP-Player maximal wiederverwendet,

deine Produktwünsche (Audio/CC-Profil, Serienmodus, TMDB, Kids, Fehler-Snackbars, Download, Live, Zapping, Casting usw.) direkt mit einplant,

und bewusst verhindert, dass du später nochmal alles umbauen musst.

Ich gliedere ihn in Phasen. Jede Phase hat:

Ziel

Module & Ebenen

Schritte

Hinweise, was aus v1 wiederverwendet wird statt neu erfunden.

Phase 0 – Grundprinzipien & Guardrails

Ziel: Bevor irgendwas portiert wird, sind die Regeln glasklar.

Verankere (falls nicht schon geschehen) in AGENTS.md / PlayerArchitecture.md:

Player-Schichten:

core/player-model – PlaybackContext, PlaybackState, PlaybackError

player/internal – SIP-Kern (Session, State, Resolver, Live, Subtitles, System, MiniPlayer-Engine)

player/input – Input-Kontexte (Touch, D-Pad) und Mapping auf PlayerCommands

playback/telegram – Telegram-spezifische Sources

playback/xtream – Xtream-spezifische Sources

data/* – Watch-Progress & Resume, Katalog

domain/* – UseCases, Kids-Policy, Serien-Logik, Normalisierung

ui/* – Screens, Snackbars, Player-Chrome

Hard Rules:

Player-Module (player/*, playback/*) importieren nie pipeline/**oder data/**.

core/player-model kennt nur primitive Typen (String, Long, SourceType …), keine Source-spezifischen Klassen.

Input-Handling (Touch, D-Pad) lebt in player/input, nicht in UI oder in Transport.

Stelle sicher, dass dein Custom-Agent diese Regeln als Befehle behandelt, nicht als Vorschläge.

Phase 1 – IST-Analyse v2-Player & SIP-Bestand

Ziel: Exakt wissen, was schon da ist und was fehlt.

1.1 v2-Player-IST

Durchsuche:

playback-telegram/**

playback-xtream/**

player/**oder internal_player/** falls schon angelgt

core/model/** nach PlaybackContext, PlaybackState, PlaybackError, etc.

Erstelle eine Liste:

Welche Klassen gibt es bereits?

z.B. XtreamPlaybackSourceFactory, TelegramPlaybackSourceFactory, irgendwelche Player-Wrapper.

Welche davon:

sind kompatibel mit der Zielstruktur → behalten.

sind halbgares v2/v1-Hybrid → markieren für Ersatz.

verletzen Layer (z.B. importieren Pipeline/Repos) → zur Bereinigung markieren.

1.2 SIP-Bestand (v1) analysieren

Aus deiner ZIP / Legacy-Repo:

player/internal/** (Bibliotheksvariante)

app/**/player/internal/** (App-spezifische Variante, falls existiert)

app/**/player/miniplayer/**

Ordne die Legacy-Dateien in Kategorien (wir hatten das schon grob):

Core Engine:

InternalPlayerEntry

InternalPlayerSession

InternalPlayerState

PlaybackContext (Legacy-Version)

Resolver & DataSources:

InternalPlaybackSourceResolver

TelegramFileDataSource

Live:

LivePlaybackController, LiveChannel, EPG-Overlays

Subtitles/CC:

SubtitleSelectionPolicy, SubtitleStyleManager, SubtitleStyle

System/TV:

InternalPlayerSystemUi, evtl. TvInputController (für Remote/D-Pad)

UI / Controls:

PlayerSurface, InternalPlayerControls, Dialoge

MiniPlayer:

MiniPlayerManager, MiniPlayerState, Tests

Markiere:

Was ist voll SIP-gold (sauber, modulär, kaum v1-Abhängigkeit)?

Wo hängen noch v1-Abhängigkeiten (alte DI, Logging, Modelle), die du beim Port säubern musst?

Ergebnis von Phase 1 ist eine Tabelle:
„v2-Player vs v1-SIP: Was existiert, was fehlt, was ersetzt wird“.

Phase 2 – Player-Modell finalisieren (core/player-model)

Ziel: PlaybackContext & Co. sauber definieren, sodass alles drauf aufbauen kann.

2.1 Definiere PlaybackContext in core/player-model

Felder (aus Produktwünschen + SIP):

data class PlaybackContext(
    val canonicalId: String,        // z.B. telegram:chatId:msgId oder xtream:vod:123
    val sourceType: SourceType,     // TELEGRAM, XTREAM, FILE, etc.
    val uri: String?,               // optional: direkte URI, falls schon bekannt
    val sourceKey: String?,         // optional: key für SourceFactory (z.B. Telegram-FileKey)
    val headers: Map<String, String>,
    val startPositionMs: Long,
    val isLive: Boolean,
    val isSeekable: Boolean,
    val extras: Map<String, String> = emptyMap(),
)

Noch keine Serien-/Kids-/TMDB-Infos hier – das geht in Domain und Normalizer, nicht in PlaybackContext.

2.2 Definiere PlaybackState & PlaybackError

PlaybackState:

IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR

plus position/duration/buffer im Session-Objekt.

PlaybackError:

NETWORK, DECODER, SOURCE_NOT_FOUND, PERMISSION, UNKNOWN

optional underlyingSourceType: SourceType

Damit hat der Player ein sauberes, quellunabhängiges Modell.

Phase 3 – SIP-Kern nach player/internal portieren

Ziel: Legacy-SIP-Kern übernehmen, nicht neu erfinden – nur an v2-APIs anpassen.

3.1 InternalPlayerState, InternalPlayerSession, InternalPlayerEntry

Portiere:

InternalPlayerState → player/internal/state

InternalPlayerSession → player/internal/session

InternalPlayerEntry → player/internal

Passe an:

Verwende PlaybackContext aus core/player-model.

Nutze v2-Logging (UnifiedLog) statt alten Loggern.

Entferne alte v1-spezifische DI (Koin, Dagger alt) – stattdessen Hilt für Konstruktor-Injection.

3.2 SourceResolver im Kern

Portiere InternalPlaybackSourceResolver nach player/internal/source.

Definiere Interface:

interface PlaybackSourceFactory {
    fun supports(sourceType: SourceType): Boolean
    fun createSource(context: PlaybackContext): PlaybackSource
}

data class PlaybackSource(
    val mediaItem: MediaItem,       // Media3
    val extraHeaders: Map<String, String> = emptyMap()
)

InternalPlaybackSourceResolver:

hält eine Liste von PlaybackSourceFactory (via DI injiziert),

wählt anhand sourceType + context die richtige Factory.

Damit muss der Kern nie Telegram/Xtream kennen.

Phase 4 – Telegram & Xtream PlaybackFactories portieren

Ziel: Bestehende v1-Logik wiederverwenden, aber in playback-*-Modulen.

4.1 playback-telegram

Portiere TelegramFileDataSource:

in playback-telegram/datasource/TelegramFileDataSource.kt

injiziere TelegramFileClient aus transport-telegram (Interface, nicht TDLib direkt).

übernimm die Zero-Copy/Prefix/MP4-Validation-Logik aus dem SIP-Gold.

Implementiere TelegramPlaybackSourceFactory:

class TelegramPlaybackSourceFactory @Inject constructor(
    private val fileClient: TelegramFileClient
) : PlaybackSourceFactory {
    override fun supports(sourceType: SourceType) = sourceType == SourceType.TELEGRAM

    override fun createSource(context: PlaybackContext): PlaybackSource {
        // entweder uri schon gesetzt oder aus sourceKey + fileClient auflösen
        // MediaItem/MediaSource mit TelegramFileDataSource bauen
    }
}

4.2 playback-xtream

Erzeuge DataSources (z.B. HLS + TS):

XtreamHlsDataSource, XtreamTsDataSource oder einfach DefaultHttpDataSource + Config.

Nutze XtreamUrlBuilder aus transport-xtream für URLs.

Implementiere XtreamPlaybackSourceFactory:

class XtreamPlaybackSourceFactory @Inject constructor(
    private val urlBuilder: XtreamUrlBuilder
) : PlaybackSourceFactory {
    override fun supports(sourceType: SourceType) = sourceType == SourceType.XTREAM

    override fun createSource(context: PlaybackContext): PlaybackSource {
        // je nach extras: live vs vod vs series Episode
    }
}

Damit sind Telegram + Xtream wieder voll „SIP-fähig“.

Phase 5 – MiniPlayer (Resize/Overlay) portieren

Ziel: v1-MiniPlayer-Verhalten nicht neu erfinden, sondern übernehmen.

5.1 Modul & Platzierung

Neues Modul: player/miniplayer (oder player/internal/miniplayer, wenn du alles in einem Modul halten willst, aber besser separat).

Portiere:

MiniPlayerManager

MiniPlayerState

alle relevanten Transitions / Bounds-Logik

dazugehörige Tests (MiniPlayer-Bounds/Transition-Tests)

5.2 Integration

InternalPlayerSession bekommt ein Feld:

val miniPlayerState: StateFlow<MiniPlayerState>

UI kann:

MiniPlayer aktivieren/deaktivieren,

zwischen Vollbild ↔ MiniPlayer wechseln.

Wiederverwendung statt Neuschreiben:
Wo du z.B. in v1 schon gute Resize-/Drag-/Snap-Logik hast, diese übernehmen und lediglich Abhängigkeiten auf alte App-States entfernen.

Phase 6 – Subtitles/CC komplett portieren

Ziel: CC/Untertitel komplett übernehmen und an deine Produktwünsche und Profile hängen.

6.1 Subtitles Engine

Portiere nach player/internal/subtitles:

SubtitleSelectionPolicy

SubtitleStyle

SubtitleStyleManager

Anpassung auf deine Anforderungen:

Pro Profil speichern:

Domain/Profil-Schicht (nicht Player) hält:

bevorzugte Sprache

Stil (Größe, Farbe, Hintergrund)

Player bekommt diese Info beim Start in PlaybackContext.extras oder über einen SubtitlePreferences-Provider.

Persistenz:

Data/Domain speichert pro Profil:
SubtitlePreferences(profileId) → lang + style + enabled?.

6.2 CC-UI / Dialog

Portiere das CC-Dialog-UI (CcMenuDialog o.ä.) nach feature/player-ui:

Consumer vom SubtitleStyleManager + Status aus InternalPlayerSession.

Player bleibt UI-los; UI ruft nur Setter.

Wiederverwendung:
Damit nutzt du die bekannte Auswahl-UI und Selection-Logik, statt etwas Neues auszudenken.

Phase 7 – Audio-Spur-Handling & Profilpräferenzen

Ziel: Audio-Spuren pro Profil merken und später in UI konfigurierbar machen.

Im Player:

InternalPlayerSession bekommt:

API: setPreferredAudioTrack(langCode: String?)

events: availableAudioTracks: StateFlow<List<AudioTrackInfo>>

Domain/Profil-Schicht:

pro Profil AudioPreferences (z.B. preferredLanguage, fallbacks).

PlayItemUseCase setzt vor Playbackstart die Präferenz.

Wichtig:
Player merkt sich nichts „hart“ – er ist stateless bzgl. Profilen. Domain hält den Zustand, Player wendet nur an.

Phase 8 – Serienmodus & TMDB-Integration

Ziel: Serienmodus mit „Next Episode“-Button + optional TMDB-basierter Intro/Abspann-Detection.

8.1 Serienmodus

Domain-Schicht:

SeriesModeSettings pro Profil:

enabled: Boolean

NextEpisodeResolver:

bekommt DomainMediaItem (Serie/Episode)

fragt data nach nächster Episode (Xtream / Telegram / gemischt)

Prüft, ob nächste Episode verfügbar.

Player-UI:

Beobachtet Ende einer Episode:

Via PlaybackState == ENDED oder positionMs/durationMs-Threshold

Zeigt unaufdringlichen Button:

„Next Episode“ (mit Namen/Episode)

Startet nächste Episode nur auf Klick, nicht automatisch, außer später eine gute Abspann-Detection implementiert ist.

8.2 TMDB für Intro/Abspann

metadata-normalizer / metadata-provider:

Wenn TMDB Infos verfügbar:

Start-Offset des Intros

End-Offset des Abspanns

Player erhält diese als:

PlaybackContext.extras["skipIntroUntilMs"]

PlaybackContext.extras["skipOutroFromMs"]

Player-UI:

Zeigt optionale Buttons „Skip Intro“ / „Skip Credits“, wenn diese Werte gesetzt sind.

Du entscheidest: zuerst optional, dann ggf. Auto-Skip als Einstellung.

Phase 9 – Kids-/Guest-Policy & Zeitfenster

Ziel: Kids/Guest Inhalte manuell steuerbar, später heuristisch erweiterbar; definierbare Zeitfenster.

Domain-Modul domain/kids:

KidsPolicy:

Welche Tags/Listen gelten als „Kids Content“ (manuell gepflegt)

Welche Profile sind „Kids Profile“.

Zeitfenster (z.B. 06:00–20:00).

KidsGate-UseCase:

prüft DomainMediaItem + Profil + Uhrzeit

gibt PlayAllowed / PlayBlocked mit Reason zurück.

Player:

bekommt nur PlaybackContext, niemals selber Kids-Logik.

PlayItemUseCase ruft erst KidsGate, dann baut PlaybackContext.

Später:

Heuristiken (TMDB-Genre, „Animation“, etc.) können in KidsPolicy ergänzt werden, ohne den Player anzufassen.

Phase 10 – Fehler-Handling, Snackbar & Retry

Ziel: Klare, userfreundliche Fehlermeldungen + automatischer + manueller Retry.

InternalPlayerSession:

liefert PlaybackError-Events (mit SourceType und Reason).

hat API retry().

Domain/UI:

Beobachtet Fehler:

zeigt Snackbar:

Klar verständlicher Text (z.B. „Verbindung zu Xtream Server unterbrochen“, „Telegram Datei konnte nicht geladen werden“).

Startet automatischen Retry, wenn:

Fehler als „transient“ markiert ist (z.B. Netzwerk, Timeout).

Bietet „Nochmal versuchen“-Button in Snackbar:

ruft retry() auf dem Player.

Optional:

bei wiederholtem Fehler:

Domain schlägt alternative Quelle vor (z.B. gleiche Episode über andere Pipeline).

Phase 11 – Download & Offline-Playback (Planen, minimal integrieren)

Ziel: nicht alles sofort bauen, aber richtig andocken.

data/downloads:

Download-Entities (DownloadItem mit canonicalId, localPath, state).

DownloadManager (Telegram Downloads via TDLib, Xtream-Downloads später sehr vorsichtig aus rechtlicher Sicht).

Player:

Wenn PlaybackContext ein localPath in extras hat, wird offline abgespielt (FileSource, kein Netzwerk).

Domain entscheidet:

„Starte Download“

„Spiele offline, wenn verfügbar“

Für jetzt:

Player so bauen, dass er sowohl uri als HTTP als auch file://-URIs bzw. localPath problemlos unterstützt.

Konkrete Download-Implementierung später.

Phase 12 – Live-TV (ohne Timeshift)

Ziel: Live-TV wie v1, aber sauber in v2.

player/internal/live:

Portiere LivePlaybackController, LiveChannel, EPG-Overlay-Logik.

playback-xtream:

gibt Live-Sources aus (HLS/TS) für isLive = true.

Domain:

ObserveLiveChannelsUseCase, ObserveEpgUseCase.

UI:

Live-List, EPG-Overlay, Zapping.

Zapping:

Links/Rechts:

wird in player/input gemappt auf NextChannel, PreviousChannel.

LivePlaybackController führt die Kanalsprünge dann aus:

PlayNextChannel(), PlayPreviousChannel().

Timeshift:

Als Future-Feature im LivePlaybackController planen, aber noch nicht implementieren.

Phase 13 – Input-Kontexte (Touch + D-Pad) & Casting/System-Controls

Ziel: Player reagiert sauber auf Touch & Remote, später auch Casting.

13.1 Input

player/input:

PlayerInputContext (TOUCH, REMOTE).

PlayerInputHandler:

mappt UI-/System-Events auf PlayerCommands (PlayPause, Seek, ToggleMiniPlayer, NextChannel, …).

UI bestimmt pro Device:

Phone → TOUCH

TV/FireTV → REMOTE

13.2 Casting & System-Controls

Casting:

Plane ein CastingController im Domain/Feature-Bereich:

kann PlaybackContext an eine Cast-Session übergeben.

Player pausiert sich selbst, wenn Casting aktiv ist.

Player selbst wird cast-fähig gemacht durch:

Abstraktion „Local vs Remote Playback“.

System-Controls:

MediaSession-Integration (Lockscreen/Notification):

InternalPlayerSession koppelt sich an eine MediaSession.

Domain/Feature-Schicht setzt Title/Artist/Thumb aus DomainMediaItem.

Phase 14 – Tests & Doku

Ziel: Sicherstellen, dass nichts von dem oben nur Slides ist.

Unit-Tests:

InternalPlaybackSourceResolverTest

InternalPlayerSessionStateTest

MiniPlayerManagerTest

SubtitleSelectionPolicyTest

Integration-Tests:

Fake TelegramFileClient → TelegramPlaybackSourceFactory → SIP-Player.

Fake Xtream-Endpoints → XtreamPlaybackSourceFactory → SIP-Player.

Doku:

PlayerArchitecture.md aktualisieren:

Module

Data-Flow-Diagramme

Input-Kontexte

INTERNAL_PLAYER_*_CONTRACT_PHASE*.md auf v2-Begriffe mappen, überall wo noch v1-Begriffe drinstehen.
