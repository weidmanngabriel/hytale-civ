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
5. Eine der drei Varianten auswählen: **Forester – Raw Hytale Snapshot** (`forester-imported`), **Woodcutter – Raw Hytale Snapshot** (`woodcutter-imported`) oder **Forester + Woodcutter – Raw Hytale Snapshot** (`forest-cycle-imported`). Alle verwenden dasselbe unveränderte Originalarchiv.
6. Im Förster-Szenario die neue Bepflanzung beobachten; im Holzfäller-Szenario echte importierte Baumziele und das Fällen beobachten; im kombinierten Szenario beides gleichzeitig. Die `treesFelled`-Metrik muss bei Holzfällerbeteiligung ansteigen.
7. Mit Reset erneut starten; jeder Szenariostart lädt frische veränderbare Voxel aus demselben unveränderten Originalarchiv. Die alten Flat-World-Tests und Miner-Szenarien bleiben unberührt.

## Was wird geprüft?

Die bestehende `.civworld.gz`-Verarbeitung liefert die Originalblocknamen. `ForesterSnapshotSurface` leitet für die Zielsuche konservativ freiliegende Gras-/Erdoberflächen und Baumstammpositionen daraus ab, ohne einen zweiten Importer einzuführen. Der Core prüft die echte 3D-Kugel (Radius 24), Abstand (mindestens vier horizontale Blöcke), Reservierungen, Navigationsergebnisse und Wiederholung der Arbeit.

Auf dem originalen Export wurden lokal 1.872.500 Zellen, 9.164 beobachtete geeignete Gras-/Erdoberflächenspalten und 1.057 Stammblöcke gezählt. Ein lokaler HTTP-Test des importierten Szenarios lief 3.200 Ticks und zeigte 43 **simulierte** Bäume.

## Noch nicht geprüft

Die Gebäudeplatzierung und Arbeitsplatzzuweisung des echten Försters, native Hytale-Sapling-Items, Verbrauch aus der originalen Kiste, echte NPC-Navigation und native Setzlingsplatzierung sowie Hytale-Wachstum. Diese Funktionen werden durch den Headless-Lauf nicht nachgewiesen.


## Lokaler Regressionstest der kombinierten Originalwelt

Die aus dem Original-Snapshot abgeleiteten Stämme werden als semantische Baumziele initialisiert. Der Headless-Holzfäller sucht sich über den vorhandenen Voxel-A* eine tatsächlich begehbare Nachbarposition des Stamms. Ein lokal neu kompilierter Simulatorlauf über **2.400 Ticks** lieferte **4 gefällte Bäume** im Holzfäller-Einzelszenario und **17 gefällte Bäume** im gemeinsamen Szenario. Diese Zahlen sind lediglich Beobachtungen des Simulators, keine Zusage für jede spätere Seed- oder Weltversion. Der ursprüngliche Export bleibt unangetastet.

## Hytale Local

Das getrennte, read-only Diagnoseszenario `foresterassets` (`civforesterassetprobe`) liest die in der gepinnten Hytale-Runtime registrierten Setzlings-Blocktypen und deren Farming-Konfiguration aus. Es nimmt **keine** Pflanzung vor und ersetzt keinen Hytale-NPC-End-to-End-Test. Bis zur echten Pflanzvalidierung ist der Förster noch nicht als produktiv abgeschlossene Hytale-Funktion freigegeben.


## Native Ingame-Testanleitung für die Försterei

1. Die aktuelle Civ-Mod und das Asset Pack des fertig integrierten Branches beziehungsweise der neuen Main-Version installieren.
2. Ingame eine **Försterei** über das Gebäudemenü bauen und die Konstruktion abschließen.
3. Die native Truhe des Gebäudes öffnen und mindestens einen echten Setzling einlagern, etwa Eiche, Birke und Fichte mit beliebigen Stückzahlen.
4. Einen Civ-Bewohner beanspruchen und in dessen Personenaktionsmenü **Förster** auswählen. Einen geeigneten Standort innerhalb von 32 Blöcken der Försterei verwenden.
5. Beobachten, wie der Förster im Umfeld auf natürlich geeignete Flächen läuft und dort Setzlinge platziert. Der Arbeitsbereich ist eine echte 3D-Kugel mit 24 Blöcken Radius um das Gebäude; zu vorhandenen Stämmen bleiben mindestens vier Blöcke horizontaler Abstand.
6. Die Truhe erneut öffnen. Die Stückzahlen der Setzlinge müssen unverändert bleiben, auch nach mehreren Pflanzungen.
7. Eine Setzlingsart aus der Truhe entfernen beziehungsweise eine neue hinzufügen; bei nachfolgenden Pflanzungen darf nur noch eine der aktuell vorhandenen Arten ausgewählt werden. Bei vollständig leerer Truhe muss der Förster warten.
8. Für Wachstum die native Hytale-Farming-Mechanik beobachten; es gibt keine garantierte Wachstumsstufe nach einer festen Simulatortickzahl.

**Automatisierter Nachweis:** Der isolierte Hytale-Local-Lauf [39120832278](https://github.com/weidmanngabriel/hytale-civ/actions/runs/39120832278) wurde grün und umfasste die drei nativen Prüfungen samt echtem Förster-NPC, Setzlingsplatzierung und unverändertem Kistenbestand. Vollständiges Auswachsen und autonomes Fällen in derselben nativen Session bleiben ein gesonderter Langzeittest. Die im Abschnitt „Noch nicht geprüft“ oben genannten älteren Einschränkungen gelten nur für den damaligen Headless-Test, nicht mehr für die bestätigten nativen Platzierungen.
