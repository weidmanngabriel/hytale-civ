# 0005: Prefab-Geometrie für Simulations-Reachability aus echten Assets ableiten

Status: Proposed

## Kontext

Der Hytale-unabhängige Simulator soll räumliche Civ-Regeln früh sichtbar und automatisiert prüfen. Gebäudegeometrie manuell ein zweites Mal für den Simulator zu pflegen wäre jedoch eine konkurrierende Wahrheit: Änderungen am echten Hytale-Prefab könnten unbemerkt vom Simulationsmodell abweichen.

Gleichzeitig ist Hytales native NPC-Navigation ein Engine-Vertrag. Eine vollständige Reimplementierung von Seek, NavMesh, Kollision, Sprunglogik oder Physik im Simulator würde eine zweite Navigation schaffen, deren grünes Ergebnis keine verlässliche Aussage über die echte Runtime wäre.

Die Civ-Prefabs liegen als JSON-Assets mit echten Blockkoordinaten vor. Zusätzlich enthalten die aktuellen Gebäude-Prefabs semantische Trigger-Volumes wie `building_bounds`, `workplace_access` und `output_storage`.

## Entscheidung

Für räumliche Simulationsprüfungen wird die Geometrie aus den echten Civ-`.prefab.json`-Dateien gelesen und auf ein bewusst kleines Modell reduziert.

Der erste Vertrag kennt:

- `SOLID` für beliebige belegte Prefab-Blöcke,
- `DOOR` für authored Türblöcke, die im Reachability-Lab als passierbar gelten,
- echte lokale Blockkoordinaten und Prefab-Anchor,
- Civ-Trigger-Volumes samt `civ.type` und `civ.building` als semantische Marker.

Gameplay-Punkte werden nicht aus der Blockform erraten. `workplace_access`, `output_storage`, `building_bounds` und spätere vergleichbare Semantik stammen aus den authored Trigger-Markern.

Ein kleines A* darf auf diesem vereinfachten Modell ausschließlich **geometrische Plausibilität** prüfen. Der aktuelle Test-Agent benötigt festen Boden und zwei freie Blockzellen Höhe, bewegt sich orthogonal und darf höchstens einen Block Höhenunterschied pro Schritt überwinden. Für isolierte Prefab-Tests wird fehlender Boden auf Y=0 außerhalb des Prefabs als flaches Testterrain behandelt.

Dieses A* ist Test-/Entwicklungsinfrastruktur. Es darf nicht vom Hytale-Adapter für Live-NPC-Bewegung verwendet werden und ändert nicht die Produktgrenze: Hytale entscheidet weiterhin, wie ein realer NPC ein Civ-Ziel erreicht.

Der erste sichtbare Proof-of-Concept ist ein `Prefab Navigation Lab`, das `Farm_01.prefab.json` direkt lädt, die vereinfachte Geometrie und Civ-Marker zeichnet und einen A*-Pfad Außen → `workplace_access` → `output_storage` Step für Step visualisiert.

## Konsequenzen

Änderungen an echten Prefabs wirken unmittelbar auf Reachability-Tests und Viewer. Eine zugemauerte Tür, eingeschlossene Arbeitszone oder geometrisch unerreichbare Storage-Zone kann damit bereits in normalen Java-Tests auffallen.

Ein gefundener Simulationspfad ist ausdrücklich **kein Beweis**, dass Hytales native Navigation denselben Weg findet. Positive Reachability muss bei engine-relevanten Features weiterhin durch fokussierte Hytale-Runtime-Tests abgesichert werden. Ein nicht gefundener Pfad ist dagegen ein starkes Signal für einen Geometrie-, Marker- oder Modellierungsfehler.

Die Vereinfachung von Blocktypen ist absichtlich konservativ: unbekannte belegte Blöcke gelten als `SOLID`. Neue passierbare Kategorien werden nur ergänzt, wenn ein konkreter Civ-Test sie benötigt und ihre Semantik eindeutig ist.

## Betrachtete Alternativen

**Gebäude für den Simulator manuell nachbauen:** wurde verworfen, weil Asset und Simulationsmodell auseinanderlaufen könnten.

**Hytales komplettes Prefab-/Navigationssystem im Simulator starten:** würde den schnellen Hytale-unabhängigen Testpfad verlieren und Engine-Verträge in die Simulation ziehen.

**A* als Produktionsnavigation verwenden:** wurde verworfen. Die reale NPC-Navigation bleibt Hytales Verantwortung.

**Nur Block-Footprints ohne Wegsuche prüfen:** wäre einfacher, könnte aber nicht erkennen, ob authored Arbeits- und Lagerbereiche durch die tatsächliche Gebäudegeometrie grundsätzlich erreichbar sind.
