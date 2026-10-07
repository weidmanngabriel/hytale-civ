# Lokaler Hytale-MCP-Server

Der lokale MCP ist ein Entwicklungswerkzeug für Build/Deployment und für direkten Zugriff auf einen laufenden Hytale-Prozess. Hytale Local bleibt davon unabhängig.

## Normal laufendes Spiel steuern

Die Civ-Mod startet beim Pluginstart eine kleine HTTP-Schnittstelle ausschließlich auf `127.0.0.1`. Standardport ist `5523`. Es gibt keinen `/civmcp on`-Schritt, keine `bridge.json`, keinen Session-Token und keinen Attach-Lifecycle mehr.

Der MCP stellt dafür bereit:

- `hytale_status`: prüft unter anderem, ob die Command Bridge erreichbar ist.
- `hytale_command`: führt einen Hytale-Serverbefehl über Hytales nativen `CommandManager` aus und gibt die Nachrichten des Commands direkt als Ergebnis zurück.

Beispiel für den Agent:

> Verwende hytale-civ-local. Prüfe hytale_status und führe danach mit hytale_command den Serverbefehl `version` aus.

Der Zugriff erfolgt als Hytale-`ConsoleSender`. Befehle, die zwingend einen Spieler als Sender benötigen, funktionieren dadurch nicht automatisch. Für wiederholbare Civ-Entwicklungsaktionen sollen gezielt console-fähige Dev-Kommandos ergänzt werden, statt erneut eine zweite Remote-Gameplay-API aufzubauen.

Die Schnittstelle ist nur auf Loopback gebunden und damit nicht aus dem Netzwerk erreichbar. Sie besitzt aktuell bewusst keine zusätzliche Authentifizierung; sie ist ein lokales Entwicklungswerkzeug. Wer Code auf demselben Betriebssystemkonto ausführen kann, kann den lokalen Port ansprechen.

## MCP einrichten

Node.js 22 oder neuer wird benötigt.

### Codex CLI / Codex-Erweiterung

Im Repository-Hauptverzeichnis:

```powershell
codex mcp add hytale-civ-local -- node "$PWD/tools/hytale-mcp/server.mjs"
codex mcp list
```

### VS Code / Copilot

`.vscode/mcp.json` ist enthalten. Über **MCP: List Servers** den Server `hytale-civ-local` starten.

## Konfiguration

`tools/hytale-mcp/config.example.json` kann als `.hytale-dev.json` ins Repository-Hauptverzeichnis kopiert werden.

Relevante Werte:

- `commandBridgePort`: Standard `5523`.
- `serverJar`, `assetsPath`, `javaExecutable`, `gamePort`, `maxHeapMb`: nur für Build/Deployment beziehungsweise den vom MCP selbst gestarteten isolierten Server relevant.

Der Command-Port der laufenden normalen Hytale-Session und der Wert im MCP müssen übereinstimmen.

## Build-/Prozesswerkzeuge

Die bisherigen lokalen Build- und Prozesswerkzeuge bleiben vorhanden:

- `hytale_build`, `hytale_job`
- `hytale_deploy`
- `hytale_start`, `hytale_stop`
- `hytale_logs`

Diese Werkzeuge besitzen nur den von ihnen selbst gestarteten Prozess. `hytale_logs` liest deshalb nicht die Logs einer normal über den Launcher gestarteten Spielsession.

## Prüfung ohne Hytale

```powershell
node --test tools/hytale-mcp/test/*.test.mjs
```

Die Node-Tests prüfen MCP-Protokoll, Command-Transport, Status, Build-/Deployment-Lifecycle und Fehlerfälle. Der Java-Build prüft die verwendeten Hytale-API-Signaturen. Ein echter In-Game-Durchlauf muss zusätzlich bestätigen, dass die installierte Civ-Mod den Port öffnet und Hytales `CommandManager` Command-Ausgabe wie erwartet zurückliefert.
