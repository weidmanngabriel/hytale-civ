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

Runtime-Tests werden unabhängig von einem Pull Request über Kommentare in der festen GitHub-Issue `#126 Hytale Runtime Test Requests` angefordert. Das Format lautet:

~~~text
/hytale-test <szenarien> <commit-sha>
~~~

Der angegebene Commit darf jeder Commit des eigenen Repositories sein, also auch ein Spike ohne Pull Request. Mehrere Szenarien werden mit `-` getrennt. Szenarionamen selbst enthalten keine Bindestriche; zusammengesetzte Begriffe werden zusammengeschrieben. `all` steht allein und expandiert auf alle in `scripts/hytale-runtime-scenarios.json` registrierten Szenarien. Bis zu acht Szenarien können explizit angefordert werden.

Die Ausführung ist absichtlich vor der lokalen Codeausführung abgesichert:

1. `.github/workflows/hytale-local.yml` reagiert auf neu erstellte `issue_comment`-Events und stammt dabei aus dem vertrauenswürdigen Default-Branch.
2. Ein GitHub-hosted Autorisierungsjob prüft zuerst, dass der Kommentar aus Issue `#126` stammt und Event-Aktor, Kommentarautor sowie Sender ausdrücklich erlaubt sind.
3. Der Befehl muss exakt der erlaubten Syntax entsprechen. Der angegebene 7- bis 40-stellige SHA wird über die GitHub-API im eigenen Repository auf einen vollständigen 40-stelligen Commit-SHA aufgelöst und muss mit dem angegebenen Präfix übereinstimmen.
4. Die Szenario-Registry wird aus genau diesem zu testenden Commit geladen. Unbekannte oder doppelte Szenarien sowie mehr als acht explizite Szenarien werden abgelehnt; `all` expandiert auf die vollständige Registry.
5. Nur bei erfolgreicher Autorisierung startet der Self-Hosted-Job. Er erhält lediglich `contents: read`, checkt exakt den autorisierten Commit-SHA mit `persist-credentials: false` aus und verifiziert den Checkout erneut.
6. Tests und Civ-Plugin werden einmal gebaut.
7. `scripts/hytale-runtime-tests.ps1` führt nur die autorisierte Szenarioliste aus und erzeugt für jedes Szenario ein eigenes isoliertes Runtime-Verzeichnis unter `RUNNER_TEMP` außerhalb des Git-Worktrees.
8. Dort werden nur Civ-Plugin und Civ-Asset-Pack installiert.
9. Der echte Server startet mit `--assets <Assets.zip>` und `--auth-mode offline`.
10. Test-only Runtime-Commands werden über `-Dcivilizations.runtimeProbe=true` aktiviert.
11. Das jeweilige Szenario wertet echten Serverlog und reale Weltzustände als Assertions aus und fährt den Server aus dem Probe sauber herunter.

Aktuell ist `weidmanngabriel` der einzige ausdrücklich erlaubte Runtime-Test-Anforderer. Der lokale Runner selbst besitzt keine Repository-Schreibrechte. Der Kommentartext wird niemals ungeprüft als Shell-Befehl verwendet; Commit und Szenarien werden vor dem Checkout beziehungsweise der Ausführung in strukturierte, validierte Werte übersetzt.

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

## Verifizierter Persistence-Restart-Vertrag

Das Runtime-Szenario `persistence` prüft die Civ-Bewohner-Persistenz über zwei echte, getrennte Hytale-Serverprozesse. Beide Prozesse verwenden dasselbe isolierte Runtime-Verzeichnis und damit denselben gespeicherten Weltzustand.

Ablauf:

1. erster Hytale-Prozess startet mit `civilizations.persistenceProbeStage=prepare`,
2. ein echter `Civ_Inhabitant` wird gespawnt und geclaimt,
3. Name, Beruf `CONSTRUCTION_WORKER`, Berufs-XP `37` und Workplace-ID `runtime-probe-workplace` werden deterministisch gesetzt,
4. die native Entity-UUID aus `UUIDComponent` wird protokolliert,
5. Hytale fährt über den normalen `shutdownServer()`-Pfad sauber herunter,
6. ein zweiter Hytale-Prozess startet im selben Runtime-Verzeichnis mit der protokollierten UUID,
7. der relevante Chunk wird geladen und `World.getEntityRef(uuid)` muss dieselbe Entity wiederfinden,
8. Claimzustand, Name, Beruf, XP, Workplace-ID und Appearance müssen unverändert vorhanden sein,
9. `PersistentDisplayName` und `DisplayNameComponent` müssen wieder rehydriert sein,
10. erst dann wird `CIV_PERSISTENCE_RESTORE_PASS` ausgegeben und auch der zweite Prozess sauber beendet.

Im verifizierten Lauf wurde dieselbe UUID im Prepare- und Restore-Prozess wiedergefunden. Beide Prozesse endeten mit Exit-Code `0`; anschließend lief auch `all` mit `woodcutter` und `persistence` im gemeinsamen Harness erfolgreich durch.

Dieser Test belegt den normalen Save-/Shutdown-/Restart-Vertrag. Ein harter Prozessabbruch oder Crash-Szenario ist davon ausdrücklich nicht abgedeckt.

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
- ein geclaimter Civ-Bewohner wird über einen sauberen Hytale-Shutdown und einen separaten zweiten Serverprozess unter derselben nativen Entity-UUID wiederhergestellt,
- persistierte Civ-Felder wie Name, Beruf, Berufs-XP, Workplace-ID und Appearance bleiben dabei erhalten,
- Civ und Hytale fahren anschließend sauber mit Exitcode `0` herunter.

Das Civ-Asset-Pack deklariert dieselbe Server-Kompatibilität wie das Java-Plugin: `ServerVersion: ^0.6.0`.

## Was noch nicht bewiesen ist

Die vorhandenen Runtime-Tests beweisen noch nicht:

- Einsammeln von Holz-Drops,
- Transport von Ressourcen durch einen Bewohner,
- Ablieferung in einer Siedlungs-/Gebäudelagerung,
- Inventargrenzen und volle Lager,
- Crash-/Hard-Kill-Recovery ohne normalen Shutdown,
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

- mehrere Holzfäller mit Baumreservierung,
- ein Construction-End-to-End-Szenario,
- später Wood-Logistics, sobald Einsammeln und Abliefern als echtes Gameplay implementiert sind.

## Sicherheits- und CI-Grenze

Der Self-Hosted-Runner greift auf lokale lizenzierte Hytale-Dateien zu und darf deshalb keinen unautorisierten Repository-Code ausführen. Die Steuerung erfolgt deshalb ausschließlich über den `issue_comment`-Workflow auf dem vertrauenswürdigen Default-Branch und nicht über Workflow-Dateien aus dem zu testenden Commit.

Der GitHub-hosted Autorisierungsjob erlaubt aktuell ausschließlich `weidmanngabriel`, akzeptiert nur Befehle aus Issue `#126`, löst den angegebenen Commit innerhalb des eigenen Repositories auf einen exakten SHA auf und validiert jedes angeforderte Szenario gegen die Registry aus genau diesem Commit. Erst danach darf der Self-Hosted-Runner diesen exakten SHA auschecken. Das Checkout speichert keine GitHub-Credentials, und der lokale Job besitzt nur Leserechte.

Das Szenario-Harness selbst stammt absichtlich aus dem getesteten Commit, weil neue Runtime-Verträge gemeinsam mit dem zu prüfenden Feature entwickelt werden müssen. Die Sicherheitsgrenze liegt deshalb nicht darin, diesen Code als vertrauenswürdig anzusehen, sondern darin, dass ausschließlich ein ausdrücklich autorisierter Benutzer einen konkreten Repository-Commit zur Ausführung freigeben kann. Der Kommentartext wird niemals ungeprüft als Shell-Befehl verwendet.

Normale Unit-, Simulations-, Build-, API- und Bare-Probe-Checks bleiben auf GitHub-hosted Runnern. Der lokale Rechner wird nur für ausdrücklich angeforderte Tests benötigt, die die echte Hytale-Runtime und `Assets.zip` brauchen. Ein ausgeschalteter lokaler Runner darf normale PRs und Releases nicht blockieren.

## Bekannte Runtime-Eigenheiten

Beim normalen Shutdown kann Hytale `0.6.8` einen `WorldCrashRecoveryHandler`-/„Reloading crashed world“-Eintrag erzeugen, obwohl anschließend `Shutdown completed!` erscheint und der Prozess mit Exitcode `0` endet. Der Runtime-Probe wertet deshalb den vollständigen Shutdown-Vertrag aus und behandelt diesen isolierten Logeintrag nicht als Civ-Gameplay-Fehler.

## Isolierte Installation mit kontrolliertem Neustart (Phase 2)

Das Hytale-Local-Szenario `deployment` prüft auf Windows `gabe` den Übergang von einer Civ-Installation zur nächsten **zwischen zwei vollständig getrennten Hytale-Serverprozessen**. Die erste Instanz speichert einen geclaimten Bewohner und fährt sauber herunter. Anschließend sichert der Runner die im isolierten Runtime-Verzeichnis liegende Civ-JAR sowie das Civ-Asset-Pack, installiert eine unterscheidbare JAR und das staged Asset-Pack und startet einen zweiten Serverprozess mit derselben World. Der zweite Prozess muss die persistierte Entity inklusive Claim, Beruf, XP und Arbeitsplatz wiederherstellen. Der Versionsunterschied des Probe-JARs ist nur ein zusätzliches ZIP-Metadatenelement, **keine** neue Gameplay-Implementierung. Ein Fehler im Installations-/Verifikationspfad löst Rückkopieren der Sandbox-Installationsdateien aus. Dieses Verfahren greift nicht auf installierte reguläre Mods, das lokale Spielerprofil oder andere laufende Hytale-Prozesse zu.

**Runtime-Nachweis:** `deployment` wurde auf `gabe` mit Commit `44b6ca85d11a48dd8522b4abfc3557b3bb786cb6` erfolgreich ausgeführt (GitHub Actions-Lauf `37907131356`). Der Ablauf protokollierte `HCIV_DEPLOY_BACKUP_CREATED`, `HCIV_DEPLOY_INSTALLED`, `CIV_PERSISTENCE_RESTORED uuid=dab583fa-cb96-3606-be54-b0a2f7a1bd60`, `HCIV_DEPLOY_RESTART_VERIFIED` und `HCIV_DEPLOY_SCENARIO_PASS`. In diesem ursprünglichen Phase-2-Lauf wurde die Rollback-Verzweigung noch nicht ausgelöst; der spätere Phase-3-Test verifiziert den Fehlerfall separat.

## Phase 3/4: Fehlerinjektion und CI-Versionen

`deploymentrollback` erzwingt in der isolierten Runtime nach dem Installieren der Test-JAR einen Fehler; der Runner stellt Plugin-JAR und Civ-Assets aus der zuvor angelegten Sicherung wieder her, vergleicht den JAR-SHA-256-Wert und startet Hytale mit der gespeicherten Civ-Welt erneut. Das Erfolgssignal lautet `HCIV_DEPLOY_ROLLBACK_RUNTIME_PASS`.

`deploymentreal` verwendet statt einer künstlich modifizierten JAR zwei tatsächlich per GitHub Actions CI erstellte Plugin-Artefakte unterschiedlicher Commit-SHAs. Der vertrauenswürdige Autorisierungsjob wählt einen vorherigen erfolgreichen `main`-Build mit gültigem SHA-Artefakt und verlangt für das Zielcommit ebenfalls ein erfolgreiches, nicht abgelaufenes CI-JAR-Artefakt. Der Self-Hosted-Runner installiert die Ausgangsversion ausschließlich in die Sandbox; nach einem sauberen Hytale-Shutdown ersetzt er sie durch die Zielversion und prüft denselben persistierten Civ-Bewohner in einem neuen Prozess. Die Civ-Assets stammen in diesem Szenario weiterhin aus dem getesteten Repository-Stand; unterschiedliche historische Asset-Pack-Versionen werden damit noch nicht getestet.

**Erfolgreiche Rollback-Fehlerinjektion:** Das Szenario `deploymentrollback` wurde mit Commit `2a73e6073f0d6b46e0f70c658303c6ee03e60864` auf `gabe` erfolgreich ausgeführt (Actions-Lauf `37909005000`). Nach absichtlich ausgelöstem Installationsfehler bestätigten `HCIV_DEPLOY_ROLLBACK_RESTORED` und `HCIV_DEPLOY_ROLLBACK_RUNTIME_PASS` den wiederhergestellten JAR-Hash und den erneuten Hytale-Start mit derselben persistierten NPC-UUID.

**Erfolgreicher realer CI-Artefakt-Wechsel:** `deploymentreal` lief auf `gabe` mit Ziel-Testcommit `b188c4ffb10b72fe38cccd6358f335a7fb1b7977` erfolgreich durch (GitHub Actions [37910197002](https://github.com/weidmanngabriel/hytale-civ/actions/runs/37910197002)). Baseline war die echte CI-JAR aus `594db1f379ce9f72521eb7a41afe5a31a898b6fd`, Ziel die echte CI-JAR aus `b188c4ffb10b72fe38cccd6358f335a7fb1b7977`. Die Runtime meldete `HCIV_ARTIFACT_BASELINE_SHA`, `HCIV_ARTIFACT_TARGET_SHA`, `HCIV_DEPLOY_REAL_ARTIFACTS`, `HCIV_DEPLOY_RESTART_VERIFIED` und `HCIV_DEPLOY_SCENARIO_PASS`. Damit sind ein commitgebundener Java-Plugin-Artefaktwechsel und die Übernahme persistierter Civ-NPC-Daten nach regulärem Hytale-Neustart nachgewiesen. Unterschiedliche historische Asset-Pack-Versionen, Produktiv-Deployments und Crash-Restore bleiben ausdrücklich ungeprüft.
