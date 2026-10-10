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
6. Für eine echte Weltregion zunächst mit installierter Civ-Mod im geladenen Hytale-Server `/worldexport x y z width height depth` ausführen (maximal 250.000 Zellen). Die Serverantwort enthält einen `.civworld.gz`-Pfad. Diesen unveränderten Snapshot auf dem Entwicklungs-PC bereithalten.
7. Den Java-Server mit **Strg+C** beenden und mit Weltdatei erneut starten:
   ```powershell
   .\gradlew.bat localSimulationServer -PsimWorldArchive="C:\Pfad\zur\region.civworld.gz"
   ```
   Viewer im Browser neu laden. Gelände und Hohlräume sollten angezeigt werden. Frei in den Fels fliegen: Solide Vorderflächen dürfen die Sicht aus dem Fels heraus nicht abschneiden; Höhlenwände dahinter sollten sichtbar sein.
8. Ein Miner-Szenario auswählen, Minerzahl verändern, starten und die Voxeldarstellung und das Ereignisprotokoll beobachten. Mit **Reset** muss die exportierte Ausgangswelt unverändert wiederhergestellt werden. Nicht jede Region enthält eine passende Anfangsposition oder genügend Platz für eine automatisch geplante Mine.

**Abnahmegrenze:** Ein Browsercheck belegt keine native Hytale-Navigation. Der Core-geplante Headless-Minenablauf dient als Diagnose der eigenen Civ-Simulationsverträge, nicht als Vollersatz für echte Hytale-Runtime-Tests. Das Live-UI besitzt keine feste Tick-Obergrenze. Gespeicherte Spielerwelten nicht ins Repository committen.

**Performance-Aufnahme:** Im Viewer **Performance aufnehmen** drücken, ungefähr 30–60 Sekunden Kamera bewegen und Miner laufen lassen, anschließend **Aufnahme stoppen & JSON speichern** drücken. Die heruntergeladene Datei `civ-live-performance-*.json` kann zur Diagnose geteilt werden. Erfasst werden FPS/Framezeiten, Render-/Mesh-Kosten, Netzwerkanfragen, Draw Calls, Dreiecke und Simulationsstatus; keine Bildschirminhalte oder Spielstanddateien. Eine wiederholte Aufnahme mit pausierter Simulation trennt Renderinglast von laufenden Terrainänderungen.
