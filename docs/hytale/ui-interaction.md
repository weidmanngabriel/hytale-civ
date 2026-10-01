# UI und Interaktion

## Verifiziert im Projekt

Civ verwendet Hytales `InteractiveCustomUIPage`-Ablauf für interaktive Menüs wie `BuildingMenuPage`, `PersonActionsPage` und `WikiPage`.

Der RTS-Prototyp hängt bewusst nicht von einem nicht verifizierten permanenten Client-Anker für dauerhaft sichtbare Buttons ab.

Ein Rechtsklick auf den aktuell ausgewählten Civ-NPC öffnet `PersonActionsPage`. Die frühere allgemeine Use-/F-Interaktion ist nicht der vorgesehene RTS-Steuerpfad.

## RTS-Kamera

`RtsCameraController` verwendet eine feste schräge Hytale-Custom-Kamera. Der RTS-Modus wechselt nicht in den Spectator-Modus. Das Zurückgeben der Kontrolle erfolgt über Hytales nativen `CameraManager.resetCamera`-Lifecycle.

## Projektgrenze

UI und Input übersetzen Spieleraktionen in Core-Befehle oder Zustandsänderungen. Civ-Spielregeln dürfen nicht in Hytale-UI-Komponenten liegen.

## Offen

Für die aktuell gepinnte Hytale-Version ist im Projekt keine verifizierte native API dokumentiert, die im verwendeten Custom-Kameramodus ausschließlich das eigene Spielermodell ausblendet. Solche Client-Effekte dürfen nicht aus Signaturen allein abgeleitet werden.
