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
