# Native APIs für die Entwicklungsbrücke

Der optionale lokale MCP nutzt eine private Bridge an der Plugin-Grenze. Einrichtung und aktueller Implementierungsumfang stehen in [local-mcp.md](../local-mcp.md).

## Strukturell verifiziert

Die im Projekt bereitgestellte Server-JAR enthält:

- `NPCPlugin.getRoleTemplateNames(boolean)` und `hasRoleName(String)` für native Rollenabfragen;
- `NPCPlugin.spawnNPC(Store, String, String, Vector3dc, Rotation3fc)` für den bereits im Soldier-Probe verwendeten Spawn;
- `Store.removeEntity(Ref, RemoveReason)` und `RemoveReason.REMOVE` für Entfernung registrierter Test-Entities;
- `World.execute(Runnable)` und `getChunkAsync(...)`, siehe [Threading](threading.md) und [Headless-Server](server-headless.md);
- `PluginManager.reload(PluginIdentifier)`. Diese Signatur ist kein Nachweis, dass Civ-ECS-Komponenten und laufende Zustände sicher hot-reloadbar sind.

Im isolierten Modus begrenzt die Bridge Mutationen auf eine eigene Flat-Welt. Der Live-Modus bindet sich explizit an eine bestehende World-Instanz und den freigebenden Einzelspieler-Besitzer. Er löst Entity-UUIDs erneut auf dem World Thread auf und verändert nur geladene Positionen im begrenzten Spielerumkreis. Snapshots enthalten Werte/IDs; kein mutable Engine-Objekt wird an MCP ausgegeben. HTTP wartet außerhalb des World Threads.

## Für den Live-Modus strukturell verifiziert

Die bereitgestellte JAR enthält zusätzlich `SingleplayerModule.isOwner(PlayerRef)` (statisch), `World.getPlayerRefs()`, `World.isAlive()`, `Universe.getWorld(UUID)` und `EntityStore.getRefFromUUID(UUID)`. `Store.forEachChunk(Query, BiConsumer)` mit `Archetype.of(...)` ist bereits im Produktionscode zur NPC-Abfrage verwendet. `AbstractPlayerCommand` ist laut offizieller [API-Dokumentation](https://docs.hytale.com/api/com/hypixel/hytale/server/core/command/system/basecommands/AbstractPlayerCommand) an den World Thread des sendenden Spielers gebunden. Die Aktivierung benutzt diesen nativen Befehlseinstieg; HTTP-/Datei-I/O der Freigabe läuft anschließend außerhalb des World Threads.

Hytale beschreibt [Einzelspieler als lokalen Server](https://hytale.com/news/2025/11/hytale-modding-strategy-and-status) mit ausgewählten Mods. Ein zusätzliches Client-Plugin oder eine dokumentierte allgemeine Hytale-MCP-API ist für diese serverseitige Bridge nicht nötig. Der Client-Lifecycle des neuen `/civmcp`-Befehls ist damit noch nicht praktisch nachgewiesen.

## Offen

Der vollständige neue Bridge-Lifecycle, Wiederholbarkeit von Spawn/Reset, Client-Verbindung zur Offline-Instanz und sichtbare Animationen/UI benötigen einen ersten realen lokalen Durchlauf. Vorhandene Soldier-/Flat-Probes belegen die verwendeten bisherigen Engine-Pfade, nicht automatisch den neuen HTTP-/MCP-Ablauf.
