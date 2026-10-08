# Player-lokale Debug-Volumes

## Verifizierte API

Für die gepinnte Hytale-Serverversion `0.6.8` enthält die bereitgestellte `HytaleServer.jar` folgende Client-Pakete:

- `AddOrUpdateTriggerVolumeDisplay(String volumeId, TriggerVolumeDisplayEntry entry)`
- `RemoveTriggerVolumeDisplay(String volumeId)`

`TriggerVolumeDisplayEntry` stellt unter anderem `volumeId`, `shapeType`, `position`, `dimensions`, `color`, `opacity` und `name` bereit. Diese Signaturen wurden direkt mit `javap` gegen die Projekt-JAR geprüft.

## Verwendung in Civ

`CivMineDebugService` verwendet diese Pakete ausschließlich als Entwicklungsvisualisierung. Die Einträge werden an den PacketHandler des anfragenden `PlayerRef` gesendet und erhalten stabile Civ-Debug-IDs. Dadurch muss Civ keine echten Trigger Volumes oder Weltblöcke erzeugen, um Arbeitsfronten, Runtime-Anker oder Designgrenzen sichtbar zu machen.

`/civdebug mine hide` sendet für alle von diesem Spieler aktivierten Mine-Debug-IDs jeweils `RemoveTriggerVolumeDisplay`.

Die aktuelle Integration behandelt die Darstellung als **Momentaufnahme**. Fortschreitende Runtime-Ziele werden nicht kontinuierlich gepusht; der Spieler führt `show`, `show anchors` oder `show all` erneut aus, um eine aktualisierte Ansicht zu erhalten.

## Evidenzgrenze

Die JAR-Signaturen belegen die Paket- und Feldstruktur. Dass diese Darstellung im Hytale-Client tatsächlich sichtbar ist, stammt aus der bereits verwendeten Civ-Debugintegration; die Signaturen allein wären dafür kein Runtime-Beweis.
