# Zwergenmine – V1

## Spielverhalten

- `dwarf_mine` ist ein eigener baubarer Gebäudetyp, zunächst mit derselben Kapazität je Phase (1 / 2 / 3 Abbauer) wie die Menschenmine.
- Der Gebäudeeingang verwendet vorläufig kopierte, unabhängig editierbare Minen-Prefabs unter `Civilizations/DwarfMine/`. Sie besitzen noch die Geometrie des bestehenden menschlichen Mineneingangs. Eigene Zwergen-Architektur für das oberirdische Gebäude ist nachfolgendes Asset-Authoring, **nicht** Teil des bereits gelieferten V1-Artworks.
- Bergarbeiter erhalten denselben Beruf `MINER`, dieselben Bewegungsaufträge und dieselbe gemeinsame Aufgabenkoordination. Die Anlage wird nur nach Gebäudetyp anders geplant.

## Unterirdisches Raster

`DwarvenMinePlanner` erzeugt eine flache, rein kardinale Tunnelgeometrie ohne organischen Form-Noise. Der erste Hauptstollen verläuft 192 Slices geradeaus; alle 32 Slices sind beidseitig 32 Slices lange Nebengänge im rechten Winkel geplant. Deren Anfänge überschneiden sich bewusst mit dem navigierbaren Hauptstollen. Die Geometrie besitzt einen gemeinsamen 3×3×3-Navigationskern wie die Menschenmine. Die Breite/Höhe ist 7×6 im Hauptstollen und 5×5 in den Seitengängen.

Die V1-Rasterplanung ist absichtlich begrenzt und eben. Neue Hauptstollen-Generationen und vertikale Verbindungen folgen erst mit einem separaten geprüften Planungsschritt. Keine parallele Hytale-Wegfindung.

## Direkte Gestaltung

Die Menschenmine führt ihre Support-, Licht- und Dekor-Aufträge über den bestehenden normalen Scheduler weiter aus. In der Zwergenmine setzt `DwarvenMineFinishPlan` stattdessen alle acht abgeschlossenen Slices seitliche Steinpfeiler und darauf Laternen. Kreuzungs-Slices selbst bleiben frei. `MinerWorkSystem` setzt diese Bauteile wenige fertig ausgehobene Slices hinter der aktiven Front **direkt** über `DwarvenMineFinishExecutor` und `MineBlockPlacement`, ohne einen späteren Dekorations-Workerjob.

Nur native geladene Hytale-Blöcke werden verwendet, der 3×3×3-Navigationskern ist nie Ziel. Falls ein Block belegt, ein Chunk nicht geladen oder ein Asset nicht vorhanden ist, wird kein Ersatzblock erfunden. Bereits erfolgreiche Teilplatzierungen bleiben, und der Bau kann innerhalb eines begrenzten anschließenden Slice-Fensters erneut versucht werden. Erfolgreiche Dekorations-IDs werden im vorhandenen persistenten Infrastruktur-Completion-Set gespeichert, damit ein Neustart keine Doppelbauten erzeugt.

Die erste Version konzentriert sich auf Pfeiler und Beleuchtung. Eigene Runen, Bodenmuster, Gewölbe und monumentale Hallen benötigen verifizierte Assets und separates Authoring; vorhandene V1-Minenraumtypen bleiben in Verwendung.

## Risiken und Grenzen

- Die aktuelle Minen-Engine deaktiviert normale Treppenaufträge; die Zwergenmine bleibt daher zunächst absichtlich auf einer Ebene.
- Raumaufträge, Navigation, Blockabbau und NPC-Entscheidungen bleiben gemeinsam. Kreuzungen sind physisch verbunden; die tatsächliche gleichzeitige Arbeit mehrerer NPCs muss noch im Spiel überprüft werden.
- `civ.building=mine` in den kopierten Anschlussmarkern kennzeichnet weiterhin die gemeinsame semantische Minen-Anschlussart, nicht den Gebäudetyp; der separate Gebäudetyp heißt `dwarf_mine`.
- Ältere Menschenminen werden weiterhin mit dem organischen Planer generiert. Bestehende persistent gespeicherte Minennetze werden nicht in ein Zwergenraster migriert.

## Tests

- Core-Tests: gerade Achsen, 90°-Nebengänge, Determinismus, erhaltene navigierbare Kreuzungen, keine Höhenübergänge, Dekor-Abstand zum Navigationskern.
- GitHub-CI: `./gradlew test` und `./gradlew build` auf demselben Pull-Request-Commit.
- In-Game-Test: Zwergenmine aus `/civbuild` setzen, Miner zuweisen, auf zwei Kreuzungen und die direkt auftauchenden Laternen warten, Mine upgradeprüfen, Server neu starten und auf Geometrie-/Task-Kontinuität prüfen. Eine bestehende Menschenmine dient parallel als Gegenprobe.

Die Eingangs-Prefabs sind zunächst austauschbare Authoring-Platzhalter. Erst eine Sichtprüfung mit echten Vanilla-Assets kann eine finale zwergische Optik bestätigen.

## Baustellen-Persistenzkorrektur

Die neuen Zwergenminen-PlacementDefinitions müssen auch in `CivConstructionPersistenceService.definitionToken` und `definition` in beide Richtungen registriert sein. Andernfalls schlägt `saveSites` nach dem Baustellen-Commit mit `Unknown construction prefab Civilizations/DwarfMine/DwarfMine_01` fehl: Die Platzierung kann bereits in der Welt sichtbar sein, während der persistente Baustellenzustand nicht vollständig gespeichert wird. Alle drei Phasen verwenden jetzt stabile Tokens `dwarf1`, `dwarf2`, `dwarf3`; ein JUnit-Roundtrip-Test schützt diesen Vertrag. Bereits unterbrochene Baustellen können trotzdem einen erneuten Platzierungsversuch benötigen, wenn vor dieser Korrektur kein Baustellen-Snapshot persistiert wurde.
