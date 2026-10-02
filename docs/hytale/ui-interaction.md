# UI und Interaktion

## Verifiziert im Projekt

Civ verwendet Hytales `InteractiveCustomUIPage`-Ablauf für interaktive Menüs wie `BuildingMenuPage`, `BuildingActionsPage`, `PersonActionsPage` und `WikiPage`.

Für nicht-interaktive, dauerhaft sichtbare Zusatzinformationen verwendet Civ außerdem Hytales keyed `CustomUIHud`-/`HudManager`-API. `CivNpcCompactHud` wird unter dem Key `civ.selectedNpc` registriert und kann über `UICommandBuilder` inkrementell aktualisiert werden, ohne den HUD-Baum jedes Mal neu zu erzeugen. Die gepinnte `HytaleServer.jar` bestätigt dafür `CustomUIHud.show()`, `CustomUIHud.update(...)`, `HudManager.addCustomHud(...)`, `HudManager.getCustomHud(key)` und `HudManager.removeCustomHud(...)`.

`CustomUIHud` stellt selbst keinen `UIEventBuilder` bereit. Interaktive Aktionen wie Detailfenster oder Teleport bleiben deshalb bei `InteractiveCustomUIPage`; der Compact-HUD ist bewusst reine Anzeige.

Die gepinnte `HytaleServer.jar` bestätigt auf `UICommandBuilder` außerdem `appendInline(selector, ui)` sowie auf `UIEventBuilder` dynamische Event-Bindings. `BuildingActionsPage` nutzt diese Kombination, um die Worker-Slots eines Gebäudes zur Laufzeit aus dessen Metadaten aufzubauen und belegte Slots als auswählbare Buttons zu binden. Diese Signaturen belegen die verfügbare API; die konkrete Darstellung und Event-Ausführung im Client bleibt runtime-abhängig und muss im Spiel verifiziert werden.

Im RTS-Modus spiegelt der Compact-HUD den per Linksklick inspizierten Civ-NPC. Beim Wechsel auf einen anderen Civ-NPC werden die angezeigten Werte sofort ausgetauscht; ein Tick-System aktualisiert den aktuell inspizierten NPC zusätzlich in einem gedrosselten Intervall, damit sich Laufzeitwerte wie die Tätigkeit ohne erneute Auswahl ändern können. Ein Linksklick ohne gültigen Civ-NPC entfernt den HUD.

Ein Rechtsklick auf den aktuell ausgewählten Civ-NPC öffnet `PersonActionsPage`. Die frühere allgemeine Use-/F-Interaktion ist nicht der vorgesehene RTS-Steuerpfad.

Ein Rechtsklick auf ein fertiges Civ-Gebäude öffnet `BuildingActionsPage`. Die Seite zeigt Gebäudename, aktuelle Phase, Worker-Belegung und die aus Typ plus Phase abgeleitete Worker-Kapazität. Belegte Worker-Slots sind auswählbar; die Auswahl wird danach in denselben bestehenden RTS-Auswahlzustand übernommen, den auch ein direkter Linksklick auf einen Bewohner verwendet. Ein anschließender Rechtsklick auf den Boden verwendet deshalb unverändert den bestehenden manuellen Bewegungsbefehl.

## RTS-Kamera

`RtsCameraController` verwendet eine feste schräge Hytale-Custom-Kamera. Der RTS-Modus wechselt nicht in den Spectator-Modus. Das Zurückgeben der Kontrolle erfolgt über Hytales nativen `CameraManager.resetCamera`-Lifecycle.

## Projektgrenze

UI und Input übersetzen Spieleraktionen in Core-Befehle oder Zustandsänderungen. Civ-Spielregeln dürfen nicht in Hytale-UI-Komponenten liegen.

Bewohnerinformationen werden für die UI über `NpcInfoProvider` in einen `NpcInfoSnapshot` projiziert. Vorhandene Civ-Daten werden dort gelesen; noch nicht implementierte Systeme wie Hunger oder Familie erscheinen nur als nicht veränderliche Platzhalter. Dadurch bleiben Compact-HUD und zukünftige Detailseite von der Gameplay-Speicherung entkoppelt und können UI-Felder ergänzen oder entfernen, ohne die Bewohnerkomponente umzubauen.

Worker-Kapazität ist keine UI-Regel. `BuildingActionsPage` erhält lediglich die bereits aus dem Hytale-unabhängigen Gebäudetypmodell abgeleitete Kapazität sowie die aktuell über persistente Arbeitsplatz-IDs zugeordneten Bewohner. Die UI blockiert derzeit ausdrücklich keine Überbelegung; die Kapazität ist in diesem Slice nur Information für spätere Gameplay-Regeln.

## Offen

Für die aktuell gepinnte Hytale-Version ist im Projekt keine verifizierte native API dokumentiert, die im verwendeten Custom-Kameramodus ausschließlich das eigene Spielermodell ausblendet. Solche Client-Effekte dürfen nicht aus Signaturen allein abgeleitet werden.

Die genaue visuelle Position und Größe des Compact-HUDs ist erst nach In-Game-Verifikation endgültig. Der erste Slice startet mit 340×190 px, 24 px Abstand rechts und 118 px Abstand unten und soll anhand eines echten Spielscreenshot feinjustiert werden.

Auch das dynamische Layout der Worker-Liste in `BuildingActionsPage` ist bis zu einem fokussierten In-Game-Test als runtime-abhängig zu behandeln; insbesondere müssen `appendInline`-Darstellung und die dynamisch gebundenen Worker-Buttons im Client bestätigt werden.
