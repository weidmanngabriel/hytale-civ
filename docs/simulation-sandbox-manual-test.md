# Lokale Civ Simulation Sandbox testen (Windows)

Diese Schritte testen ausschließlich die Headless-Simulation. Das echte Hytale-Spiel muss für den normalen Start **nicht** laufen. Voraussetzungen: JDK 25, Node.js, Git und das ausgecheckte Repository.

1. **PowerShell 1** im Repository öffnen und starten:
   ```powershell
   .\gradlew.bat localSimulationServer
   ```
   Das Fenster offen lassen. Es sollte `http://localhost:8765/api/state` melden.
2. **PowerShell 2** öffnen und den Viewer starten:
   ```powershell
   cd web-viewer
   npm ci
   npm run dev
   ```
3. Im Browser `http://localhost:5173/` aufrufen. Auf der Startseite sollte **CIV LIVE SANDBOX** stehen. Szenario auswählen, **Start** drücken. Der Tick-Zähler läuft; **Pause** stoppt ihn; **Einzelschritt** erhöht ihn genau einmal; **Reset** beginnt beim gleichen Startzustand.
4. Auf das 3D-Bild klicken, um die Maussteuerung zu aktivieren. Mit WASD fliegen, Maus drehen, Leertaste aufwärts und Shift abwärts. Mit dem Mausrad einige Rastungen scrollen: Das angezeigte Flugtempo soll nur leicht steigen oder sinken (je Rastung ca. 5 %). **Escape** gibt die Maus wieder frei.
5. Im rechten Inspector einen Arbeiter wählen. Die Szenario-Parameter verändern, **Startzustand übernehmen**, dann laufen lassen und kontrollieren, dass die NPC-Zahl stimmt. Die Ereignisliste muss reale Zustandswechsel zeigen, sobald Arbeit anfällt.
6. Für eine echte Weltregion zunächst mit installierter Civ-Mod im geladenen Hytale-Server `/worldexport x y z width height depth` ausführen (maximal 2.000.000 Zellen; sehr große Exporte können den laufenden Server vorübergehend belasten). Die Serverantwort enthält einen `.civworld.gz`-Pfad. Diesen unveränderten Snapshot auf dem Entwicklungs-PC bereithalten.
7. Den Java-Server mit **Strg+C** beenden und mit Weltdatei erneut starten:
   ```powershell
   .\gradlew.bat localSimulationServer -PsimWorldArchive="C:\Pfad\zur\region.civworld.gz"
   ```
   Viewer im Browser neu laden. Gelände und Hohlräume sollten angezeigt werden. Frei in den Fels fliegen: Solide Vorderflächen dürfen die Sicht aus dem Fels heraus nicht abschneiden; Höhlenwände dahinter sollten sichtbar sein.
8. Ein Miner-Szenario auswählen, Minerzahl verändern, starten und die Voxeldarstellung und das Ereignisprotokoll beobachten. Mit **Reset** muss die exportierte Ausgangswelt unverändert wiederhergestellt werden. Nicht jede Region enthält eine passende Anfangsposition oder genügend Platz für eine automatisch geplante Mine.

**Abnahmegrenze:** Ein Browsercheck belegt keine native Hytale-Navigation. Der Core-geplante Headless-Minenablauf dient als Diagnose der eigenen Civ-Simulationsverträge, nicht als Vollersatz für echte Hytale-Runtime-Tests. Das Live-UI besitzt keine feste Tick-Obergrenze. Gespeicherte Spielerwelten nicht ins Repository committen.

**Performance-Aufnahme:** Im Viewer **Performance aufnehmen** drücken, ungefähr 30–60 Sekunden Kamera bewegen und Miner laufen lassen, anschließend **Aufnahme stoppen & JSON speichern** drücken. Die heruntergeladene Datei `civ-live-performance-*.json` kann zur Diagnose geteilt werden. Erfasst werden FPS/Framezeiten, Render-/Mesh-Kosten, Netzwerkanfragen, Draw Calls, Dreiecke und Simulationsstatus; keine Bildschirminhalte oder Spielstanddateien. Eine wiederholte Aufnahme mit pausierter Simulation trennt Renderinglast von laufenden Terrainänderungen.

**Trace bei blockierten Minern:** Nach Start der Performance-Aufnahme die Miner circa 30 Sekunden arbeiten lassen. Die exportierte JSON-Datei enthält pro Sekunde die NPC-Zustände/Positionen und die letzten Ereignisse sowie den Render-/Netzwerkaufwand. Die Aufnahme kann direkt an ChatGPT zur Diagnose gesendet werden.

**Hinweis für große Archive:** Beim Start des Java-Servers werden Miner nicht mehr automatisch erstellt. Nach dem Laden des Viewers zunächst die Welt ohne NPCs prüfen; **Miner starten** löst anschließend die gesonderte Miner-Initialisierung aus. Erst wenn `Civ local simulation API: http://localhost:8765/api/state` erscheint, ist der HTTP-Server erreichbar. Bei sehr großen Archiven kann das Einlesen der Datei vor dieser Meldung noch dauern.

**Startdiagnose:** `[CIV STARTUP] Reading archive`, `Archive decoded`, `Creating voxel index and HTTP server` und `READY` protokollieren Phase, Gesamtdauer und JVM-Heap. Falls `READY` fehlt, die letzte sichtbare Startphase und etwaige Exception kopieren. Optional PowerShell-Ausgabe mit `Tee-Object -FilePath civ-startup.log` sichern; die Gradle-Prozentanzeige ist bei dem dauerhaft laufenden Task nicht aussagekräftig.

## Mine_01-Labor (Phasen A–C) – einfacher Abnahmetest

1. Nach `git pull` Java-Server und Browser-Viewer **neu starten**, mit `-PsimWorldArchive=\"C:\\Pfad\\zur\\region.civworld.gz\"` für den Java-Server. Hytale selbst muss nicht laufen.
2. Rechts im **Minen-Labor** auf **Mine_01 automatisch platzieren** klicken. Falls für die Region keine passende Position gefunden wird, stattdessen eine explizite **Prefab-Anker**-Position X/Y/Z wählen; die Mine_01-Basis liegt 16 Blöcke unter dem vorgesehenen Gelände-Zugang und das gesamte Prefab muss im Export liegen.
3. Auf **Zur Mine fliegen** klicken. Gebäude-/Zugangsmarker, Tunnelanschluss sowie geplante Arbeitsfronten erscheinen als farbige Debug-Elemente. **Marker / Arbeitsfront** und **NPC-Wege und Ziele** lassen sich unabhängig ein-/ausblenden.
4. Im Miner-Abschnitt **3** einstellen und **Miner starten** drücken. Danach **Start**. Die NPC-Zustände und ihre Bewegungsziele kontrollieren. Der Headless-Miner verwendet eine vereinfachte Bewegung im Prefab und A* für die Voxel-Tunnel, nicht Hytales echten Seek-Pathfinder.
5. In der **Dev-Konsole (Simulator)** nacheinander `/sim help`, `/civdev mines`, `/sim mine info`, `/sim markers off`, `/sim markers on` und optional `/sim block X Y Z AIR` ausprobieren. Diese Konsole führt nur freigegebene Simulationsbefehle aus, keine beliebigen Hytale-Commands.
6. Auf **Reset** klicken. Das Prefab muss wiederhergestellt werden. Für fehlerhafte Miner-Navigation mit **Performance aufnehmen** 30–60 Sekunden protokollieren, **Aufnahme stoppen & JSON speichern** klicken und die JSON-Datei teilen.

**Bekannte Grenze:** Gemeinsame Route (Zugang → Connector → Arbeitsfront), Core-Minenplanung und Front-Claims werden verwendet. Der komplette produktive Miner-Task-Ablauf mit allen Räumen, Infrastrukturmaßnahmen und Recoveries ist noch kein gemeinsamer Core-Controller; vollständige Parität gehört weiterhin zu Phase 5B. Keine automatische Hytale-Runtime in der CI.

**Gezielte Miner-Diagnose:** Nach Platzieren der Mine und Starten der Miner unter „Dev-Konsole“ `/sim worker miner-2` eingeben. Die Ausgabe enthält Position, Zustand, aktuelles Wegziel, Zahl verbleibender Wegpunkte und den gemeldeten Blockadegrund. Bei Bedarf nacheinander alle Miner abfragen; `/sim worker` ist read-only und verändert keine Aufgaben.
