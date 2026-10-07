# Lokaler Hytale-MCP-Server

Optionales Entwicklungswerkzeug mit zwei Modi: Der Agent kann eine eigene Testinstanz verwalten oder sich mit einer ausdrücklich freigegebenen normalen Einzelspieler-Session verbinden. Es startet keinen Client. Build/Deployment bleiben auf die eigene Testinstanz beschränkt.

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

## Mit deinem normalen Spiel verbinden

Für den Verbindungsmodus reichen Node.js ≥22, das aktuelle Repository und die aktuelle Civ-Mod inklusive Asset Pack in deiner Hytale-Welt. JDK-/Server-/Assets-Pfade in `.hytale-dev.json` sind erst für lokale Builds beziehungsweise den separaten Testserver erforderlich. Die MCP-Registrierung oben bleibt dieselbe.

1. Neue Civ-Version normal installieren und die Welt einmal neu öffnen, damit der neue Java-Code geladen wird. Das MCP-Deployment installiert weiterhin ausschließlich in die isolierte Runtime.
2. Als Besitzer der lokalen Einzelspieler-Session im Spiel `/civmcp on` eingeben. Die Bestätigung zeigt den Pfad der Verbindungsdatei. Keine Launcher-/JVM-Startparameter erforderlich.
3. Dem lokalen Agent sagen:

   > Verwende hytale-civ-local. Verbinde dich mit meinem laufenden Spiel über hytale_connect. Zeige mit hytale_context meine Welt, Position und NPCs in der Nähe.

4. `hytale_connect` liest standardmäßig `~/.hytale-civ/bridge.json` (`%USERPROFILE%\.hytale-civ\bridge.json` unter Windows). Falls Java und Node verschiedene Home-Verzeichnisse verwenden, den im Spiel angezeigten absoluten Pfad als `connectionFile` übergeben. Inhalt niemals in Chat, Logs oder Git kopieren: Er enthält den Zugriffstoken.
5. Einen vorhandenen Civ-NPC aus `hytale_context.nearby` anhand seiner UUID mit `hytale_select` auswählen. Dessen Handle erlaubt `hytale_move`, `hytale_profession` und Beobachtung. Das Auslesen der Umgebung wählt niemanden automatisch aus. Nicht beanspruchte Vanilla-NPCs müssen zunächst über den normalen `/civclaim`-Ablauf zu Civ-Bewohnern werden.
6. Alternativ mit `hytale_spawn` einen NPC an einem ausdrücklich gewählten freien Platz in deiner Nähe erzeugen. Die Koordinaten sind absolute Weltkoordinaten; `hytale_context` liefert die Spielerposition. Der Agent prüft den Platz mit dir beziehungsweise anhand des vorhandenen Weltwissens; die Brücke garantiert keinen kollisionsfreien Spawn.
7. `hytale_reset` entfernt nur aktuell geladene NPCs, die diese Bridge-Session selbst erzeugt hat. Bereits vorhandene ausgewählte NPCs bleiben bestehen. Nicht geladene erzeugte NPCs behalten ihre Handles und werden bei einem späteren Reset erneut versucht; `removed` zählt nur tatsächlich entfernte NPCs.
8. `hytale_disconnect` trennt den Agent, ohne Spiel oder NPCs zu stoppen. `/civmcp off` schließt zusätzlich die Brücke. Vor `off`/Weltende bei Bedarf die Test-NPCs zurücksetzen: Die Liste erzeugter NPCs ist nicht persistent und geht beim Ausschalten der Brücke verloren. Erzeugte NPCs können durch Hytales normale Speicherung im Spielstand bleiben.

Die Freigabe gilt für die Welt, in der `/civmcp on` ausgeführt wurde, und den lokalen Besitzer. Beim Verlassen dieser Welt schlagen Zugriffe fehl. Für einen Weltwechsel erst `hytale_disconnect`, dann `/civmcp off` und in der neuen Welt `/civmcp on` ausführen; danach neu verbinden. Beim Plugin-Shutdown wird die Brücke geschlossen. Ein erneutes `on` bei aktiver Brücke wechselt weder Welt noch Token.

Nur Hytales nativer Einzelspieler-Besitzer darf `on/off` ausführen; LAN-Gäste und Spieler auf fremden dedizierten Servern können keine Brücke öffnen. Die HTTP-Verbindung bleibt auf `127.0.0.1`. Ein exklusiver Dateilock verhindert, dass zwei normale Spielprozesse dieselbe Verbindungsdatei übernehmen. Nach einem Crash gibt der OS-Lock die Datei frei; das nächste `on` ersetzt alte Zugangsdaten. Windows nutzt die Rechte des Benutzerprofils, POSIX schreibt die Zugangsdaten mit `0600`.

Live-Mutationen sind auf bereits geladene Chunks innerhalb von 64 Blöcken um den Besitzer beschränkt. Pro Session sind höchstens 32 erzeugte und insgesamt 64 verfolgte NPCs zulässig. Ausgewählte NPCs werden über UUID auf dem World Thread erneut aufgelöst, statt alte Entity-Slots weiterzuverwenden. Der Kontext listet höchstens 100 geladene NPCs im Umkreis.

**Aktionen verändern den echten Spielstand.** Bewegung und Berufsänderung sind normale Civ-Aufträge und wirken auch auf vorhandene Bewohner. Der MCP stoppt oder installiert nichts in der verbundenen Spielsession: `start/stop/deploy` sind während einer Live-Verbindung gesperrt. `hytale_build` bleibt verfügbar; Installation neuer Java-Versionen in deiner normalen Spielinstallation und erneutes Öffnen der Welt bleiben normale lokale Entwicklungsschritte.

Arena und fertiges Soldier-Szenario stehen ausschließlich im separaten Testserver zur Verfügung. In deiner Welt spawnen Experimente an bewusst gewählten Koordinaten. `hytale_logs` bietet keine fremden Prozesslogs; in Live-Sessions stattdessen Zustände über `context/entities/observe` abfragen und bei Bedarf den normalen lokalen Hytale-Serverlog lesen. Automatische Screenshots, Kamera-/Tastatursteuerung und Hot-Reload sind weiterhin nicht enthalten.

### Einfacher Abnahmetest

1. Welt öffnen, `/civmcp on`, dann Agent verbinden lassen. Weltname und Spielerposition müssen deiner Session entsprechen.
2. Einen Civ-NPC auswählen und ihm ein nahes freies Bewegungsziel geben. Bewegung im Spiel und mit `hytale_observe` prüfen.
3. Einen zweiten Test-NPC erzeugen und `hytale_reset` ausführen. Nur der erzeugte NPC darf verschwinden; der ausgewählte vorhandene NPC bleibt bestehen.
4. `hytale_disconnect` ausführen. Spiel und beide normalen Spielprozesse bleiben offen; erneutes Verbinden funktioniert ohne Weltneustart.
5. `/civmcp off` ausführen. Weitere MCP-Zugriffe müssen fehlschlagen; `/civmcp on` plus neues Verbinden muss wieder funktionieren.

Der erste vollständige Client-/Einzelspieler-Durchlauf ist noch praktisch zu verifizieren. CI beweist Kompilierung, Zugriffsschutz und Lifecycle-Grenzen, nicht das sichtbare Spielerlebnis.

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
| `hytale_connect`, `hytale_disconnect` | Live-Spiel verbinden / trennen, ohne Prozessbesitz |
| `hytale_context`, `hytale_select` | Spieler und nahe NPCs lesen / bestehenden Civ-NPC gezielt auswählen |
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

Im isolierten Modus maximal 32 erzeugte NPCs; Positionen bleiben in der vorgeladenen Arena. Ein Civ-Beruf ist nur für `Civ_Inhabitant` zulässig. Andere Berufe sind verfügbar, aber ohne passende Gebäude/Felder/Rohstoffe noch kein kompletter Berufsversuch.

## Grenzen und Lifecycle

- MCP verwendet stdio; die HTTP-Brücke ist eine private interne Schnittstelle. Sie ist mit `-Dcivilizations.devBridge=true` opt-in, an `127.0.0.1` gebunden und prüft zufälligen Token, Session-ID und fehlenden Browser-Origin.
- Die eigene Runtime liegt unter `.hytale-dev/runtime`; ein Ownership-Marker bindet sie an den Checkout. Deployment ersetzt nur deren `mods`, nicht die normale Spielinstallation. Verzeichnislinks werden dort nicht übernommen.
- Standardports: Spiel `127.0.0.1:5521`, Brücke `127.0.0.1:5522`; lokal konfigurierbar.
- Beim Schließen des MCP-Clients bleibt eine verbundene fremde Spielsession unverändert. Für dessen eigenen Java-Kindprozess wird ein nativer Shutdown angefordert. Nach zehn Sekunden darf nur dieser eigene Prozess beendet werden. `hytale_stop` wartet 15 Sekunden und meldet einen noch ausstehenden Shutdown.
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

Diese Tests sichern MCP-Lifecycle, stdio, Argumentvalidierung, Fehlerantworten, Logs und Deployment-Dateibesitz. Ein POSIX-Lifecycle-Test führt Build/Deployment/Start/Status/Stop mit einem eigenen Fake-Prozess aus; er beweist keine Hytale-Semantik. Die CI prüft die transport- und konfigurationsbezogenen Node-Tests auf Linux und Windows. Java-Tests prüfen den echten HTTP-Zugriffsschutz und dass Reset ausschließlich eigene Entities entfernt, einschließlich behaltenem Tracking bei nicht geladenen Entities. Node-Tests verwenden einen externen lokalen HTTP-Testserver für Attach/Detach, Authentifizierung, Session-Mismatch und die Sperre fremder Prozesssteuerung. Gradle prüft die Bridge gegen die gepinnte Hytale-API. Der erste echte lokale Soldier-Durchlauf ist damit noch nicht nachgewiesen.
