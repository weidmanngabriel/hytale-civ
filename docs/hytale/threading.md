# Threading und World-Ausführung

## Zweck

Diese Seite beschreibt die für `hytale-civ` relevanten Threading-Verträge der gepinnten Hytale-Version `0.6.8`. Sie soll verhindern, dass Civ Welt-/ECS-Zustand aus beliebigen Hintergrundthreads liest oder verändert.

## Verifizierter Hytale-Vertrag

**Verifiziert:** Jede Hytale-Welt besitzt einen eigenen Hauptthread. Das offizielle Server-Handbuch beschreibt, dass jede Welt auf ihrem eigenen Main Thread läuft und parallele Arbeit in einen gemeinsamen Thread-Pool auslagern kann.

**Verifiziert:** `com.hypixel.hytale.server.core.universe.world.World` erweitert in der gepinnten `HytaleServer.jar` `TickingThread` und implementiert `java.util.concurrent.Executor`.

**Verifiziert:** `World.execute(Runnable)` ist der direkte Executor-Einstieg für Arbeit auf dem Thread dieser Welt. Die offizielle API-Dokumentation beschreibt außerdem `World.scheduleAfter(Runnable, long, TimeUnit)` als verzögerten Dispatch auf den Thread dieser Welt. Ein verzögerter Auftrag wird verworfen, wenn die Welt vor dem Dispatch keine Tasks mehr annimmt.

**Verifiziert:** Hytales `AbstractWorldCommand` und `AbstractAsyncWorldCommand` dokumentieren ausdrücklich, dass World Commands über den World Thread ausgeführt werden, damit Zugriffe auf World Resources threadsicher stattfinden.

**Befund:** `AbstractPlayerCommand` führt den synchronen Command-Einstieg auf dem World Thread des Spielers aus. Wenn ein Command blockierende Datei-/Netzwerk-Arbeit in einen Worker auslagert, muss auch seine spätere Spielerantwort auf diesen World Thread zurückdispatcht werden. `World` implementiert dafür `Executor`; Civ verwendet bei `/civmcp` `thenAcceptAsync(..., world)` statt die Hytale-Nachricht direkt aus dem Common Pool zu senden.

**Verifiziert:** `TickingThread` stellt in der gepinnten JAR `debugAssertInTickingThread()` und `isInThread()` bereit. Diese Methoden sind nützliche Diagnosewerkzeuge, aber kein Ersatz für einen sauberen Ownership-Vertrag im Civ-Code.

Quellen für diese Aussagen:

- gepinnte `HytaleServer.jar` 0.6.8, insbesondere `World`, `TickingThread`, `AbstractWorldCommand` und `AbstractAsyncWorldCommand`,
- offizielle Hytale Server API 0.6.8,
- offizielles Hytale Server Manual.

## Projektvertrag für Civ

1. Welt-, Chunk- und Entity-/ECS-Zugriffe werden grundsätzlich auf dem zuständigen World Thread ausgeführt, sofern eine konkrete Hytale-API nicht ausdrücklich einen anderen threadsicheren Vertrag dokumentiert.
2. Code außerhalb des World Threads darf keine mutable Hytale-Entity, `Store`, `Ref`, Chunk-Instanz oder andere weltgebundene mutable Engine-Objekte als langlebigen Arbeitszustand verwenden.
3. Wenn Arbeit aus einem anderen Thread zurück in eine Welt muss, wird über den zugehörigen `World`-Executor dispatcht, statt einen eigenen Civ-Executor für World Mutations einzuführen.
4. Blocking-Waits wie `Future.get()`, `CompletableFuture.join()` oder vergleichbare Synchronisationspunkte gehören nicht in ECS-/Tick-/Event-Hotpaths. Asynchrone Ergebnisse werden weiterverkettet oder kontrolliert zurück auf den World Thread dispatcht.
5. CPU-intensive oder blockierende Arbeit darf nur ausgelagert werden, wenn ihr Input vorher in einen Hytale-unabhängigen, unveränderlichen Snapshot übersetzt wurde. Das Ergebnis wird anschließend wieder auf dem World Thread angewendet.
6. Civ erzeugt keine eigene allgemeine Thread-Pool-Abstraktion, solange kein konkretes Feature sie benötigt. Hytales vorhandene World-Executor- und Async-Verträge werden bevorzugt.
7. Ein Methodenname wie `getChunkAsync` ist kein Beweis dafür, dass der zurückgegebene Chunk anschließend aus beliebigen Threads sicher gelesen oder verändert werden darf. Die Thread-Affinität des verwendeten Ergebnisses muss separat belegt sein.

## Audit des aktuellen Civ-Codes

Stand dieses Audits: Hytale 0.6.8 / aktueller `main`-Stand vor Einführung dieser Seite.

**Befund:** Der aktuelle Produktionscode besitzt keine eigene allgemeine Executor-, Thread- oder `CompletableFuture`-Schicht für Gameplay. Die registrierten Civ-ECS-Systeme und Event-/Command-Adapter arbeiten synchron an der Hytale-Grenze. Es wurde kein aktueller Civ-Hotpath gefunden, der mit `join()`/`get()` auf asynchrone Engine-Arbeit wartet.

**Befund:** Der bekannte Chunk-Zugriff des Holzfällers verwendet während der ECS-Verarbeitung `World.getChunkIfLoaded(...)` und lädt dadurch bewusst keinen Chunk synchron nach. Diese bestehende Regel bleibt bestehen: Chunk-Aktivierung während laufender Store-/Systemverarbeitung wird vermieden.

**Befund:** Die Plugin-Verdrahtung registriert globale Events, Maus-Events und ECS-Systeme, aber keine eigenen Hintergrundthreads. Das ist für den aktuellen Stand die bevorzugte Ausgangslage.

## Prüfliste für neue Hytale-Arbeit

Vor einer Änderung, die Futures, Scheduler, Hintergrundarbeit oder World-Zugriffe einführt:

1. Gehört die Operation zu genau einer `World`?
2. Läuft der Aufruf bereits auf deren World Thread?
3. Falls nein: Kann `world.execute(...)` oder `world.scheduleAfter(...)` verwendet werden?
4. Wird ein mutable Hytale-Objekt über die Thread-Grenze getragen? Falls ja, zuerst auf IDs/Werte/Snapshots reduzieren.
5. Blockiert irgendwo ein World-/ECS-/Event-Thread auf `get()`, `join()`, I/O oder fremde Locks?
6. Ist die angenommene Thread-Semantik durch die gepinnte JAR und/oder offizielle Doku belegt?
7. Falls nicht: gezielten Runtime-Test bauen und das Ergebnis hier dokumentieren.

## Offen

- Für welche einzelnen Event-Klassen Hytale 0.6.8 garantiert, auf welchem Thread sie dispatcht, ist nicht pauschal dokumentiert. Event-Threading wird deshalb pro Event geprüft, sobald Civ davon abhängt.
- Die genauen Threading-Verträge einzelner asynchroner Chunk-/Storage-APIs sind noch nicht für Civ verifiziert. Bis dahin werden ihre Ergebnisse nicht als frei thread-safe behandelt.
- Cross-World-Operationen brauchen einen expliziten Ownership-/Dispatch-Plan, sobald Civ ein Feature dafür einführt.
