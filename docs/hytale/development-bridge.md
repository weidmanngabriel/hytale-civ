# Native APIs für die Entwicklungsbrücke

Der optionale lokale MCP nutzt eine private Bridge an der Plugin-Grenze. Einrichtung und aktueller Implementierungsumfang stehen in [local-mcp.md](../local-mcp.md).

## Strukturell verifiziert

Die im Projekt bereitgestellte Server-JAR enthält:

- `NPCPlugin.getRoleTemplateNames(boolean)` und `hasRoleName(String)` für native Rollenabfragen;
- `NPCPlugin.spawnNPC(Store, String, String, Vector3dc, Rotation3fc)` für den bereits im Soldier-Probe verwendeten Spawn;
- `Store.removeEntity(Ref, RemoveReason)` und `RemoveReason.REMOVE` für Entfernung registrierter Test-Entities;
- `World.execute(Runnable)` und `getChunkAsync(...)`, siehe [Threading](threading.md) und [Headless-Server](server-headless.md);
- `PluginManager.reload(PluginIdentifier)`. Diese Signatur ist kein Nachweis, dass Civ-ECS-Komponenten und laufende Zustände sicher hot-reloadbar sind.

Die Bridge begrenzt Engine-Mutationen auf eine eigene Flat-Welt und verfolgt nur dort selbst erzeugte Entity-Refs. Snapshots enthalten Werte/IDs; kein mutable Engine-Objekt wird an MCP ausgegeben. HTTP wartet außerhalb des World Threads.

## Offen

Der vollständige neue Bridge-Lifecycle, Wiederholbarkeit von Spawn/Reset, Client-Verbindung zur Offline-Instanz und sichtbare Animationen/UI benötigen einen ersten realen lokalen Durchlauf. Vorhandene Soldier-/Flat-Probes belegen die verwendeten bisherigen Engine-Pfade, nicht automatisch den neuen HTTP-/MCP-Ablauf.
