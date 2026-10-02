# Headless-Server und Runtime-Probes

Diese Seite beschreibt den verifizierten Stand für automatisierte Hytale-Server-Laufzeittests in `hytale-civ`.

## Verifizierte Voraussetzungen

Für Hytale Server `0.6.8` werden Java 25, `HytaleServer.jar` und die zugehörige `Assets.zip` benötigt.

Die Server-JAR unterstützt den CLI-Parameter `--assets`. Dieser akzeptiert einen Pfad zu einem Asset-Verzeichnis oder direkt zu einer ZIP-Datei. Für lokale Runtime-Tests verwendet Civ deshalb die vorhandene Hytale-Installation direkt:

~~~text
java -jar HytaleServer.jar --assets <Pfad-zur-Assets.zip> ...
~~~

Die `Assets.zip` muss nicht kopiert, entpackt oder in das Test-Arbeitsverzeichnis verschoben werden.

## Bare Probe auf GitHub-hosted CI

Der normale GitHub-hosted Build besitzt keine lizenzierte `Assets.zip`. Er kann deshalb keinen vollständigen Hytale-Serverstart durchführen.

Der dortige Bare-Probe startet dennoch die echte gepinnte Hytale-Server-JAR und prüft, dass Civ vom echten Plugin-Manager erkannt wird. Hytale `0.6.8` lädt jedoch auch mit `--bare` das Asset-Modul. Ohne `Assets.zip` endet der Prozess daher erwartungsgemäß an der fehlenden Basis-Asset-Grenze.

`--bare` ist folglich kein Ersatz für einen vollständigen Runtime-Test. Er beweist nur frühe Server-/Plugin-Manager-Kompatibilität bis zum Asset-Ladevorgang.

## Self-Hosted Runtime-Setup

Der Workflow `.github/workflows/hytale-local.yml` läuft auf einem vertrauenswürdigen Windows-Self-Hosted-Runner mit lokaler Hytale-Installation. Die Pfade werden ausschließlich über Runner-Umgebungsvariablen bereitgestellt:

~~~text
HYTALE_SERVER_JAR
HYTALE_ASSETS_PATH
~~~

Maschinenspezifische Pfade, `HytaleServer.jar` und `Assets.zip` werden nicht committed und nicht als CI-Artefakte veröffentlicht.

Der Workflow:

1. prüft beide lokalen Hytale-Dateien,
2. prüft Java 25,
3. baut das Civ-Plugin,
4. erstellt pro Lauf ein isoliertes Runtime-Verzeichnis unter `RUNNER_TEMP` außerhalb des Git-Worktrees,
5. installiert dort nur Civ-Plugin und Civ-Asset-Pack,
6. startet den echten Server mit `--assets <Assets.zip>` und `--auth-mode offline`,
7. aktiviert test-only Runtime-Commands über `-Dcivilizations.runtimeProbe=true`,
8. startet den gewählten Probe als Hytale-Boot-Command,
9. wertet den echten Serverlog und reale Weltzustände als Assertions aus,
10. fährt den Server aus dem Probe selbst sauber herunter.

Das Runtime-Verzeichnis liegt absichtlich außerhalb des Git-Worktrees. Hytales Prefab-Cache kann unter Windows sehr tiefe Pfade erzeugen; ein früher Spike-Lauf konnte dadurch späteres Git-Cleanup mit `Filename too long` stören. Cleanup des isolierten Temp-Verzeichnisses ist deshalb zeitlich begrenzt und Best-Effort. Ein langsames Dateibaum-Cleanup darf keinen bereits erfolgreichen Gameplay-Vertrag in einen Fehltest verwandeln.

## Deterministische Testwelten

Für Engine-Verträge verwenden Runtime-Tests keine zufällige Default-World-Geometrie mehr. Hytale `0.6.8` stellt den nativen World-Generator-Key `Flat` bereit; Civ erzeugt damit für den Test eine eigene temporäre Welt.

Verifiziert ist:

- `Universe.addWorld(name, "Flat", "default")` erzeugt headless eine echte Hytale-Welt,
- der Flat-Generator liefert bei der verwendeten Konfiguration `Soil_Grass` auf Y=0 und freien Entity-Raum darüber,
- benötigte Chunks können vor dem Aufbau explizit mit `World.getChunkAsync(...)` geladen werden,
- Prefabs können über `PrefabStore` geladen und mit `BlockSelection.placeNoReturn(...)` in diese Welt gesetzt werden,
- reale Vanilla-Prefabs aus der geladenen `Assets.zip` können als Test-Fixtures verwendet werden.

Zufälliges Worldgen erwies sich im Movement-Spike als ungeeignet für deterministische Regressionstests: ein echter NPC bewegte sich, konnte aber aufgrund zufälliger Geländegeometrie ein Ziel nicht zuverlässig erreichen. Eine kontrollierte Testfläche beziehungsweise native Flat-World trennt Gameplay-/Adapterfehler von zufälligen Terrainbedingungen.

## Gemeinsame Szenario-Fixtures

Die fachliche Ausgangslage eines Szenarios soll Hytale-unabhängig beschrieben werden. Der erste gemeinsame Vertrag liegt in `WoodcutterBasicScenario`:

- feste Szenario-ID,
- feste Startposition des Holzfällers,
- feste semantische Baum-Anker,
- fester Name der temporären Runtime-Welt.

Der Simulator interpretiert die Baum-Anker als abstrakte Bäume. Der Hytale-Adapter platziert an denselben fachlichen Positionen echte Vanilla-Prefabs. Engine-spezifische Details wie Prefab-Origin, Chunk-Preload und Hytale-Entity-Spawn bleiben außerhalb der gemeinsamen Szenariodefinition.

Damit prüfen Simulator und Hytale denselben fachlichen Fall, aber unterschiedliche Verantwortlichkeiten:

- **Simulator:** schnelle, deterministische Civ-Zustands- und Entscheidungslogik.
- **Hytale Runtime:** echte Engine-Verträge wie Worldgen, Prefabs, EntityStore, NPC-Navigation, ECS-Ticks und native Weltmutation.

Hytales Pathfinding wird ausdrücklich nicht im Simulator nachgebaut.

## Verifizierter Woodcutter-End-to-End-Vertrag

Der aktuelle `civwoodcutterprobe` verwendet reale Produktionspfade und keine direkte Test-Fernsteuerung des Holzfällerjobs.

Ablauf:

1. native Hytale-Flat-World erzeugen,
2. benötigte Chunks vorladen,
3. drei reale Vanilla-Oaks vom Prefab `Trees/Oak/Stage_1/Oak_Stage1_001` platzieren,
4. reale `Woods`-Blöcke als Ausgangszustand zählen,
5. echten `Civ_Inhabitant` über `NPCPlugin.spawnNPC(...)` erzeugen,
6. über `CivUnitRegistry` claimen,
7. Beruf `WOODCUTTER` zuweisen,
8. `WoodcutterWorkSystem` autonom den nächsten Baum suchen und reservieren lassen,
9. den echten NPC über Hytales nativen Bewegungsweg zur Arbeitsposition laufen lassen,
10. reale World-/ECS-Ticks für die Chop-Dauer verstreichen lassen,
11. den Baum über den produktiven nativen Harvest-/Fell-Pfad entfernen,
12. die reale Abnahme der Holzblöcke in der Welt prüfen,
13. prüfen, dass der Holzfäller danach wieder einen weiteren Baum auswählt,
14. erst dann `CIV_WOODCUTTER_RUNTIME_PASS` ausgeben und den Server sauber stoppen.

Der erfolgreiche Referenzlauf hatte folgende Messwerte:

~~~text
initialWood=54
first tree blocks=18
remainingWood=36
moved≈4.0 blocks
first fell≈224 world ticks
next target selected≈232 world ticks
server exit code=0
~~~

Im echten Produktionslog wurden dabei nacheinander unter anderem `state=target-assigned`, `state=arrived`, `state=chopping`, `state=ready-to-fell` und `state=fell-success` beobachtet. Danach wurde ein zweiter Baum als neues Ziel gewählt.

Der Lauf wurde ohne Codeänderung erneut ausgeführt und war wieder grün. Das Setup ist damit nicht nur als einmaliger erfolgreicher Start verifiziert.

## Vanilla-Baum-Metadaten

Für den verwendeten Oak wurden in Hytale `0.6.8` reale `Woods`-Blocktypen wie `Wood_Oak_Trunk`, `Wood_Oak_Trunk_Full` und `Wood_Oak_Branch_Long` beobachtet. Der bestehende produktive Woodcutter-Classifier für Stammblöcke (`Woods` plus Block-ID mit `trunk`) passt damit zum getesteten Vanilla-Oak.

Prefab-Anker sind Engine-Geometrie und nicht automatisch identisch mit dem semantischen Baumfuß einer gemeinsamen Test-Fixture. Diese Übersetzung gehört deshalb in den Hytale-Testadapter und nicht in Core oder gemeinsame Szenariodaten.

## Was die Runtime-Tests jetzt beweisen

Für die konkret verwendete Hytale-0.6.8-Runtime ist automatisiert belegt:

- Basisassets werden aus der echten `Assets.zip` geladen,
- das Civ-Asset-Pack wird geladen,
- `Civilizations:HytaleCiv` wird aktiviert,
- der vollständige Universe-/Server-Boot wird erreicht,
- echte Hytale-Welten können headless programmatisch erzeugt werden,
- native Flat-Worlds eignen sich als deterministische Test-Fixtures,
- echte Vanilla-Prefabs können programmgesteuert platziert und wieder aus der Welt gelesen werden,
- echte Civ-NPCs können ohne verbundenen Client gespawnt und geclaimt werden,
- reale World-/ECS-Ticks laufen,
- produktive Civ-Bewegung wird von Hytales NPC-Navigation ausgeführt,
- ein realer Holzfäller kann autonom einen Vanilla-Baum finden, hinlaufen, arbeiten und ihn fällen,
- die native Weltmutation ist über reale Blockzustände beobachtbar,
- der Holzfäller nimmt danach autonom einen weiteren Baum als Arbeit auf,
- Civ und Hytale fahren anschließend sauber mit Exitcode `0` herunter.

Das Civ-Asset-Pack deklariert dieselbe Server-Kompatibilität wie das Java-Plugin: `ServerVersion: ^0.6.0`.

## Was noch nicht bewiesen ist

Die vorhandenen Runtime-Tests beweisen noch nicht:

- Einsammeln von Holz-Drops,
- Transport von Ressourcen durch einen Bewohner,
- Ablieferung in einer Siedlungs-/Gebäudelagerung,
- Inventargrenzen und volle Lager,
- Save → Shutdown → zweiter Serverstart → Restore desselben Gameplay-Zustands,
- mehrere konkurrierende Holzfäller und Reservierungsraces unter echter Runtime-Last,
- Spieler-spezifischen Event-Dispatch,
- Client-UI, Kamera oder Mausinteraktion,
- sichtbare Animationen oder Renderer-Ausgabe; der Server kann nur den serverseitigen Animation-/State-Pfad bestätigen.

Insbesondere `FELL_TREE → COLLECT_WOOD → DELIVER_WOOD` existiert derzeit noch nicht als vollständiger Core-Holzfällerzustand. Das wäre ein neues Gameplay-Feature und darf nicht nur als Test-Harness simuliert werden.

## Empfohlene Testaufteilung

Die dauerhafte Testarchitektur soll drei Ebenen unterscheiden:

1. **Core-/Simulator-Szenarien:** sehr schnell, vollständig deterministisch und für die fachliche Civ-Logik.
2. **Self-Hosted Hytale-Runtime-Szenarien:** wenige repräsentative Szenarien für reale Engine-Verträge mit echter `Assets.zip`.
3. **Manuelle Client-/UX-Checks:** Rendering, Kamera, Mausinteraktion und andere Client-only Verträge.

Gemeinsame Szenariodaten dürfen von Simulator und Hytale-Runner geteilt werden. Assertions müssen aber zur Ebene passen. Ein Simulator-PASS beweist keine Hytale-Navigation; ein Hytale-Runtime-PASS soll nicht jede Core-Invariante doppelt testen.

Als nächste hochwertige Runtime-Szenarien bieten sich an:

- Persistenz über zwei echte Serverstarts,
- mehrere Holzfäller mit Baumreservierung,
- ein Construction-End-to-End-Szenario,
- später Wood-Logistics, sobald Einsammeln und Abliefern als echtes Gameplay implementiert sind.

## Sicherheits- und CI-Grenze

Der Self-Hosted-Runner greift auf lokale lizenzierte Hytale-Dateien zu. Er soll deshalb nicht für beliebigen fremden PR-Code verwendet werden. Der Runtime-Workflow muss vor einer Übernahme nach `main` auf vertrauenswürdige beziehungsweise bewusst ausgelöste Läufe beschränkt werden. Der aktuelle automatische Push-Trigger ist Spike-spezifisch.

Normale Unit-, Simulations-, Build-, API- und Bare-Probe-Checks bleiben auf GitHub-hosted Runnern. Der lokale Rechner wird nur für Tests benötigt, die die echte Hytale-Runtime und `Assets.zip` brauchen. Ein ausgeschalteter lokaler Runner darf normale PRs und Releases nicht blockieren.

## Bekannte Runtime-Eigenheiten

Beim normalen Shutdown kann Hytale `0.6.8` einen `WorldCrashRecoveryHandler`-/„Reloading crashed world“-Eintrag erzeugen, obwohl anschließend `Shutdown completed!` erscheint und der Prozess mit Exitcode `0` endet. Der Runtime-Probe wertet deshalb den vollständigen Shutdown-Vertrag aus und behandelt diesen isolierten Logeintrag nicht als Civ-Gameplay-Fehler.
