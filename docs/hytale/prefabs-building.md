# Prefabs und Bauen

## Aktueller Placement-Lifecycle

`PrefabPlacementService` besitzt den Civ-Baustellen-Placement-Lifecycle.

Während der Mausbewegung erzeugt oder verschiebt Civ eine native `PersistentPrefabPreview`-Entität am validierten Civ-Anker. Linksklick übernimmt diese Preview als Baustelle; Rechtsklick entfernt sie.

Der Civ-Commit-Pfad verwendet dafür nicht mehr `BlockSelection.place`. Auch `PrefabPasteEvent` ist nicht Teil dieses Commit-Pfads.

## Anker und Höhe

Für die aktuell verwendeten Creator-Prefabs liegt der logische Civ-Bauanker einen Block unter dem anvisierten Oberflächenblock. `groundSinkBlocks` wird beim Erzeugen dieses terrain-relativen Ankers angewendet.

`PrefabPlacementService` übersetzt diesen Civ-Anker anschließend zentral in den von Hytales Prefab-APIs erwarteten Placement-Origin. Diese Übersetzung muss für Preview, Baustellen-Layer und finales Prefab identisch sein, damit die sichtbare Höhe übereinstimmt.

## Aktuelle Validierungsgrenze

Im derzeitigen Preview-Spike wird die frühere blockweise Kollisions-/Terrainprüfung bewusst nicht vor dem Preview-Spawn ausgeführt. Diese Prüfung stammte aus dem Sofort-Paste-Pfad und störte die isolierte Verifikation von `PersistentPrefabPreview` bei eingesenkten Baustellenankern.

Die Kollisionsregeln müssen für den Baustellen-Lifecycle erneut passend eingeführt und zur Laufzeit verifiziert werden.

## Fertige Gebäude

`BuildingPlacementRegistry` verwaltet weiterhin bereits fertig platzierte Civ-Bauflächen. Eine neue Baustellen-Preview ist noch kein fertiges Gebäude und darf deshalb nicht vorzeitig als fertige `FarmBuilding`-Instanz registriert werden.
