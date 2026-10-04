# 0004: Räumliche Gameplay-Logik vor Hytale mit Golden- und Voxel-Tests validieren

Status: Proposed

## Kontext

Die Mine enthält räumliche Civ-Regeln, die sich unabhängig von Hytales Navigation und Rendering ausdrücken lassen: Tunnelbreite und -höhe, Segmentlänge, Reihenfolge der 4x4-Abbauflächen, Anschluss gerader und gedrehter Segmente sowie Positionen von Stützelementen.

Bisher können einzelne Geometriefunktionen bereits als Core-Tests laufen. Fehler werden jedoch leicht erst im echten Spiel sichtbar, wenn mehrere räumliche Schritte zusammenspielen. Ein vollständiger Hytale-Runtime-Test ist dafür vergleichsweise langsam und vermischt zwei Fragen: Ist die Civ-Geometrie korrekt, und setzt Hytale diese bereits korrekte Geometrie richtig um?

Ein zweiter vollständiger Welt- oder Pathfinding-Simulator wäre zugleich falsch: Er würde Hytales Engine-Verhalten duplizieren und könnte mit der echten Runtime auseinanderlaufen.

## Entscheidung

Räumliche Civ-Features werden in drei Stufen geprüft:

1. **Golden-Geometrie:** Kleine, von Hand überprüfbare 2D- beziehungsweise konstante-Y-Fälle vergleichen Core-Ergebnisse mit expliziten Weltkoordinaten. Der erwartete Wert darf nicht durch dieselbe Produktionsfunktion berechnet werden, die geprüft wird.
2. **3D-Voxel-Szenario:** Eine minimale Hytale-unabhängige Voxelwelt kennt nur für die jeweilige Regel notwendige Zellzustände wie `SOLID`, `AIR` und semantische Support-Zellen. Sie besitzt keine Physik und kein Pathfinding. Core-Abläufe mutieren diese Welt; anschließend wird der komplette relevante Weltbereich gegen einen unabhängig aufgebauten Sollzustand verglichen.
3. **Hytale-Runtime:** Erst nachdem die Civ-Sollgeometrie auf den unteren Ebenen validiert ist, prüft ein fokussierter Runtime-Vertrag, ob native Navigation, Harvesting, Prefabs, Rotation und reale Weltmutation diesen Sollzustand tatsächlich umsetzen.

2D ist dabei keine eigene Engine. Dieselben `BlockPosition(x,y,z)`-Koordinaten werden verwendet; ein 2D-Test betrachtet lediglich eine feste Y-Ebene oder eine Draufsicht.

`MineSimulationWorld` liegt im Hytale-unabhängigen Simulationspaket und ist der gemeinsame räumliche Zustand für automatisierte Szenario-Tests und den Desktop-Viewer. Dadurch visualisiert der Viewer exakt denselben Weltzustand, den die Tests beobachten, statt eine zweite Darstellungssimulation zu besitzen. Unabhängige Golden-Erwartungen bleiben weiterhin reine Testfixtures.

Der Viewer darf diesen Zustand nur projizieren. Für die Mine bietet er Draufsicht, einzelne Y-Layer und eine feste isometrische Cutaway-Ansicht sowie ein optionales Soll-Overlay. Keine dieser Ansichten verändert Gameplay-Regeln oder berechnet einen alternativen Tunnelzustand.

Die Voxelwelt darf keine Hytale-Wegfindung, Blockphysik, Prefab-Origins, Chunks, Beleuchtung oder Assetsemantik nachbauen. Bewegung im Headless-Pfad ist nur eine einfache geradlinige Ausführung eines Core-Intents. Wenn ein Test solche Engine-Verträge benötigt, gehört er auf die Hytale-Runtime-Ebene.

Für Support-Frames darf der Core eine Hytale-unabhängige semantische Geometrie beschreiben. Der Hytale-Adapter bleibt dafür verantwortlich, diese Semantik auf das konkrete Prefab beziehungsweise native Blockassets abzubilden.

`MinerBasicScenario` beschreibt den gemeinsamen deterministischen Startzustand für den ersten Miner-Slice: Miner-Startposition, Tunnelanker und Richtung. Viewer, Headless-Simulation und ein späterer Hytale-Runtime-Test dürfen dieselbe Fixture verwenden; Engine-Details bleiben außerhalb dieser Definition.

## Konsequenzen

Mine-Geometrie und Miner-Ablauf können lokal und in normaler CI sehr schnell iteriert werden. Fehler in Segmentanschlüssen, Off-by-one-Koordinaten, falscher Höhe, Reihenfolge der Arbeitsflächen oder unerwarteter Weltmutation werden gefunden, bevor ein Hytale-Serverlauf nötig ist.

Entwickler können denselben Ablauf im Simulation Viewer Tick für Tick beobachten und zwischen Draufsicht, Layer- und isometrischer Darstellung wechseln. Der Inspector zeigt gleichzeitig Core-State, aktuellen Intent, Fortschritt, Zielblock und bestätigte Supports.

Ein fehlgeschlagener späterer Runtime-Test hat einen kleineren Suchraum: Die fachliche Sollgeometrie ist bereits unabhängig validiert, sodass primär Adapter- oder Engine-Verhalten untersucht werden muss.

Golden-Erwartungen müssen bewusst klein und reviewbar bleiben. Große erwartete Welten sollen nicht durch Kopieren der Produktionsalgorithmen erzeugt werden, weil Test und Produktion sonst denselben Fehler teilen könnten.

Die zusätzliche Voxelwelt ist kein allgemeines Minecraft-/Hytale-Weltmodell. Neue Zellzustände oder Operationen werden nur ergänzt, wenn ein konkreter Civ-Test oder eine reine Darstellung desselben Zustands sie benötigt.

## Betrachtete Alternativen

**Nur Hytale-Runtime-Tests:** würden reale Engine-Integration prüfen, aber jede Geometrieiteration verlangsamen und fachliche Fehler mit Engine-Fehlern vermischen.

**Eigene 2D- und 3D-Engines:** würden dieselbe Civ-Geometrie doppelt implementieren. Stattdessen bleibt 2D nur eine Projektion derselben 3D-Koordinaten.

**Eigene Viewer-Welt neben den Tests:** würde leicht vom automatisierten Szenariozustand abweichen. Deshalb liest der Viewer denselben `MineSimulationWorld`-Snapshot wie die Tests.

**Erwartungsgeometrie aus `MineSegment` erneut berechnen:** wäre einfach, ist aber als Oracle ungeeignet. Ein Fehler in der Produktionsgeometrie könnte dadurch auf beiden Seiten identisch auftreten und unentdeckt bleiben.
