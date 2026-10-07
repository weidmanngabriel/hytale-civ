# ADR 0009: Lokaler MCP für interaktive Hytale-Entwicklung

Status: Accepted

## Kontext

Feste Runtime-Probes prüfen Engine-Verträge und beenden die Sitzung. Für interaktive Entwicklung braucht der Coding-Agent zusätzlich einen einfachen Weg, in einen bereits laufenden lokalen Hytale-Prozess einzugreifen und Antworten direkt zu erhalten.

Der zuerst gebaute Attach-Modus mit `/civmcp on`, `bridge.json`, Session-Token, dynamischem Port und eigener strukturierter Gameplay-Bridge erwies sich praktisch als zu komplex und lieferte im Fehlerfall zu wenig direkte Diagnose.

## Entscheidung

Der lokale stdio-MCP bleibt außerhalb der JVM und behält Build, isoliertes Deployment sowie Prozesssteuerung für ausschließlich selbst gestartete Serverprozesse.

Der Zugriff auf ein normal laufendes Spiel wird dagegen auf eine kleine localhost-only Command Bridge reduziert:

- Die Civ-Mod bindet beim Pluginstart `127.0.0.1:5523` (konfigurierbar).
- `GET /health` dient nur der Erreichbarkeitsprüfung.
- `POST /command` führt einen nativen Hytale-Serverbefehl über `CommandManager.handleCommand` aus.
- Ein eigener `CommandSender` delegiert Identität und Rechte an `ConsoleSender`, sammelt aber `sendMessage`-Ausgaben und gibt sie direkt an den MCP zurück.
- Der MCP stellt dies als `hytale_command` bereit.

Der frühere Attach-/Entity-Bridge-Pfad wird entfernt. Es gibt keine `bridge.json`, keinen `/civmcp on`-Befehl und keine zweite strukturierte Remote-Gameplay-API.

## Konsequenzen

Der Zugriffspfad ist deutlich kleiner und unabhängig von Serverlogs. Ein erfolgreicher Command liefert seine Ausgabe direkt an den Agent.

Die Schnittstelle läuft als Serverkonsole. Player-only Commands funktionieren nicht automatisch. Wiederkehrende Civ-Entwicklungsaktionen sollen deshalb bei Bedarf als kleine console-fähige Dev-Kommandos ergänzt werden.

Die HTTP-Schnittstelle bindet nur Loopback und ist als lokales Entwicklungswerkzeug gedacht. Sie besitzt aktuell keine zusätzliche Authentifizierung.

GitHub Hytale Local bleibt unverändert und unabhängig. Ob es später noch benötigt wird, wird erst entschieden, wenn der MCP-Zugriff praktisch stabil verifiziert ist.
