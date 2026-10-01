# Lifecycle und Runtime-Zustand

## Zweck

Diese Seite beschreibt, welcher Civ-Zustand persistent ist, welcher nur als Laufzeitprojektion existiert und an welchem Hytale-Lifecycle diese Projektionen aufgebaut werden. Ziel ist, veraltete Welt-, Entity- oder Spielerzustände nicht über einen neuen Runtime-Lifecycle mitzuschleppen.

## Verifizierter Weltstart in Hytale 0.6.8

**Verifiziert:** Die gepinnte `HytaleServer.jar` enthält `com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent`.

**Verifiziert:** Der Bytecode von `World.onStart()` startet in Hytale 0.6.8 die relevanten World-Subsysteme einschließlich Chunk- und Entity-Store und dispatcht anschließend `StartWorldEvent`. `TickingThread.run()` ruft `onStart()` auf, bevor der normale Tick-Loop beginnt.

**Verifiziert:** `StartWorldEvent` ist ein synchrones World-Event. Für Civ ist es deshalb der passende Einstieg, um persistente Weltinformationen in laufzeitgebundene Registries zu projizieren, bevor normales Gameplay auf diese Registries zugreift.

Der Civ-Plugin-Bootstrap registriert deshalb den Wiederaufbau von Gebäuden, Farmen und Feldern auf `StartWorldEvent`. Der frühere Wiederaufbau beim Eintritt eines Spielers in die Welt war an den falschen Lifecycle gekoppelt: Weltzustand darf nicht davon abhängen, dass zuerst ein Spieler beitritt.

## Ownership der aktuellen Civ-Zustände

| Zustand | Autorität | Lifecycle |
|---|---|---|
| `CivInhabitantData` | persistente Hytale-ECS-Komponente auf der Bewohner-Entity | bleibt über normale Entity-Speicherung erhalten |
| `CivUnitRegistry` | Runtime-Cache für aktuell geladene Bewohner und Engine-Zielzustand | keine persistente Autorität; Einträge werden bei Entity-Nutzung validiert |
| `CivActivityRegistry` | Runtime-Zustand für aktuelle Civ-Aktivität und Intents | nicht persistent; an die geladene Entity gebunden |
| fertige Civ-Gebäude | `CivBuildingDataResource` über `CivBuildingPersistenceService` | persistente Welt-Autorität |
| `BuildingPlacementRegistry.buildings` | Runtime-Projektion der persistenten Gebäude | wird beim Weltstart vollständig aus Persistenz wiederhergestellt |
| `FarmBuildingRegistry` | abgeleitete Runtime-Projektion fertiger Farmgebäude | wird beim Weltstart geleert und aus persistenten Gebäuden rekonstruiert |
| `FarmFieldRegistry` | abgeleitete Runtime-Projektion fertiger Felder | wird beim Weltstart geleert und aus persistenten Gebäuden rekonstruiert |
| Baustellen-Footprint-Reservierungen | rein transient | werden beim Welt-Restore verworfen |
| aktive Bauvorschau / Construction Site | rein transient | spieler-/runtimegebunden; derzeit kein allgemeiner Welt-Unload-Vertrag |
| RTS-Session und Claim-Arming | rein transient pro Spieler | werden bei `PlayerDisconnectEvent` entfernt |

## Projektvertrag

1. Persistente Civ-Weltzustände werden unabhängig von Spielerbeitritten aufgebaut.
2. Fertige Gebäude werden beim `StartWorldEvent` aus `CivBuildingDataResource` geladen und die Runtime-Projektionen daraus neu erzeugt.
3. Nicht persistente Baustellen-Reservierungen dürfen einen World-Restore nicht überleben. `BuildingPlacementRegistry.restoreWorld(...)` entfernt deshalb zuerst alle Reservierungen für diese Welt.
4. Runtime-Registries sind keine zweite Persistenzquelle. Wenn ein persistenter und ein runtimegebundener Zustand widersprechen, wird die Runtime-Projektion aus der persistenten Autorität neu aufgebaut.
5. Player-Session-Zustand wird beim Disconnect vollständig entfernt und darf nicht als Weltzustand missverstanden werden.

## Audit-Befunde

### Behoben

Der bisherige `BuildingPlacementRegistry.restoreWorld(...)`-Pfad ersetzte die Liste fertiger Gebäude, ließ aber bereits vorhandene Baustellen-Reservierungen derselben World-UUID stehen. Bei einem erneuten Welt-Lifecycle im selben Plugin-Prozess konnten dadurch nicht persistente Flächen weiterhin als blockiert gelten.

Der Restore löscht diese Reservierungen jetzt explizit. Ein Regressionstest stellt sicher, dass eine vor dem Restore reservierte Fläche danach nicht mehr kollidiert.

Der Wiederaufbau der persistenten Gebäude-/Farm-/Feld-Projektionen wurde außerdem vom Spieler-Beitritt auf `StartWorldEvent` verschoben. Damit entsteht die Runtime-Welt unabhängig davon, wann oder ob ein Spieler beitritt.

### Bereits sauber

`RtsInteractionController` entfernt beim `PlayerDisconnectEvent` die RTS-Session und den Claim-Zustand, räumt die aktive Placement-Preview auf und bricht spielereigene Baustellen ab. Dieser Zustand ist damit klar spielergebunden.

### Noch offen

`CivUnitRegistry` und `CivActivityRegistry` halten Runtime-Referenzen auf geladene Entity-Stores und entfernen veraltete Entity-Slots bei Nutzung beziehungsweise über bestehende Entity-Lifecycle-Pfade. Für einen vollständigen World-Unload ist jedoch noch kein von Civ verifizierter, nicht abbrechbarer Post-Removal-Hook dokumentiert, an dem alte World-/Store-Referenzen garantiert und zentral entfernt werden können.

`RemoveWorldEvent` reicht dafür nicht als alleinige Grundlage: Das Event gehört zum Removal-Vorgang und ist abbrechbar. Civ darf daher nicht im Voraus seine gesamte Runtime-Projektion löschen und anschließend annehmen, dass die Welt garantiert entfernt wurde.

Ebenso besitzt `PrefabPlacementService` derzeit keinen verifizierten allgemeinen `clearWorld(...)`-Pfad für Construction Sites und Previews. Player-Disconnect ist abgedeckt; ein Welt-Unload ohne vorherigen normalen Spieler-Cleanup bleibt als Runtime-Frage offen.

Bis ein verlässlicher Lifecycle-Hook oder ein fokussierter Runtime-Test vorliegt, wird hier bewusst keine spekulative Cleanup-Logik ergänzt.

## Prüfliste für neue Runtime-Registries

Bei einer neuen Registry oder einem neuen Cache muss vor der Implementierung beantwortet werden:

1. Was ist die persistente Autorität?
2. Ist die Registry nur eine Projektion oder selbst Gameplay-Autorität?
3. Wem gehört der Zustand: Spieler, Entity, World oder Plugin?
4. Welcher verifizierte Event-/Lifecycle-Punkt baut ihn auf?
5. Welcher verifizierte Lifecycle-Punkt entfernt ihn wieder?
6. Darf derselbe World-/Entity-Identifier später mit einem neuen Runtime-Objekt wieder erscheinen?
7. Gibt es einen Test, der Restore beziehungsweise Cleanup gegen veraltete Daten absichert?

Wenn Punkt 4 oder 5 nicht zuverlässig beantwortet ist, bleibt das Verhalten als offen dokumentiert statt durch einen vermuteten Hytale-Hook implementiert zu werden.
