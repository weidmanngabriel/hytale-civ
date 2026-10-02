# Headless-Server und Runtime-Probe

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

`--bare` ist folglich kein Ersatz für einen vollständigen Runtime-Test. Es beweist nur frühe Server-/Plugin-Manager-Kompatibilität bis zum Asset-Ladevorgang.

## Self-Hosted Gameplay-Runtime-Probe

Der Workflow `.github/workflows/hytale-local.yml` läuft auf einem vertrauenswürdigen Windows-Self-Hosted-Runner mit lokaler Hytale-Installation. Die Pfade werden ausschließlich über Runner-Umgebungsvariablen bereitgestellt:

~~~text
HYTALE_SERVER_JAR
HYTALE_ASSETS_PATH
~~~

Maschinenspezifische Pfade, `HytaleServer.jar` und `Assets.zip` werden nicht committed und nicht als CI-Artefakte veröffentlicht.

Der Workflow:

1. prüft, dass beide lokalen Hytale-Dateien vorhanden sind,
2. prüft Java 25,
3. baut das Civ-Plugin,
4. erstellt pro Lauf ein isoliertes Runtime-Verzeichnis unter `RUNNER_TEMP` außerhalb des Git-Worktrees,
5. installiert dort nur Civ-Plugin und Civ-Asset-Pack,
6. startet den echten Server mit `--assets <Assets.zip>` und `--auth-mode offline`,
7. aktiviert den test-only Runtime-Command über `-Dcivilizations.runtimeProbe=true`,
8. startet `civruntimeprobe` als Hytale-Boot-Command,
9. wertet den Serverlog als Runtime-Assertion aus,
10. fährt den Server aus dem Probe selbst sauber herunter.

Das Runtime-Verzeichnis liegt absichtlich nicht unter `build/` oder einem anderen Git-Worktree-Pfad. Hytales Prefab-Cache kann unter Windows sehr tiefe Pfade erzeugen; ein früher Spike-Lauf konnte dadurch späteres Git-Cleanup mit `Filename too long` stören. Cleanup des isolierten Temp-Verzeichnisses ist deshalb zeitlich begrenzt und nur Best-Effort. Ein langsames Dateibaum-Cleanup darf keinen bereits erfolgreichen Gameplay-Vertrag in einen Fehltest verwandeln.

## Aktuell automatisierter Gameplay-Vertrag

Der Runtime-Command verwendet ausschließlich vorhandene Civ- und Hytale-Produktionspfade, abgesehen von der test-only Orchestrierung:

1. die Default-Welt wird aus dem echten `Universe` geholt,
2. der echte Welt-Spawnpunkt wird bestimmt,
3. der Spawn-Chunk wird über `World.getChunkAsync(...)` explizit geladen,
4. die Ausführung kehrt über `world.execute(...)` auf den World-Thread zurück,
5. ein echter `Civ_Inhabitant` wird nativ über `NPCPlugin.spawnNPC(...)` gespawnt,
6. der NPC wird über `CivUnitRegistry` als Civ-Bewohner geclaimt,
7. seine persistente Civ-Identität muss vorhanden sein,
8. ein manueller Bewegungsauftrag wird über `CivActivityRegistry` angelegt,
9. mehrere echte Hytale-World-/ECS-Ticks müssen verstreichen,
10. `CivManualMovementSystem` muss den angekommenen Bewegungsauftrag verarbeiten und abschließen,
11. der native Civ-Move-Target-State muss anschließend wieder leer sein,
12. erst dann wird `CIV_RUNTIME_PROBE_PASS` ausgegeben und der Server sauber gestoppt.

Der erste erfolgreiche Multi-Tick-Lauf hat zwischen Start und Assertion 30 echte World-Ticks beobachtet. Die Assertion verlangt bewusst nur mindestens zwei Ticks, damit der Test nicht von der exakten Tickrate des Runner-Rechners abhängt.

Der Bewegungsauftrag zielt derzeit absichtlich auf die aktuelle NPC-Position. Das ist kein Trick, um Bewegung vorzutäuschen: Der Auftrag läuft durch denselben Civ-Activity- und Hytale-ECS-Pfad wie ein normaler manueller Move. Diese Wahl macht den Canary jedoch unabhängig von zufälligem Terrain und beweist deshalb **nicht** Hytales Wegfindung über Distanz.

## Was der Runtime-Probe jetzt beweist

Für die konkret verwendete Hytale-0.6.8-Runtime ist automatisiert belegt:

- Basisassets werden aus der echten `Assets.zip` geladen,
- das Civ-Asset-Pack wird geladen,
- `Civilizations:HytaleCiv` wird aktiviert,
- die Default-Welt wird hinzugefügt,
- `Universe ready!` und der vollständige Server-Boot werden erreicht,
- ein echter Civ-NPC kann headless in einer geladenen Welt gespawnt werden,
- Claim und persistente Civ-Identität funktionieren im echten EntityStore,
- echte World-/ECS-Ticks laufen ohne verbundenen Client,
- der produktive `CivManualMovementSystem` verarbeitet einen realen Civ-Bewegungsauftrag,
- der Auftrag wird korrekt abgeschlossen und der native Move-Target-State bereinigt,
- Civ und Hytale fahren danach sauber mit Exitcode `0` herunter.

Das Civ-Asset-Pack deklariert dieselbe Server-Kompatibilität wie das Java-Plugin: `ServerVersion: ^0.6.0`.

## Was der Runtime-Probe nicht beweist

Der Canary soll klein und deterministisch bleiben. Er beweist ausdrücklich noch nicht:

- Hytale-Wegfindung über mehrere Blöcke oder um Hindernisse,
- tatsächliche Transform-Bewegung über Distanz,
- Holzfällen, Farming, Bauen oder andere native Weltmutationen,
- Save → Shutdown → zweiter Serverstart → Restore,
- Spieler-spezifischen Event-Dispatch,
- Client-UI, Kamera oder Mausinteraktion,
- sichtbare Prefab-, Pfad- oder andere Renderer-Ausgabe.

Diese Grenzen sind gewollt. Domänenregeln gehören weiterhin in Core-/Simulations-Tests. Der Self-Hosted-Server ist für wenige wichtige Engine-Verträge gedacht, die ohne echte Hytale-Runtime nicht glaubwürdig getestet werden können.

## Sinnvolle nächste Runtime-Tests

Die höchste zusätzliche Aussagekraft liefern getrennte, fokussierte Probes:

1. **Persistence-Restart:** im ersten Serverprozess einen Civ-Bewohner erzeugen/claimen und relevanten persistenten Zustand speichern; sauber stoppen; denselben isolierten Weltzustand in einem zweiten Serverprozess laden und Name/Claim/Beruf wiederfinden.
2. **Controlled Pathfinding:** einen NPC in einer kontrollierten, bereits geladenen Testfläche einige Blöcke bewegen und sowohl Transform-Änderung als auch Abschluss des Civ-Intents prüfen. Dieser Test sollte nicht von zufälligem Default-Terrain abhängen.
3. **Ein nativer Welt-Job:** später genau einen stabilen Produktionspfad wie Baumernte oder Building-Completion gegen echte Hytale-Weltmutation prüfen.

Diese Probes sollten getrennt bleiben. Ein großer End-to-End-Test wäre langsamer, schwerer zu diagnostizieren und würde Core-Regeln unnötig an Hytale koppeln.

## Sicherheits- und CI-Grenze

Der Self-Hosted-Runner greift auf lokale lizenzierte Hytale-Dateien zu. Er soll deshalb nicht für beliebigen fremden PR-Code verwendet werden. Der Runtime-Workflow muss auf vertrauenswürdige Branches beziehungsweise bewusst ausgelöste Läufe beschränkt bleiben.

Normale Unit-, Simulations-, Build-, API- und Bare-Probe-Checks bleiben auf GitHub-hosted Runnern. Der lokale Rechner wird nur für Tests benötigt, die die echte Hytale-Runtime und `Assets.zip` brauchen. Ein ausgeschalteter lokaler Runner darf normale PRs und Releases nicht blockieren.
