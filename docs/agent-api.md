# Hytale Agent API – erste Ausbaustufe

Ziel ist eine erweiterbare, serverseitige Agent-Steuerung für Hytale und Civ. Dies ist **kein** vollständiger virtueller Hytale-Client: Unterscheidung von native Engine API, Civ-Service und client-only Funktion ist verbindlich.

## Ausführung und Vertrauen

- Das installierte Civ-Plugin registriert \`civagent\` als console-fähige Hytale-Befehlsgruppe. Alle Funktionen laufen im World-Executor der **default world**.
- Die bestehende lokale Command Bridge auf \`127.0.0.1:5523\` stellt nur den Transport. Sie wird nicht ins Netzwerk geöffnet.
- Die bestehende Hytale-Live-Integration nutzt Issue #266; \`/hytale-live runner gabe agent ...\` bzw. einen JSON-Batch-Schritt mit \`command:"agent ..."\` übersetzt **nur** die erlaubten Funktionsformen zu \`civagent\`. Die GitHub-hosted Autorisierung validiert vor dem Windows-Runner; dieser prüft alle Kommandos nochmals.
- Jede native Antwort beginnt mit \`CIVAGENT_RESULT\` und einem JSON-Objekt \`{"action":"...","data":{...}}\`. Fehler beginnen mit \`CIVAGENT_ERROR\`; die Live-Ausführung behandelt auch solche Command-Fehler als gescheitert.
- \`civdev\` für NPCs, Beruf, Navigation und Minenzuweisung bleibt parallel verfügbar; die neue Fassade vermeidet doppelte Gameplay-Implementierung.

## Aktuell verfügbare Befehle

| Konsolenbefehl | Effekt | Seiteneffekt |
|---|---|---|
| \`civagent capabilities\` | implementierte Aktionen und Grenzen als JSON | nein |
| \`civagent players\` | verbundene Spieler mit UUID und Position | nein |
| \`civagent buildings\` | fertige Civ-Gebäude (IDs, Typ, Phase) | nein |
| \`civagent sites\` | aktive Baustellen und Bau-Layer | nein |
| \`civagent block X Y Z\` | Asset-ID und numerische Block-ID einer geladenen Weltposition | nein |
| \`civagent set-block X Y Z BLOCK_ASSET\` | **ein** Block über natives \`BlockOperations.setBlock\` und nachfolgendes Rücklesen | ja |
| \`civagent create-site PLAYER_UUID TYPE X Y Z\` | normale Civ-Baustelle am angezielten Bodenblock; \`TYPE\`: \`mine\`, \`farm\`, \`wheat_field\` | ja |
| \`civagent mine-recover MINE_UUID MODE\` | \`status\`, \`workers\`, \`fronts\`, \`all\` der bestehenden Recovery-Logik | ja außer status |

Alle Koordinaten beziehen sich auf Blöcke der geladenen default world. \`create-site\` verwendet die UUID eines **aktuell verbundenen Spielers**, da die native Prefab-Preview ein \`PlayerRef\` benötigt. Der Pfad deckt bereits das gebäudeeigene Overlap-/Loaded-Chunk-Checking, die normale Civ-Konstruktion und Persistenz ab. Er überspringt **nicht** den Bauablauf: Für die fertige Mine werden Bauarbeiter benötigt, danach können Miner zugewiesen werden.

Die Block-Schreibfunktion verweigert unbekannte Block-Assets, nicht geladene Chunks und geschützte Civ-Gebäude-/Baustellenbereiche. Sie schreibt einzelne Blöcke; ein großflächiger Terrain-Editor oder ein Batch-Rollback ist in dieser Version nicht enthalten. Physik-/Connected-Blocks-Verhalten bleibt eine Runtime-Frage.

## JSON-Batch-Beispiel (ohne Weltänderungen)

Ein Kommentar auf Issue #266:

~~~text
/hytale-live-batch runner gabe
{"version":1,"steps":[{"id":"abilities","action":"command","command":"agent capabilities"},{"id":"players","action":"command","command":"agent players"},{"id":"pause","action":"wait","seconds":2},{"id":"mines","action":"command","command":"mines"},{"id":"check","action":"assert","from":"abilities","contains":"CIVAGENT_RESULT"}]}
~~~

JSON-Resultate der einzelnen Befehle liegen unter \`steps[*].output\` im Workflow-Artefakt. Die tatsächliche Ausgabe der installierten Mod darf nicht mit Fähigkeiten verwechselt werden, die erst im Quellcode implementiert oder in CI gebaut sind.

## Gezielte Testanleitung

1. Die von CI gebaute/releaste **aktuelle Civ-Mod** im eigenen Spiel installieren und das Spiel neu starten, damit \`civagent\` vorhanden ist.
2. Im Spiel einen geladenen Bereich auswählen. Per Live-Batch \`agent capabilities\` und \`agent players\` abfragen; UUID und Position des richtigen Spielers notieren.
3. Read-only \`agent block X Y Z\` ausführen; bekannte geladene Blockposition verwenden.
4. Für einen neuen Bauplatz \`agent create-site PLAYER_UUID mine X Y Z\` auf einer freien Fläche ausführen. Das Ergebnis enthält die Baustellen-ID; \`agent sites\` muss sie wiederfinden.
5. Bauarbeiter über den normalen Civ-Flow zuweisen. Erst nach Fertigstellung wird die Mine unter \`civdev mines\` registriert.
6. Miner über \`civdev spawn\`, \`civdev profession\` und \`civdev assign-mine\` zuweisen und tatsächlichen Arbeitsfortschritt beobachten.
7. Fehler über \`CIVAGENT_ERROR\` und den Ergebnisbericht des korrelierten Live-Runs auswerten.

**Nicht als erfolgreich melden:** reine Befehlsakzeptanz ohne neu vorhandenes Bauwerk, Miner-Zuweisung ohne physische Arbeit oder einen bloß abgeschlossenen GitHub-Workflow ohne \`HCIV_LIVE_RESULT\`/\`HCIV_LIVE_BATCH_RESULT\`.

## Noch nicht implementiert

Die universelle Agent API ist ein langfristiger Ausbau. Noch fehlen unter anderem Inventar- und Item-Aktionen, Teleportation, Prefab-Asset-Erkundung, generische Entity-Änderungen, Teilgebiet-Terrainänderungen, Client-Kamera-/UI-Bedienung und dynamische Variablen in Live-Batches. Solche Aktionen erfordern separat verifizierte native APIs, Parametervalidierung und Tests; nicht durch rohe Konsolen- oder PowerShell-Ausführung ersetzen.
