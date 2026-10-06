# 0010: Java-Szenarien als Browser-Replay veröffentlichen

Status: Proposed

## Entscheidung

Die getesteten Java-Core-Abläufe bleiben die einzige fachliche Implementierung. Der neue Three.js-Viewer spielt versionierte Aufzeichnungen ab, statt Gameplay in JavaScript nachzubauen. Ein Recorder exportiert die initiale Voxelwelt und geordnete Änderungen sowie vollständige kleine Arbeiter-/Metrics-Snapshots. Rückwärtssprünge rekonstruieren den Zustand vom Anfang. Kamera, Picking und Playback gehören ausschließlich zur Präsentation.

Der erste Katalog enthält alle `SimulationScenarios` als 30-Sekunden-Läufe und `MinePrefabNavigationScenario` in allen vier Orientierungen bis zum Abschluss. Die Mine verwendet semantische Aktionen; ihre Wiedergabegeschwindigkeit ist keine simulierte Hytale-Arbeitszeit. Bestehende A*- und Fake-Bewegung bleiben geometrische Testhilfen, keine Hytale-Navigation.

Die Blockdarstellung erzeugt ausschließlich solid/air-Grenzflächen mit Normalen zur Luft und rendert deren Vorderseiten. Dadurch sieht eine Kamera innerhalb einer festen Masse durch diese Masse hindurch bis zur nächsten gegenüberliegenden Hohlraumwand. Materialwechsel innerhalb einer festen Masse erzeugen keine künstlichen Sichtflächen. Dies wird mit einem unabhängigen kleinen Hohlraumfall und Three.js-Raycasts geprüft.

## Veröffentlichung

`Simulation Recordings` läuft auf relevanten Pushes innerhalb dieses Repositories sowie manuell auf einem gewählten Branch. Java-Tests und Export liefern ein 30 Tage aufbewahrtes JSON-Artefakt; Testfehler und partielle Szenariofehler bleiben unterscheidbar. Die bestehende normale CI/Release-Pipeline bleibt erhalten.

`Simulation Pages` läuft aus vertrauenswürdigem `main`-Code. Der Publisher sammelt abgeschlossene interne Läufe desselben Recording-Workflows: höchstens drei pro Branch, insgesamt 40, innerhalb von 30 Tagen. Er validiert Commit, Branch, Run-ID, Attempt, Schema, Dateinamen und Größen. ZIP-Einträge werden im Speicher gelesen und niemals als Code ausgeführt oder mit ihren Pfaden extrahiert. Nur geprüfte JSON-Aufzeichnungen gelangen in die Pages-Site. Ein separater Deploy-Job besitzt die Pages-Schreibrechte.

Jede Veröffentlichung rekonstruiert den vollständigen Katalog aus den noch vorhandenen Artefakten. Veröffentlichungen sind serialisiert; sie überschreiben nicht nur den letzten Branch. Es entstehen keine Ergebnis-Commits. Alte/gelöschte/abgelaufene Artefakte sind kein dauerhaftes Archiv. Fehlgeschlagene Builds erscheinen ohne Replay mit einem Fehlerstatus. URLs identifizieren Commit und Run-Attempt statt nur einen beweglichen Branch-Namen.

Der Viewer wird immer aus `main` gebaut. Branches liefern Szenario-Daten; Änderungen am Viewer selbst müssen zum veröffentlichten Viewer integriert werden. Ein inkompatibles Schema wird explizit abgelehnt. Die Site ist öffentlich und enthält keine lizenzierten Hytale-Basisassets, nur vereinfachte Civ-Fixtures und deren Ergebnisse.

## Grenzen

Replay erlaubt freie Beobachtung, keine neuen Gameplay-Kommandos. Rendering und Touch-Steuerung ersetzen keine Hytale-Engine-/Client-Prüfung. Originalblockmodelle, Texturen, native Kollision, Navigation und reale Arbeitszeiten werden nicht behauptet. CPU-/GPU-Leistung auf echten Mobilgeräten bleibt gesondert zu überprüfen.
