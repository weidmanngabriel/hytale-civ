# 0004: Räumliche Gameplay-Logik vor Hytale mit Golden- und Voxel-Tests validieren

Status: Proposed

## Kontext

Die Mine enthält räumliche Civ-Regeln, die sich unabhängig von Hytales Navigation und Rendering ausdrücken lassen: Tunnelbreite und -höhe, Segmentlänge, Reihenfolge der 4x4-Abbauflächen, Anschluss gerader und gedrehter Segmente sowie Positionen von Stützelementen.

Bisher können einzelne Geometriefunktionen bereits als Core-Tests laufen. Fehler werden jedoch leicht erst im echten Spiel sichtbar, wenn mehrere räumliche Schritte zusammenspielen. Ein vollständiger Hytale-Runtime-Test ist dafür vergleichsweise langsam und vermischt zwei Fragen: Ist die Civ-Geometrie korrekt, und setzt Hytale diese bereits korrekte Geometrie richtig um?

Ein zweiter vollständiger Welt- oder Pathfinding-Simulator wäre zugleich falsch: Er würde Hytales Engine-Verhalten duplizieren und könnte mit der echten Runtime auseinanderlaufen.

## Entscheidung

Räumliche Civ-Features werden in drei Stufen geprüft:

1. **Golden-Geometrie:** Kleine, von Hand überprüfbare 2D- beziehungsweise konstante-Y-Fälle vergleichen Core-Ergebnisse mit expliziten Weltkoordinaten. Der erwartete Wert darf nicht durch dieselbe Produktionsfunktion berechnet werden, die geprüft wird.
2. **3D-Voxel-Szenario:** Eine minimale testseitige Voxelwelt kennt nur für die jeweilige Regel notwendige Zellzustände wie `SOLID`, `AIR` und semantische Support-Zellen. Sie besitzt keine Physik und kein Pathfinding. Produktionsgeometrie mutiert diese Welt; anschließend wird der komplette relevante Weltbereich gegen einen unabhängig aufgebauten Sollzustand verglichen.
3. **Hytale-Runtime:** Erst nachdem die Civ-Sollgeometrie auf den unteren Ebenen validiert ist, prüft ein fokussierter Runtime-Vertrag, ob native Navigation, Harvesting, Prefabs, Rotation und reale Weltmutation diesen Sollzustand tatsächlich umsetzen.

2D ist dabei keine eigene Engine. Dieselben `BlockPosition(x,y,z)`-Koordinaten werden verwendet; ein 2D-Test betrachtet lediglich eine feste Y-Ebene oder eine Draufsicht.

Die Voxelwelt bleibt Testinfrastruktur. Sie darf keine Hytale-Wegfindung, Blockphysik, Prefab-Origins oder Assetsemantik nachbauen. Wenn ein Test solche Engine-Verträge benötigt, gehört er auf die Hytale-Runtime-Ebene.

Für Support-Frames darf der Core eine Hytale-unabhängige semantische Geometrie beschreiben. Der Hytale-Adapter bleibt dafür verantwortlich, diese Semantik auf das konkrete Prefab beziehungsweise native Blockassets abzubilden.

## Konsequenzen

Mine-Geometrie kann lokal und in normaler CI sehr schnell iteriert werden. Fehler in Segmentanschlüssen, Off-by-one-Koordinaten, falscher Höhe oder unerwarteter Weltmutation werden gefunden, bevor ein Hytale-Serverlauf nötig ist.

Ein fehlgeschlagener späterer Runtime-Test hat einen kleineren Suchraum: Die fachliche Sollgeometrie ist bereits unabhängig validiert, sodass primär Adapter- oder Engine-Verhalten untersucht werden muss.

Golden-Erwartungen müssen bewusst klein und reviewbar bleiben. Große erwartete Welten sollen nicht durch Kopieren der Produktionsalgorithmen erzeugt werden, weil Test und Produktion sonst denselben Fehler teilen könnten.

Die zusätzliche Voxelwelt ist kein allgemeines Minecraft-/Hytale-Weltmodell. Neue Zellzustände oder Operationen werden nur ergänzt, wenn ein konkreter Civ-Test sie benötigt.

## Betrachtete Alternativen

**Nur Hytale-Runtime-Tests:** würden reale Engine-Integration prüfen, aber jede Geometrieiteration verlangsamen und fachliche Fehler mit Engine-Fehlern vermischen.

**Eigene 2D- und 3D-Engines:** würden dieselbe Civ-Geometrie doppelt implementieren. Stattdessen bleibt 2D nur eine Projektion derselben 3D-Koordinaten.

**Erwartungsgeometrie aus `MineSegment` erneut berechnen:** wäre einfach, ist aber als Oracle ungeeignet. Ein Fehler in der Produktionsgeometrie könnte dadurch auf beiden Seiten identisch auftreten und unentdeckt bleiben.
