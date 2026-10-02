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

## Vollständige lokale Server-Probe

Der Workflow `.github/workflows/hytale-local.yml` läuft auf einem vertrauenswürdigen Windows-Self-Hosted-Runner mit lokaler Hytale-Installation. Die Pfade werden ausschließlich über Runner-Umgebungsvariablen bereitgestellt:

~~~text
HYTALE_SERVER_JAR
HYTALE_ASSETS_PATH
~~~

Maschinenspezifische Pfade, `HytaleServer.jar` und `Assets.zip` werden nicht committed und nicht als CI-Artefakte veröffentlicht.

Der Test:

1. prüft, dass beide lokalen Dateien vorhanden sind,
2. prüft Java 25,
3. baut das Civ-Plugin,
4. erstellt ein isoliertes Server-Arbeitsverzeichnis,
5. installiert dort nur Civ-Plugin und Civ-Asset-Pack,
6. startet den echten Server mit `--assets <Assets.zip>` und `--auth-mode offline`,
7. lässt den Server über `--boot-command stop` kontrolliert wieder herunterfahren,
8. wertet den Serverlog aus.

Als Erfolg gelten derzeit mindestens folgende Runtime-Signale:

- die Basisassets werden erfolgreich geladen,
- das Civ-Asset-Pack wird geladen,
- `Civilizations:HytaleCiv` wird aktiviert,
- die Default-Welt wird hinzugefügt,
- der Server erreicht `Universe ready!`,
- der Server erreicht den vollständigen Boot-Zustand,
- Civ wird beim Shutdown sauber deaktiviert,
- der Prozess endet mit Exitcode `0`.

Das Civ-Asset-Pack deklariert dafür dieselbe Server-Kompatibilität wie das Java-Plugin: `ServerVersion: ^0.6.0`.

## Was dieser Test beweist

Der Test beweist für die konkret verwendete lokale Hytale-Runtime, dass Civ als echtes Plugin und Asset-Pack gemeinsam mit den Basisassets einen vollständigen Server-Boot bis zur geladenen Default-Welt übersteht und sauber beendet werden kann.

Damit ist insbesondere die frühere Unsicherheit geklärt, ob eine Welt ohne verbundenen Client serverseitig überhaupt bis zum normalen Universe-/World-Start gebracht werden kann: der Startpfad funktioniert headless.

## Was dieser Test nicht beweist

Der Test verbindet keinen Client und führt derzeit keine Spieler- oder NPC-Aktionen aus. Er beweist deshalb ausdrücklich nicht:

- Client-UI oder Kamera-Verhalten,
- Maus-/Interaktions-Dispatch,
- sichtbare Prefab- oder Pfad-Darstellung,
- tatsächliche NPC-Navigation zu einem Ziel,
- einen vollständigen Civ-Gameplay-Zyklus über mehrere Server-Ticks,
- Save/Reload-Persistenz über zwei getrennte Serverstarts.

Solche Verträge benötigen entweder eine erweiterte Server-Probe mit gezielten Test-Hooks oder weiterhin einen manuellen Client-Test.

## Sicherheits- und CI-Grenze

Der Self-Hosted-Runner greift auf lokale lizenzierte Hytale-Dateien zu. Er soll deshalb nicht für beliebigen fremden PR-Code verwendet werden. Der Runtime-Workflow muss auf vertrauenswürdige Branches beziehungsweise bewusst ausgelöste Läufe beschränkt bleiben.

Normale Unit-, Simulations-, Build- und API-Checks bleiben auf GitHub-hosted Runnern. Der lokale Rechner wird nur für Tests benötigt, die die echte Hytale-Runtime und `Assets.zip` brauchen.
