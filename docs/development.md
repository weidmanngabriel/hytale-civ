# Entwicklungsablauf

## Voraussetzungen

- JDK 25
- Git
- Keine globale Gradle-Installation; den Wrapper verwenden.

## Lokaler Ablauf

~~~text
Code/Assets ändern
    ↓
./gradlew test
    ↓
./gradlew build
    ↓
optional: ./gradlew deployToHytale
    ↓
in Hytale testen
~~~

Unter Windows <code>gradlew.bat</code> verwenden.

## Simulation Viewer

Der Hytale-unabhängige Desktop-Viewer startet mit:

~~~bash
./gradlew simulationViewer
~~~

Unter Windows entsprechend:

~~~bat
gradlew.bat simulationViewer
~~~

Der Viewer startet ein kleines Demo-Szenario mit Holzfäller, Bauarbeiter und Farmer. Linksklick wählt einen Bewohner aus, Rechtsklick setzt für den ausgewählten Bewohner ein manuelles Ziel. <code>Step</code> führt genau einen 50-ms-Simulationsschritt aus; <code>x1</code>, <code>x10</code>, <code>x100</code> und <code>Max</code> beschleunigen nur die Headless-Simulation und verändern keine Gameplayregeln. Rechts werden aktueller Bewohnerzustand und deterministische <code>SimulationMetrics</code> angezeigt.

Der Viewer ersetzt keine automatisierten Tests. Die Szenario-Tests laufen als normale JUnit-Tests mit <code>./gradlew test</code>. Im GitHub-Workflow werden sie für jeden Pull Request und anschließend erneut bei Pushes auf <code>main</code> beziehungsweise auf <code>v*</code>-Tags ausgeführt.

## Hytale-Abhängigkeit

Release-Repository: <code>https://maven.hytale.com/release</code>

Abhängigkeit: <code>com.hypixel.hytale:Server</code>

<code>hytaleServerVersion</code> wird zentral und bewusst auf eine konkrete Version in <code>gradle.properties</code> festgesetzt. Aktuell ist das <code>0.6.8</code>. Dadurch kann ein neuer Hytale-Release den Build nicht unbemerkt verändern. Ein Upgrade erfolgt durch eine ausdrückliche Änderung dieser Eigenschaft und anschließende Build-/API-Prüfung. Vor Änderungen an der Hytale-API-Nutzung oder am Dependency-Selektor muss die aktuelle offizielle Dokumentation geprüft werden.

## Hytale-API-Inspektion

Für Hytale-spezifische Entwicklungsarbeit ist die im Chat-Projekt hinterlegte `HytaleServer.jar` der bevorzugte erste Prüfweg. Sie entspricht der fest gepinnten Hytale-Version und wird direkt als Binärdatei untersucht; ein vorab erzeugter API-Snapshot ist dafür nicht nötig.

Typischer Ablauf in einer Coding-Agent-Sitzung:

~~~bash
jar tf HytaleServer.jar | grep CommandBuffer
javap -classpath HytaleServer.jar -public com.hypixel.hytale.component.CommandBuffer
javap -classpath HytaleServer.jar -c -p com.hypixel.hytale.component.CommandBuffer
~~~

Mit der Klassenliste lassen sich einfache Namen auf vollständige Klassennamen abbilden. `javap` liefert Signaturen on demand; `javap -c -p` oder ein gleichwertiges Classfile-/Bytecode-Werkzeug erlaubt bei Bedarf eine tiefere Einzelklassenanalyse. Die JAR selbst wird nicht ins Repository eingecheckt und nicht als CI-Artefakt veröffentlicht.

Signaturen und Bytecode belegen nur, welche Klassen, Member und Implementierungsdetails in genau dieser JAR vorhanden sind. Sie beweisen keine Runtime-Semantik. Client-Verhalten, Lifecycle, Event-Dispatch und vergleichbare Engine-Eigenschaften müssen weiterhin gezielt im Spiel beziehungsweise über offizielle Hytale-Dokumentation verifiziert werden.

## Asset Pack

Vom Ersteller bearbeitbare Hytale-Assets liegen unter:

~~~text
asset-pack/
~~~

Das Verzeichnis ist ein eigenständiges Hytale Asset Pack und enthält deshalb eine eigene <code>manifest.json</code>. Java-/Plugin-Ressourcen bleiben in <code>src/main/resources</code>; das Plugin-Manifest darf nicht aus dem JAR verschoben werden.

<code>./gradlew build</code> erzeugt ein Distributions-ZIP unter:

~~~text
build/distributions/hytale-civ-<version>-bundle.zip
~~~

Das ZIP enthält:

~~~text
hytale-civ.jar
hytale-civ-assets/
~~~

Das äußere ZIP dient nur als Release- beziehungsweise Download-Container. Das Asset-Verzeichnis bleibt vom JAR getrennt, damit installierte Assets bearbeitet oder ersetzt werden können, ohne Java neu zu kompilieren.

Gameplay-Daten sollen nur dann ins Asset Pack verschoben werden, wenn dafür ein konkreter Hytale-Asset-Typ benötigt wird. Core-Simulationsregeln und Domänenzustand bleiben in der bestehenden Java-Architektur, solange ein späteres Feature keine andere Grenze begründet.

## Prefabs im Spiel bearbeiten

Hytales Prefab-Editor kann Prefabs aus Asset Packs laden und wieder in ein ausgewähltes Asset Pack speichern. Während der Entwicklung soll deshalb das Repository-Verzeichnis <code>asset-pack/</code> die maßgebliche bearbeitbare Quelle sein und nicht eine separat deployte Kopie.

Empfohlenes lokales Setup:

~~~text
Git-Repository
└── asset-pack/                    ← maßgebliche bearbeitbare Dateien

Hytale-Mods-Verzeichnis
└── hytale-civ-assets              ← Verzeichnislink/Junction auf repo asset-pack/
~~~

Damit sieht der Bearbeitungsablauf so aus:

~~~text
Hytale-Prefab-Editor öffnen
    ↓
Civilizations/Farm/Farm_01 laden
    ↓
Blöcke und Creator-Marker bearbeiten
    ↓
in HytaleCivAssets speichern
    ↓
asset-pack/ im Git-Working-Tree ändert sich direkt
    ↓
Diff prüfen, testen, committen
~~~

<code>deployToHytale</code> darf beim Bearbeiten von Prefabs im Spiel nicht als maßgebliche Quelle verwendet werden. Dieser Task kopiert das Asset Pack absichtlich in das Mods-Verzeichnis. Spätere Änderungen im Spiel würden dann die installierte Kopie statt das Repository verändern. Deployment-Kopien sind für Laufzeittests gedacht; für Round-Trip-Prefab-Bearbeitung soll ein Verzeichnislink beziehungsweise eine Junction verwendet werden.

Beispielhafte Entwicklungslinks; die Pfade sind Platzhalter und dürfen nicht committed werden.

Windows Command Prompt mit einer Directory Junction:

~~~bat
mklink /J "%APPDATA%\Hytale\UserData\Mods\hytale-civ-assets" "C:\path\to\hytale-civ\asset-pack"
~~~

macOS/Linux:

~~~bash
ln -s /path/to/hytale-civ/asset-pack /path/to/Hytale/UserData/Mods/hytale-civ-assets
~~~

Falls bereits ein normales deploytes Verzeichnis <code>hytale-civ-assets</code> existiert, muss diese Kopie vor dem Erstellen des Links entfernt oder umbenannt werden. Maschinenspezifische Hytale-Pfade oder Links dürfen niemals committed werden.

## Lokales Deployment

Für direkt vom lokalen Coding-Agent gesteuerte Experimente gibt es den optionalen [lokalen Hytale-MCP-Server](local-mcp.md). Die Civ-Mod stellt in einer laufenden Session eine localhost-only Command Bridge bereit; `hytale_command` führt native Serverbefehle aus und liefert deren Command-Ausgabe direkt zurück. Der frühere `/civmcp on`-/Attach-Lifecycle wurde entfernt. Build, Deployment und Prozesssteuerung des MCP bleiben auf dessen eigene isolierte Runtime begrenzt. Das Werkzeug ist unabhängig vom Self-Hosted-Runner und kein Merge-Gate.

<code>HYTALE_MODS_DIR</code> setzen und ausführen:

~~~bash
./gradlew deployToHytale
~~~

Alternativ:

~~~bash
./gradlew deployToHytale -PhytaleModsDir=/path/to/mods
~~~

Der Task kopiert sowohl das Plugin-JAR als auch <code>hytale-civ-assets/</code> in das konfigurierte Mods-Verzeichnis. Normale Tests und Builds benötigen diese Einstellung nicht.

## Hytale Local / Runtime-Tests

Hytale Local bezeichnet im Projekt die gezielte Ausführung echter Hytale-Runtime-Szenarien auf dem lokalen Windows-Self-Hosted-Runner. Es ergänzt Unit-, Simulations- und Adaptertests dort, wo eine Frage nur in der echten Engine zuverlässig beantwortet werden kann, zum Beispiel bei Lifecycle, Event-Dispatch, Navigation, Client-/Server-Interaktion, Asset-Verhalten oder anderen runtime-abhängigen Hytale-Verträgen.

Hytale Local ist bewusst **kein allgemeiner Standard-Testschritt** und kein Merge- oder Release-Gate. Es soll eingesetzt werden, wenn ein konkreter Runtime-Test eine relevante Unsicherheit reduziert oder eine schwer reproduzierbare Engine-Interaktion gezielt diagnostiziert. Reine Core-Logik, Dokumentationsänderungen und Verhalten, das zuverlässig unterhalb der Hytale-Grenze getestet werden kann, gehören weiterhin in die normalen automatisierten Tests.

Die Nutzung ist **opt-in pro Chat**: Vor der ersten Nutzung von Hytale Local in einem Chat fragt der Coding-Agent einmal, ob der lokale Runner in diesem Chat verwendet werden darf, und beschreibt kurz, welche offene Frage der vorgeschlagene Lauf klären soll. Nach ausdrücklicher Zustimmung gilt diese Erlaubnis für alle weiteren Aufgaben im selben Chat; eine erneute Nachfrage ist nicht nötig. In einem neuen Chat muss erneut gefragt werden. Ausnahme: Wenn die aktuelle Aufgabe selbst den Hytale-Local-Runner, Workflow, Harness, die Szenario-Registry oder ein Runtime-Szenario entwickelt, verändert, debuggt oder validiert, gilt die Ausführung der dafür nötigen fokussierten Hytale-Local-Läufe als implizit freigegeben. Es soll weiterhin möglichst das kleinste passende Szenario verwendet werden; <code>all</code> ist für echte breite Regressionen reserviert.

Technisch wird der Lauf über die feste GitHub-Issue <code>#126 Hytale Runtime Test Requests</code> angefordert. Der Befehl referenziert die gewünschten Szenarien und den exakt zu testenden Commit. GitHub prüft die Anfrage zuerst auf einem GitHub-hosted Runner; erst eine autorisierte Anfrage erreicht den lokalen Self-Hosted-Runner. Dort wird genau dieser Commit ausgecheckt. Wenn ein vollständig erfolgreicher normaler CI-Lauf bereits ein SHA-adressiertes Plugin-Artefakt für exakt diesen Commit veröffentlicht hat, wird dieses getestete JAR wiederverwendet. Andernfalls führt der lokale Runner als Fallback weiterhin <code>test + jar</code> aus. Anschließend laufen die erlaubten Szenarien aus <code>scripts/hytale-runtime-scenarios.json</code> in isolierten Hytale-Runtime-Verzeichnissen. Die Ergebnisse bleiben dadurch einem konkreten Commit und Szenario zuordenbar.

## Integration von Änderungen

Jede Änderung wird auf einem temporären Branch umgesetzt. Zwischen-Commits sind während der Arbeit erlaubt.

Vor der Integration:

1. Vorgesehenen Code, Tests und betroffene Dokumentation auf dem temporären Branch fertigstellen.
2. <code>./gradlew test</code> und <code>./gradlew build</code> ausführen, sofern die lokale Umgebung dies erlaubt.
3. Einen Pull Request gegen <code>main</code> öffnen, damit GitHub Actions die finale Version prüft.
4. Wenn die Validierung Änderungen verlangt, diese auf denselben temporären Branch pushen und erneut validieren.
5. Nur den final validierten Branch per Squash-Merge integrieren, damit genau ein sinnvoller Commit für die Änderung auf <code>main</code> verbleibt.
6. Danach den Workflow auf <code>main</code> sowie das erzeugte Pre-Release prüfen.

Lokale Hytale-Runtime-Szenarien sind in dieser Entwicklungsphase **optional**. Sie dienen zur Diagnose und zur gezielten Verifikation unsicherer Engine-Verträge, sind aber weder Voraussetzung für einen Merge noch für einen Release. Ein fehlender oder nicht ausgeführter lokaler Runtime-Lauf blockiert eine Änderung deshalb nicht. Wenn ein solcher Lauf nach ausdrücklicher oder gemäß obiger Ausnahme impliziter Zustimmung ausgeführt wird, soll weiterhin möglichst nur das kleinste relevante Szenario verwendet werden.

Nach dem final validierten Zustand dürfen keine zusätzlichen Änderungen auf denselben Branch gepusht und anschließend ungeprüft gemerged werden. Ein Fix nach dem Merge beginnt auf einem neuen temporären Branch und wird ein eigener Squash-Commit.

## GitHub-Workflow

Pull Requests führen Tests und einen vollständigen Java-25-Gradle-Build für den exakten Quell-Commit des Feature-Branches aus. Normale Pushes auf Nicht-Main-Branches starten keinen zweiten, doppelten Workflow. Pushes auf <code>main</code> und Tags mit Präfix <code>v</code> führen weiterhin CI aus, weil sie Development- und Stable-Releases erzeugen. Wird derselbe Pull Request beziehungsweise Branch mit einem neueren Commit aktualisiert, bricht GitHub Actions den älteren laufenden Workflow ab, sodass nur die neueste Revision weiterläuft. Zusätzlich zum Release-Bundle veröffentlicht der Build das Plugin-JAR als SHA-adressiertes Actions-Artefakt <code>hytale-runtime-plugin-&lt;40-stelliger-commit-sha&gt;</code> für 14 Tage. Fehlgeschlagene Testberichte werden zur Analyse ebenfalls hochgeladen.

Echte Hytale-Runtime-Tests werden unabhängig von einem Pull Request über die feste GitHub-Issue <code>#126 Hytale Runtime Test Requests</code> angefordert. Sie sind ein optionales Entwicklerwerkzeug und kein Bestandteil der verpflichtenden PR- oder Release-Gates. Ein Kommentar hat das Format <code>/hytale-test &lt;szenarien&gt; &lt;commit-sha&gt;</code>. Mehrere Szenarien werden mit <code>-</code> getrennt; Szenarionamen selbst enthalten deshalb keine Bindestriche und zusammengesetzte Begriffe werden zusammengeschrieben. Bis zu acht Szenarien können explizit angegeben werden. <code>all</code> steht alleine und expandiert auf alle Szenarien aus <code>scripts/hytale-runtime-scenarios.json</code>.

Beispiele:

~~~text
/hytale-test woodcutter ee453d7
/hytale-test woodcutter-persistence ee453d7
/hytale-test all ee453d7
~~~

Der angegebene Commit darf jeder Commit im eigenen Repository sein, also auch ein Spike ohne Pull Request. Ein 7- bis 40-stelliger hexadezimaler SHA wird über die GitHub-API auf den vollständigen 40-stelligen Commit-SHA aufgelöst und muss mit dem angegebenen Präfix übereinstimmen.

Der Workflow <code>.github/workflows/hytale-local.yml</code> reagiert auf neu erstellte <code>issue_comment</code>-Events. Der Workflow selbst liegt auf dem vertrauenswürdigen Default-Branch. Bevor ein Self-Hosted-Job startet, prüft ein GitHub-hosted Autorisierungsjob, dass der Kommentar aus Issue <code>#126</code> stammt, Event-Aktor, Kommentarautor und Sender ausdrücklich erlaubt sind, die Befehlssyntax gültig ist, der Commit im eigenen Repository auflösbar ist und jedes angeforderte Szenario in der Registry des exakt zu testenden Commits steht. Aktuell ist <code>weidmanngabriel</code> der einzige erlaubte Anforderer. Unbekannte oder doppelte Szenarien sowie mehr als acht explizite Szenarien erreichen den Self-Hosted-Runner nicht. Derselbe Autorisierungsjob sucht zusätzlich nach einem vollständig erfolgreichen <code>CI</code>-Workflow-Lauf für genau diesen SHA und akzeptiert nur das nicht abgelaufene Artefakt mit dem erwarteten SHA-Namen.

Der Windows-Self-Hosted-Runner erhält nur <code>contents: read</code> und <code>actions: read</code>, checkt ausschließlich den bereits autorisierten exakten Commit-SHA mit <code>persist-credentials: false</code> aus und verifiziert den Checkout erneut. Ist das getestete SHA-Artefakt vorhanden, lädt der Runner das Plugin-JAR direkt nach <code>build/libs</code> und überspringt den lokalen Gradle-Test-/Build-Schritt. Fehlt das Artefakt, wird weiterhin lokal <code>gradlew.bat test jar --no-daemon</code> als Fallback ausgeführt. <code>scripts/hytale-runtime-tests.ps1</code> führt danach die ausgewählten Runtime-Szenarien aus; jedes Szenario erhält ein eigenes isoliertes Hytale-Runtime-Verzeichnis. Die Registry <code>scripts/hytale-runtime-scenarios.json</code> ist die gemeinsame Allowlist für Controller und Harness. Ein neuer Runtime-Vertrag kann registriert werden, wenn er als Entwicklungs- oder Diagnosehilfe einen konkreten Nutzen hat und der zugehörige Harness-Code im selben Commit vorhanden ist.

Der Self-Hosted-Runner benötigt weiterhin die lokale Hytale-Installation sowie die lizenzierten Basisassets. Die Runtime-Logs nennen den exakten Commit-SHA, den anfordernden Benutzer und die aufgelösten Szenarien. Nach dem Runtime-Schritt lädt der Workflow die getrennten Standardausgabe-, Fehlerausgabe- und Statusdateien als Actions-Artefakt <code>hytale-runtime-logs-&lt;run-id&gt;-&lt;attempt&gt;</code> hoch; durch <code>if: always()</code> geschieht das auch bei einem fehlgeschlagenen oder abgebrochenen Szenario, sofern die Dateien bereits angelegt wurden. Die Artefakte werden 14 Tage aufbewahrt. Normale PRs und Releases hängen nicht von der Verfügbarkeit des lokalen Runners ab; er wird nur durch einen erfolgreich autorisierten Befehl in Issue <code>#126</code> belegt.

Jeder erfolgreiche Push auf <code>main</code> erzeugt ein SemVer-kompatibles Development-Pre-Release. Die Basisversion stammt aus <code>projectVersion</code> in <code>gradle.properties</code>. Der Workflow entfernt <code>-SNAPSHOT</code> und hängt die GitHub-Actions-Run-Nummer an:

~~~text
projectVersion=0.1.0-SNAPSHOT
→ v0.1.0-dev.42
~~~

Dieselbe aufgelöste Version wird an Gradle übergeben, damit Build-Metadaten von JAR/Distribution und GitHub Release dieselbe Projektversion verwenden. Das herunterladbare Release-Asset heißt dann:

~~~text
hytale-civ-0.1.0-dev.42.zip
~~~

Der Commit-SHA bleibt zur Nachverfolgbarkeit in den Release Notes, wird aber nicht mehr als Release-Version verwendet. Dadurch bleiben Development-Releases natürlich sortierbar und stabile Versionen wie <code>v0.1.0</code> bilden das Ende der jeweiligen SemVer-Linie.

Die Versionsregeln sind:
- neues Feature: <code>MINOR</code> erhöhen und <code>PATCH</code> auf 0 setzen, z. B. <code>0.1.0 → 0.2.0</code>;
- Bugfix: <code>PATCH</code> erhöhen, z. B. <code>0.2.0 → 0.2.1</code>;
- Zwischenstände: automatisch als <code>X.Y.Z-dev.&lt;run&gt;</code>;
- erster stabiler Hauptrelease: <code>1.0.0</code>.

Vor Beginn der Entwicklung für die nächste stabile Versionslinie muss <code>projectVersion</code> entsprechend erhöht werden.

Die GitHub-Release-Beschreibung enthält den Betreff des veröffentlichten Commits. Da abgeschlossene Projektänderungen per Squash-Merge integriert werden, erhält jedes Main-Release damit eine einzeilige Zusammenfassung der jeweiligen Änderung.

Für stabile Versionen ein SemVer-Tag pushen, zum Beispiel:

~~~bash
git tag v0.1.0
git push origin v0.1.0
~~~

Ein erfolgreicher Tag-Build erzeugt ein normales GitHub Release mit demselben ZIP-Bundle als Anhang. Tags mit Präfix <code>v</code> gelten als stabile Releases; normale Builds auf <code>main</code> bleiben Pre-Releases.

Ein erneuter Lauf eines Release-Jobs ist idempotent: Existiert das Release bereits, aktualisiert der Workflow die kurze Release-Notiz und ersetzt das ZIP-Asset, statt ein Duplikat anzulegen.

## Browser-Viewer und Branch-Aufzeichnungen

Einmalig unter **Settings → Pages → Build and deployment → Source: GitHub Actions** wählen. Die Site liegt unter `https://weidmanngabriel.github.io/hytale-civ/`.

Auf relevanten Branch-Pushes führt `.github/workflows/simulation-recordings.yml` Java-Tests und `exportSimulationRecordings` aus. Der Export wird auch bei fehlgeschlagenen Tests versucht; ein abgebrochenes Szenario liefert seinen bis dahin aufgezeichneten Zustand und den Fehler. Ein nicht kompilierbarer Stand hat keinen Replay. `Simulation Pages` veröffentlicht nach Abschluss einen vollständigen Katalog aus vorhandenen internen Artefakten. Es entstehen keine Ergebnis-Commits. Ein Actions-Lauf kann mehrere Minuten dauern; ein Quellcode-Push ist noch keine abgeschlossene Veröffentlichung.

Im Viewer zuerst Branch, dann Lauf und Szenario wählen. Das Test-Badge bezeichnet die Java-Tests dieses Recording-Laufs, nicht sämtliche CI-/Runtime-Prüfungen. Commit-Link und Actions-Link machen den Stand überprüfbar. Für die erste Version werden höchstens drei Läufe pro Branch und insgesamt 40 Läufe innerhalb von 30 Tagen angeboten. Gelöschte/abgelaufene Artefakte sind nicht dauerhaft wiederherstellbar. Unter Actions kann `Simulation Recordings` auf einem Branch und `Simulation Pages` auf `main` manuell erneut gestartet werden.

Lokal:

```sh
./gradlew exportSimulationRecordings
npm ci --prefix web-viewer
npm test --prefix web-viewer
npm run dev --prefix web-viewer
```

Unter Windows lautet der erste Befehl `gradlew.bat exportSimulationRecordings`. Im lokalen Viewer kann eine einzelne Datei aus `build/simulation-recordings/*.json` geöffnet werden. Lokale Dateien haben keinen bestätigten Commit-/CI-Status. Für eine statische Auslieferung dient `npm run build --prefix web-viewer`; die Site liegt dann in `web-viewer/dist/`.

Vieweränderungen werden lokal geprüft und erst nach Integration in `main` auf der gemeinsamen Site veröffentlicht. Entwicklungsbranches liefern Daten für den stabilen Viewer. Lizenzierte Hytale-Basisassets werden nicht veröffentlicht. Der Hytale-Local-Runner wird für diesen Workflow nicht verwendet.