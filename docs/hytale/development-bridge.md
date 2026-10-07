# Native APIs für den lokalen Entwicklungszugriff

Der lokale MCP greift nicht mehr über eine eigene Gameplay-Bridge mit Attach-Lifecycle auf Hytale zu. Stattdessen startet die Civ-Mod beim Pluginstart eine kleine localhost-only Command Bridge.

## Verifizierte native Bausteine

Die gepinnte Hytale-JAR enthält:

- `CommandManager.get()`;
- `CommandManager.handleCommand(CommandSender, String)` mit `CompletableFuture<Void>`;
- `ConsoleSender.INSTANCE`;
- `CommandSender.sendMessage(Message)`;
- `MessageUtil.toAnsiString(Message)`.

Diese Bausteine erlauben, einen nativen Hytale-Serverbefehl aus einem lokalen HTTP-Aufruf heraus auszuführen und dessen Command-Ausgabe direkt zu sammeln.

Die Implementierung verwendet dafür einen eigenen `CommandSender`, der Berechtigungen und Identität an Hytales `ConsoleSender` delegiert, aber `sendMessage` abfängt. Dadurch muss die Entwicklungssteuerung keine Serverlogs auswerten.

## Transport

Die Civ-Mod bindet die Command Bridge ausschließlich an `127.0.0.1`, standardmäßig Port `5523`.

- `GET /health` bestätigt, dass das Plugin und der lokale Endpoint laufen.
- `POST /command` mit `{"command":"..."}` führt einen Hytale-Serverbefehl aus und liefert `success`, `output` und gegebenenfalls `error` zurück.

Der Node-MCP stellt diesen Zugriff als `hytale_command` bereit.

Es gibt keinen `/civmcp on`-Befehl, keine `bridge.json`, keine zufällige Portdatei und keinen Token-/Session-Lifecycle mehr.

## Console-fähige Civ-Entwicklungscommands

Die gepinnte Hytale-JAR stellt `AbstractAsyncCommand`, `CommandManager`, `Universe.getDefaultWorld()`, `World` als `Executor`, `EntityStore.getRefFromUUID(UUID)`, `Store.forEachChunk(Query, ...)`, `NPCPlugin.spawnNPC(...)`, `UUIDComponent` und `TransformComponent` bereit. Civ nutzt diese Bausteine für eine kleine `civdev`-Command-Sammlung statt für eine zweite Remote-Entity-API.

Die Commands lesen beziehungsweise verändern nur die aktuelle Default-World und dispatchen World-/Entity-Arbeit auf deren World-Executor. NPC-Auflistung und Snapshots verwenden native ECS-Komponenten; Civ-Beruf, Workplace und manueller Bewegungszustand werden aus den bestehenden Civ-Registries beziehungsweise der persistenten `CivInhabitantData` ergänzt. Spawn delegiert an `NPCPlugin.spawnNPC`; Bewegung und Berufswechsel delegieren an die bestehenden Civ-Pfade.

`civdev npc <uuid> --json` ist der maschinenlesbare Diagnoseeinstieg für MCP. `civdev mines` listet registrierte Minen mit Phase, Worker-Anzahl, Upgrade- und Anschlussstatus sowie Bounds. `civdev assign-mine <npc-uuid> <mine-uuid>` verwendet denselben Miner-Zuweisungspfad wie die RTS-Oberfläche; er prüft Civ-Bewohner, Mine, Upgrade-Zustand und Tunnelanschluss und bricht manuelle Bewegung ab. Er kombiniert UUID, Rolle, Displayname, Position, native Gesundheit, Item in Hand, Civ-Zustand, Movement-Zustand und – falls gesetzt – das native Combat-Target.

Für reproduzierbare Live-Diagnose hält Civ zusätzlich ausschließlich in-memory eine Liste der über `civdev` erzeugten UUIDs. `civdev reset` entfernt nur diese Entities über Hytales verifiziertes `Store.removeEntity(..., RemoveReason.REMOVE)`; normale Welt-Entities werden nicht berührt. Ein kleiner nicht-paralleler Diagnose-Sampler beobachtet geladene Civ-Bewohner im Abstand von 250 ms und hält pro UUID höchstens 128 Zustandswechsel in einem Ringpuffer. Diese Historie ist Diagnosezustand, keine Gameplay-Wahrheit und wird nicht persistiert.

## Grenze

Der Remote-Befehl läuft als Serverkonsole. Ein `AbstractPlayerCommand`, der zwingend einen Spieler-Sender benötigt, kann darüber nicht automatisch verwendet werden. Für Civ-spezifische Entwicklungsaktionen sollen bei Bedarf kleine console-fähige Commands ergänzt werden.

Der Endpoint ist ein lokales Entwicklungswerkzeug. Die Loopback-Bindung verhindert Netzwerkzugriff, ersetzt aber keine lokale Prozess-/Benutzerisolation.

## Offen

Der reale Launcher-/Singleplayer-Durchlauf muss noch bestätigen:

1. Die installierte Civ-Mod öffnet den Command-Port zuverlässig.
2. `hytale_status` erkennt den Endpoint.
3. `hytale_command version` liefert eine native Command-Antwort.
4. Ein geeigneter console-fähiger Civ-Command kann sichtbaren Spielzustand verändern.
