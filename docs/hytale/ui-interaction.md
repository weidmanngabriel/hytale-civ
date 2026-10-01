# UI und Interaktion

## Verifiziert im Projekt

Civ verwendet Hytales `InteractiveCustomUIPage`-Ablauf für interaktive Menüs wie `BuildingMenuPage`, `PersonActionsPage` und `WikiPage`.

Für nicht-interaktive, dauerhaft sichtbare Zusatzinformationen verwendet Civ außerdem Hytales keyed `CustomUIHud`-/`HudManager`-API. `CivNpcCompactHud` wird unter dem Key `civ.selectedNpc` registriert und kann über `UICommandBuilder` inkrementell aktualisiert werden, ohne den HUD-Baum jedes Mal neu zu erzeugen. Die gepinnte `HytaleServer.jar` bestätigt dafür `CustomUIHud.show()`, `CustomUIHud.update(...)`, `HudManager.addCustomHud(...)`, `HudManager.getCustomHud(key)` und `HudManager.removeCustomHud(...)`.

`CustomUIHud` stellt selbst keinen `UIEventBuilder` bereit. Interaktive Aktionen wie Detailfenster oder Teleport bleiben deshalb bei `InteractiveCustomUIPage`; der Compact-HUD ist bewusst reine Anzeige.

Im RTS-Modus spiegelt der Compact-HUD den per Linksklick inspizierten Civ-NPC. Beim Wechsel auf einen anderen Civ-NPC werden die angezeigten Werte sofort ausgetauscht; ein Tick-System aktualisiert den aktuell inspizierten NPC zusätzlich in einem gedrosselten Intervall, damit sich Laufzeitwerte wie die Tätigkeit ohne erneute Auswahl ändern können. Ein Linksklick ohne gültigen Civ-NPC entfernt den HUD.

Ein Rechtsklick auf den aktuell ausgewählten Civ-NPC öffnet `PersonActionsPage`. Die frühere allgemeine Use-/F-Interaktion ist nicht der vorgesehene RTS-Steuerpfad.

## RTS-Kamera

`RtsCameraController` verwendet eine feste schräge Hytale-Custom-Kamera. Der RTS-Modus wechselt nicht in den Spectator-Modus. Das Zurückgeben der Kontrolle erfolgt über Hytales nativen `CameraManager.resetCamera`-Lifecycle.

## Projektgrenze

UI und Input übersetzen Spieleraktionen in Core-Befehle oder Zustandsänderungen. Civ-Spielregeln dürfen nicht in Hytale-UI-Komponenten liegen.

Bewohnerinformationen werden für die UI über `NpcInfoProvider` in einen `NpcInfoSnapshot` projiziert. Vorhandene Civ-Daten werden dort gelesen; noch nicht implementierte Systeme wie Hunger oder Familie erscheinen nur als nicht veränderliche Platzhalter. Dadurch bleiben Compact-HUD und zukünftige Detailseite von der Gameplay-Speicherung entkoppelt und können UI-Felder ergänzen oder entfernen, ohne die Bewohnerkomponente umzubauen.

## Offen

Für die aktuell gepinnte Hytale-Version ist im Projekt keine verifizierte native API dokumentiert, die im verwendeten Custom-Kameramodus ausschließlich das eigene Spielermodell ausblendet. Solche Client-Effekte dürfen nicht aus Signaturen allein abgeleitet werden.

Die genaue visuelle Position und Größe des Compact-HUDs ist erst nach In-Game-Verifikation endgültig. Der erste Slice startet mit 340×190 px, 24 px Abstand rechts und 118 px Abstand unten und soll anhand eines echten Spielscreenshot feinjustiert werden.
