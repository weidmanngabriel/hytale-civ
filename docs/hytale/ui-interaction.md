# UI und Interaktion

## Verifiziert im Projekt

Civ verwendet Hytales `InteractiveCustomUIPage`-Ablauf für interaktive Menüs wie `BuildingMenuPage`, `BuildingActionsPage`, `ConstructionDetailsPage`, `PersonActionsPage` und `WikiPage`.

Für nicht-interaktive Zusatzinformationen verwendet Civ Hytales keyed `CustomUIHud`-/`HudManager`-API. `CivNpcCompactHud` läuft unter `civ.selectedNpc`, `CivBuildingCompactHud` unter `civ.selectedBuilding`. Beide HUDs werden bei einer neuen Auswahl sofort erzeugt beziehungsweise aktualisiert. Spätere Laufzeitänderungen werden über Hytales natives `DelayedEntitySystem` in 0,75-Sekunden-Abständen nachgezogen; Civ lässt dafür nicht mehr jeden Spieler in jedem Engine-Tick durch einen eigenen Timer laufen.

`CustomUIHud` stellt selbst keinen `UIEventBuilder` bereit. Der Compact-HUD ist deshalb reine Anzeige. Detail- und Aktionsfunktionen bleiben in `InteractiveCustomUIPage`.

Die gepinnte `HytaleServer.jar` bestätigt auf `UICommandBuilder` `set(...)` und `appendInline(selector, ui)` sowie auf `UIEventBuilder` Event-Bindings. Ein echter Client-Test am 2026-10-02 hat für `BuildingActionsPage` gezeigt, dass `appendInline("#WorkerList", ...)` unmittelbar nach dem Laden der Seite mit `Failed to parse or resolve document for Custom UI AppendInline command. Selector: #WorkerList` zum Client-Abbruch führt. Die Worker-Liste verwendet deshalb statisch deklarierte Slots in `CivBuildingActions.ui`; Java setzt nur bekannte `Text`-/`Visible`-Properties und bindet Events an bereits vorhandene Buttons.

## RTS-Auswahl

NPCs, fertige Gebäude und aktive Baustellen verwenden dasselbe zweistufige Auswahlmuster:

1. Der erste Links- oder Rechtsklick auf ein noch nicht ausgewähltes Ziel wählt es aus und zeigt den passenden Compact-HUD.
2. Ein zweiter **Linksklick** auf dasselbe bereits ausgewählte Ziel öffnet dessen große Detail-/Aktionsseite.
3. Ein zweiter Rechtsklick öffnet kein großes UI. Er bleibt für kontextuelle RTS-Aktionen reserviert.
4. Ein Linksklick ins Leere hebt die Auswahl auf.
5. Ein Klick auf ein anderes Civ-Ziel wechselt Auswahl und Compact-HUD dorthin.

Für NPCs öffnet der zweite Linksklick `PersonActionsPage`. Für fertige Gebäude öffnet er `BuildingActionsPage`. Für aktive Baustellen öffnet er die zunächst lesende `ConstructionDetailsPage` mit Phase, Status und Baufortschritt.

Die bestehende First-Person-Use-Interaktion ist davon getrennt und kann weiterhin direkt `PersonActionsPage` öffnen; das zweistufige Muster beschreibt die RTS-Maussteuerung.

Ein im Gebäude-UI ausgewählter Worker wird in denselben RTS-Auswahlzustand übernommen wie ein direkt angeklickter Bewohner. Ein Rechtsklick auf den Boden kann deshalb weiterhin den bestehenden manuellen Bewegungsbefehl verwenden. Kontextuelle Rechtsklick-Funktionen auf bereits ausgewählte Gebäude dürfen dieses reservierte Interaktionsmuster später erweitern, ohne den zweiten Linksklick für das Detailfenster zu verändern.

## Gebäude- und Baustellen-HUD

Der Compact-HUD eines fertigen Gebäudes zeigt Name, Phase, Zustand und Worker-Belegung. Der Compact-HUD einer aktiven Baustelle zeigt Name, Zielphase, `Im Bau` und den persistenten Fortschritt in Bauabschnitten.

Besitzt der Gebäudetyp laut Core-Katalog eine weitere Phase, erhält `BuildingActionsPage` zusätzlich die bereits abgeleitete nächste Phase und zeigt einen statisch deklarierten Upgrade-Button mit `Auf Phase X erweitern`. Die UI entscheidet nicht selbst, welche Phasen existieren. Während eines laufenden Ausbaus zeigt dieselbe Seite einen Sperrhinweis; Upgrade- und Abriss-Button sind dann verborgen. Bei Mine Phase 3 existiert keine nächste Phase und entsprechend kein Upgrade-Button.

## Native Boundary-Anzeige

Civ verwendet für ausgewählte Gebäude/Baustellen und Platzierungskollisionen Hytales clientseitige Trigger-Volume-Darstellung über `UpdateTriggerVolumeDisplay` mit `TriggerVolumeDisplayEntry`-Boxen. Die gepinnte `HytaleServer.jar` bestätigt, dass ein Packet mehrere Boxen gleichzeitig tragen kann und dass die Box-Dimensionen als Half-Extents angegeben werden.

Die Anzeige ist rein spielerlokal: Civ setzt dafür keine Weltblöcke, Partikel oder persistenten Hilfs-Entities. Bei einer ausgewählten Baustelle wird deren persistenter `PlacementFootprint` visualisiert; bei einem fertigen Gebäude dessen `building_bounds`.

Für separat verwaltete Debug-Overlays bestätigt die gepinnte `HytaleServer.jar` zusätzlich die To-Client-Pakete `AddOrUpdateTriggerVolumeDisplay(String, TriggerVolumeDisplayEntry)` und `RemoveTriggerVolumeDisplay(String)`. Civs Mine-Debug verwendet diese keyed Variante, damit jeder Debug-Eintrag eine stabile ID besitzt und gezielt wieder entfernt werden kann, ohne `DebugUtils.clear(world)` oder temporäre Weltblöcke zu benötigen. `TriggerVolumeDisplayEntry` unterstützt dabei Box/Sphere/Cylinder sowie Position, Half-Extents, Farbe, Opacity, Name/Label und Rotation. Das Mine-Debug-Overlay ist rein lesend und spielerlokal.

Während `/civbuild` bleibt Hytales nativer Prefab-Ghost die normale Platzierungsvorschau. Die zusätzliche Boundary-Anzeige wird nur benötigt, wenn der geplante Footprint ein bestehendes Gebäude oder eine aktive Baustelle schneidet. Dann zeigt Civ gleichzeitig den geplanten Bereich und die kollidierenden Bereiche. Sobald keine Kollision mehr besteht, wird die Zusatzanzeige entfernt.

Diese Kollisionsdarstellung ist ereignisgetrieben: `PlayerMouseMotionEvent` stößt die Prüfung nur an, wenn sich der anvisierte Block tatsächlich geändert hat. Der schnelle Preview-Pfad liest nur die im Prefab authored Boundary-Geometrie und erzeugt keinen vollständigen Terrain-Snapshot. Die vollständige Placement-Validierung und der Terrain-Snapshot bleiben auf den bestätigenden Linksklick beschränkt.

## RTS-Kamera

`RtsCameraController` verwendet eine feste schräge Hytale-Custom-Kamera. Der RTS-Modus wechselt nicht in den Spectator-Modus. Das Zurückgeben der Kontrolle erfolgt über Hytales nativen `CameraManager.resetCamera`-Lifecycle.

## Projektgrenze

UI und Input übersetzen Spieleraktionen in Core-Befehle oder Zustandsänderungen. Civ-Spielregeln dürfen nicht in Hytale-UI-Komponenten liegen.

Bewohnerinformationen werden für die UI über `NpcInfoProvider` in einen `NpcInfoSnapshot` projiziert. Vorhandene Civ-Daten werden dort gelesen; noch nicht implementierte Systeme wie Hunger oder Familie erscheinen nur als nicht veränderliche Platzhalter. Dadurch bleiben Compact-HUD und Detailseiten von der Gameplay-Speicherung entkoppelt.

Worker-Kapazität und Upgrade-Folge sind keine UI-Regeln. `BuildingActionsPage` erhält lediglich die bereits aus dem Hytale-unabhängigen Gebäudetypmodell abgeleitete Kapazität, nächste Phase, den laufenden Upgrade-Zustand sowie die aktuell über persistente Arbeitsplatz-IDs zugeordneten Bewohner.

Die derzeitige `BuildingActionsPage` deklariert drei Worker-Slots statisch, passend zur aktuell höchsten bekannten Mine-Kapazität (Phase 3). Wenn spätere Gebäudetypen mehr Slots benötigen, muss das UI erweitert oder auf eine separat im Client verifizierte dynamische/Paging-Lösung umgestellt werden; die Gameplay-Metadaten selbst bleiben davon unabhängig.

## Offen / Runtime-Verifikation

Die neue Boundary-Darstellung ist durch Packet-Struktur und Servercode der gepinnten JAR verifiziert, aber ihre endgültige Optik im RTS-Kameramodus braucht noch einen echten Client-Test. Besonders zu prüfen sind Höhe/Deckkraft der Footprint-Fläche und die gleichzeitige Darstellung von geplantem und blockierendem Bereich.

`UpdateTriggerVolumeDisplay` verwendet denselben clientseitigen Darstellungsweg wie Hytales Trigger-Volume-Werkzeug. Solange ein Spieler parallel zu Civ-RTS auch native Trigger-Volume-Editorwerkzeuge verwendet, kann die jeweils zuletzt gesendete Anzeige die andere ersetzen. Für den normalen Civ-Spielablauf werden die Werkzeuge nicht parallel verwendet; falls sich diese Annahme ändert, braucht die Darstellung eine explizite Koordination. Offen ist ebenfalls, wie sich ein später gesendetes vollständiges `UpdateTriggerVolumeDisplay` gegenüber bereits per `AddOrUpdateTriggerVolumeDisplay` gesetzten keyed Mine-Debug-Einträgen verhält; die Paketstruktur allein beweist diese Client-Merge-Semantik nicht.

Die genaue visuelle Position und Größe der Compact-HUDs bleibt In-Game-Feintuning. Der aktuelle Auswahlbereich sitzt unten links bei 24 px Abstand und 118 px Abstand zum unteren Rand.

Die statischen Worker-Slots, die Upgrade-Darstellung und der neue Baustellen-Detailflow müssen weiterhin fokussiert im echten Client geprüft werden.

## Civ-Managementdashboard

`CivDashboardPage` verwendet `InteractiveCustomUIPage` und statisch deklarierte sieben Zeilen in `Pages/CivDashboard.ui`. Tabs und Seitenwechsel öffnen jeweils eine neue native Seite; damit wird das im Client problematische `appendInline` bewusst vermieden. Die Seite nutzt native Aktivierungs-Events und bestehende RTS-Auswahlaktionen. Die lokale Spielerwelt bestimmt die Gebäudeliste, die Bewohnerliste stammt aus gültigen geladenen Civ-Referenzen der aktuellen Entity-Store-Instanz. Noch offen: tatsächliche Skalierung/Layout und die Darstellung bei 30–50 Einträgen im Hytale-Client. Das Menü erstellt keine Civ-spezifischen Weltänderungen.

## Performance-HUD und UI

Ein aktiver Civ-Performance-Recorder erzeugt über `CivPerformanceHudSystem` (1-s-`DelayedEntitySystem`) den keyed `CustomUIHud` `civ.performanceTracking`. Der lila HUD am oberen rechten Rand zeigt „Performance Tracking aktiv“ und die Restzeit. Er ist **nicht anklickbar**, weil `CustomUIHud` keine `UIEventBuilder`-Bindings anbietet. In der nativen `CivDashboardPage` führt die dritte Registerkarte „Debug / Performance“ zu Start/Stopp, Status, Zahlen und Refresh. Angezeigte Systemdaten aktualisieren sich auf explizites „Aktualisieren“ oder beim erneuten Öffnen, nicht automatisch in jedem Tick. Runtime-Test von Layout und Lesbarkeit steht noch aus.
