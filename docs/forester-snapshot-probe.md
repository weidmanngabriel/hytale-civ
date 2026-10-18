# Försterei: Snapshot-Prototyp im lokalen 3D-Simulator

**Status:** Diagnoseszenario, kein Hytale-NPC-End-to-End-Nachweis.

## Starten

1. Den Feature-Branch `feat/forester-shared-core-phase1` auschecken.
2. Java 21 und die Projektabhängigkeiten bereithalten.
3. Im Repository ausführen (Windows: `gradlew.bat`):

   ```powershell
   .\gradlew.bat localSimulationServer -PsimWorldArchive="src/test/resources/fixtures/region.civworld.gz"
   ```

4. Den bereits vorhandenen 3D-Viewer unter `http://localhost:5173/live.html` öffnen (Viewer-Dev-Server wie in `docs/simulation-sandbox-manual-test.md` starten).
5. Szenario **Forester – Raw Hytale Snapshot** (`forester-imported`) auswählen und mit **Play** oder **Step** starten.
6. Die Bewegung des Försters und nach der simulierten Wachstumszeit neue Baumpositionen beobachten.
7. Mit Reset erneut starten; bei gleichen Eingaben und Seed muss der Lauf deterministisch bleiben.

## Was wird geprüft?

Die bestehende `.civworld.gz`-Verarbeitung liefert die Originalblocknamen. `ForesterSnapshotSurface` leitet für die Zielsuche konservativ freiliegende Gras-/Erdoberflächen und Baumstammpositionen daraus ab, ohne einen zweiten Importer einzuführen. Der Core prüft die echte 3D-Kugel (Radius 24), Abstand (mindestens vier horizontale Blöcke), Reservierungen, Navigationsergebnisse und Wiederholung der Arbeit.

Auf dem originalen Export wurden lokal 1.872.500 Zellen, 9.164 beobachtete geeignete Gras-/Erdoberflächenspalten und 1.057 Stammblöcke gezählt. Ein lokaler HTTP-Test des importierten Szenarios lief 3.200 Ticks und zeigte 43 **simulierte** Bäume.

## Noch nicht geprüft

Die Gebäudeplatzierung und Arbeitsplatzzuweisung des echten Försters, native Hytale-Sapling-Items, Verbrauch aus der originalen Kiste, echte NPC-Navigation und native Setzlingsplatzierung sowie Hytale-Wachstum. Diese Funktionen werden durch den Headless-Lauf nicht nachgewiesen.
