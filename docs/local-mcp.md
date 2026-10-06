# Lokaler Hytale-MCP-Server

Optionales Entwicklungswerkzeug: Ein lokaler Agent baut Civ, installiert die Mod in eine eigene Serverinstanz und experimentiert mit echten NPCs. Es startet keinen Client und verwendet nicht die normale Hytale-Mods-Installation.

## Einrichtung unter Windows

1. Aktuellen Repository-Stand holen.
2. Node.js 22 oder neuer installieren; `node --version` im VS-Code-Terminal prüfen.
3. JDK 25, `HytaleServer.jar` und `Assets.zip` bereitstellen. Die Runtime muss zur gepinnten Version in `gradle.properties` passen, aktuell `0.6.8`. Automatische Hytale-Updates sind nicht enthalten.
4. `tools/hytale-mcp/config.example.json` als `.hytale-dev.json` ins Repository-Hauptverzeichnis kopieren und die drei Pfadplatzhalter ersetzen. `javaExecutable` muss auf die `bin/java.exe` des JDK 25 zeigen. Daraus wird auch Gradles `JAVA_HOME` abgeleitet.
5. MCP im verwendeten Agent konfigurieren, anschließend dessen Sitzung/Erweiterung neu starten.

Konfiguration und Runtime sind gitignored. Alternativ können `HYTALE_SERVER_JAR`, `HYTALE_ASSETS_PATH` und `JAVA_HOME` in der Umgebung des MCP-Prozesses gesetzt werden. Werkzeug-Erkennung funktioniert bereits ohne Spielinstallation; ein Runtime-Start benötigt die vollständige Einrichtung.

### Codex CLI / Codex-Erweiterung

Einmal im Repository-Hauptverzeichnis in PowerShell ausführen:

```powershell
codex mcp add hytale-civ-local -- node "$PWD/tools/hytale-mcp/server.mjs"
codex mcp list
```

CLI und IDE-Erweiterung teilen die Konfiguration. Ohne installierten CLI-Befehl: Im Zahnradmenü der Codex-Erweiterung **MCP servers > Add server** wählen, Typ **STDIO**, Befehl `node`, Argument der absolute lokale Pfad zu `tools/hytale-mcp/server.mjs`; speichern und die Erweiterung neu starten.

### VS Codes eingebauter Agent / Copilot

`.vscode/mcp.json` ist enthalten. Über **MCP: List Servers** `hytale-civ-local` starten und im Agent-Modus dessen Werkzeuge aktivieren. Diese Datei konfiguriert nicht automatisch die separate Codex-Erweiterung.

Quellen: [offizielle Codex-MCP-Dokumentation](https://developers.openai.com/codex/mcp), [VS-Code-MCP-Konfiguration](https://code.visualstudio.com/docs/agents/reference/mcp-configuration).

## Erster Soldier-Durchlauf

Dem lokalen Agent diese Anweisung geben:

> Verwende hytale-civ-local. Prüfe den Status, baue die Mod, warte auf den erfolgreichen Build, deploye und starte den Entwicklungsserver. Sobald er bereit ist, erstelle die Arena und starte das Soldier-Szenario. Beobachte echte Positionen und HP zehn Sekunden lang und zeige mir Ergebnisse und relevante Logs.

Werkzeugfolge:

1. `hytale_status` prüft die Konfiguration.
2. `hytale_build` startet Gradle `test build`. Die Job-ID merken.
3. `hytale_job` mit der ID aufrufen, bis `state: succeeded` erscheint; `after` liest Ausgabe inkrementell.
4. `hytale_deploy` mit dieser ID installiert den letzten erfolgreichen Build. Ein laufender Entwicklungsserver muss vorher mit `hytale_stop` beendet werden.
5. `hytale_start`, danach `hytale_status`, bis `bridge.ready: true` erscheint.
6. `hytale_arena`, dann `hytale_soldier_scenario` erzeugen die Flat-Arena, einen Civ-Soldier mit normaler Berufsausrüstung und `Chicken_Undead` als Gegner.
7. `hytale_observe` mit `seconds: 10` liefert Samples mit nativen numerischen HP, Positionen und vorhandenen Civ-Zuständen.
8. `hytale_move` mit dem Soldier-Handle und `x: -6, y: 1, z: 0.5` prüft den normalen manuellen Bewegungsauftrag einschließlich Combat-Unterbrechung. Danach erneut beobachten.
9. `hytale_reset` entfernt die registrierten Test-NPCs. Das nächste Szenario benötigt keinen neuen Serverboot.
10. Nach Java-Änderungen erneut bauen, Ergebnis abwarten, stoppen, deployen, starten und das Experiment wiederholen.

Ein akzeptierter Auftrag ist kein Erfolgstest. Reale HP-Änderungen und Zustandsfolgen müssen beobachtet werden. Damage-Filter-Logs entstehen vor endgültiger Anwendung/Stornierung und beweisen allein keinen Treffer.

## Werkzeuge

| Werkzeug | Aufgabe |
|---|---|
| `hytale_status` | Konfiguration, HEAD/Dirty-Zustand, eigener Prozess und Bereitschaft |
| `hytale_build`, `hytale_job` | Asynchroner Gradle-Test-/Build-Job und Ausgabe |
| `hytale_deploy` | Erfolgreichen neuesten Build in die eigene Runtime kopieren |
| `hytale_start`, `hytale_stop` | Eigene Offline-Instanz starten / nativ herunterfahren |
| `hytale_logs` | Begrenzte stdout/stderr-Ausgabe mit Cursor/Textfilter |
| `hytale_roles` | NPC-Rollen im tatsächlichen Server suchen |
| `hytale_arena` | Flat-Testwelt erzeugen, neun Chunks vorladen |
| `hytale_soldier_scenario` | Soldier und nativen Gegner erzeugen |
| `hytale_spawn` | Native Rolle in der Arena erzeugen; optional Civ-Beruf |
| `hytale_entities`, `hytale_observe` | Einmalige / zeitlich gesampelte Zustandsabfrage |
| `hytale_move`, `hytale_profession` | Vorhandene Civ-Aufträge/Berufsaufrufe |
| `hytale_reset` | Nur von dieser Brücke registrierte NPCs entfernen |

Maximal 32 registrierte NPCs; Positionen bleiben in der vorgeladenen Arena. Ein Civ-Beruf ist nur für `Civ_Inhabitant` zulässig. Andere Berufe sind verfügbar, aber ohne passende Gebäude/Felder/Rohstoffe noch kein kompletter Berufsversuch.

## Grenzen und Lifecycle

- MCP verwendet stdio; die HTTP-Brücke ist eine private interne Schnittstelle. Sie ist mit `-Dcivilizations.devBridge=true` opt-in, an `127.0.0.1` gebunden und prüft zufälligen Token, Session-ID und fehlenden Browser-Origin.
- Die eigene Runtime liegt unter `.hytale-dev/runtime`; ein Ownership-Marker bindet sie an den Checkout. Deployment ersetzt nur deren `mods`, nicht die normale Spielinstallation. Verzeichnislinks werden dort nicht übernommen.
- Standardports: Spiel `127.0.0.1:5521`, Brücke `127.0.0.1:5522`; lokal konfigurierbar.
- Beim Schließen des MCP-Clients wird für dessen Java-Kindprozess ein nativer Shutdown angefordert. Nach zehn Sekunden darf nur dieser eigene Prozess beendet werden. `hytale_stop` wartet 15 Sekunden und meldet einen noch ausstehenden Shutdown.
- Java-Änderungen benötigen Build + Neustart. Zuverlässiges Plugin-Hot-Reload ist nicht nachgewiesen. NPC-Reset hält die laufende Engine warm.
- Alle Entity-/Weltzugriffe laufen auf dem zuständigen World Thread. HTTP-Wartezeiten blockieren keinen World Thread.
- Ein Mutationstimeout kann eine trotzdem ausgeführte Aktion bedeuten. Zustand prüfen, nicht automatisch erneut spawnen.
- Artefaktidentität: Plugin-SHA-256 plus HEAD-/Dirty-Zustand bei Buildbeginn. Ein Dirty-Build beweist keinen unveränderten Commit-Code.
- Eine ungültige Entity-Ref beweist keinen Tod. HP und weitere Beobachtungen müssen getrennt ausgewertet werden.
- Arenen bekommen pro Serverstart einen neuen Namen. Weltdateien/Logs bleiben lokal; nach vollständigem Shutdown kann `.hytale-dev/runtime` manuell entfernt werden.
- Nicht enthalten: automatische Clientsteuerung/Screenshots, freie Java-Ausführung, Gebäudefixtures, Hot-Reload. Ein Client zur Offline-Instanz sowie Animationen/UI sind noch praktisch zu prüfen.
- Hytale Local bleibt unabhängig: Diese direkte lokale Sitzung benutzt keine GitHub-Issue-Kommentare oder Self-Hosted-Runner-Jobs.

## Prüfung ohne Hytale

```powershell
node --test tools/hytale-mcp/test/*.test.mjs
```

Diese Tests sichern MCP-Lifecycle, stdio, Argumentvalidierung, Fehlerantworten, Logs und Deployment-Dateibesitz. Ein POSIX-Lifecycle-Test führt Build/Deployment/Start/Status/Stop mit einem eigenen Fake-Prozess aus; er beweist keine Hytale-Semantik. Die CI prüft die transport- und konfigurationsbezogenen Node-Tests auf Linux und Windows. Java-Tests prüfen den echten HTTP-Zugriffsschutz ohne Gameplay-Runtime. Gradle prüft die Bridge gegen die gepinnte Hytale-API. Der erste echte lokale Soldier-Durchlauf ist damit noch nicht nachgewiesen.
