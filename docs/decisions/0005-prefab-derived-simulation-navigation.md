# 0005: Prefab-Geometrie für Simulations-Reachability aus echten Assets ableiten

Status: Proposed

## Kontext

Der Hytale-unabhängige Simulator soll räumliche Civ-Regeln früh sichtbar und automatisiert prüfen. Gebäudegeometrie manuell ein zweites Mal für den Simulator zu pflegen wäre jedoch eine konkurrierende Wahrheit: Änderungen am echten Hytale-Prefab könnten unbemerkt vom Simulationsmodell abweichen.

Gleichzeitig ist Hytales native NPC-Navigation ein Engine-Vertrag. Eine vollständige Reimplementierung von Seek, NavMesh, Kollision, Sprunglogik oder Physik im Simulator würde eine zweite Navigation schaffen, deren grünes Ergebnis keine verlässliche Aussage über die echte Runtime wäre.

Die Civ-Prefabs liegen als JSON-Assets mit echten Blockkoordinaten vor. Zusätzlich enthalten die aktuellen Gebäude-Prefabs semantische Trigger-Volumes wie `building_bounds`, `workplace_access`, `output_storage` und bei der Mine `mine_tunnel_connector`.

## Entscheidung

Für räumliche Simulationsprüfungen wird die Geometrie aus den echten Civ-`.prefab.json`-Dateien gelesen und auf ein bewusst kleines Modell reduziert.

Der erste Vertrag kennt:

- `SOLID` für beliebige belegte Prefab-Blöcke,
- `DOOR` für authored Türblöcke, die im Reachability-Lab als passierbar gelten,
- echte lokale Blockkoordinaten und Prefab-Anchor,
- Civ-Trigger-Volumes samt `civ.type` und `civ.building` als semantische Marker.

Gameplay-Punkte werden nicht aus der Blockform erraten. `workplace_access`, `output_storage`, `building_bounds`, `mine_tunnel_connector` und spätere vergleichbare Semantik stammen aus den authored Trigger-Markern.

Ein kleines A* darf auf diesem vereinfachten Modell ausschließlich **geometrische Plausibilität** prüfen. Der aktuelle Test-Agent benötigt festen Boden und zwei freie Blockzellen Höhe, bewegt sich orthogonal und darf höchstens einen Block Höhenunterschied pro Schritt überwinden. Für isolierte Prefab-Tests wird fehlender Boden auf Y=0 außerhalb des Prefabs als flaches Testterrain behandelt.

Dieses A* ist Test-/Entwicklungsinfrastruktur. Es darf nicht vom Hytale-Adapter für Live-NPC-Bewegung verwendet werden und ändert nicht die Produktgrenze: Hytale entscheidet weiterhin, wie ein realer NPC ein Civ-Ziel erreicht.

Der Farm-Proof-of-Concept lädt `Farm_01.prefab.json` direkt und visualisiert einen A*-Pfad Außen → `workplace_access` → `output_storage`.

Die Mine erweitert denselben Ansatz: `Mine_01.prefab.json` liefert echte Gebäudegeometrie sowie `workplace_access` und `mine_tunnel_connector`. A* prüft die Reachability bis zum Connector; anschließend übernimmt der echte Core-`MinerJob` die schrittweise 4×4×8-Ausgrabung. `Mine_Support_01.prefab.json` wird ebenfalls direkt geladen und als echtes Asset validiert. Die dynamische Support-Platzierung verwendet vorerst weiterhin die semantische `MineSupportFrame`-Geometrie, solange die native Hytale-Prefab-Rotation/Origin-Übersetzung nicht separat verifiziert wurde.

Die initiale Tunnelrichtung ist aktuell **keine aus dem vereinfachten Prefabmodell gelesene authored Eigenschaft**. Das Mine-Lab konfiguriert sie explizit als `NORTH` und zeigt dies im Inspector als Simulationsannahme an. Eine spätere Orientierungsübernahme darf erst erfolgen, wenn die relevante Hytale-Prefab-/Transform-Semantik verifiziert ist.

## Konsequenzen

Änderungen an echten Prefabs wirken unmittelbar auf Reachability-Tests und Viewer. Eine zugemauerte Tür, eingeschlossene Arbeitszone oder geometrisch unerreichbare Storage-/Tunnelzone kann damit bereits in normalen Java-Tests auffallen.

Ein gefundener Simulationspfad ist ausdrücklich **kein Beweis**, dass Hytales native Navigation denselben Weg findet. Positive Reachability muss bei engine-relevanten Features weiterhin durch fokussierte Hytale-Runtime-Tests abgesichert werden. Ein nicht gefundener Pfad ist dagegen ein starkes Signal für einen Geometrie-, Marker- oder Modellierungsfehler.

Die Vereinfachung von Blocktypen ist absichtlich konservativ: unbekannte belegte Blöcke gelten als `SOLID`. Neue passierbare Kategorien werden nur ergänzt, wenn ein konkreter Civ-Test sie benötigt und ihre Semantik eindeutig ist.

Für die Mine entstehen zwei getrennte Aussagen: Das Prefab-A* beweist nur, dass der authored Tunnel-Connector in der vereinfachten Geometrie plausibel erreichbar ist. Der `MinerJob`-Voxelpfad beweist anschließend die Civ-Ausgrabungsreihenfolge. Hytales echte Navigation, Harvesting, Prefab-Placement, Rotation und Kollision bleiben Runtime-Verträge.

## Betrachtete Alternativen

**Gebäude für den Simulator manuell nachbauen:** wurde verworfen, weil Asset und Simulationsmodell auseinanderlaufen könnten.

**Hytales komplettes Prefab-/Navigationssystem im Simulator starten:** würde den schnellen Hytale-unabhängigen Testpfad verlieren und Engine-Verträge in die Simulation ziehen.

**A* als Produktionsnavigation verwenden:** wurde verworfen. Die reale NPC-Navigation bleibt Hytales Verantwortung.

**Prefab-Rotation aus ungeprüften JSON-/Transform-Feldern ableiten:** wurde vorerst verworfen. Position/Marker werden verwendet; Richtung und native Prefab-Placement-Semantik werden erst nach separater Verifikation übernommen.

**Nur Block-Footprints ohne Wegsuche prüfen:** wäre einfacher, könnte aber nicht erkennen, ob authored Arbeits-, Lager- und Tunnelbereiche durch die tatsächliche Gebäudegeometrie grundsätzlich erreichbar sind.
