# UI und Interaktion

## Verifiziert im Projekt

Civ verwendet Hytales `InteractiveCustomUIPage`-Ablauf für interaktive Menüs wie `BuildingMenuPage`, `BuildingActionsPage`, `PersonActionsPage` und `WikiPage`.

Für nicht-interaktive, dauerhaft sichtbare Zusatzinformationen verwendet Civ außerdem Hytales keyed `CustomUIHud`-/`HudManager`-API. `CivNpcCompactHud` wird unter dem Key `civ.selectedNpc` registriert und kann über `UICommandBuilder` inkrementell aktualisiert werden, ohne den HUD-Baum jedes Mal neu zu erzeugen. Die gepinnte `HytaleServer.jar` bestätigt dafür `CustomUIHud.show()`, `CustomUIHud.update(...)`, `HudManager.addCustomHud(...)`, `HudManager.getCustomHud(key)` und `HudManager.removeCustomHud(...)`.

`CustomUIHud` stellt selbst keinen `UIEventBuilder` bereit. Interaktive Aktionen wie Detailfenster oder Teleport bleiben deshalb bei `InteractiveCustomUIPage`; der Compact-HUD ist bewusst reine Anzeige.

Die gepinnte `HytaleServer.jar` bestätigt auf `UICommandBuilder` `set(...)` und `appendInline(selector, ui)` sowie auf `UIEventBuilder` Event-Bindings. Ein echter Client-Test am 2026-10-02 hat für `BuildingActionsPage` gezeigt, dass `appendInline("#WorkerList", ...)` unmittelbar nach dem Laden der Seite mit `Failed to parse or resolve document for Custom UI AppendInline command. Selector: #WorkerList` zum Client-Abbruch führt. Die Worker-Liste verwendet deshalb statisch deklarierte Slots in `CivBuildingActions.ui`; Java setzt nur bekannte `Text`-/`Visible`-Properties und bindet Events an bereits vorhandene Buttons.

Im RTS-Modus spiegelt der Compact-HUD den per Linksklick inspizierten Civ-NPC. Beim Wechsel auf einen anderen Civ-NPC werden die angezeigten Werte sofort ausgetauscht; ein Tick-System aktualisiert den aktuell inspizierten NPC zusätzlich in einem gedrosselten Intervall, damit sich Laufzeitwerte wie die Tätigkeit ohne erneute Auswahl ändern können. Ein Linksklick ohne gültigen Civ-NPC entfernt den HUD.

Ein Rechtsklick auf den aktuell ausgewählten Civ-NPC öffnet `PersonActionsPage`. Die frühere allgemeine Use-/F-Interaktion ist nicht der vorgesehene RTS-Steuerpfad.

Ein Rechtsklick auf ein fertiges Civ-Gebäude öffnet `BuildingActionsPage`. Die Seite zeigt Gebäudename, aktuelle Phase, Worker-Belegung und die aus Typ plus Phase abgeleitete Worker-Kapazität. Belegte Worker-Slots sind auswählbar; die Auswahl wird danach in denselben bestehenden RTS-Auswahlzustand übernommen, den auch ein direkter Linksklick auf einen Bewohner verwendet. Ein anschließender Rechtsklick auf den Boden verwendet deshalb unverändert den bestehenden manuellen Bewegungsbefehl.

Besitzt der Gebäudetyp laut Core-Katalog eine weitere Phase, erhält `BuildingActionsPage` zusätzlich die bereits abgeleitete nächste Phase und zeigt einen statisch deklarierten Upgrade-Button mit `Auf Phase X erweitern`. Die UI entscheidet nicht selbst, welche Phasen existieren. Während eines laufenden Ausbaus zeigt dieselbe Seite einen Sperrhinweis; Upgrade- und Abriss-Button sind dann verborgen. Bei der höchsten Mine-Phase 3 existiert keine nächste Phase und entsprechend kein Upgrade-Button.

## RTS-Kamera

`RtsCameraController` verwendet eine feste schräge Hytale-Custom-Kamera. Der RTS-Modus wechselt nicht in den Spectator-Modus. Das Zurückgeben der Kontrolle erfolgt über Hytales nativen `CameraManager.resetCamera`-Lifecycle.

## Projektgrenze

UI und Input übersetzen Spieleraktionen in Core-Befehle oder Zustandsänderungen. Civ-Spielregeln dürfen nicht in Hytale-UI-Komponenten liegen.

Bewohnerinformationen werden für die UI über `NpcInfoProvider` in einen `NpcInfoSnapshot` projiziert. Vorhandene Civ-Daten werden dort gelesen; noch nicht implementierte Systeme wie Hunger oder Familie erscheinen nur als nicht veränderliche Platzhalter. Dadurch bleiben Compact-HUD und zukünftige Detailseite von der Gameplay-Speicherung entkoppelt und können UI-Felder ergänzen oder entfernen, ohne die Bewohnerkomponente umzubauen.

Worker-Kapazität und Upgrade-Folge sind keine UI-Regeln. `BuildingActionsPage` erhält lediglich die bereits aus dem Hytale-unabhängigen Gebäudetypmodell abgeleitete Kapazität, nächste Phase, den laufenden Upgrade-Zustand sowie die aktuell über persistente Arbeitsplatz-IDs zugeordneten Bewohner. Die UI blockiert derzeit ausdrücklich keine Überbelegung; die Kapazität ist in diesem Slice nur Information für spätere Gameplay-Regeln.

Die derzeitige `BuildingActionsPage` deklariert drei Worker-Slots statisch, passend zur aktuell höchsten bekannten Mine-Kapazität (Phase 3). Wenn spätere Gebäudetypen mehr Slots benötigen, muss das UI erweitert oder auf eine separat im Client verifizierte dynamische/Paging-Lösung umgestellt werden; die Gameplay-Metadaten selbst bleiben davon unabhängig.

## Offen

Für die aktuell gepinnte Hytale-Version ist im Projekt keine verifizierte native API dokumentiert, die im verwendeten Custom-Kameramodus ausschließlich das eigene Spielermodell ausblendet. Solche Client-Effekte dürfen nicht aus Signaturen allein abgeleitet werden.

Die genaue visuelle Position und Größe des Compact-HUDs ist erst nach In-Game-Verifikation endgültig. Der erste Slice startet mit 340×190 px, 24 px Abstand rechts und 118 px Abstand unten und soll anhand eines echten Spielscreenshot feinjustiert werden.

Die statischen Worker-Slots müssen nach dem Fix noch einmal fokussiert im echten Client geprüft werden: Seite öffnen, freie Slots anzeigen, einen zugeordneten Worker auswählen und danach dessen normalen RTS-Bewegungsbefehl ausführen.

Die neue Upgrade-Darstellung muss ebenfalls noch fokussiert im echten Client geprüft werden: Phase-1-Mine öffnen, Upgrade-Button und Sperrstatus kontrollieren, Phase 2 und 3 fertigbauen und sicherstellen, dass der Button auf Phase 3 verschwindet.
