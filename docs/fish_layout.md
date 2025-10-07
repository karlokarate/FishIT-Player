Fish Module Übersicht

- app/src/main/java/com/chris/m3usuite/ui/layout/FishTheme.kt
    - Tokens (FishDimens): Größe/Abstände/Ecken/Scale/Glow/Padding (tileWidth/Height/Corner/Spacing, contentPaddingHorizontal, focusScale, focusBorderWidth, reflectionAlpha, enableGlow, showTitleWhenUnfocused).
    - Theme-Wrapper (FishTheme): injiziert Tokens via LocalFishDimens; steuert alle FishTiles/Rows zentral.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishTile.kt
    - Einheitliches Tile: ContentScale.Fit, abgerundete Ecken, Reflection, Fokus‑Scale + Frame, globaler Glow (via FocusKit), optionaler Selection‑Rahmen.
    - Slots: topStartBadge (z. B. „+ / ✓“), bottomEndActions (z. B. Play/Assign), footer (z. B. Plot).
    - Verhalten: Kein per‑Tile Bring‑Into‑View (Row regelt Scroll), onFocusChanged‑Hook (z. B. Logging).
- app/src/main/java/com/chris/m3usuite/ui/layout/FishRow.kt
    - Reihen (minimaler Layer, optionaler Header nur bei Übergabe):
        - FishRowLight: einfache LazyRow + Fokus (fixes Spacing/Padding aus Tokens).
        - FishRow: Media‑Engine für List<MediaItem> (DPAD, edgeLeftExpandChrome, initialFocusEligible, Fokus‑Persistenz).
        - FishRowPaged: Paged‑Engine für LazyPagingItems<MediaItem>.
    - Delegiert an FocusKit (TvRowLight/Media/Paged); zentrale DPAD‑/Scroll‑Logik; keine Row‑Rahmen/Overlays.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishVodContent.kt
    - VOD‑Inhalte für Tiles: displayTitle (+Jahr), Poster‑Wahl (poster > logo > backdrop), Resume‑Fortschritt (0..1), NEW‑Badge, Auswahl‑Badge (+/✓), Bottom‑End‑Aktionen (Play + Assign), onClick (Assign/Details),             
      onFocusChanged (Logging).
    - Liefert ein fertiges VodTileContent zur direkten Befüllung von FishTile.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishSeriesContent.kt
    - Serien‑Basisinhalte: Title (+Jahr), Poster‑Wahl (poster > backdrop), Auswahl‑Badge, Klick (Assign/Details), (Platzhalter für Staffeln/Episoden).
    - Bereit für Erweiterungen (z. B. „Weiter schauen“, Episoden‑Progress).
- app/src/main/java/com/chris/m3usuite/ui/layout/FishLiveContent.kt
    - Live‑Basisinhalte: Title, Logo‑Wahl (logo > poster > backdrop), (Platzhalter für EPG Now/Next‑Progress), Klick/Actions‑Slot.
    - Bereit für EPG‑Anbindung (Now/Next + Fortschritt).
- app/src/main/java/com/chris/m3usuite/ui/layout/FishMeta.kt
    - Meta‑Helper: displayVodTitle (Titel (+Jahr)), pickVodPoster, PlotFooter (2 Zeilen Ellipsis), rememberVodResumeFraction (Composable IO‑Helper).
    - Wiederverwendbar in allen Content‑Modulen.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishActions.kt
    - Aktionen/Badges: AssignBadge (+/✓, Top‑Left), VodBottomActions (Play), AssignBottomAction (BookmarkAdd, Bottom‑End).
    - Bottom‑Buttons sind nicht fokusierbar (DPAD springt nicht in Overlays).
- app/src/main/java/com/chris/m3usuite/ui/layout/FishLogging.kt
    - Fokus‑Logs: OBX‑Titelauflösung + GlobalDebug (z. B. für VOD); wird über onFocusChanged aus Content aufgerufen.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishResumeTile.kt
    - Generische Resume‑Karte (VOD/Serien‑Folgen): 2‑zeiliger Titel, optionales Subtitle (z. B. Fortschritt), Play/Clear‑Actions, Fokus‑Scale + Glow via FocusKit.                                                               


- VodTileCard (Tile)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1089
    - Ersetzt durch: FishTile + FishVodContent.buildVodTileContent
- TelegramTileCard (Tile)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1855
    - Ersetzt durch: FishTile + „Tagged“-Badge (Badge‑Slot). Für Content (TG) später: FishTelegramContent (analog).

Hinweis: Es existieren .orig‑Dateien mit älteren Versionen; maßgeblich ist die „ohne .orig“.

B. Rows (alte Engines + Render) → neu: FishRow (Light/Media/Paged)

- LiveRow (List, Media)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1363
    - Ersetzt durch: FishRow(items, stateKey, edgeLeftExpandChrome, initialFocusEligible) { FishTile + LiveContent }
- SeriesRow (List, Media)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1655
    - Ersetzt durch: FishRow(items, stateKey, edgeLeftExpandChrome, initialFocusEligible) { FishTile + SeriesContent }
- VodRow (List, Media)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1692
    - Ersetzt durch: FishRow(items, stateKey, edgeLeftExpandChrome, initialFocusEligible) { FishTile + VodContent }
- VodRowPaged (Paged)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1729
    - Ersetzt durch: FishRowPaged(pagingItems, stateKey, edgeLeftExpandChrome) { idx, mi → FishTile + VodContent }
- LiveRowPaged (Paged)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1755
    - Ersetzt durch: FishRowPaged(pagingItems, stateKey, edgeLeftExpandChrome) { idx, mi → FishTile + LiveContent }
- SeriesRowPaged (Paged)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1790
    - Ersetzt durch: FishRowPaged(pagingItems, stateKey, edgeLeftExpandChrome) { idx, mi → FishTile + SeriesContent }

Engine-Aufrufer heute (RowCore/RowCorePaged) → neu: FocusKit via FishRow

- MediaRowCore
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1377, 1669, 1706, 1824
- MediaRowCorePaged
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1738, 1766, 1799, 1845
- Ersetzen durch: FishRow/FishRowPaged (delegieren intern an FocusKit.TvRowMedia/Paged).

Raw LazyRow (vermeiden; über FishRow ersetzen)

- StartScreen Telegram‑Reihe (Suche)
    - app/src/main/java/com/chris/m3usuite/ui/home/StartScreen.kt:1034
    - Ersetzt durch: FishRowLight(stateKey = "start_tg_search", itemCount = ...) { FishTile + Badge „T“ }

C. Alte Cards (CARDS_V1) → entfernen/ersetzen

- PosterCard (Cards v1)
    - Definition: app/src/main/java/com/chris/m3usuite/ui/cards/PosterCard.kt:23
    - Verwendungen (CARDS_V1 Pfad):
        - HomeRows SeriesTileCard: app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:725
        - HomeRows VodTileCard: app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:998
    - Ersetzt durch: FishTile + passende FishContent (Series/VOD), optional Title‑always via Token (falls Parität gewünscht).
- ChannelCard (Cards v1)
    - Definition: app/src/main/java/com/chris/m3usuite/ui/cards/ChannelCard.kt:24
    - Verwendung:
        - HomeRows LiveTileCard: app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:314
    - Ersetzt durch: FishTile + FishLiveContent (Logo + EPG später).
- PosterCardTagged (Telegram)
    - Definition: app/src/main/java/com/chris/m3usuite/ui/cards/PosterCardTagged.kt:19
    - Verwendungen:
        - StartScreen Suche: app/src/main/java/com/chris/m3usuite/ui/home/StartScreen.kt:1039
        - HomeRows TelegramTileCard: app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1707
    - Ersetzt durch: FishTile + custom topStartBadge Slot („T“).

Weitere Cards (für spätere Detail/Masks)

- SeasonCard: app/src/main/java/com/chris/m3usuite/ui/cards/SeasonCard.kt:22 (Details/Lists)
- EpisodeRow: app/src/main/java/com/chris/m3usuite/ui/cards/EpisodeRow.kt:22 (Seriendetails)
- Empfehlung: Später auf FishTile/FishResumeTile/Slots migrieren (nicht in VOD‑Rows).

CARDS_V1 Gates (beim Portieren entfernen)

- HomeRows (mehrere Stellen): app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:312, 724, 997, 1706
- Details (später):
    - SeriesDetail: app/src/main/java/com/chris/m3usuite/ui/screens/SeriesDetailScreen.kt:1336
    - LiveDetail: app/src/main/java/com/chris/m3usuite/ui/screens/LiveDetailScreen.kt:792

D. Assign‑Selektion/Badges/Actions → neu: FishActions/FishVodContent                                                                                                                                                             
Bisherige Stellen mit Selektion:

- HomeRows Tiles: app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:295, 816, 1112 (LocalAssignSelection)
- StartScreen: mehrere ProvideAssignSelection‑Scopings (z. B. app/src/main/java/com/chris/m3usuite/ui/home/StartScreen.kt:666, 835, 1131, 1274)
- Neu:
    - FishVodContent nutzt LocalAssignSelection zentral (Top‑Badge +/✓, Klick toggelt in Assign‑Mode).
    - FishActions.AssignBottomAction bietet Bottom‑Assign Action (nicht fokusierbar).

E. Resume (VOD/Series) → neu: FishResumeTile

- Vorhandene Resume‑UI:
    - app/src/main/java/com/chris/m3usuite/ui/components/ResumeCarousel.kt:336 ff (ResumeVodRow + ResumeCard)
- Neu:
    - FishResumeTile (UI) universell; späte Portierung von Resume‑Carousels.
    - Resume‑Daten: weiter via ResumeRepository (bereits in FishMeta.rememberVodResumeFraction).

F. Fokus/DPAD/Glow

- Bisher: focusScaleOnTv + tvFocusGlow + tvClickable je Tile/Row (viele verstreute Aufrufe).
- Neu:
    - FishTile kapselt Fokus‑Visuals (Scale + Frame + optional Glow via Token).
    - FishRow kapselt DPAD/Scroll/edgeLeft→Chrome/Initial Focus (über FocusKit).
    - Glow über FocusKit‑Wrapper global (deaktivierbar via Token).

G. Sonstiges

- Poster/Headers: AppAsyncImage + RequestHeadersProvider (zentrale Headers) bleiben – FishTile nutzt sie.
- Obx Logging: FishLogging.logVodFocus (aufgerufen per onFocusChanged im buildVodTileContent).  