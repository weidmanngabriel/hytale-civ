# ADR 0009: Lokaler MCP für interaktive Hytale-Entwicklung

Status: Proposed

## Kontext

Feste Runtime-Probes prüfen Engine-Verträge und beenden die Sitzung. API-Experimente benötigen wiederholbare Spielaktionen und Beobachtungen ohne manuellen Plugin-/Log-Austausch.

## Entscheidung

Ein lokaler stdio-MCP-Prozess steuert Build, isoliertes Deployment und eine eigene Hytale-Instanz. Eine opt-in HTTP-Brücke in Civ übersetzt begrenzte Entwicklungsaktionen in vorhandene Civ-Aufrufe und native APIs. Der Core bekommt keine MCP-Abhängigkeit oder neue Gameplay-Regeln.

MCP bleibt außerhalb der JVM, damit auch bei gestopptem oder fehlerhaftem Server Builds und Logs erreichbar bleiben. Die kleine stdio-Implementierung benötigt nur Node-Builtins und wird durch Protokoll-/Transporttests geschützt. Optionale Resources, Prompts, Sampling und Cancellation werden nicht angeboten.

Eine allgemeine Community-MCP-Abhängigkeit würde für die benötigten Civ-Zustände weiterhin eigene Adapter erfordern. Daher wird zunächst eine kleine projektspezifische Brücke verwendet. Native Engine-Zustände bleiben die Wahrheit; simulierte Treffer/Schadenswerte gehören nicht in dieses Werkzeug.

## Konsequenzen

Der Server bleibt zwischen NPC-Experimenten warm; Java-Änderungen benötigen zunächst automatisierten Neustart. Tests ohne Engine sichern Protokoll und Prozess-/Dateigrenzen. Neue Live-/Clientverträge bleiben lokal zu verifizieren. GitHub Hytale Local bleibt ein unabhängiges optionales Diagnosewerkzeug. Vor öffentlicher Nutzung sind reale Windows-/Clientläufe und zusätzliche Versions-/Lifecycle-Prüfungen erforderlich.

## Erweiterung: normale Einzelspieler-Session

Ein Attach-Modus verbindet eine explizit mit `/civmcp on` aktivierte Bridge. Die Aktivierung erfolgt als nativer Player Command mit Einzelspieler-Owner-Prüfung, damit keine unbestätigten Launcher-Startparameter vorausgesetzt werden. Eine lokale, gesperrte Verbindungsdatei enthält zufällige Session-Zugangsdaten und einen OS-gewählten Loopback-Port. Der MCP nimmt keinen Besitz des fremden Spielprozesses an; Start/Stop/Deployment sind während Attach gesperrt.

Die Live-Bridge bindet eine World-Instanz und den freigebenden Besitzer. Bestehende Civ-NPCs müssen explizit anhand ihrer UUID ausgewählt werden und sind von selbst erzeugten NPCs getrennt. Ein Reset darf nur eigene geladene NPCs entfernen. Die Session-Handle-Liste wird nicht persistiert; Abschalten erhält Spielzustand und NPCs. Das vermeidet versteckte Löschaktionen beim Trennen, verlangt aber einen bewussten Reset vor dem Abschalten, wenn Test-NPCs entfernt werden sollen.
